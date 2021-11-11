package com.github.hoshikurama.ticketmanager.database.impl

import com.github.hoshikurama.ticketmanager.TMLocale
import com.github.hoshikurama.ticketmanager.database.Database
import com.github.hoshikurama.ticketmanager.database.DatabaseBuilders
import com.github.hoshikurama.ticketmanager.database.Result
import com.github.hoshikurama.ticketmanager.database.SearchConstraint
import com.github.hoshikurama.ticketmanager.misc.*
import com.github.hoshikurama.ticketmanager.ticket.BasicTicket
import com.github.hoshikurama.ticketmanager.ticket.BasicTicketImpl
import com.github.hoshikurama.ticketmanager.ticket.FullTicket
import com.github.hoshikurama.ticketmanager.ticket.plus
import com.github.jasync.sql.db.ConnectionPoolConfiguration
import com.github.jasync.sql.db.RowData
import com.github.jasync.sql.db.SuspendingConnection
import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.mysql.MySQLConnectionBuilder
import com.github.jasync.sql.db.mysql.MySQLQueryResult
import com.github.jasync.sql.db.util.ExecutorServiceUtils
import kotlinx.coroutines.*
import java.time.Instant
import java.util.*
import java.util.concurrent.Executor

class MySQL(
    host: String,
    port: String,
    dbName: String,
    username: String,
    password: String,
    private val asyncDispatcher: CoroutineDispatcher = Dispatchers.Default,
    asyncExecutor: Executor = ExecutorServiceUtils.CommonPool,
) : Database {

    private val asyncScope: CoroutineScope
        get() = CoroutineScope(asyncDispatcher)

    private val connectionPool = MySQLConnectionBuilder.createConnectionPool(
        ConnectionPoolConfiguration(
            host = host,
            port = port.toInt(),
            database = dbName,
            username = username,
            password = password,
            coroutineDispatcher = asyncDispatcher,
            executionContext = asyncExecutor,
        )
    )
    private val suspendingCon: SuspendingConnection
        get() = connectionPool.asSuspending

    override val type = Database.Type.MYSQL

    override suspend fun setAssignment(ticketID: Int, assignment: String?) {
        suspendingCon.sendPreparedStatement("UPDATE TicketManager_V4_Tickets SET ASSIGNED_TO = ? WHERE ID = $ticketID;", listOf(assignment))
    }

    override suspend fun setCreatorStatusUpdate(ticketID: Int, status: Boolean) {
        suspendingCon.sendPreparedStatement("UPDATE TicketManager_V4_Tickets SET STATUS_UPDATE_FOR_CREATOR = ? WHERE ID = $ticketID;", listOf(status))
    }

    override suspend fun setPriority(ticketID: Int, priority: BasicTicket.Priority) {
        suspendingCon.sendPreparedStatement("UPDATE TicketManager_V4_Tickets SET PRIORITY = ? WHERE ID = $ticketID;", listOf(priority.level))
    }

    override suspend fun setStatus(ticketID: Int, status: BasicTicket.Status) {
        suspendingCon.sendPreparedStatement("UPDATE TicketManager_V4_Tickets SET STATUS = ? WHERE ID = $ticketID;", listOf(status.name))
    }

    override suspend fun insertAction(id: Int, action: FullTicket.Action) {
        suspendingCon.sendPreparedStatement(
            query = "INSERT INTO TicketManager_V4_Actions (TICKET_ID,ACTION_TYPE,CREATOR_UUID,MESSAGE,TIMESTAMP) VALUES (?,?,?,?,?);",
            listOf(
                id,
                action.type.name,
                action.user?.toString(),
                action.message,
                action.timestamp,
            )
        )
    }

    override suspend fun insertTicket(fullTicket: FullTicket): Int = coroutineScope {
        val newID = (suspendingCon.sendPreparedStatement(
            query = "INSERT INTO TicketManager_V4_Tickets (CREATOR_UUID, PRIORITY, STATUS, ASSIGNED_TO, STATUS_UPDATE_FOR_CREATOR, LOCATION) VALUES (?,?,?,?,?,?);",
            listOf(
                fullTicket.creatorUUID?.toString(),
                fullTicket.priority.level,
                fullTicket.status.name,
                fullTicket.assignedTo,
                fullTicket.creatorStatusUpdate,
                fullTicket.location?.toString()
            )
        ) as MySQLQueryResult).lastInsertId.toInt()

        asyncScope.launch {
            fullTicket.actions.forEach {
                insertAction(newID, it)
            }
        }

        return@coroutineScope newID
    }

    override suspend fun getBasicTicketsOrNull(ids: List<Int>): List<BasicTicket>? {
        val idsSQL = ids.joinToString(", ") { "$it" }

        return suspendingCon.sendQuery("SELECT * FROM TicketManager_V4_Tickets WHERE ID IN ($idsSQL);")
            .rows
            .pMap { it.toBasicTicket() }
            .run { if (size == 0) null else this }
    }

    override suspend fun getFullTickets(basicTickets: List<BasicTicket>): List<FullTicket> {
        return basicTickets.pMap {
            it + getActions(it.id)
        }
    }

    override suspend fun getOpenTickets(page: Int, pageSize: Int): Result<BasicTicket> {
        return getTicketsFilteredBy(page, pageSize, "SELECT * FROM TicketManager_V4_Tickets WHERE STATUS = ?;", listOf(BasicTicket.Status.OPEN.name))
    }

    override suspend fun getOpenTicketsAssignedTo(
        page: Int,
        pageSize: Int,
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): Result<BasicTicket> {
        val groupsSQLStatement = unfixedGroupAssignment.joinToString(" OR ") { "ASSIGNED_TO = ?" }
        val groupsFixed = unfixedGroupAssignment.map { "::$it" }

        return getTicketsFilteredBy(page, pageSize, "SELECT * FROM TicketManager_V4_Tickets WHERE STATUS = ? AND ($groupsSQLStatement);", listOf(BasicTicket.Status.OPEN.name) + groupsFixed)
    }

    override suspend fun getOpenTicketsNotAssigned(page: Int, pageSize: Int): Result<BasicTicket> {
        return getTicketsFilteredBy(page, pageSize, "SELECT * FROM TicketManager_V4_Tickets WHERE STATUS = ? AND ASSIGNED_TO IS NULL", listOf(BasicTicket.Status.OPEN.name))
    }

    private suspend fun getTicketsFilteredBy(page: Int, pageSize: Int, preparedQuery: String, values: List<Any?>): Result<BasicTicket> {
        val totalSize: Int
        val totalPages: Int

        val results = suspendingCon.sendPreparedStatement(preparedQuery, values)
            .rows
            .pMap { it.toBasicTicket() }
            .sortedWith(compareByDescending<BasicTicket> { it.priority.level }.thenByDescending { it.id })
            .apply { totalSize = count() }
            .run { if (pageSize == 0) listOf(this) else chunked(pageSize) }
            .apply { totalPages = count() }

        val fixedPage = when {
            totalPages == 0 || page < 1 -> 1
            page in 1..totalPages -> page
            else -> totalPages
        }

        return Result(
            filteredResults = results.getOrElse(fixedPage-1) { listOf() },
            totalPages = totalPages,
            totalResults = totalSize,
            returnedPage = fixedPage,
        )
    }

    override suspend fun massCloseTickets(lowerBound: Int, upperBound: Int, actor: UUID?) {
        asyncScope.launch {
            val rows = suspendingCon.sendPreparedStatement("SELECT ID FROM TicketManager_V4_Tickets WHERE STATUS = ? AND ID BETWEEN $lowerBound AND $upperBound;", listOf(BasicTicket.Status.OPEN.name))
                .rows
                .pMap { it.getInt(0)!! }

            // Sets ticket status
            launch {
                val idString = rows.joinToString(", ")
                suspendingCon.sendPreparedStatement(
                    query = "UPDATE TicketManager_V4_Tickets SET STATUS = ? WHERE ID IN ($idString);",
                    values = listOf(BasicTicket.Status.CLOSED.name)
                )
            }

            // Adds to Actions Table
            rows.pForEach {
                insertAction(
                    action = FullTicket.Action(
                        type = FullTicket.Action.Type.MASS_CLOSE,
                        user = actor,
                        message = null,
                        timestamp = Instant.now().epochSecond
                    ),
                    id = it
                )
            }
        }
    }

    override suspend fun countOpenTickets(): Int {
        return suspendingCon.sendPreparedStatement("SELECT COUNT(*) FROM TicketManager_V4_Tickets WHERE STATUS = ?;", listOf(BasicTicket.Status.OPEN))
            .rows
            .map { it.getInt(1)!! }
            .run { first() }
    }

    override suspend fun countOpenTicketsAssignedTo(assignment: String, unfixedGroupAssignment: List<String>): Int {
        val groupsSQLStatement = unfixedGroupAssignment.joinToString(" OR ") { "ASSIGNED_TO = ?" }
        val groupsFixed = unfixedGroupAssignment.map { "::$it" }

        return suspendingCon.sendPreparedStatement("SELECT COUNT(*) FROM TicketManager_V4_Tickets WHERE STATUS = ? AND ($groupsSQLStatement);", listOf(BasicTicket.Status.OPEN.name) + groupsFixed)
            .rows
            .map { it.getInt(1)!! }
            .run { first() }
    }

    override suspend fun searchDatabase(
        locale: TMLocale,
        constraints: SearchConstraint,
        page: Int,
        pageSize: Int
    ): Result<FullTicket> {
        val args = mutableListOf<Any?>()
        val searches = mutableListOf<String>()
        val functions = mutableListOf<FullTicketPredicate>()

        fun addToCorrectLocations(value: String?, field: String) = value
            ?.apply(args::add)
            ?.let { "$field = ?" }
            ?.apply(searches::add)
            ?: "$field IS NULL".apply(searches::add)

        constraints.run {
            // Main Search Query Arguments
            creator?.run { addToCorrectLocations(value?.toString(), "CREATOR_UUID") }
            assigned?.run { addToCorrectLocations(value, "ASSIGNED_TO") }
            priority?.run { addToCorrectLocations(value.name, "PRIORITY") }
            status?.run { addToCorrectLocations(value.name, "STATUS") }
            closedBy?.run {
                var query = "ID IN (SELECT DISTINCT TICKET_ID FROM TicketManager_V4_Actions WHERE (ACTION_TYPE = ? OR ACTION_TYPE = ?) AND CREATOR_UUID "
                args.add(FullTicket.Action.Type.CLOSE)
                args.add(FullTicket.Action.Type.MASS_CLOSE)

                value?.toString()
                    ?.apply { query += "= ?)"}
                    ?.apply(args::add)
                    ?: kotlin.run { query += "IS NULL)" }
            }
            world?.value?.run {
                "$this%".apply(args::add)
                "ID LIKE ?".apply(searches::add)
            }

            // Functional Search
            lastClosedBy?.run {{ t: FullTicket -> t.actions.lastOrNull { it.type == FullTicket.Action.Type.CLOSE || it.type == FullTicket.Action.Type.MASS_CLOSE }?.run { user == value } ?: false }}?.apply(functions::add)
            creationTime?.run {{ t: FullTicket -> t.actions[0].timestamp >= value}}?.apply(functions::add)
            keywords?.run {{ t: FullTicket ->
                val comments = t.actions
                    .filter { it.type == FullTicket.Action.Type.OPEN || it.type == FullTicket.Action.Type.COMMENT }
                    .map { it.message!! }
                value.map { w -> comments.any { it.lowercase().contains(w.lowercase()) } }
                    .all { it }
            }}?.apply(functions::add)
        }

        // Builds composed function
        val combinedFunction = if (functions.isNotEmpty()) { t: FullTicket -> functions.all { it(t) }}
        else { _: FullTicket -> true }

        // Builds final search string
        var searchString = "SELECT * FROM TicketManager_V4_Tickets"
        if (searches.isNotEmpty())
            searchString += "WHERE ${searches.joinToString(" AND ")}"

        // Searches
        val totalSize: Int
        val totalPages: Int
        val results = suspendingCon.sendPreparedStatement("$searchString;", args).rows
            .pMap { it.toBasicTicket() }
            .run { getFullTickets(this) }
            .pFilter { combinedFunction(it) }
            .sortedWith(compareByDescending { it.id})
            .apply { totalSize = count() }
            .run { if (!equals(0)) chunked(pageSize) else listOf(this) }
            .apply { totalPages = count() }

        val fixedPage = when {
            totalPages == 0 || page < 1 -> 1
            page in 1..totalPages -> page
            else -> totalPages
        }

        return Result(
            filteredResults = results.getOrElse(fixedPage-1) { listOf() },
            totalPages = totalPages,
            totalResults = totalSize,
            returnedPage = fixedPage,
        )
    }

    override suspend fun getTicketIDsWithUpdates(): List<Int> {
        return suspendingCon.sendPreparedStatement(
            query = "SELECT ID FROM TicketManager_V4_Tickets WHERE STATUS_UPDATE_FOR_CREATOR = ?;",
            values = listOf(true)
        )
            .rows
            .pMap { it.getInt(0)!! }
    }

    override suspend fun getTicketIDsWithUpdatesFor(uuid: UUID): List<Int> {
        return suspendingCon.sendPreparedStatement(
            query = "SELECT ID FROM TicketManager_V4_Tickets WHERE STATUS_UPDATE_FOR_CREATOR = ? AND CREATOR_UUID = ?;",
            values = listOf(true, uuid.toString())
        )
            .rows
            .pMap { it.getInt(0)!! }
    }

    override suspend fun closeDatabase() {
        connectionPool.disconnect()
    }

    override suspend fun initializeDatabase() {
        suspendingCon.connect()

        if (!tableExists("TicketManager_V4_Tickets")) {
            suspendingCon.sendQuery(
                """
                    CREATE TABLE TicketManager_V4_Tickets (
                        ID INT NOT NULL AUTO_INCREMENT,
                        CREATOR_UUID VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_general_ci,
                        PRIORITY TINYINT NOT NULL,
                        STATUS VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                        ASSIGNED_TO VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                        STATUS_UPDATE_FOR_CREATOR BOOLEAN NOT NULL,
                        LOCATION VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                        KEY STATUS_V4 (STATUS) USING BTREE,
                        KEY STATUS_UPDATE_FOR_CREATOR_V4 (STATUS_UPDATE_FOR_CREATOR) USING BTREE,
                        PRIMARY KEY (ID)
                    ) ENGINE=InnoDB;
                """.trimIndent()
            )
        }
        if (!tableExists("TicketManager_V4_Actions")) {
            suspendingCon.sendQuery(
                """
                CREATE TABLE TicketManager_V4_Actions (
                    ACTION_ID INT NOT NULL AUTO_INCREMENT,
                    TICKET_ID INT NOT NULL,
                    ACTION_TYPE VARCHAR(20) CHARACTER SET latin1 COLLATE latin1_general_ci NOT NULL,
                    CREATOR_UUID VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_general_ci,
                    MESSAGE TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                    TIMESTAMP BIGINT NOT NULL,
                    PRIMARY KEY (ACTION_ID)
                ) ENGINE=InnoDB;
            """.trimIndent()
            )
        }
    }

    override suspend fun migrateDatabase(
        to: Database.Type,
        databaseBuilders: DatabaseBuilders,
        onBegin: suspend () -> Unit,
        onComplete: suspend () -> Unit,
        onError: suspend (Exception) -> Unit
    ) = coroutineScope {
        launch { onBegin() }
        var otherDB: Database? = null

        try {
            when (to) {
                Database.Type.MYSQL -> return@coroutineScope
                Database.Type.SQLITE,
                Database.Type.MEMORY -> {
                    otherDB = if (to == Database.Type.MEMORY) databaseBuilders.memoryBuilder.build() else databaseBuilders.sqLiteBuilder.build()
                    otherDB.initializeDatabase()

                    val tickets = suspendingCon.sendPreparedStatement("SELECT * FROM TicketManager_V4_Tickets").rows
                            .pMap { it.toBasicTicket() }
                            .pMap(this@MySQL::getFullTicket)

                    when (to) {
                        Database.Type.MEMORY -> tickets.pForEach(otherDB::insertTicket)
                        Database.Type.SQLITE -> tickets.forEach { otherDB.insertTicket(it) }
                        Database.Type.MYSQL -> Unit
                    }
                }
            }

            onComplete()
        } catch (e: Exception) {
            launch { onError(e) }
        } finally {
            otherDB?.closeDatabase()
        }
    }

    private suspend fun getActions(id: Int): List<FullTicket.Action> {
        return suspendingCon.sendPreparedStatement(query = "SELECT ACTION_ID, ACTION_TYPE, CREATOR_UUID, MESSAGE, TIMESTAMP FROM TicketManager_V4_Actions WHERE TICKET_ID = $id;")
            .rows
            .map { it.toAction() }
    }

    // PRIVATE FUNCTIONS
    private suspend fun tableExists(table: String): Boolean {
        suspendingCon.sendQuery("SHOW TABLES;").rows
            .forEach {
                if (it.getString(0)!!.lowercase() == table.lowercase())
                    return true
            }
        return false
    }

    private fun RowData.toBasicTicket(): BasicTicket {
        return BasicTicketImpl(
            id = getInt(0)!!,
            assignedTo = getString(4),
            creatorStatusUpdate = getBoolean(5)!!,
            creatorUUID = getString(1)?.let(UUID::fromString),
            priority = getByte(2)!!.let(::byteToPriority),
            status = getString(3)!!.let(BasicTicket.Status::valueOf),
            location = getString(6)?.split(" ")?.let {
                BasicTicket.TicketLocation(
                    world = it[0],
                    x = it[1].toInt(),
                    y = it[2].toInt(),
                    z = it[3].toInt()
                )
            }
        )
    }

    private fun RowData.toAction(): FullTicket.Action {
        return FullTicket.Action(
            type = FullTicket.Action.Type.valueOf(getString(1)!!),
            user = getString(2)?.let(UUID::fromString),
            message = getString(3),
            timestamp = getLong(4)!!,
        )
    }

}

