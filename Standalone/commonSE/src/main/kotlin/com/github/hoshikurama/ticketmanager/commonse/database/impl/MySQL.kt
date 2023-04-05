package com.github.hoshikurama.ticketmanager.commonse.database.impl

import com.github.hoshikurama.ticketmanager.commonse.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.commonse.database.Result
import com.github.hoshikurama.ticketmanager.commonse.database.SearchConstraint
import com.github.hoshikurama.ticketmanager.commonse.misc.*
import com.github.hoshikurama.ticketmanager.commonse.ticket.Creator
import com.github.hoshikurama.ticketmanager.commonse.ticket.Ticket
import com.github.jasync.sql.db.ConnectionPoolConfiguration
import com.github.jasync.sql.db.RowData
import com.github.jasync.sql.db.mysql.MySQLConnectionBuilder
import com.github.jasync.sql.db.mysql.MySQLQueryResult
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class MySQL(
    host: String,
    port: String,
    dbName: String,
    username: String,
    password: String,
): AsyncDatabase {

    override val type = AsyncDatabase.Type.MYSQL

    private val connectionPool = MySQLConnectionBuilder.createConnectionPool(
        ConnectionPoolConfiguration(
            host = host,
            port = port.toInt(),
            database = dbName,
            username = username,
            password = password,
        )
    )

    override fun setAssignment(ticketID: Long, assignment: String?) {
        connectionPool.sendPreparedStatement(
            "UPDATE TicketManager_V8_Tickets SET ASSIGNED_TO = ? WHERE ID = $ticketID;",
            listOf(assignment)
        )
    }

    override fun setCreatorStatusUpdate(ticketID: Long, status: Boolean) {
        connectionPool.sendPreparedStatement(
            "UPDATE TicketManager_V8_Tickets SET STATUS_UPDATE_FOR_CREATOR = ? WHERE ID = $ticketID;",
            listOf(status)
        )
    }

    override fun setPriority(ticketID: Long, priority: Ticket.Priority) {
        connectionPool.sendPreparedStatement(
            "UPDATE TicketManager_V8_Tickets SET PRIORITY = ? WHERE ID = $ticketID;",
            listOf(priority.level)
        )
    }

    override fun setStatus(ticketID: Long, status: Ticket.Status) {
        connectionPool.sendPreparedStatement(
            "UPDATE TicketManager_V8_Tickets SET STATUS = ? WHERE ID = $ticketID;",
            listOf(status.name)
        )
    }

    override fun insertAction(id: Long, action: Ticket.Action) {
        connectionPool.sendPreparedStatement(
            query = "INSERT INTO TicketManager_V8_Actions (TICKET_ID, ACTION_TYPE, CREATOR, MESSAGE, EPOCH_TIME, SERVER, WORLD, WORLD_X, WORLD_Y, WORLD_Z) VALUES (?,?,?,?,?,?,?,?,?,?);",
            listOf(
                id,
                action.type.name,
                action.user.toString(),
                action.message,
                action.timestamp,
                action.location.server,
                action.location.world,
                action.location.x,
                action.location.y,
                action.location.z,
            )
        )
    }

    override suspend fun insertTicketAsync(ticket: Ticket): Long {
        val id = connectionPool.sendPreparedStatement(
            query = "INSERT INTO TicketManager_V8_Tickets (CREATOR, PRIORITY, STATUS, ASSIGNED_TO, STATUS_UPDATE_FOR_CREATOR) VALUES (?,?,?,?,?);",
            listOf(
                ticket.creator.toString(),
                ticket.priority.level,
                ticket.status.name,
                ticket.assignedTo,
                ticket.creatorStatusUpdate,
            )
        )
            .asDeferredThenAwait()
            .let { it as MySQLQueryResult }
            .let(MySQLQueryResult::lastInsertId)

        ticket.actions.forEach { insertAction(id, it) }

        return id
    }

    override suspend fun getTicketsAsync(ids: List<Long>): List<Ticket> {
        if (ids.isEmpty()) return listOf()
        val idsSQL = ids.joinToString(", ") { "$it" }

        val ticketsCF = connectionPool.sendQuery("SELECT * FROM TicketManager_V8_Tickets WHERE ID IN ($idsSQL);")
            .thenApplyAsync { r -> r.rows.map { it.toTicket() } }

        val actionsCF = connectionPool.sendQuery("SELECT * FROM TicketManager_V8_Actions WHERE TICKET_ID IN ($idsSQL);")
            .thenApplyAsync { r -> r.rows.map { it.getLong(1)!! to it.toAction() } }

        val tickets = ticketsCF.asDeferredThenAwait()
        val actionMap = actionsCF.asDeferredThenAwait()
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sortedBy(Ticket.Action::timestamp) }

        return tickets.map { it + actionMap[it.id]!! }
    }

    override suspend fun getTicketOrNullAsync(id: Long): Ticket? {
        return connectionPool.sendPreparedStatement("SELECT * FROM TicketManager_V8_Tickets WHERE ID = ?", listOf(id))
            .asDeferredThenAwait()
            .rows.firstOrNull()?.toTicket()
            ?.plus(getActions(id))
    }

    override suspend fun getOpenTicketsAsync(page: Int, pageSize: Int): Result {
        return ticketsFilteredByAsync(
            page, pageSize, "SELECT ID FROM TicketManager_V8_Tickets WHERE STATUS = ?;", listOf(
                Ticket.Status.OPEN.name
            )
        )
    }

    override suspend fun getOpenTicketsAssignedToAsync(
        page: Int,
        pageSize: Int,
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): Result {
        val groupsFixed = unfixedGroupAssignment.map { "::$it" }
        val assignedSQL = (unfixedGroupAssignment + assignment).joinToString(" OR ") { "ASSIGNED_TO = ?" }
        val args = (listOf(Ticket.Status.OPEN.name, assignment) + groupsFixed)



        return ticketsFilteredByAsync(
            page,
            pageSize,
            "SELECT ID FROM TicketManager_V8_Tickets WHERE STATUS = ? AND ($assignedSQL);",
            args
        )
    }

    override suspend fun getOpenTicketsNotAssignedAsync(page: Int, pageSize: Int): Result {
        return ticketsFilteredByAsync(
            page, pageSize, "SELECT ID FROM TicketManager_V8_Tickets WHERE STATUS = ? AND ASSIGNED_TO IS NULL", listOf(
                Ticket.Status.OPEN.name
            )
        )
    }

    private suspend fun ticketsFilteredByAsync(
        page: Int,
        pageSize: Int,
        preparedQuery: String,
        values: List<Any?>
    ): Result {
        val totalSize = AtomicInteger(0)
        val totalPages = AtomicInteger(0)

        val sortedTickets = connectionPool.sendPreparedStatement(preparedQuery, values)
            .asDeferredThenAwait()
            .rows.map { it.getLong(0)!! }
            .let { getTicketsAsync(it) }
            .sortedWith(compareByDescending<Ticket> { it.priority.level }
                .thenByDescending { it.id })

        totalSize.set(sortedTickets.count())

        val chunkedTickets = sortedTickets.let {
            if (pageSize == 0 || it.isEmpty())
                listOf(it)
            else it.chunked(pageSize)
        }

        totalPages.set(chunkedTickets.count())

        val fixedPage = when {
            totalPages.get() == 0 || page < 1 -> 1
            page in 1..totalPages.get() -> page
            else -> totalPages.get()
        }

        return Result(
            filteredResults = chunkedTickets.getOrElse(fixedPage - 1) { listOf() },
            totalPages = totalPages.get(),
            totalResults = totalSize.get(),
            returnedPage = fixedPage,
        )
    }

    override fun massCloseTickets(
        lowerBound: Long,
        upperBound: Long,
        actor: Creator,
        ticketLoc: Ticket.TicketLocation
    ) {
        val curTime = Instant.now().epochSecond

        val rows = connectionPool.sendPreparedStatement(
            "SELECT ID FROM TicketManager_V8_Tickets WHERE STATUS = ? AND ID BETWEEN $lowerBound AND $upperBound;",
            listOf(Ticket.Status.OPEN.name)
        )
            .thenApply { r -> r.rows.map { it.getLong(0)!! } }

        // Sets Ticket Statuses
        rows.thenAcceptAsync {
            val idString = it.joinToString(", ")
            connectionPool.sendPreparedStatement(
                query = "UPDATE TicketManager_V8_Tickets SET STATUS = ? WHERE ID IN ($idString);",
                values = listOf(Ticket.Status.CLOSED.name)
            )
        }

        rows.thenAcceptAsync { r ->
            r.forEach {
                insertAction(
                    action = Ticket.Action(
                        type = Ticket.Action.Type.MASS_CLOSE,
                        user = actor,
                        message = null,
                        timestamp = curTime,
                        location = ticketLoc
                    ),
                    id = it
                )
            }
        }
    }

    override suspend fun countOpenTicketsAsync(): Long {
        return connectionPool.sendPreparedStatement(
            "SELECT COUNT(*) FROM TicketManager_V8_Tickets WHERE STATUS = ?;", listOf(
                Ticket.Status.OPEN.name
            )
        )
            .asDeferredThenAwait()
            .rows.firstNotNullOf { it.getLong(0) }
    }

    override suspend fun countOpenTicketsAssignedToAsync(
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): Long {
        val groupsFixed = unfixedGroupAssignment.map { "::$it" }
        val assignedSQL = (unfixedGroupAssignment + assignment).joinToString(" OR ") { "ASSIGNED_TO = ?" }
        val args = (listOf(Ticket.Status.OPEN.name, assignment) + groupsFixed)

        return connectionPool.sendPreparedStatement(
            "SELECT COUNT(*) FROM TicketManager_V8_Tickets WHERE STATUS = ? AND ($assignedSQL);",
            args
        )
            .asDeferredThenAwait()
            .rows.firstNotNullOf { it.getLong(0) }
    }

    override suspend fun searchDatabaseAsync(
        constraints: SearchConstraint,
        page: Int,
        pageSize: Int
    ): Result {
        val args = mutableListOf<Any?>()
        val searches = mutableListOf<String>()
        val functions = mutableListOf<TicketPredicate>()

        fun addToCorrectLocations(value: String?, field: String) = value
            ?.apply(args::add)
            ?.let { "$field = ?" }
            ?.apply(searches::add)
            ?: "$field IS NULL".apply(searches::add)

        constraints.run {
            // Database Searches
            creator?.run { addToCorrectLocations(value.toString(), "CREATOR") }
            assigned?.run { addToCorrectLocations(value, "ASSIGNED_TO") }
            status?.run { addToCorrectLocations(value.name, "STATUS") }
            closedBy?.run {
                searches.add("ID IN (SELECT DISTINCT TICKET_ID FROM TicketManager_V8_Actions WHERE (ACTION_TYPE = ? OR ACTION_TYPE = ?) AND CREATOR = ?)")
                args.add(Ticket.Action.Type.CLOSE)
                args.add(Ticket.Action.Type.MASS_CLOSE)
                args.add(value.toString())
            }
            world?.run {
                searches.add("ID IN (SELECT DISTINCT TICKET_ID FROM TicketManager_V8_Actions WHERE WORLD = ?)")
                args.add(value)
            }

            // Functional Searches
            lastClosedBy?.run {
                { t: Ticket ->
                    t.actions.lastOrNull { it.type == Ticket.Action.Type.CLOSE || it.type == Ticket.Action.Type.MASS_CLOSE }
                        ?.run { user equal value } ?: false
                }
            }?.apply(functions::add)
            creationTime?.run { { t: Ticket -> t.actions[0].timestamp >= value } }?.apply(functions::add)
            constraints.run {
                keywords?.run {
                    { t: Ticket ->
                        val comments = t.actions
                            .filter { it.type == Ticket.Action.Type.OPEN || it.type == Ticket.Action.Type.COMMENT }
                            .map { it.message!! }
                        value.map { w -> comments.any { it.lowercase().contains(w.lowercase()) } }
                            .all { it }
                    }
                }?.apply(functions::add)
            }
        }

        // Builds composed function
        val combinedFunction = if (functions.isNotEmpty()) { t: Ticket -> functions.all { it(t) } }
        else { _: Ticket -> true }

        // Builds final search string
        var searchString = "SELECT * FROM TicketManager_V8_Tickets"
        if (searches.isNotEmpty())
            searchString += " WHERE ${searches.joinToString(" AND ")}"

        // Searches
        val totalSize = AtomicInteger(0)
        val totalPages = AtomicInteger(0)

        // Query
        val baseTickets = connectionPool.sendPreparedStatement("$searchString;", args)
            .thenApplyAsync { r -> r.rows.map { it.toTicket() } }
            .asDeferredThenAwait()

        val fullTickets =
            if (baseTickets.isEmpty()) emptyList() // I remember this was due to some weird bug
            else {
                val constraintsStr = baseTickets.map(Ticket::id).joinToString(", ")
                val actionMap = connectionPool.sendQuery("SELECT * FROM TicketManager_V8_Actions WHERE TICKET_ID IN ($constraintsStr);")
                    .asDeferredThenAwait()
                    .rows.map { it.getLong(1)!! to it.toAction() }
                    .groupBy({ it.first }, { it.second })
                baseTickets.map { it + actionMap[it.id]!! }
            }

        // Tickets after filtering and sorting
        val chunkedTargetTickets = fullTickets.asParallelStream()
            .filter(combinedFunction)
            .toList()
            .sortedWith(compareByDescending { it.id })
            .also { totalSize.set(it.count()) }
            .let { if (pageSize == 0 || it.isEmpty()) listOf(it) else it.chunked(pageSize) }
            .also { totalPages.set(it.count()) }

        val fixedPage = when {
            totalPages.get() == 0 || page < 1 -> 1
            page in 1..totalPages.get() -> page
            else -> totalPages.get()
        }

        return Result(
            filteredResults = chunkedTargetTickets.getOrElse(fixedPage - 1) { listOf() },
            totalPages = totalPages.get(),
            totalResults = totalSize.get(),
            returnedPage = fixedPage,
        )
    }

    override suspend fun getTicketIDsWithUpdatesAsync(): List<Long> {
        return connectionPool.sendPreparedStatement(
            query = "SELECT ID FROM TicketManager_V8_Tickets WHERE STATUS_UPDATE_FOR_CREATOR = ?;",
            values = listOf(true)
        )
            .asDeferredThenAwait()
            .rows.map { it.getLong(0)!! }
    }

    override suspend fun getTicketIDsWithUpdatesForAsync(creator: Creator): List<Long> {
        return connectionPool.sendPreparedStatement(
            query = "SELECT ID FROM TicketManager_V8_Tickets WHERE STATUS_UPDATE_FOR_CREATOR = ? AND CREATOR = ?;",
            values = listOf(true, creator.toString())
        )
            .asDeferredThenAwait()
            .rows.map { it.getLong(0)!! }
    }

    override suspend fun closeDatabase() {
        connectionPool.disconnect()
    }

    override fun initializeDatabase() {
        connectionPool.connect()

        tableNotExists("TicketManager_V8_Tickets")
            .thenApplyAsync {
                if (it) {
                    connectionPool.sendQuery(
                        """
                        CREATE TABLE TicketManager_V8_Tickets (
                            ID BIGINT NOT NULL AUTO_INCREMENT,
                            CREATOR VARCHAR(70) CHARACTER SET latin1 COLLATE latin1_general_ci NOT NULL,
                            PRIORITY TINYINT NOT NULL,
                            STATUS VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                            ASSIGNED_TO VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                            STATUS_UPDATE_FOR_CREATOR BOOLEAN NOT NULL,
                            KEY STATUS_V (STATUS) USING BTREE,
                            KEY STATUS_UPDATE_FOR_CREATOR_V (STATUS_UPDATE_FOR_CREATOR) USING BTREE,
                            PRIMARY KEY (ID)
                    ) ENGINE=InnoDB;
                    """.replace("\n", "").trimIndent()
                    )
                }
            }

        tableNotExists("TicketManager_V8_Actions")
            .thenApplyAsync {
                if (it) {
                    connectionPool.sendQuery(
                        """
                        CREATE TABLE TicketManager_V8_Actions (
                            ACTION_ID BIGINT NOT NULL AUTO_INCREMENT,
                            TICKET_ID BIGINT NOT NULL,
                            ACTION_TYPE VARCHAR(20) CHARACTER SET latin1 COLLATE latin1_general_ci NOT NULL,
                            CREATOR VARCHAR(64) CHARACTER SET latin1 COLLATE latin1_general_ci,
                            MESSAGE TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                            EPOCH_TIME BIGINT NOT NULL,
                            SERVER VARCHAR(100) CHARACTER SET utf8mb4,
                            WORLD VARCHAR(100) CHARACTER SET utf8mb4,
                            WORLD_X INT,
                            WORLD_Y INT,
                            WORLD_Z INT,
                            KEY TICKET_ID_V (TICKET_ID) USING BTREE,
                            PRIMARY KEY (ACTION_ID)
                        ) ENGINE=InnoDB;
                        """.replace("\n", "").trimIndent()
                    )
                }
            }
    }

    override suspend fun insertTicketForMigration(other: AsyncDatabase) {
        connectionPool.sendPreparedStatement("SELECT ID FROM TicketManager_V8_Tickets;")
            .asDeferredThenAwait()
            .rows.map { it.getLong(0)!! }
            .mapNotNull { getTicketOrNullAsync(it) }
            .forEach { other.insertTicketAsync(it) }
    }

    // PRIVATE FUNCTIONS
    private fun tableNotExists(table: String): CompletableFuture<Boolean> {
        return connectionPool.sendQuery("SHOW TABLES;")
            .thenApply { q -> q.rows.none { it.getString(0)!!.lowercase() == table.lowercase() } }
    }

    private suspend fun getActions(id: Long): List<Ticket.Action> {
        return connectionPool.sendPreparedStatement(query = "SELECT * FROM TicketManager_V8_Actions WHERE TICKET_ID = $id;")
            .asDeferredThenAwait()
            .let { r -> r.rows.map { it.toAction() }}
            .sortedBy(Ticket.Action::timestamp)
    }

    private fun RowData.toAction(): Ticket.Action {
        return Ticket.Action(
            type = Ticket.Action.Type.valueOf(getString(2)!!),
            user = getString(3)!!.let { mapToCreatorOrNull(it) ?: throw Exception("Unsupported Creator Type: $it") },
            message = getString(4),
            timestamp = getLong(5)!!,
            location = Ticket.TicketLocation(
                server = getString(6),
                world = getString(7),
                x = getInt(8),
                y = getInt(9),
                z = getInt(10),
            )
        )
    }

    private fun RowData.toTicket(): Ticket {
        return Ticket(
            id = getLong(0)!!,
            assignedTo = getString(4),
            creatorStatusUpdate = getBoolean(5)!!,
            priority = getByte(2)!!.let(::byteToPriority),
            status = getString(3)!!.let(Ticket.Status::valueOf),
            creator = getString(1)!!.let { mapToCreatorOrNull(it) ?: throw Exception("Unsupported Creator Type: $it") },
        )
    }
}