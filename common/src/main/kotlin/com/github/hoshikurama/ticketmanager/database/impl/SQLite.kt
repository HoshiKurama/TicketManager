package com.github.hoshikurama.ticketmanager.database.impl

import com.github.hoshikurama.ticketmanager.database.Database
import com.github.hoshikurama.ticketmanager.database.DatabaseBuilders
import com.github.hoshikurama.ticketmanager.database.Result
import com.github.hoshikurama.ticketmanager.database.SearchConstraint
import com.github.hoshikurama.ticketmanager.misc.*
import com.github.hoshikurama.ticketmanager.ticket.BasicTicket
import com.github.hoshikurama.ticketmanager.ticket.BasicTicketImpl
import com.github.hoshikurama.ticketmanager.ticket.FullTicket
import com.github.hoshikurama.ticketmanager.ticket.plus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotliquery.*
import java.sql.DriverManager
import java.time.Instant
import java.util.*

class SQLite(absoluteDataFolderPath: String) : Database {
    override val type = Database.Type.SQLITE
    private val url: String = "jdbc:sqlite:$absoluteDataFolderPath/TicketManager-SQLite.db"

    private fun getSession() = Session(Connection(DriverManager.getConnection(url)))

    override suspend fun setAssignment(ticketID: Int, assignment: String?) {
        using(getSession()) {
            it.run(queryOf("UPDATE TicketManager_V4_Tickets SET ASSIGNED_TO = ? WHERE ID = $ticketID;", assignment).asUpdate)
        }
    }

    override suspend fun setCreatorStatusUpdate(ticketID: Int, status: Boolean) {
        using(getSession()) {
            it.run(queryOf("UPDATE TicketManager_V4_Tickets SET STATUS_UPDATE_FOR_CREATOR = ? WHERE ID = $ticketID;", status).asUpdate)
        }
    }

    override suspend fun setPriority(ticketID: Int, priority: BasicTicket.Priority) {
        using(getSession()) {
            it.run(queryOf("UPDATE TicketManager_V4_Tickets SET PRIORITY = ? WHERE ID = $ticketID;", priority.level).asUpdate)
        }
    }

    override suspend fun setStatus(ticketID: Int, status: BasicTicket.Status) {
        using(getSession()) {
            it.run(queryOf("UPDATE TicketManager_V4_Tickets SET STATUS = ? WHERE ID = $ticketID;", status.name).asUpdate)
        }
    }

    override suspend fun insertAction(id: Int, action: FullTicket.Action) {
        using(getSession()) {
            insertAction(action, id, it)
        }
    }

    override suspend fun insertTicket(fullTicket: FullTicket): Int {
        return using(getSession()) { session ->
            val newID = insertTicket(fullTicket, session)!!.toInt()

            fullTicket.actions.forEach {
                insertAction(it, newID, session)
            }

            newID
        }
    }

    override suspend fun getBasicTicketsOrNull(ids: List<Int>): List<BasicTicket>? {
        val idsSQL = ids.joinToString(", ") { "$it" }

        return using(getSession()) { session ->
            session.run(queryOf("SELECT * FROM TicketManager_V4_Tickets WHERE ID IN ($idsSQL);")
                .map { it.toBasicTicket() }
                .asList
            )
                .ifEmpty { null }
        }
    }

    override suspend fun getFullTickets(basicTickets: List<BasicTicket>): List<FullTicket> {
        return using(getSession()) { session ->
            basicTickets.map { it + getActions(it.id, session) }
        }
    }

    override suspend fun getOpenTickets(page: Int, pageSize: Int): Result<BasicTicket> {
        return getTicketsFilteredBy(page, pageSize, "SELECT * FROM TicketManager_V4_Tickets WHERE STATUS = ?;", BasicTicket.Status.OPEN.name)
    }

    override suspend fun getOpenTicketsAssignedTo(
        page: Int,
        pageSize: Int,
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): Result<BasicTicket> {
        val groupsSQLStatement = unfixedGroupAssignment.joinToString(" OR ") { "ASSIGNED_TO = ?" }
        val groupsFixed = unfixedGroupAssignment.map { "::$it" }
        val args = listOf(BasicTicket.Status.OPEN.name) + groupsFixed

        return getTicketsFilteredBy(page, pageSize, "SELECT * FROM TicketManager_V4_Tickets WHERE STATUS = ? AND ($groupsSQLStatement);", args)
    }

    override suspend fun getOpenTicketsNotAssigned(page: Int, pageSize: Int): Result<BasicTicket> {
        return getTicketsFilteredBy(page, pageSize, "SELECT * FROM TicketManager_V4_Tickets WHERE STATUS = ? AND ASSIGNED_TO IS NULL", BasicTicket.Status.OPEN.name)
    }