/*
THIS IS NO LONGER SUPPORTED AS V2 DATABASES ARE LONG GONE!

Any future database updates will result in a new plugin specifically designed for
updating. This will allow for legacy code and ticket building to stay separate from
this non-legacy code.

This code is only here as a reference to how older data must be parsed to convert from
V2 to V4

   override suspend fun updateDatabase(
       onBegin: suspend () -> Unit,
       onComplete: suspend () -> Unit,
       offlinePlayerNameToUuidOrNull: (String) -> UUID?
   ) = coroutineScope {
       suspendingCon.sendPreparedStatement("SELECT * FROM TicketManagerTicketsV2;").rows
       .pMap {
           FullTicket(
               id = it.getInt(0)!!,
               priority = byteToPriority(it.getByte(2)!!),
               creatorStatusUpdate = it.getBoolean(9)!!,
               status = it.getString(1)!!.let(BasicTicket.Status::valueOf),
               assignedTo = it.getString(5),

               creatorUUID = it.getString(4)!!.let { s  ->
                   if (s.lowercase() == "console") null
                   else UUID.fromString(s)
               },

               location = it.getString(6)!!.run {
                   if (equals("NoLocation")) null
                   else BasicTicket.TicketLocation.fromString(this)
               },

               actions = it.getString(8)!!
                   .split("/MySQLNewLine/")
                   .filter(String::isNotBlank)
                   .map { s -> s.split("/MySQLSep/") }
                   .mapIndexed { index, action ->
                       FullTicket.Action(
                           message = action[1],
                           type = if (index == 0) FullTicket.Action.Type.OPEN else FullTicket.Action.Type.COMMENT,
                           timestamp = it[7] as Long,
                           user =
                           if (action[0].lowercase() == "console") null
                           else offlinePlayerNameToUuidOrNull(action[0])
                       )
                   }
           )
       }
           .pForEach { t ->
               val newID = insertTicket(t)
               t.actions.forEach { insertAction(newID, it) }
           }

       onComplete()
   }
    */