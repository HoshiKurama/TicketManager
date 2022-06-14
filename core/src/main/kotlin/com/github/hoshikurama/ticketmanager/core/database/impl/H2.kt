package com.github.hoshikurama.ticketmanager.core.database.impl

import com.github.hoshikurama.ticketmanager.core.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.core.database.Result
import com.github.hoshikurama.ticketmanager.core.database.SearchConstraint
import com.github.hoshikurama.ticketmanager.core.misc.TicketPredicate
import com.github.hoshikurama.ticketmanager.core.misc.byteToPriority
import com.github.hoshikurama.ticketmanager.core.misc.mapToCreatorOrNull
import com.github.hoshikurama.ticketmanager.core.ticket.Creator
import com.github.hoshikurama.ticketmanager.core.ticket.Ticket
import kotliquery.*
import org.h2.jdbcx.JdbcConnectionPool
import java.sql.Statement
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class H2(
    absoluteDataFolderPath: String
) : AsyncDatabase {
    override val type = AsyncDatabase.Type.H2

    private val connectionPool: JdbcConnectionPool
    init {
        val fixedURL = "jdbc:h2:file:$absoluteDataFolderPath/TicketManager-H2-V8.db"
            .replace("C:", "")
            .replace("\\", "/")

        connectionPool = JdbcConnectionPool.create(fixedURL,"","")
        connectionPool.maxConnections = 6

    }

    private inline fun <T> usingSession(crossinline f: Session.() -> T): T {
        return using(sessionOf(connectionPool)) { f(it) }
    }


    override fun setAssignment(ticketID: Long, assignment: String?) {
        usingSession {
            update(queryOf("UPDATE \"TicketManager_V8_Tickets\" SET ASSIGNED_TO = ? WHERE ID = ?;", assignment, ticketID))
        }
    }

    override fun setCreatorStatusUpdate(ticketID: Long, status: Boolean) {
        usingSession {
            update(queryOf("UPDATE \"TicketManager_V8_Tickets\" SET STATUS_UPDATE_FOR_CREATOR = ? WHERE ID = ?;", status, ticketID))
        }
    }

    override fun setPriority(ticketID: Long, priority: Ticket.Priority) {
        usingSession {
            update(queryOf("UPDATE \"TicketManager_V8_Tickets\" SET PRIORITY = ? WHERE ID = ?;", priority.level, ticketID))
        }
    }

    override fun setStatus(ticketID: Long, status: Ticket.Status) {
        usingSession {
            update(queryOf("UPDATE \"TicketManager_V8_Tickets\" SET STATUS = ? WHERE ID = ?;", status.name, ticketID))
        }
    }

    override fun insertAction(id: Long, action: Ticket.Action) {
        usingSession {
            update(
                queryOf("INSERT INTO \"TicketManager_V8_Actions\" (TICKET_ID, ACTION_TYPE, CREATOR, MESSAGE, EPOCH_TIME, SERVER, WORLD, WORLD_X, WORLD_Y, WORLD_Z) VALUES (?,?,?,?,?,?,?,?,?,?);",
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
    }

    override fun insertTicketAsync(ticket: Ticket): CompletableFuture<Long> {
        var connection: java.sql.Connection? = null

        val id = try {
            connection = connectionPool.connection
            val statement = connection.prepareStatement("INSERT INTO \"TicketManager_V8_Tickets\" (CREATOR, PRIORITY, STATUS, ASSIGNED_TO, STATUS_UPDATE_FOR_CREATOR) VALUES(?,?,?,?,?);", Statement.RETURN_GENERATED_KEYS)
            statement.setString(1, ticket.creator.toString())
            statement.setByte(2, ticket.priority.level)
            statement.setString(3, ticket.status.name)
            statement.setString(4, ticket.assignedTo)
            statement.setBoolean(5, ticket.creatorStatusUpdate)
            statement.executeUpdate()
            statement.generatedKeys.let {
                it.next()
                it.getLong(1)
            }
        } catch (e: Exception) {
            throw e
        } finally {
            connection?.close()
        }

        CompletableFuture.runAsync {
            ticket.actions.forEach {
                usingSession {
                    update(
                        queryOf("INSERT INTO \"TicketManager_V8_Actions\" (TICKET_ID, ACTION_TYPE, CREATOR, MESSAGE, EPOCH_TIME, SERVER, WORLD, WORLD_X, WORLD_Y, WORLD_Z) VALUES (?,?,?,?,?,?,?,?,?,?);",
                            id,
                            it.type.name,
                            it.user.toString(),
                            it.message,
                            it.timestamp,
                            it.location.server,
                            it.location.world,
                            it.location.x,
                            it.location.y,
                            it.location.z,
                        )
                    )
                }
            }
        }

        return CompletableFuture.completedFuture(id)
    }

    override fun getTicketsAsync(ids: List<Long>): CompletableFuture<List<Ticket>> {
        val idsSQL = ids.joinToString(", ") { "$it" }

        val ticketsCF = CompletableFuture.supplyAsync {
            usingSession {
                run(queryOf("SELECT * FROM \"TicketManager_V8_Tickets\" WHERE ID IN ($idsSQL);")
                    .map { it.toTicket() }
                    .asList
                )
            }
        }

        val actionsCF = CompletableFuture.supplyAsync {
            usingSession {
                run(queryOf("SELECT * FROM \"TicketManager_V8_Actions\" WHERE TICKET_ID IN ($idsSQL);")
                    .map { it.long(2) to it.toAction() }
                    .asList
                )
            }
        }

        return CompletableFuture.allOf(actionsCF, ticketsCF).thenApplyAsync {
            val tickets = ticketsCF.join()
            val actionMap = actionsCF.get()
                .groupBy({ it.first }, { it.second })
                .mapValues { it.value.sortedBy(Ticket.Action::timestamp) }

            tickets.map { it + actionMap[it.id]!! }
        }
    }

    override fun getTicketOrNullAsync(id: Long): CompletableFuture<Ticket?> {

        val ticketCF = CompletableFuture.supplyAsync {
            usingSession {
                run(queryOf("SELECT * FROM \"TicketManager_V8_Tickets\" WHERE ID = ?", id)
                    .map { it.toTicket() }
                    .asSingle
                )
            }
        }

        val actionsCF = CompletableFuture.supplyAsync {
            usingSession {
                run(
                    queryOf("SELECT * FROM \"TicketManager_V8_Actions\" WHERE TICKET_ID = ?", id)
                        .map { it.toAction() }
                        .asList
                )
            }.sortedBy(Ticket.Action::timestamp)
        }

        return CompletableFuture.allOf(ticketCF, actionsCF).thenApplyAsync {
            val ticket = ticketCF.join()
            val actions = actionsCF.join()

            ticket?.let { it + actions }
        }
    }

    override fun getOpenTicketsAsync(page: Int, pageSize: Int): CompletableFuture<Result> {
        return ticketsFilteredByAsync(page, pageSize, "SELECT * FROM \"TicketManager_V8_Tickets\" WHERE STATUS = ?;", Ticket.Status.OPEN.name)
    }

    override fun getOpenTicketsAssignedToAsync(
        page: Int,
        pageSize: Int,
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): CompletableFuture<Result> {
        val groupsFixed = unfixedGroupAssignment.map { "::$it" }
        val assignedSQL = (unfixedGroupAssignment + assignment).joinToString(" OR ") { "ASSIGNED_TO = ?" }
        val args = (listOf(Ticket.Status.OPEN.name, assignment) + groupsFixed).toTypedArray()

        return ticketsFilteredByAsync(page, pageSize, "SELECT * FROM \"TicketManager_V8_Tickets\" WHERE STATUS = ? AND ($assignedSQL);", *args)
    }

    override fun getOpenTicketsNotAssignedAsync(page: Int, pageSize: Int): CompletableFuture<Result> {
        return ticketsFilteredByAsync(page, pageSize, "SELECT * FROM \"TicketManager_V8_Tickets\" WHERE STATUS = ? AND ASSIGNED_TO IS NULL", Ticket.Status.OPEN.name)
    }

    private fun ticketsFilteredByAsync(page: Int, pageSize: Int, preparedQuery: String, vararg params: Any?): CompletableFuture<Result> {
        val totalSize = AtomicInteger(0)
        val totalPages = AtomicInteger(0)

        return CompletableFuture.supplyAsync {
            usingSession {
                run(queryOf(preparedQuery, *params)
                    .map { it.long(1) }
                    .asList
            ) }
        }
            .thenComposeAsync(::getTicketsAsync)
            .thenApplyAsync { l -> l.sortedWith(compareByDescending<Ticket> { it.priority.level }.thenByDescending(Ticket::id)) }
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

    override fun massCloseTickets(lowerBound: Long, upperBound: Long, actor: Creator, ticketLoc: Ticket.TicketLocation) {
        val curTime = Instant.now().epochSecond

        val ids = usingSession {
            run(queryOf("SELECT ID FROM \"TicketManager_V8_Tickets\" WHERE STATUS = ? AND ID BETWEEN ? AND ?;", Ticket.Status.OPEN.name, lowerBound, upperBound)
                .map { it.long(1) }
                .asList
            )
        }

        // Sets Ticket statuses
        CompletableFuture.runAsync {
            usingSession {
                val idString = ids.joinToString(", ")
                update(queryOf("UPDATE \"TicketManager_V8_Tickets\" SET STATUS = ? WHERE ID IN ($idString);", Ticket.Status.CLOSED.name))
            }
        }

        // Adds Actions
        CompletableFuture.runAsync {
            val action = Ticket.Action(
                type = Ticket.Action.Type.MASS_CLOSE,
                user = actor,
                message = null,
                timestamp = curTime,
                location = ticketLoc
            )
            ids.forEach { insertAction(it, action) }
        }
    }

    override fun countOpenTicketsAsync(): CompletableFuture<Long> {
        return CompletableFuture.completedFuture(
            usingSession {
                run(queryOf("SELECT COUNT(*) FROM \"TicketManager_V8_Tickets\" WHERE STATUS = ?;", Ticket.Status.OPEN.name)
                    .map { it.long(1) }
                    .asSingle
                )
            }
        )
    }

    override fun countOpenTicketsAssignedToAsync(
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): CompletableFuture<Long> {
        val groupsFixed = unfixedGroupAssignment.map { "::$it" }
        val assignedSQL = (unfixedGroupAssignment + assignment).joinToString(" OR ") { "ASSIGNED_TO = ?" }
        val args = (listOf(Ticket.Status.OPEN.name, assignment) + groupsFixed).toTypedArray()

        return CompletableFuture.completedFuture(
            usingSession {
                run(queryOf("SELECT COUNT(*) FROM \"TicketManager_V8_Tickets\" WHERE STATUS = ? AND ($assignedSQL);", *args)
                    .map { it.long(1) }
                    .asSingle
                )
            }
        )
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
                searches.add("ID IN (SELECT DISTINCT TICKET_ID FROM \"TicketManager_V8_Actions\" WHERE (ACTION_TYPE = ? OR ACTION_TYPE = ?) AND CREATOR = ?)")
                args.add(Ticket.Action.Type.CLOSE)
                args.add(Ticket.Action.Type.MASS_CLOSE)
                args.add(value.toString())
            }
            world?.run {
                searches.add("ID IN (SELECT DISTINCT TICKET_ID FROM \"TicketManager_V8_Actions\" WHERE WORLD = ?)")
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
        var searchString = "SELECT * FROM \"TicketManager_V8_Tickets\""
        if (searches.isNotEmpty())
            searchString += " WHERE ${searches.joinToString(" AND ")}"

        // Searches
        val totalSize = AtomicInteger(0)
        val totalPages = AtomicInteger(0)

        val ticketsCF = CompletableFuture.supplyAsync {
            usingSession {
                run(
                    queryOf("$searchString;", *args.toTypedArray())
                        .map { it.toTicket() }
                        .asList
                )
            }
        }

        return ticketsCF.thenApplyAsync { l -> l.map { it.id }.joinToString(", ") }
            .thenApplyAsync {
                usingSession {
                    run(queryOf("SELECT * FROM \"TicketManager_V8_Actions\" WHERE TICKET_ID IN ($it);")
                        .map { it.long(2) to it.toAction() }
                        .asList
                    )
                }
            }
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

    override fun getTicketIDsWithUpdatesAsync(): CompletableFuture<List<Long>> {
        val ids = usingSession {
            run(queryOf("SELECT ID FROM \"TicketManager_V8_Tickets\" WHERE STATUS_UPDATE_FOR_CREATOR = ?;", true)
                .map { it.long(1) }
                .asList
            )
        }

        return CompletableFuture.completedFuture(ids)
    }

    override fun getTicketIDsWithUpdatesForAsync(creator: Creator): CompletableFuture<List<Long>> {
        val ids = usingSession {
            run(queryOf( "SELECT ID FROM \"TicketManager_V8_Tickets\" WHERE STATUS_UPDATE_FOR_CREATOR = ? AND CREATOR = ?;", true, creator.toString())
                .map { it.long(1) }
                .asList
            )
        }

        return CompletableFuture.completedFuture(ids)
    }

    override fun closeDatabase() {
        connectionPool.dispose()
    }

    override fun insertTicketForMigration(other: AsyncDatabase) {
        usingSession {
            run(queryOf("SELECT ID FROM \"TicketManager_V8_Tickets\";")
                .map { it.long(1) }
                .asList
            )
        }
            .forEach { id -> getTicketOrNullAsync(id).thenApplyAsync { other.insertTicketAsync(it!!)} }
    }

    private fun Row.toTicket(): Ticket {
        return Ticket(
            id = long(1),
            creator = string(2).let { s -> mapToCreatorOrNull(s) ?: throw Exception("Unsupported Creator Type: $s") },
            priority = byteToPriority(byte(3)),
            status = Ticket.Status.valueOf(string(4)),
            assignedTo = stringOrNull(5),
            creatorStatusUpdate = boolean(6),
        )
    }

    private fun Row.toAction(): Ticket.Action {
        return Ticket.Action(
            type = Ticket.Action.Type.valueOf(string(3)),
            user = string(4).let { mapToCreatorOrNull(it) ?: throw Exception("Unsupported Creator Type: $it") },
            message = stringOrNull(5),
            timestamp = long(6),
            location = Ticket.TicketLocation(
                server = stringOrNull(7),
                world = stringOrNull(8),
                x = intOrNull(9),
                y = intOrNull(10),
                z = intOrNull(11),
            )
        )
    }

    override fun initializeDatabase() {
        usingSession {

            // Ticket Table
            execute(queryOf("""
                create table if not exists "TicketManager_V8_Tickets"
                (
                    ID                        NUMBER GENERATED BY DEFAULT AS IDENTITY (START WITH 1 INCREMENT BY 1) not null,
                    CREATOR                   VARCHAR_IGNORECASE(70) not null,
                    PRIORITY                  TINYINT                not null,
                    STATUS                    VARCHAR_IGNORECASE(10) not null,
                    ASSIGNED_TO               VARCHAR_IGNORECASE(255),
                    STATUS_UPDATE_FOR_CREATOR BOOLEAN                not null,
                    constraint "Ticket_ID"
                        primary key (ID)
                );""".replace("\n", "").trimIndent()))

            execute(queryOf("""create unique index if not exists INDEX_ID on "TicketManager_V8_Tickets" (ID);"""))
            execute(queryOf("""create index if not exists INDEX_STATUS_UPDATE_FOR_CREATOR on "TicketManager_V8_Tickets" (STATUS_UPDATE_FOR_CREATOR);"""))
            execute(queryOf("""create index if not exists INDEX_STATUS on "TicketManager_V8_Tickets" (STATUS);"""))


            // Actions Table
            execute(queryOf("""
                CREATE TABLE IF NOT EXISTS "TicketManager_V8_Actions"
                (
                    ACTION_ID       NUMBER GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1) not null,
                    TICKET_ID       BIGINT                  NOT NULL,
                    ACTION_TYPE     VARCHAR_IGNORECASE(20)  NOT NULL,
                    CREATOR         VARCHAR_IGNORECASE(70)  NOT NULL,
                    MESSAGE         LONGVARCHAR,
                    EPOCH_TIME      BIGINT                  NOT NULL,
                    SERVER          VARCHAR(100),
                    WORLD           VARCHAR(100),
                    WORLD_X         INT,
                    WORLD_Y         INT,
                    WORLD_Z         INT,
                    constraint "Actions_Action_ID"
                        primary key (ACTION_ID)
                );""".replace("\n", "").trimIndent()))

            execute(queryOf("""CREATE INDEX IF NOT EXISTS INDEX_TICKET_ID ON "TicketManager_V8_Actions" (TICKET_ID);"""))
        }
    }
}