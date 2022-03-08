package com.github.hoshikurama.ticketmanager.database.impl

import com.github.hoshikurama.ticketmanager.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.database.DatabaseBuilders
import com.github.hoshikurama.ticketmanager.database.Result
import com.github.hoshikurama.ticketmanager.database.SearchConstraint
import com.github.hoshikurama.ticketmanager.misc.TicketPredicate
import com.github.hoshikurama.ticketmanager.misc.byteToPriority
import com.github.hoshikurama.ticketmanager.ticket.Console
import com.github.hoshikurama.ticketmanager.ticket.Creator
import com.github.hoshikurama.ticketmanager.ticket.Ticket
import com.github.hoshikurama.ticketmanager.ticket.User
import com.github.jasync.sql.db.ConnectionPoolConfiguration
import com.github.jasync.sql.db.RowData
import com.github.jasync.sql.db.mysql.MySQLConnectionBuilder
import com.github.jasync.sql.db.mysql.MySQLQueryResult
import com.github.jasync.sql.db.util.map
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class AsyncMySQL(
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

    override fun setAssignment(ticketID: Int, assignment: String?) {
        connectionPool.sendPreparedStatement("UPDATE TicketManager_V8_Tickets SET ASSIGNED_TO = ? WHERE ID = $ticketID;", listOf(assignment))
    }

    override fun setCreatorStatusUpdate(ticketID: Int, status: Boolean) {
        connectionPool.sendPreparedStatement("UPDATE TicketManager_V8_Tickets SET STATUS_UPDATE_FOR_CREATOR = ? WHERE ID = $ticketID;", listOf(status))
    }

    override fun setPriority(ticketID: Int, priority: Ticket.Priority) {
        connectionPool.sendPreparedStatement("UPDATE TicketManager_V8_Tickets SET PRIORITY = ? WHERE ID = $ticketID;", listOf(priority.level))
    }

    override fun setStatus(ticketID: Int, status: Ticket.Status) {
        connectionPool.sendPreparedStatement("UPDATE TicketManager_V8_Tickets SET STATUS = ? WHERE ID = $ticketID;", listOf(status.name))
    }

    override fun insertAction(id: Int, action: Ticket.Action) {
        connectionPool.sendPreparedStatement(
            query = "INSERT INTO TicketManager_V8_Actions (TICKET_ID, ACTION_TYPE, CREATOR, MESSAGE, TIMESTAMP, SERVER, WORLD, WORLD_X, WORLD_Y, WORLD_Z) VALUES (?,?,?,?,?,?,?,?,?,?);",
            listOf(
                id,
                action.type.name,
                action.user.toString(),
                action.message,
                action.timestamp,
            )
        )
    }

    override fun insertTicketAsync(ticket: Ticket): CompletableFuture<Int> {
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
            .thenApplyAsync {
                (it as MySQLQueryResult).lastInsertId.toInt()
            }

        id.thenApplyAsync { id1 -> ticket.actions.forEach { insertAction(id1, it) } }

        return id
    }

    override fun getTicketsAsync(ids: List<Int>): CompletableFuture<List<Ticket>> {
        val idsSQL = ids.joinToString(", ") { "$it" }

        val ticketsCF = connectionPool.sendQuery("SELECT * FROM TicketManager_V8_Tickets WHERE ID IN ($idsSQL);")
            .thenApplyAsync { r -> r.rows.map { it.toTicket() } }

        val actionsCF = connectionPool.sendQuery("SELECT * FROM TicketManager_V8_Actions WHERE TICKET_ID IN ($idsSQL);")
            .thenApplyAsync { r -> r.rows.map { it.getInt(1)!! to it.toAction() } }

        return CompletableFuture.allOf(ticketsCF, actionsCF)
            .thenApplyAsync {
                val tickets = ticketsCF.get()
                val actionMap = actionsCF.get().groupBy({ it.first }, { it.second })

                tickets.map { it + actionMap[it.id]!! }
            }
    }

    override fun getTicketOrNullAsync(id: Int): CompletableFuture<Ticket?> {

        val ticketCF = connectionPool.sendPreparedStatement("SELECT * FROM TicketManager_V8_Tickets WHERE ID = ?", listOf(id))
            .thenApply { r -> r.rows.firstOrNull()?.toTicket() }

        return getActions(id).thenCombine(ticketCF) { actions, ticket -> ticket?.let { it + actions } }
    }

    override fun getOpenTicketsAsync(page: Int, pageSize: Int): CompletableFuture<Result> {
        return getBasicTicketsFilteredByAsync(page, pageSize, "SELECT ID FROM TicketManager_V8_Tickets WHERE STATUS = ?;", listOf(Ticket.Status.OPEN.name))
    }

    override fun getOpenTicketsAssignedToAsync(
        page: Int,
        pageSize: Int,
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): CompletableFuture<Result> {
        val groupsSQLStatement = unfixedGroupAssignment.joinToString(" OR ") { "ASSIGNED_TO = ?" }
        val groupsFixed = unfixedGroupAssignment.map { "::$it" }

        return getBasicTicketsFilteredByAsync(page, pageSize, "SELECT ID FROM TicketManager_V8_Tickets WHERE STATUS = ? AND ($groupsSQLStatement);", listOf(Ticket.Status.OPEN.name) + groupsFixed)
    }

    override fun getOpenTicketsNotAssignedAsync(page: Int, pageSize: Int): CompletableFuture<Result> {
        return getBasicTicketsFilteredByAsync(page, pageSize, "SELECT ID FROM TicketManager_V8_Tickets WHERE STATUS = ? AND ASSIGNED_TO IS NULL", listOf(Ticket.Status.OPEN.name))
    }

    private fun getBasicTicketsFilteredByAsync(page: Int, pageSize: Int, preparedQuery: String, values: List<Any?>): CompletableFuture<Result> {
        val totalSize = AtomicInteger(0)
        val totalPages = AtomicInteger(0)

        return connectionPool.sendPreparedStatement(preparedQuery, values)
            .thenComposeAsync { r -> r.rows.map { it.getInt(0)!! }.run(::getTicketsAsync) }
            .thenApplyAsync { l -> l.sortedWith(compareByDescending<Ticket> { it.priority.level }.thenByDescending { it.id }) }
            .thenApply { totalSize.set(it.count()); it }
            .thenApply { if (pageSize == 0 || it.isEmpty()) listOf(it) else it.chunked(pageSize) }
            .thenApply { totalPages.set(it.count()); it }
            .thenApplyAsync {
                val fixedPage = when {
                    totalPages.get() == 0 || page < 1 -> 1
                    page in 1..totalPages.get()-> page
                    else -> totalPages.get()
                }

                Result(
                    filteredResults = it.getOrElse(fixedPage-1) { listOf() },
                    totalPages = totalPages.get(),
                    totalResults = totalSize.get(),
                    returnedPage = fixedPage,
                )
            }
    }

    override fun massCloseTickets(lowerBound: Int, upperBound: Int, actor: Creator, ticketLoc:  Ticket.TicketLocation) {
        val rows = connectionPool.sendPreparedStatement("SELECT ID FROM TicketManager_V8_Tickets WHERE STATUS = ? AND ID BETWEEN $lowerBound AND $upperBound;", listOf(Ticket.Status.OPEN.name))
            .thenApply { r -> r.rows.map { it.getInt(0)!! } }

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
                        timestamp = Instant.now().epochSecond,
                        location = ticketLoc
                    ),
                    id = it
                )
            }
        }
    }

    override fun countOpenTicketsAsync(): CompletableFuture<Int> {
        return connectionPool.sendPreparedStatement("SELECT COUNT(*) FROM TicketManager_V4_Tickets WHERE STATUS = ?;", listOf(Ticket.Status.OPEN.name))
            .thenApply { r -> r.rows.firstNotNullOf { it.getLong(0)?.toInt() } }
    }

    override fun countOpenTicketsAssignedToAsync(
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): CompletableFuture<Int> {
        val groupsSQLStatement = unfixedGroupAssignment.joinToString(" OR ") { "ASSIGNED_TO = ?" }
        val groupsFixed = unfixedGroupAssignment.map { "::$it" }

        return connectionPool.sendPreparedStatement("SELECT COUNT(*) FROM TicketManager_V4_Tickets WHERE STATUS = ? AND ($groupsSQLStatement);", listOf(Ticket.Status.OPEN.name) + groupsFixed)
            .thenApply { r -> r.rows.firstNotNullOf { it.getLong(0)?.toInt() } }
    }

    override fun searchDatabaseAsync(
        constraints: SearchConstraint,
        page: Int,
        pageSize: Int
    ): CompletableFuture<Result> {
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

        val ticketsCF = connectionPool.sendPreparedStatement("$searchString;", args)
            .thenApply { r -> r.rows.map { it.toTicket() } }

        return ticketsCF.thenApplyAsync { l -> l.map { it.id }.joinToString(", ") }
            .thenComposeAsync { connectionPool.sendQuery("SELECT * FROM TicketManager_V8_Actions WHERE TICKET_ID IN ($it);") }
            .thenApplyAsync { r -> r.rows.map { it.getInt(1)!! to it.toAction() } }
            .thenCombineAsync(ticketsCF) { actions, tickets ->
                val actionMap = actions.groupBy({ it.first }, { it.second })
                tickets.map { it + actionMap[it.id]!! }
            }
            .thenApplyAsync { l -> l.filter(combinedFunction).sortedWith(compareByDescending { it.id}) }
            .thenApply { totalSize.set(it.count()); it }
            .thenApply { if (pageSize == 0 || it.isEmpty()) listOf(it) else it.chunked(pageSize) }
            .thenApply { totalPages.set(it.count()); it }
            .thenApply {
                val fixedPage = when {
                    totalPages.get() == 0 || page < 1 -> 1
                    page in 1..totalPages.get() -> page
                    else -> totalPages.get()
                }

                Result(
                    filteredResults = it.getOrElse(fixedPage-1) { listOf() },
                    totalPages = totalPages.get(),
                    totalResults = totalSize.get(),
                    returnedPage = fixedPage,
                )
            }
    }

    override fun getTicketIDsWithUpdatesAsync(): CompletableFuture<List<Int>> {
        return connectionPool.sendPreparedStatement(
            query = "SELECT ID FROM TicketManager_V8_Tickets WHERE STATUS_UPDATE_FOR_CREATOR = ?;",
            values = listOf(true)
        )
            .thenApplyAsync { r -> r.rows.map { it.getInt(0)!! } }
    }

    override fun getTicketIDsWithUpdatesForAsync(creator: Creator): CompletableFuture<List<Int>> {
        return connectionPool.sendPreparedStatement(
            query = "SELECT ID FROM TicketManager_V8_Tickets WHERE STATUS_UPDATE_FOR_CREATOR = ? AND CREATOR = ?;",
            values = listOf(true, creator.toString())
        )
            .thenApplyAsync { r -> r.rows.map { it.getInt(0)!! } }
    }

    override fun closeDatabase() {
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
                            ID INT NOT NULL AUTO_INCREMENT,
                            CREATOR VARCHAR(70) CHARACTER SET latin1 COLLATE latin1_general_ci NOT NULL,
                            PRIORITY TINYINT NOT NULL,
                            STATUS VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                            ASSIGNED_TO VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                            STATUS_UPDATE_FOR_CREATOR BOOLEAN NOT NULL,
                            KEY STATUS_V8 (STATUS) USING BTREE,
                            KEY STATUS_UPDATE_FOR_CREATOR_V8 (STATUS_UPDATE_FOR_CREATOR) USING BTREE,
                            PRIMARY KEY (ID)
                    ) ENGINE=InnoDB;
                    """.trimIndent()
                    )
                }
            }

        tableNotExists("TicketManager_V8_Actions")
            .thenApplyAsync {
                if (it) {
                    connectionPool.sendQuery(
                        """
                        CREATE TABLE TicketManager_V8_Actions (
                            ACTION_ID INT NOT NULL AUTO_INCREMENT,
                            TICKET_ID INT NOT NULL,
                            ACTION_TYPE VARCHAR(20) CHARACTER SET latin1 COLLATE latin1_general_ci NOT NULL,
                            CREATOR VARCHAR(70) CHARACTER SET latin1 COLLATE latin1_general_ci,
                            MESSAGE TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                            TIMESTAMP BIGINT NOT NULL,
                            SERVER VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                            WORLD VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                            WORLD_X INT,
                            WORLD_Y INT,
                            WORLD_Z INT,
                            PRIMARY KEY (ACTION_ID)
                        ) ENGINE=InnoDB;
                        """.trimIndent()
                    )
                }
            }
    }

    override fun migrateDatabase(
        to: AsyncDatabase.Type,
        databaseBuilders: DatabaseBuilders,
        onBegin:  () -> Unit,
        onComplete:  () -> Unit,
        onError: (Exception) -> Unit
    ) {
        TODO("Not yet implemented")
    }


    // PRIVATE FUNCTIONS
    private fun tableNotExists(table: String): CompletableFuture<Boolean> {
        return connectionPool.sendQuery("SHOW TABLES;")
            .thenApply { q -> q.rows.none { it.getString(0)!!.lowercase() == table.lowercase() } }
    }

    private fun getActions(id: Int): CompletableFuture<List<Ticket.Action>> {
        return connectionPool.sendPreparedStatement(query = "SELECT * FROM TicketManager_V8_Actions WHERE TICKET_ID = $id;")
            .map { r -> r.rows.map { it.toAction() } }
    }

    private fun RowData.toAction(): Ticket.Action {
        return Ticket.Action(
            type = Ticket.Action.Type.valueOf(getString(2)!!),
            user = getString(3)!!.split(".").let {
                when (it[0]) {
                    "CONSOLE" -> Console
                    "USER" -> User(UUID.fromString(it[1]))
                    else -> throw Exception("Unsupported Creator Type: ${it[0]}")
                }
            },
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
            id = getInt(0)!!,
            assignedTo = getString(4),
            creatorStatusUpdate = getBoolean(5)!!,
            priority = getByte(2)!!.let(::byteToPriority),
            status = getString(3)!!.let(Ticket.Status::valueOf),
            creator = getString(1)!!.split(".").let {
                when (it[0]) {
                    "CONSOLE" -> Console
                    "USER" -> User(UUID.fromString(it[1]))
                    else -> throw Exception("Unsupported Creator Type: ${it[0]}")
                }
            },
        )
    }
}

