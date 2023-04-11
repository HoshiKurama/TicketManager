package com.github.hoshikurama.ticketmanager.commonse.api.impl.database

import com.github.hoshikurama.ticketmanager.api.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.database.DBResult
import com.github.hoshikurama.ticketmanager.api.database.SearchConstraints
import com.github.hoshikurama.ticketmanager.api.ticket.Creator
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import com.github.hoshikurama.ticketmanager.commonse.api.impl.DBResultSTD
import com.github.hoshikurama.ticketmanager.commonse.api.impl.TicketSTD
import com.github.hoshikurama.ticketmanager.commonse.old.misc.*
import kotliquery.*

import org.h2.jdbcx.JdbcConnectionPool
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class CachedH2(absoluteDataFolderPath: String) : AsyncDatabase {

    private val ticketMap = ConcurrentHashMap<Long, Ticket>()
    private val nextTicketID = AtomicLong(1L)
    private val sqlPool: JdbcConnectionPool

    init {
        val fixedURL = "jdbc:h2:file:$absoluteDataFolderPath/TicketManager-H2-V8.db"
            .replace("C:", "")
            .replace("\\", "/")

        sqlPool = JdbcConnectionPool.create(fixedURL,"","")
        sqlPool.maxConnections = 3
    }

    private inline fun sendQuery(crossinline f: Session.() -> Unit) = CompletableFuture.runAsync {
        using(sessionOf(sqlPool)) { f(it) }
    }

    override fun setAssignmentAsync(ticketID: Long, assignment: String?): CompletableFuture<Void> {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = TicketSTD(t.id, t.creator, t.priority, t.status, assignment, t.creatorStatusUpdate, t.actions)

        return sendQuery { update(queryOf("UPDATE \"TicketManager_V8_Tickets\" SET ASSIGNED_TO = ? WHERE ID = ?;", assignment, ticketID)) }
    }

    override fun setCreatorStatusUpdateAsync(ticketID: Long, status: Boolean): CompletableFuture<Void> {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = TicketSTD(t.id, t.creator, t.priority, t.status, t.assignedTo, status, t.actions)

        return sendQuery { update(queryOf("UPDATE \"TicketManager_V8_Tickets\" SET STATUS_UPDATE_FOR_CREATOR = ? WHERE ID = ?;", status, ticketID)) }
    }

    override fun setPriorityAsync(ticketID: Long, priority: Ticket.Priority): CompletableFuture<Void> {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = TicketSTD(t.id, t.creator, priority, t.status, t.assignedTo, t.creatorStatusUpdate, t.actions)

        return sendQuery { update(queryOf("UPDATE \"TicketManager_V8_Tickets\" SET PRIORITY = ? WHERE ID = ?;", priority.level, ticketID)) }
    }

    override fun setStatusAsync(ticketID: Long, status: Ticket.Status): CompletableFuture<Void> {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = TicketSTD(t.id, t.creator, t.priority, status, t.assignedTo, t.creatorStatusUpdate, t.actions)

        return sendQuery { update(queryOf("UPDATE \"TicketManager_V8_Tickets\" SET STATUS = ? WHERE ID = ?;", status.name, ticketID)) }
    }

    override fun insertActionAsync(id: Long, action: Ticket.Action): CompletableFuture<Void> {
        ticketMap[id] = ticketMap[id]!! + action

        return sendQuery {
            execute(
                queryOf("INSERT INTO \"TicketManager_V8_Actions\" (TICKET_ID, ACTION_TYPE, CREATOR, MESSAGE, EPOCH_TIME, SERVER, WORLD, WORLD_X, WORLD_Y, WORLD_Z) VALUES (?,?,?,?,?,?,?,?,?,?);",
                    id,
                    action.type.getTypeEnum().name,
                    action.user.toString(),
                    action.type.getMessage(),
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

    override fun insertNewTicketAsync(ticket: Ticket): CompletableFuture<Long> {
        val newID = nextTicketID.getAndIncrement()
        val newTicket = TicketSTD(newID, ticket.creator, ticket.priority, ticket.status, ticket.assignedTo, ticket.creatorStatusUpdate, ticket.actions)
        ticketMap[newID] = newTicket

        // Writes ticket
        sendQuery {
            update(
                queryOf("INSERT INTO \"TicketManager_V8_Tickets\" (ID, CREATOR, PRIORITY, STATUS, ASSIGNED_TO, STATUS_UPDATE_FOR_CREATOR) VALUES(?,?,?,?,?,?);",
                    newTicket.id,
                    newTicket.creator.asString(),
                    newTicket.priority.level,
                    newTicket.status.name,
                    newTicket.assignedTo,
                    newTicket.creatorStatusUpdate
                )
            )
        }

        // Writes actions
        newTicket.actions.forEach {
            sendQuery {
                update(
                    queryOf("INSERT INTO \"TicketManager_V8_Actions\" (TICKET_ID, ACTION_TYPE, CREATOR, MESSAGE, EPOCH_TIME, SERVER, WORLD, WORLD_X, WORLD_Y, WORLD_Z) VALUES (?,?,?,?,?,?,?,?,?,?);",
                        newTicket.id,
                        it.type.getTypeEnum().name,
                        it.user.toString(),
                        it.type.getMessage(),
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

        return CompletableFuture.completedFuture(newID)
    }

     private fun getTicketsAsync(ids: List<Long>): List<Ticket> {
         return ids.asParallelStream().map(ticketMap::get).filterNotNull().toList()
     }

    override fun getTicketOrNullAsync(id: Long): CompletableFuture<Ticket?> {
        return CompletableFuture.completedFuture(ticketMap[id])
    }

    override fun getOpenTicketsAsync(page: Int, pageSize: Int): CompletableFuture<DBResult> {
        return getTicketsFilteredBy(page, pageSize) { it.status == Ticket.Status.OPEN }
    }

    override fun getOpenTicketsAssignedToAsync(
        page: Int,
        pageSize: Int,
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): CompletableFuture<DBResult> {
        val assignments = unfixedGroupAssignment.map { "::$it" } + assignment
        return getTicketsFilteredBy(page, pageSize) { it.status == Ticket.Status.OPEN && it.assignedTo in assignments }
    }

    override fun getOpenTicketsNotAssignedAsync(page: Int, pageSize: Int): CompletableFuture<DBResult> {
        return getTicketsFilteredBy(page, pageSize) { it.status == Ticket.Status.OPEN && it.assignedTo == null }
    }

    private fun getTicketsFilteredBy(page: Int, pageSize: Int, f: TicketPredicate): CompletableFuture<DBResult> {

        val totalSize: Int
        val totalPages: Int

        val results = ticketMap.values.toList()
            .asParallelStream()
            .filter(f)
            .toList()
            .sortedWith(compareByDescending<Ticket> { it.priority.level }.thenByDescending { it.id })
            .apply { totalSize = count() }
            .run { if (pageSize == 0 || isEmpty()) listOf(this) else chunked(pageSize) }
            .apply { totalPages = count() }

        val fixedPage = when {
            totalPages == 0 || page < 1 -> 1
            page in 1..totalPages -> page
            else -> totalPages
        }

        return CompletableFuture.completedFuture(
            DBResultSTD(
                filteredResults = results.getOrElse(fixedPage-1) { listOf() },
                totalPages = totalPages,
                totalResults = totalSize,
                returnedPage = fixedPage,
            )
        )
    }

    override fun massCloseTicketsAsync(lowerBound: Long, upperBound: Long, actor: Creator, ticketLoc: Ticket.CreationLocation): CompletableFuture<Void> {
        val curTime = Instant.now().epochSecond

        // Memory Operations
        val ticketStream = (lowerBound..upperBound).asSequence()
            .asParallelStream()
            .mapNotNull { ticketMap[it] }
            .filter { it.status == Ticket.Status.OPEN }
            .map {
                // Side effects occur here intentionally
                val action = TicketSTD.ActionSTD(TicketSTD.ActionSTD.MassCloseSTD, actor, timestamp = curTime, location = ticketLoc)
                val newTicket = TicketSTD(it.id, it.creator, it.priority, Ticket.Status.CLOSED, it.assignedTo, it.creatorStatusUpdate, it.actions + action)
                ticketMap[it.id] = newTicket
                newTicket
            }

        // SQL operations
        val ticketIds = ticketStream.map(Ticket::id).toList()
        val action = TicketSTD.ActionSTD(
            type = TicketSTD.ActionSTD.MassCloseSTD,
            user = actor,
            timestamp = Instant.now().epochSecond,
            location = ticketLoc
        )

        sendQuery { update(queryOf("UPDATE \"TicketManager_V8_Tickets\" SET STATUS = ? WHERE ID IN (${ticketIds.joinToString(", ")});", Ticket.Status.CLOSED.name)) }
        return sendQuery { ticketIds.map { insertActionAsync(it, action) }.flatten() }
    }

    override fun countOpenTicketsAsync(): CompletableFuture<Long> {
       return ticketMap.values.toList()
            .asParallelStream()
            .filter { it.status == Ticket.Status.OPEN }
            .count()
           .let { CompletableFuture.completedFuture(it) }

    }

    override fun countOpenTicketsAssignedToAsync(
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): CompletableFuture<Long> {
        val assignments = unfixedGroupAssignment.map { "::$it" } + assignment
        return ticketMap.values.toList()
            .asParallelStream()
            .filter { it.status == Ticket.Status.OPEN && it.assignedTo in assignments }
            .count()
            .let { CompletableFuture.completedFuture(it) }
    }

    override fun searchDatabaseAsync(
        constraints: SearchConstraints,
        page: Int,
        pageSize: Int
    ): CompletableFuture<DBResult> {
        val functions = mutableListOf<TicketPredicate>()

        constraints.run {
            // Builds Constraints
            val closeVariations = listOf(Ticket.Action.Type.TypeEnum.CLOSE, Ticket.Action.Type.TypeEnum.MASS_CLOSE)

            status?.run {{ t: Ticket -> t.status == value }}?.apply(functions::add)
            priority?.run {{ t: Ticket -> t.priority == value }}?.apply(functions::add)
            creator?.run {{ t: Ticket -> t.creator == value }}?.apply(functions::add)
            assigned?.run {{ t: Ticket -> t.assignedTo == value }}?.apply(functions::add)
            creationTime?.run {{ t: Ticket -> t.actions[0].timestamp >= value}}?.apply(functions::add)
            world?.run {{ t: Ticket -> t.actions[0].location.world?.equals(value) ?: false }}?.apply(functions::add)
            closedBy?.run {{ t: Ticket -> t.actions.any { it.type.getTypeEnum() in closeVariations && it.user == value }}}?.apply(functions::add)
            lastClosedBy?.run {{ t: Ticket -> t.actions.lastOrNull { it.type.getTypeEnum() in closeVariations }?.run { user == value } ?: false }}?.apply(functions::add)

            keywords?.run {{ t: Ticket ->
                val comments = t.actions
                    .filter { it.type is Ticket.Action.Type.OPEN || it.type is Ticket.Action.Type.COMMENT }
                    .map { it.type.getMessage()!! }
                value.map { w -> comments.any { it.lowercase().contains(w.lowercase()) } }
                    .all { it }
            }}?.apply(functions::add)
        }

        val combinedFunction = if (functions.isNotEmpty()) { t: Ticket -> functions.all { it(t) }}
        else { _: Ticket -> true }

        val totalSize: Int
        val maxPages: Int
        val results = ticketMap.values.toList().asParallelStream()
            .filter(combinedFunction)
            .toList()
            .apply { totalSize = count() }
            .sortedWith(compareByDescending { it.id })
            .run { if (pageSize == 0 || isEmpty()) listOf(this) else chunked(pageSize) }
            .apply { maxPages = count() }

        val fixedPage = when {
            maxPages == 0 || page < 1 -> 1
            page in 1..maxPages -> page
            else -> maxPages
        }

        return CompletableFuture.completedFuture(
            DBResultSTD(
                filteredResults = results.getOrElse(fixedPage-1) { listOf() },
                totalPages = maxPages,
                totalResults = totalSize,
                returnedPage = fixedPage,
            )
        )
    }

    override fun getTicketIDsWithUpdatesAsync(): CompletableFuture<List<Long>> {
        return ticketMap.values.toList()
            .asParallelStream()
            .filter { it.creatorStatusUpdate }
            .map { it.id }
            .toList()
            .let { CompletableFuture.completedFuture(it) }
    }

    override fun getTicketIDsWithUpdatesForAsync(creator: Creator): CompletableFuture<List<Long>> {
        return ticketMap.values.toList()
            .asParallelStream()
            .filter { it.creatorStatusUpdate && it.creator == creator }
            .map { it.id }
            .toList()
            .let { CompletableFuture.completedFuture(it) }
    }

    override fun getOwnedTicketIDsAsync(creator: Creator): CompletableFuture<List<Long>> = CompletableFuture.supplyAsync {
        ticketMap.values.toList()
            .asParallelStream()
            .filter { it.creator equalTo creator }
            .map { it.id }
            .toList()
    }

    override fun closeDatabase() {
        sqlPool.connection.createStatement().execute("SHUTDOWN")
    }

    override fun initializeDatabase() {
        // This part doesn't submit to the work-stealing pool since it's important this executes before continuing
        // Creates table if it doesn't exist
        using(sessionOf(sqlPool)) {
            // Ticket Table
            it.execute(queryOf("""
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

            it.execute(queryOf("""create unique index if not exists INDEX_ID on "TicketManager_V8_Tickets" (ID);"""))
            it.execute(queryOf("""create index if not exists INDEX_STATUS_UPDATE_FOR_CREATOR on "TicketManager_V8_Tickets" (STATUS_UPDATE_FOR_CREATOR);"""))
            it.execute(queryOf("""create index if not exists INDEX_STATUS on "TicketManager_V8_Tickets" (STATUS);"""))


            // Actions Table
            it.execute(queryOf("""
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

            it.execute(queryOf("""CREATE INDEX IF NOT EXISTS INDEX_TICKET_ID ON "TicketManager_V8_Actions" (TICKET_ID);"""))
        }

        // Loads tickets to memory
        val basicTicketsCF = CompletableFuture.supplyAsync {
            using(sessionOf(sqlPool)) { session ->
                session.run(queryOf("SELECT * FROM \"TicketManager_V8_Tickets\";")
                    .map {it.toTicket() }.asList
                )
            }
        }

        val actionsCF = CompletableFuture.supplyAsync {
            using(sessionOf(sqlPool)) { session ->
                session.run(queryOf("SELECT * FROM \"TicketManager_V8_Actions\";")
                    .map { it.long(2) to it.toAction() }.asList
                )
            }
        }

        // Intentionally blocking to prevent startup from continuing until database is 100% ready
        val basicTickets = basicTicketsCF.join()
        val actions = actionsCF.join()

        // Future me: The performance for this is FINE. Don't waste time multi-threading it
        // Combine actions and basic tickets to FullTickets and adds those to ticket map
        actions.groupBy({ it.first }, { it.second })
            .mapValues { sortActions(it.value) }
            .run { basicTickets.map { it + get(it.id)!! } }
            .forEach { ticketMap[it.id] = it }

        if (ticketMap.isNotEmpty()) nextTicketID.set(ticketMap.values.maxOf { it.id } + 1L)
    }

    private fun sortActions(actions: List<Ticket.Action>) = actions.sortedBy(Ticket.Action::timestamp)
}

private fun Row.toAction(): TicketSTD.ActionSTD {
    return TicketSTD.ActionSTD(
        type = kotlin.run {
            val typeEnum = Ticket.Action.Type.TypeEnum.valueOf(string(3))
            val msg = stringOrNull(5)

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
        user = string(4).let { mapToCreatorOrNull(it) ?: throw Exception("Unsupported Creator Type: $it") },
        timestamp = long(6),
        location = kotlin.run {
            val x = intOrNull(9)

            if (x == null) TicketSTD.ConsoleTicketLocationSTD(server = stringOrNull(7))
            else TicketSTD.PlayerTicketLocationSTD(
                server = stringOrNull(7),
                world = string(8),
                x = int(9),
                y = int(10),
                z = int(11),
            )
        }
    )
}

private fun Row.toTicket(): TicketSTD {
    return TicketSTD(
        id = long(1),
        creator = string(2).let { s -> mapToCreatorOrNull(s) ?: throw Exception("Unsupported Creator Type: $s") },
        priority = byteToPriority(byte(3)),
        status = Ticket.Status.valueOf(string(4)),
        assignedTo = stringOrNull(5),
        creatorStatusUpdate = boolean(6),
    )
}

private fun Ticket.Action.Type.getMessage(): String? = when (this) {
    is Ticket.Action.Type.OPEN -> message
    is Ticket.Action.Type.ASSIGN -> assignment
    is Ticket.Action.Type.COMMENT -> comment
    is Ticket.Action.Type.SET_PRIORITY -> priority.level.toString()
    else -> null
}