    private fun getTicketsFilteredBy(page: Int, pageSize: Int, query: String, vararg params: Any?): Result<BasicTicket> {
        val totalSize: Int
        val totalPages: Int

        val results =
            using(getSession()) { session ->
                session.run(
                    queryOf(query, *params)
                        .map { it.toBasicTicket() }
                        .asList
                )
            }
            .sortedWith(compareByDescending<BasicTicket> { it.priority.level }.thenByDescending { it.id })
            .apply { totalSize = count() }
            .run { if (pageSize == 0 || isEmpty()) listOf(this) else chunked(pageSize) }
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
        using(getSession()) { session ->
            val rows = session.run(
                queryOf("SELECT ID FROM TicketManager_V4_Tickets WHERE STATUS = ? AND ID BETWEEN $lowerBound AND $upperBound;", BasicTicket.Status.OPEN.name)
                    .map { it.int(1) }
                    .asList
            )

            // Sets ticket statuses
            val idString = rows.joinToString(", ")
            session.run(queryOf("UPDATE TicketManager_V4_Tickets SET STATUS = ? WHERE ID IN ($idString);", BasicTicket.Status.CLOSED.name).asUpdate)

            // Adds to Action Table
            rows.forEach {
                insertAction(
                    action = FullTicket.Action(
                        type = FullTicket.Action.Type.MASS_CLOSE,
                        user = actor,
                        message = null,
                        timestamp = Instant.now().epochSecond
                    ),
                    id = it,
                    session = session
                )
            }
        }
    }

    override suspend fun countOpenTickets(): Int {
        return using(getSession()) { session ->
            session.run(
                queryOf("SELECT COUNT(*) FROM TicketManager_V4_Tickets WHERE STATUS = ?;", BasicTicket.Status.OPEN)
                    .map { it.int(1) }
                    .asSingle
            )
        } ?: 0
    }

    override suspend fun countOpenTicketsAssignedTo(assignment: String, unfixedGroupAssignment: List<String>): Int {
        val groupsSQLStatement = unfixedGroupAssignment.joinToString(" OR ") { "ASSIGNED_TO = ?" }
        val groupsFixed = unfixedGroupAssignment.map { "::$it" }

        return using(getSession()) { session ->
            session.run(
                queryOf("SELECT COUNT(*) FROM TicketManager_V4_Tickets WHERE STATUS = ? AND ($groupsSQLStatement);", BasicTicket.Status.OPEN.name, *groupsFixed.toTypedArray())
                    .map { it.int(1) }
                    .asSingle
            )
        } ?: 0
    }

    override suspend fun searchDatabase(
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
            searchString += " WHERE ${searches.joinToString(" AND ")}"

        // Perform search and other functions
        val totalSize: Int
        val totalPages: Int
        val results = using(getSession()) { session ->
            session.run(
                queryOf("$searchString;", *args.toTypedArray())
                    .map { it.toBasicTicket() }
                    .asList
            )
        }
            .run { getFullTickets(this) }
            .asParallelStream()
            .filter(combinedFunction)
            .toList()
            .sortedWith(compareByDescending { it.id})
            .apply { totalSize = count() }
            .run { if (pageSize == 0 || isEmpty()) listOf(this) else chunked(pageSize) }
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
        return using(getSession()) { session ->
            session.run(
                queryOf("SELECT ID FROM TicketManager_V4_Tickets WHERE STATUS_UPDATE_FOR_CREATOR = ?;", true)
                    .map { it.int(1) }
                    .asList
            )
        }
    }

    override suspend fun getTicketIDsWithUpdatesFor(uuid: UUID): List<Int> {
        return using(getSession()) { session ->
            session.run(
                queryOf("SELECT ID FROM TicketManager_V4_Tickets WHERE STATUS_UPDATE_FOR_CREATOR = ? AND CREATOR_UUID = ?;", true, uuid.toString())
                    .map { it.int(1) }
                    .asList
            )
        }
    }

    override suspend fun closeDatabase() {
        // NOT needed as database makes individual connections
    }

