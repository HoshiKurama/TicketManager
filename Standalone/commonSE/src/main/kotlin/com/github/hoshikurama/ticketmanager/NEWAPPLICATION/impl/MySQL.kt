package com.github.hoshikurama.ticketmanager.NEWAPPLICATION.impl

import com.github.hoshikurama.ticketmanager.api.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.database.DBResult
import com.github.hoshikurama.ticketmanager.api.database.SearchConstraints
import com.github.hoshikurama.ticketmanager.api.ticket.Creator
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import com.github.hoshikurama.ticketmanager.commonse.api.impl.DBResultSTD
import com.github.hoshikurama.ticketmanager.commonse.api.impl.TicketSTD
import com.github.hoshikurama.ticketmanager.commonse.old.misc.*
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

    private val connectionPool = MySQLConnectionBuilder.createConnectionPool(
        ConnectionPoolConfiguration(
            host = host,
            port = port.toInt(),
            database = dbName,
            username = username,
            password = password,
        )
    )

    override fun setAssignmentAsync(ticketID: Long, assignment: String?): CompletableFuture<Void> {
        return connectionPool.sendPreparedStatement(
            "UPDATE TicketManager_V8_Tickets SET ASSIGNED_TO = ? WHERE ID = $ticketID;",
            listOf(assignment)
        ).thenAcceptAsync { } // Just used to pass back Void
    }

    override fun setCreatorStatusUpdateAsync(ticketID: Long, status: Boolean): CompletableFuture<Void> {
        return connectionPool.sendPreparedStatement(
            "UPDATE TicketManager_V8_Tickets SET STATUS_UPDATE_FOR_CREATOR = ? WHERE ID = $ticketID;",
            listOf(status)
        ).thenAcceptAsync { } // Just used to pass back Void
    }

    override fun setPriorityAsync(ticketID: Long, priority: Ticket.Priority): CompletableFuture<Void> {
        return connectionPool.sendPreparedStatement(
            "UPDATE TicketManager_V8_Tickets SET PRIORITY = ? WHERE ID = $ticketID;",
            listOf(priority.level)
        ).thenAcceptAsync { } // Just used to pass back Void
    }

    override fun setStatusAsync(ticketID: Long, status: Ticket.Status): CompletableFuture<Void> {
        return connectionPool.sendPreparedStatement(
            "UPDATE TicketManager_V8_Tickets SET STATUS = ? WHERE ID = $ticketID;",
            listOf(status.name)
        ).thenAcceptAsync { } // Just used to pass back Void
    }

    override fun insertActionAsync(id: Long, action: Ticket.Action): CompletableFuture<Void> {
        return connectionPool.sendPreparedStatement(
            query = "INSERT INTO TicketManager_V8_Actions (TICKET_ID, ACTION_TYPE, CREATOR, MESSAGE, EPOCH_TIME, SERVER, WORLD, WORLD_X, WORLD_Y, WORLD_Z) VALUES (?,?,?,?,?,?,?,?,?,?);",
            listOf(
                id,
                action.type.getTypeEnum().name,
                action.user.asString(),
                action.type.getMessage(),
                action.timestamp,
                action.location.server,
                action.location.world,
                action.location.x,
                action.location.y,
                action.location.z,
            )
        ).thenAcceptAsync { }
    }

    override fun insertNewTicketAsync(ticket: Ticket): CompletableFuture<Long> {
        val id = connectionPool.sendPreparedStatement(
            query = "INSERT INTO TicketManager_V8_Tickets (CREATOR, PRIORITY, STATUS, ASSIGNED_TO, STATUS_UPDATE_FOR_CREATOR) VALUES (?,?,?,?,?);",
            listOf(
                ticket.creator.asString(),
                ticket.priority.level,
                ticket.status.name,
                ticket.assignedTo,
                ticket.creatorStatusUpdate,
            )
        ).thenApplyAsync { (it as MySQLQueryResult).lastInsertId }

        id.thenApplyAsync { id1 -> ticket.actions.forEach { insertActionAsync(id1, it) } }

        return id
    }

    private fun getTicketsAsync(ids: List<Long>): CompletableFuture<List<Ticket>> {
        if (ids.isEmpty()) return CompletableFuture.completedFuture(listOf())
        val idsSQL = ids.joinToString(", ") { "$it" }

        val ticketsCF = connectionPool.sendQuery("SELECT * FROM TicketManager_V8_Tickets WHERE ID IN ($idsSQL);")
            .thenApplyAsync { r -> r.rows.map { it.toTicket() } }

        val actionsCF = connectionPool.sendQuery("SELECT * FROM TicketManager_V8_Actions WHERE TICKET_ID IN ($idsSQL);")
            .thenApplyAsync { r -> r.rows.map { it.getLong(1)!! to it.toAction() } }

        return CompletableFuture.allOf(ticketsCF, actionsCF)
            .thenApplyAsync {
                val tickets = ticketsCF.join()
                val actionMap = actionsCF.join()
                    .groupBy({ it.first }, { it.second })
                    .mapValues { it.value.sortedBy(Ticket.Action::timestamp) }

                tickets.map { it + actionMap[it.id]!! }
            }
    }

    override fun getTicketOrNullAsync(id: Long): CompletableFuture<Ticket?> {
        val ticketCF = connectionPool.sendPreparedStatement("SELECT * FROM TicketManager_V8_Tickets WHERE ID = ?", listOf(id))
                .thenApply { r -> r.rows.firstOrNull()?.toTicket() }

        return getActionsAsync(id).thenCombine(ticketCF) { actions, ticket -> ticket?.let { it + actions } }
    }

    override fun getOpenTicketsAsync(page: Int, pageSize: Int): CompletableFuture<DBResult> {
        return ticketsFilteredByAsync(
            page, pageSize, "SELECT ID FROM TicketManager_V8_Tickets WHERE STATUS = ?;", listOf(
                Ticket.Status.OPEN.name
            )
        )
    }

    override fun getOpenTicketsAssignedToAsync(
        page: Int,
        pageSize: Int,
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): CompletableFuture<DBResult> {
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

    override fun getOpenTicketsNotAssignedAsync(page: Int, pageSize: Int): CompletableFuture<DBResult> {
        return ticketsFilteredByAsync(
            page, pageSize, "SELECT ID FROM TicketManager_V8_Tickets WHERE STATUS = ? AND ASSIGNED_TO IS NULL", listOf(
                Ticket.Status.OPEN.name
            )
        )
    }

    private fun ticketsFilteredByAsync(
        page: Int,
        pageSize: Int,
        preparedQuery: String,
        values: List<Any?>
    ): CompletableFuture<DBResult> {
        val totalSize = AtomicInteger(0)
        val totalPages = AtomicInteger(0)

        return connectionPool.sendPreparedStatement(preparedQuery, values)
            .thenComposeAsync { r -> r.rows.map { it.getLong(0)!! }.run(::getTicketsAsync) }
            .thenApplyAsync { tickets ->
                val sortedTickets = tickets.sortedWith(compareByDescending<Ticket> { it.priority.level }
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

                DBResultSTD(
                    filteredResults = chunkedTickets.getOrElse(fixedPage - 1) { listOf() },
                    totalPages = totalPages.get(),
                    totalResults = totalSize.get(),
                    returnedPage = fixedPage,
                )
            }
    }

    override fun massCloseTicketsAsync(
        lowerBound: Long,
        upperBound: Long,
        actor: Creator,
        ticketLoc: Ticket.CreationLocation
    ): CompletableFuture<Void> {
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

        return rows.thenAcceptAsync { r ->
            r.map {
                insertActionAsync(
                    action = TicketSTD.ActionSTD(
                        type = TicketSTD.ActionSTD.MassCloseSTD,
                        user = actor,
                        timestamp = curTime,
                        location = ticketLoc
                    ),
                    id = it
                )
            }.flatten()
        }
    }

    override fun countOpenTicketsAsync(): CompletableFuture<Long> {
        return connectionPool.sendPreparedStatement(
            "SELECT COUNT(*) FROM TicketManager_V8_Tickets WHERE STATUS = ?;", listOf(
                Ticket.Status.OPEN.name
            )
        )
            .thenApply { r -> r.rows.firstNotNullOf { it.getLong(0) } }
    }

    override fun countOpenTicketsAssignedToAsync(
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): CompletableFuture<Long> {
        val groupsFixed = unfixedGroupAssignment.map { "::$it" }
        val assignedSQL = (unfixedGroupAssignment + assignment).joinToString(" OR ") { "ASSIGNED_TO = ?" }
        val args = (listOf(Ticket.Status.OPEN.name, assignment) + groupsFixed)

        return connectionPool.sendPreparedStatement(
            "SELECT COUNT(*) FROM TicketManager_V8_Tickets WHERE STATUS = ? AND ($assignedSQL);",
            args
        )
            .thenApply { r -> r.rows.firstNotNullOf { it.getLong(0) } }
    }

    override fun searchDatabaseAsync(
        constraints: SearchConstraints,
        page: Int,
        pageSize: Int
    ): CompletableFuture<DBResult> {
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
                args.add(Ticket.Action.Type.TypeEnum.CLOSE.name)
                args.add(Ticket.Action.Type.TypeEnum.MASS_CLOSE.name)
                args.add(value.toString())
            }
            world?.run {
                searches.add("ID IN (SELECT DISTINCT TICKET_ID FROM TicketManager_V8_Actions WHERE WORLD = ?)")
                args.add(value)
            }

            // Functional Searches
            lastClosedBy?.run {
                { t: Ticket ->
                    t.actions.lastOrNull { it.type is Ticket.Action.Type.CLOSE || it.type is Ticket.Action.Type.MASS_CLOSE }
                        ?.run { user equalTo value } ?: false
                }
            }?.apply(functions::add)
            creationTime?.run { { t: Ticket -> t.actions[0].timestamp >= value } }?.apply(functions::add)
            constraints.run {
                keywords?.run {
                    { t: Ticket ->
                        val comments = t.actions
                            .filter { it.type is Ticket.Action.Type.OPEN || it.type is Ticket.Action.Type.COMMENT }
                            .map { it.type.getMessage()!! }
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
        var searchString = "SELECT ID FROM TicketManager_V8_Tickets"
        if (searches.isNotEmpty())
            searchString += " WHERE ${searches.joinToString(" AND ")}"

        // Searches
        val totalSize = AtomicInteger(0)
        val totalPages = AtomicInteger(0)

        // Query
        return connectionPool.sendPreparedStatement("$searchString;", args)
            .thenComposeAsync { r -> r.rows
                .map { it.getLong(0)!! }
                .toList() // Note: This hopefully solves some weird Null issue
                .run(::getTicketsAsync)
            }
            .thenApplyAsync { fullTickets ->
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

                DBResultSTD(
                    filteredResults = chunkedTargetTickets.getOrElse(fixedPage - 1) { listOf() },
                    totalPages = totalPages.get(),
                    totalResults = totalSize.get(),
                    returnedPage = fixedPage,
                )
            }
    }

    override fun getTicketIDsWithUpdatesAsync(): CompletableFuture<List<Long>> {
        return connectionPool.sendPreparedStatement(
            query = "SELECT ID FROM TicketManager_V8_Tickets WHERE STATUS_UPDATE_FOR_CREATOR = ?;",
            values = listOf(true)
        )
            .thenApplyAsync { r -> r.rows.map { it.getLong(0)!! } }
    }

    override fun getTicketIDsWithUpdatesForAsync(creator: Creator): CompletableFuture<List<Long>> {
        return connectionPool.sendPreparedStatement(
            query = "SELECT ID FROM TicketManager_V8_Tickets WHERE STATUS_UPDATE_FOR_CREATOR = ? AND CREATOR = ?;",
            values = listOf(true, creator.asString())
        )
            .thenApplyAsync { r -> r.rows.map { it.getLong(0)!! } }
    }

    override fun getOwnedTicketIDsAsync(creator: Creator): CompletableFuture<List<Long>> {
        return connectionPool.sendPreparedStatement(
            query = "SELECT ID FROM TicketManager_V8_Tickets WHERE CREATOR = ?",
            values = listOf(creator.asString())
        )
            .thenApplyAsync { r -> r.rows.map { it.getLong(0)!! } }
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

    // PRIVATE FUNCTIONS
    private fun tableNotExists(table: String): CompletableFuture<Boolean> {
        return connectionPool.sendQuery("SHOW TABLES;")
            .thenApply { q -> q.rows.none { it.getString(0)!!.lowercase() == table.lowercase() } }
    }

    private fun getActionsAsync(id: Long): CompletableFuture<List<Ticket.Action>> {
        return connectionPool.sendPreparedStatement(query = "SELECT * FROM TicketManager_V8_Actions WHERE TICKET_ID = $id;")
            .thenApplyAsync { r -> r.rows
                .map { it.toAction() }
                .sortedBy(Ticket.Action::timestamp)
            }
    }

    private fun RowData.toAction(): TicketSTD.ActionSTD {
        return TicketSTD.ActionSTD(
            type = kotlin.run {
                val typeEnum = Ticket.Action.Type.TypeEnum.valueOf(getString(2)!!)
                val msg = getString(4)

                when (typeEnum) {
                    Ticket.Action.Type.TypeEnum.ASSIGN -> TicketSTD.ActionSTD.AssignSTD(msg)
                    Ticket.Action.Type.TypeEnum.CLOSE -> TicketSTD.ActionSTD.CloseSTD
                    Ticket.Action.Type.TypeEnum.COMMENT -> TicketSTD.ActionSTD.CommentSTD(msg!!)
                    Ticket.Action.Type.TypeEnum.OPEN -> TicketSTD.ActionSTD.OpenSTD(msg!!)
                    Ticket.Action.Type.TypeEnum.REOPEN -> TicketSTD.ActionSTD.ReopenSTD
                    Ticket.Action.Type.TypeEnum.SET_PRIORITY -> TicketSTD.ActionSTD.SetPrioritySTD(byteToPriority(msg!!.toByte()))
                    Ticket.Action.Type.TypeEnum.MASS_CLOSE -> TicketSTD.ActionSTD.MassCloseSTD
                }
            },
            user = getString(3)!!.let { mapToCreatorOrNull(it) ?: throw Exception("Unsupported Creator Type: $it") },
            timestamp = getLong(5)!!,
            location = kotlin.run {
                val x = getInt(8)

                if (x == null) TicketSTD.ConsoleTicketLocationSTD(server = getString(6))
                else TicketSTD.PlayerTicketLocationSTD(
                    server = getString(6),
                    world = getString(7)!!,
                    x = x,
                    y = getInt(9)!!,
                    z = getInt(10)!!,
                )
            }
        )
    }

    private fun RowData.toTicket(): TicketSTD {
        return TicketSTD(
            id = getLong(0)!!,
            assignedTo = getString(4),
            creatorStatusUpdate = getBoolean(5)!!,
            priority = getByte(2)!!.let(::byteToPriority),
            status = getString(3)!!.let(Ticket.Status::valueOf),
            creator = getString(1)!!.let { mapToCreatorOrNull(it) ?: throw Exception("Unsupported Creator Type: $it") },
        )
    }
}

private fun Ticket.Action.Type.getMessage(): String? = when (this) {
    is Ticket.Action.Type.OPEN -> message
    is Ticket.Action.Type.ASSIGN -> assignment
    is Ticket.Action.Type.COMMENT -> comment
    is Ticket.Action.Type.SET_PRIORITY -> priority.level.toString()
    else -> null
}