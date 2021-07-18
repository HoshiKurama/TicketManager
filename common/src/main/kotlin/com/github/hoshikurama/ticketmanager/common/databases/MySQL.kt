package com.github.hoshikurama.ticketmanager.common.databases

import com.github.hoshikurama.ticketmanager.common.TMLocale
import com.github.hoshikurama.ticketmanager.common.byteToPriority
import com.github.hoshikurama.ticketmanager.common.sortActions
import com.github.hoshikurama.ticketmanager.common.ticket.BasicTicket
import com.github.hoshikurama.ticketmanager.common.ticket.ConcreteBasicTicket
import com.github.hoshikurama.ticketmanager.common.ticket.FullTicket
import com.github.hoshikurama.ticketmanager.common.ticket.toTicketLocation
import com.github.jasync.sql.db.ConnectionPoolConfiguration
import com.github.jasync.sql.db.RowData
import com.github.jasync.sql.db.SuspendingConnection
import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.mysql.MySQLConnectionBuilder
import com.github.jasync.sql.db.mysql.MySQLQueryResult
import com.github.jasync.sql.db.util.ExecutorServiceUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import java.time.Instant
import java.util.*
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext

class MySQL(
    host: String,
    port: String,
    dbName: String,
    username: String,
    password: String,
    asyncDispatcher: CoroutineDispatcher = Dispatchers.Default,
    asyncExecutor: Executor = ExecutorServiceUtils.CommonPool,
) : Database {
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

    override val type = Database.Type.MySQL

    override suspend fun getActionsAsFlow(ticketID: Int) = flow {
        suspendingCon.sendPreparedStatement(query = "SELECT ACTION_ID, ACTION_TYPE, CREATOR_UUID, MESSAGE, TIMESTAMP FROM TicketManager_V4_Actions WHERE TICKET_ID = $ticketID;").rows
            .forEach { emit(it.toAction()) }
    }

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

    override suspend fun getBasicTicket(ticketID: Int): BasicTicket? {
        val query = suspendingCon.sendPreparedStatement("SELECT * FROM TicketManager_V4_Tickets WHERE ID = $ticketID;").rows
        return query.firstOrNull()?.toBasicTicket()
    }

    override suspend fun addAction(ticketID: Int, action: FullTicket.Action) {
        writeAction(ticketID, action)
    }

    override suspend fun addFullTicket(fullTicket: FullTicket) {
        writeBasicTicket(fullTicket)
        fullTicket.actions.forEach {
            writeAction(fullTicket.id, it)
        }
    }

    override suspend fun addNewTicket(basicTicket: BasicTicket, context: CoroutineContext, message: String): Int {
        return withContext(context) {
            val id = writeBasicTicket(basicTicket).toInt()
            launch { writeAction(id, FullTicket.Action(FullTicket.Action.Type.OPEN, basicTicket.creatorUUID, message)) }
            id
        }
    }

    override suspend fun massCloseTickets(lowerBound: Int, upperBound: Int, uuid: UUID?, context: CoroutineContext) {
        withContext(context) {
            val statusPairs = flow {
                suspendingCon.sendPreparedStatement("SELECT ID, STATUS FROM TicketManager_V4_Tickets WHERE ID BETWEEN $lowerBound AND $upperBound;")
                    .rows
                    .map { (it.getInt(0)!!) to BasicTicket.Status.valueOf(it.getString(1)!!) }
                    .filter { it.second == BasicTicket.Status.OPEN }
                    .forEach { emit(it.first) }
            }

            launch {
                val idString = statusPairs.toList().joinToString(", ")
                suspendingCon.sendPreparedStatement(
                    query = "UPDATE TicketManager_V4_Tickets SET STATUS = ? WHERE ID IN ($idString);",
                    values = listOf(BasicTicket.Status.CLOSED.name)
                )
            }

            statusPairs.collect {
                launch {
                    writeAction(
                        action = FullTicket.Action(
                            type = FullTicket.Action.Type.MASS_CLOSE,
                            user = uuid,
                            message = null,
                            timestamp = Instant.now().epochSecond
                        ),
                        ticketID = it
                    )
                }
            }
        }
    }

    override suspend fun getOpenIDPriorityPairs(): Flow<Pair<Int, Byte>> = flow {
        suspendingCon.sendPreparedStatement(
            query = "SELECT ID, PRIORITY FROM TicketManager_V4_Tickets WHERE STATUS = ?;",
            values = listOf(BasicTicket.Status.OPEN.name)
        )
            .rows
            .map { it.getInt(0)!! to it.getByte(1)!! }
            .forEach { emit(it) }
    }

    override suspend fun getAssignedOpenIDPriorityPairs(
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): Flow<Pair<Int, Byte>> = flow {
        val groupsSQLStatement = unfixedGroupAssignment.joinToString(" OR ") { "ASSIGNED_TO = ?" }
        val groupsFixed = unfixedGroupAssignment.map { "::$it" }

        suspendingCon.sendPreparedStatement(
            query = "SELECT ID, PRIORITY FROM TicketManager_V4_Tickets WHERE STATUS = ? AND ($groupsSQLStatement);",
            values = listOf(BasicTicket.Status.OPEN.name) + groupsFixed
        )
            .rows
            .map { it.getInt(0)!! to it.getByte(1)!! }
            .forEach { emit(it) }
    }

    override suspend fun getUnassignedOpenIDPriorityPairs(): Flow<Pair<Int, Byte>> = flow {
        suspendingCon.sendPreparedStatement(
            query = "SELECT ID, PRIORITY FROM TicketManager_V4_Tickets WHERE STATUS = ? AND ASSIGNED_TO IS NULL",
            values = listOf(BasicTicket.Status.OPEN.name)
        )
            .rows
            .map { it.getInt(0)!! to it.getByte(1)!! }
            .forEach { emit(it) }
    }

    override suspend fun getIDsWithUpdates(): Flow<Int> = flow {
        suspendingCon.sendPreparedStatement(
            query = "SELECT ID FROM TicketManager_V4_Tickets WHERE STATUS_UPDATE_FOR_CREATOR = ?;",
            values = listOf(true)
        )
            .rows
            .map { it.getInt(0)!! }
            .forEach { emit(it) }
    }

    override suspend fun getIDsWithUpdatesFor(uuid: UUID): Flow<Int> = flow {
        suspendingCon.sendPreparedStatement(
            query = "SELECT ID FROM TicketManager_V4_Tickets WHERE STATUS_UPDATE_FOR_CREATOR = ? AND CREATOR_UUID = ?;",
            values = listOf(true, uuid.toString())
        )
            .rows
            .map { it.getInt(0)!! }
            .forEach { emit(it) }
    }

    override suspend fun getBasicTickets(ids: List<Int>): Flow<BasicTicket> = flow {
        val idsSQL = ids.joinToString(", ") { "$it" }

        suspendingCon.sendQuery("SELECT * FROM TicketManager_V4_Tickets WHERE ID IN ($idsSQL);")
            .rows
            .map { it.toBasicTicket() }
            .forEach { emit(it) }
    }

    override suspend fun getFullTicketsFromBasics(
        basicTickets: List<BasicTicket>,
        context: CoroutineContext
    ): Flow<FullTicket> = flow {
        basicTickets
            .map {
                withContext(context) {
                    async { it.toFullTicket() }
                }
            }
            .map { it.await() }
            .forEach { emit(it) }
    }

    override suspend fun getFullTickets(ids: List<Int>, context: CoroutineContext): Flow<FullTicket> = flow {
        val idsSQL = ids.joinToString(", ") { "$it" }
        suspendingCon.sendQuery("SELECT * FROM TicketManager_V4_Tickets WHERE ID IN ($idsSQL);")
            .rows
            .map { it.toBasicTicket() }
            .map {
                withContext(context) {
                    async { it.toFullTicket() }
                }
            }
            .map { it.await() }
            .forEach { emit(it) }
    }

    override suspend fun searchDatabase(
        context: CoroutineContext,
        locale: TMLocale,
        mainTableConstraints: List<Pair<String, String?>>,
        searchFunction: (FullTicket) -> Boolean
    ): Flow<FullTicket> = flow {
        fun equalsOrIs(string: String?) = if (string == null) "IS NULL" else "= ?"

        val mainTableSQL = mainTableConstraints.joinToString(" AND ") {
            when (it.first) {
                locale.searchAssigned -> "ASSIGNED_TO ${equalsOrIs(it.second)}"
                locale.searchCreator -> "CREATOR_UUID ${equalsOrIs(it.second)}"
                locale.searchPriority -> "PRIORITY = ?"
                locale.searchStatus -> "STATUS = ?"
                else -> ""
            }
        }
        var statementSQL = "SELECT * FROM TicketManager_V4_Tickets"
        if (mainTableConstraints.isNotEmpty())
            statementSQL += " WHERE $mainTableSQL"

        suspendingCon.sendPreparedStatement("$statementSQL;", mainTableConstraints.mapNotNull { it.second })
            .rows
            .map { it.toBasicTicket() }
            .map {
                withContext(context) {
                    async { it.toFullTicket() }
                }
            }
            .map { it.await() }
            .filter(searchFunction)
            .forEach{ emit(it) }
    }


    override suspend fun closeDatabase() {
        connectionPool.disconnect()
    }

    override suspend fun initialiseDatabase() {
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

    override suspend fun updateNeeded(): Boolean {
        return tableExists("TicketManagerTicketsV2")
    }

    override suspend fun migrateDatabase(
        context: CoroutineContext,
        to: Database.Type,
        mySQLBuilder: suspend () -> MySQL,
        sqLiteBuilder: suspend () -> SQLite,
        memoryBuilder: suspend () -> Memory,
        onBegin: suspend () -> Unit,
        onComplete: suspend () -> Unit
    ) {
        onBegin()

        when (to) {
            Database.Type.MySQL -> return

            Database.Type.SQLite -> {
                val sqlite = sqLiteBuilder()
                sqlite.initialiseDatabase()

                // Gets all tables from MySQL
                suspendingCon.sendPreparedStatement("SELECT * FROM TicketManager_V4_Tickets")
                    .rows
                    .map { it.toBasicTicket().toFullTicket(this) }
                    .forEach {
                        withContext(context) {
                            launch { sqlite.addFullTicket(it) }
                        }
                    }

                sqlite.closeDatabase()
            }

            Database.Type.Memory -> {
                val memory = sqLiteBuilder()
                memory.initialiseDatabase()

                // Gets all tables from MySQL
                suspendingCon.sendPreparedStatement("SELECT * FROM TicketManager_V4_Tickets")
                    .rows
                    .map { it.toBasicTicket().toFullTicket(this) }
                    .forEach { memory.addFullTicket(it) }

                memory.closeDatabase()
            }
        }

        onComplete()
    }

    override suspend fun updateDatabase(
        context: CoroutineContext,
        onBegin: suspend () -> Unit,
        onComplete: suspend () -> Unit,
        offlinePlayerNameToUuidOrNull: (String) -> UUID?
    ) {
        withContext(context) {
            onBegin()

            suspendingCon.sendPreparedStatement("SELECT * FROM TicketManagerTicketsV2;").rows
                .forEach {
                    val fullTicket = FullTicket(
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
                            else toTicketLocation()
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
                        ,
                    )

                    launch {
                        val id = writeBasicTicket(fullTicket).toInt()
                        fullTicket.actions.forEach { a -> writeAction(id, a) }
                    }
                }

            onComplete()
        }
    }

    // PRIVATE FUNCTIONS
    private suspend fun tableExists(table: String): Boolean {
        suspendingCon.sendQuery("SHOW TABLES;").rows
            .forEach {
                if (it.getString(0) == table.lowercase())
                    return true
            }
        return false
    }

    private suspend fun writeBasicTicket(ticket: BasicTicket): Long {
        val query = suspendingCon.sendPreparedStatement(
            query = "INSERT INTO TicketManager_V4_Tickets (CREATOR_UUID, PRIORITY, STATUS, ASSIGNED_TO, STATUS_UPDATE_FOR_CREATOR, LOCATION) VALUES (?,?,?,?,?,?);",
            listOf(
                ticket.creatorUUID?.toString(),
                ticket.priority.level,
                ticket.status.name,
                ticket.assignedTo,
                ticket.creatorStatusUpdate,
                ticket.location?.toString()
            )
        ) as MySQLQueryResult

        return query.lastInsertId
    }

    private suspend fun writeAction(ticketID: Int, action: FullTicket.Action) {
        suspendingCon.sendPreparedStatement(
            query = "INSERT INTO TicketManager_V4_Actions (TICKET_ID,ACTION_TYPE,CREATOR_UUID,MESSAGE,TIMESTAMP) VALUES (?,?,?,?,?);",
            listOf(
                ticketID,
                action.type.name,
                action.user?.toString(),
                action.message,
                action.timestamp,
            )
        )
    }

    private fun RowData.toBasicTicket(): BasicTicket {
        return ConcreteBasicTicket(
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

    private suspend fun BasicTicket.toFullTicket() =
        FullTicket(this, getActionsAsFlow(id).toList().sortedWith(sortActions))
}