    override suspend fun initializeDatabase() {
        // Creates table if doesn't exist
        using(getSession()) {
            if (!tableExists("TicketManager_V4_Tickets")) {
                it.run(
                    queryOf("""
                        CREATE TABLE TicketManager_V4_Tickets (
                        ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        CREATOR_UUID VARCHAR(36) COLLATE NOCASE,
                        PRIORITY TINYINT NOT NULL,
                        STATUS VARCHAR(10) COLLATE NOCASE NOT NULL,
                        ASSIGNED_TO VARCHAR(255) COLLATE NOCASE,
                        STATUS_UPDATE_FOR_CREATOR BOOLEAN NOT NULL,
                        LOCATION VARCHAR(255) COLLATE NOCASE
                        );""".trimIndent()
                    ).asExecute
                )
                it.run(queryOf("CREATE INDEX STATUS_V4 ON TicketManager_V4_Tickets (STATUS)").asExecute)
                it.run(queryOf("CREATE INDEX STATUS_UPDATE_FOR_CREATOR_V4 ON TicketManager_V4_Tickets (STATUS_UPDATE_FOR_CREATOR)").asExecute)
            }

            if (!tableExists("TicketManager_V4_Actions")) {
                it.run(
                    queryOf("""
                        CREATE TABLE TicketManager_V4_Actions (
                        ACTION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                        TICKET_ID INTEGER NOT NULL,
                        ACTION_TYPE VARCHAR(20) COLLATE NOCASE NOT NULL,
                        CREATOR_UUID VARCHAR(36) COLLATE NOCASE,
                        MESSAGE TEXT COLLATE NOCASE,
                        TIMESTAMP BIGINT NOT NULL
                        );""".trimIndent()
                    ).asExecute
                )
            }
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

        val getFullTickets = suspend {
            using(getSession()) { session ->
                session.run(
                    queryOf("SELECT * FROM TicketManager_V4_Tickets")
                        .map { it.toBasicTicket() }
                        .asList
                )
            }
                .parallelFlowMap(this@SQLite::getFullTicket)
        }

        try {
            when (to) {
                Database.Type.SQLITE -> return@coroutineScope
                Database.Type.CACHED_SQLITE -> return@coroutineScope

                Database.Type.MEMORY, Database.Type.MYSQL -> {
                    val otherDB = databaseBuilders.run { if (equals(Database.Type.MEMORY)) memoryBuilder else mySQLBuilder }
                        .build()
                        .also { it.initializeDatabase() }

                    getFullTickets().parallelFlowForEach(otherDB::insertTicket)
                    otherDB.closeDatabase()
                }
            }
            onComplete()
        } catch (e: Exception) {
            launch { onError(e) }
        }
    }


    // Private Functions

    private fun insertTicket(ticket: BasicTicket, session: Session): Long? {
        return session.run(queryOf("INSERT INTO TicketManager_V4_Tickets (CREATOR_UUID, PRIORITY, STATUS, ASSIGNED_TO, STATUS_UPDATE_FOR_CREATOR, LOCATION) VALUES (?,?,?,?,?,?);",
            ticket.creatorUUID,
            ticket.priority.level,
            ticket.status.name,
            ticket.assignedTo,
            ticket.creatorStatusUpdate,
            ticket.location?.toString()
        ).asUpdateAndReturnGeneratedKey)
    }

    private fun insertAction(action: FullTicket.Action, id: Int, session: Session) {
        session.run(queryOf("INSERT INTO TicketManager_V4_Actions (TICKET_ID,ACTION_TYPE,CREATOR_UUID,MESSAGE,TIMESTAMP) VALUES (?,?,?,?,?);",
            id,
            action.type.name,
            action.user?.toString(),
            action.message,
            action.timestamp
        ).asExecute)
    }

    private fun tableExists(table: String): Boolean {
        return using(getSession().connection.underlying.metaData.getTables(null, null, table, null)) {
            while (it.next())
                if (it.getString("TABLE_NAME")?.lowercase()?.equals(table.lowercase()) == true) return@using true
            return@using false
        }
    }

    private fun Row.toBasicTicket(): BasicTicket {
        return BasicTicketImpl(
            id = int(1),
            creatorUUID = stringOrNull(2)?.let(UUID::fromString),
            priority = byteToPriority(byte(3)),
            status = BasicTicket.Status.valueOf(string(4)),
            assignedTo = stringOrNull(5),
            creatorStatusUpdate = boolean(6),
            location = stringOrNull(7)?.split(" ")?.let {
                BasicTicket.TicketLocation(
                    world = it[0],
                    x = it[1].toInt(),
                    y = it[2].toInt(),
                    z = it[3].toInt()
                )
            }
        )
    }

    private fun Row.toAction(): FullTicket.Action {
        return FullTicket.Action(
            type = FullTicket.Action.Type.valueOf(string(2)),
            user = stringOrNull(3)?.let { UUID.fromString(it) },
            message = stringOrNull(4),
            timestamp = long(5)
        )
    }

    private fun getActions(id: Int, session: Session = getSession()): List<FullTicket.Action> {
        return session.run(queryOf("SELECT ACTION_ID, ACTION_TYPE, CREATOR_UUID, MESSAGE, TIMESTAMP FROM TicketManager_V4_Actions WHERE TICKET_ID = $id;")
            .map { row -> row.toAction() }
            .asList
        )
    }
}