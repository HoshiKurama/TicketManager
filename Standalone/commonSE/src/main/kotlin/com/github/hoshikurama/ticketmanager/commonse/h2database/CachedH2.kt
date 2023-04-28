package com.github.hoshikurama.ticketmanager.commonse.h2database

import com.github.hoshikurama.ticketmanager.api.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.database.DBResult
import com.github.hoshikurama.ticketmanager.api.database.SearchConstraints
import com.github.hoshikurama.ticketmanager.api.ticket.*
import com.github.hoshikurama.ticketmanager.api.ticket.TicketCreationLocation.*
import com.github.hoshikurama.ticketmanager.commonse.misc.*
import com.github.hoshikurama.ticketmanager.commonse.utilities.asParallelStream
import com.github.hoshikurama.ticketmanager.commonse.utilities.mapNotNull
import com.github.hoshikurama.ticketmanager.shaded.org.h2.jdbcx.JdbcConnectionPool
import kotliquery.*
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

    override fun setAssignmentAsync(ticketID: Long, assignment: TicketAssignmentType): CompletableFuture<Void> {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = Ticket(t.id, t.creator, t.priority, t.status, assignment, t.creatorStatusUpdate, t.actions)

        return sendQuery { update(queryOf("UPDATE \"TicketManager_V8_Tickets\" SET ASSIGNED_TO = ? WHERE ID = ?;", assignment.asString(), ticketID)) }
    }

    override fun setCreatorStatusUpdateAsync(ticketID: Long, status: Boolean): CompletableFuture<Void> {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = Ticket(t.id, t.creator, t.priority, t.status, t.assignedTo, status, t.actions)

        return sendQuery { update(queryOf("UPDATE \"TicketManager_V8_Tickets\" SET STATUS_UPDATE_FOR_CREATOR = ? WHERE ID = ?;", status, ticketID)) }
    }

    override fun setPriorityAsync(ticketID: Long, priority: Ticket.Priority): CompletableFuture<Void> {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = Ticket(t.id, t.creator, priority, t.status, t.assignedTo, t.creatorStatusUpdate, t.actions)

        return sendQuery { update(queryOf("UPDATE \"TicketManager_V8_Tickets\" SET PRIORITY = ? WHERE ID = ?;", priority.level, ticketID)) }
    }

    override fun setStatusAsync(ticketID: Long, status: Ticket.Status): CompletableFuture<Void> {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = Ticket(t.id, t.creator, t.priority, status, t.assignedTo, t.creatorStatusUpdate, t.actions)

        return sendQuery { update(queryOf("UPDATE \"TicketManager_V8_Tickets\" SET STATUS = ? WHERE ID = ?;", status.name, ticketID)) }
    }

    override fun insertActionAsync(id: Long, action: TicketAction): CompletableFuture<Void> {
        ticketMap[id] = ticketMap[id]!! + action

        return sendQuery {
            execute(
                queryOf("INSERT INTO \"TicketManager_V8_Actions\" (TICKET_ID, ACTION_TYPE, CREATOR, MESSAGE, EPOCH_TIME, SERVER, WORLD, WORLD_X, WORLD_Y, WORLD_Z) VALUES (?,?,?,?,?,?,?,?,?,?);",
                    id,
                    action.type.asEnum.name,
                    action.user.asString(),
                    action.type.getMessage(),
                    action.timestamp,
                    action.location.server,
                    action.location.let { if (it is FromPlayer) it.world else null },
                    action.location.let { if (it is FromPlayer) it.x else null },
                    action.location.let { if (it is FromPlayer) it.y else null },
                    action.location.let { if (it is FromPlayer) it.z else null },
                )
            )
        }
    }

    override fun insertNewTicketAsync(ticket: Ticket): CompletableFuture<Long> {
        val newID = nextTicketID.getAndIncrement()
        val newTicket = Ticket(newID, ticket.creator, ticket.priority, ticket.status, ticket.assignedTo, ticket.creatorStatusUpdate, ticket.actions)
        ticketMap[newID] = newTicket

        // Writes ticket
        sendQuery {
            update(
                queryOf("INSERT INTO \"TicketManager_V8_Tickets\" (ID, CREATOR, PRIORITY, STATUS, ASSIGNED_TO, STATUS_UPDATE_FOR_CREATOR) VALUES(?,?,?,?,?,?);",
                    newTicket.id,
                    newTicket.creator.asString(),
                    newTicket.priority.level,
                    newTicket.status.name,
                    newTicket.assignedTo.asString(),
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
                        it.type.asEnum.name,
                        it.user.asString(),
                        it.type.getMessage(),
                        it.timestamp,
                        it.location.server,
                        it.location.let { if (it is FromPlayer) it.world else null },
                        it.location.let { if (it is FromPlayer) it.x else null },
                        it.location.let { if (it is FromPlayer) it.y else null },
                        it.location.let { if (it is FromPlayer) it.z else null },
                    )
                )
            }
        }

        return CompletableFuture.completedFuture(newID)
    }

     private fun getTicketsAsync(ids: List<Long>): List<Ticket> {
         return ids.asParallelStream().mapNotNull(ticketMap::get).toList()
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
        assignment: TicketAssignmentType,
        unfixedGroupAssignment: List<String>
    ): CompletableFuture<DBResult> {
        val assignments = unfixedGroupAssignment.map { TicketAssignmentType.Other("::$it") } + assignment
        return getTicketsFilteredBy(page, pageSize) { it.status == Ticket.Status.OPEN && it.assignedTo in assignments }
    }

    override fun getOpenTicketsNotAssignedAsync(page: Int, pageSize: Int): CompletableFuture<DBResult> {
        return getTicketsFilteredBy(page, pageSize) { it.status == Ticket.Status.OPEN && it.assignedTo == TicketAssignmentType.Nobody }
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
            DBResult(
                filteredResults = results.getOrElse(fixedPage-1) { listOf() },
                totalPages = totalPages,
                totalResults = totalSize,
                returnedPage = fixedPage,
            )
        )
    }

    override fun massCloseTicketsAsync(lowerBound: Long, upperBound: Long, actor: TicketCreator, ticketLoc: TicketCreationLocation): CompletableFuture<Void> {
        val curTime = Instant.now().epochSecond

        // Memory Operations
        val ticketStream = (lowerBound..upperBound).asSequence()
            .asParallelStream()
            .mapNotNull { ticketMap[it] }
            .filter { it.status == Ticket.Status.OPEN }
            .map {
                // Side effects occur here intentionally
                val action = TicketAction(TicketAction.MassClose, actor, timestamp = curTime, location = ticketLoc)
                val newTicket = Ticket(it.id, it.creator, it.priority, Ticket.Status.CLOSED, it.assignedTo, it.creatorStatusUpdate, it.actions + action)
                ticketMap[it.id] = newTicket
                newTicket
            }

        // SQL operations
        val ticketIds = ticketStream.map(Ticket::id).toList()
        val action = TicketAction(
            type = TicketAction.MassClose,
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
        val assignments = unfixedGroupAssignment.map { TicketAssignmentType.Other("::$it" ) } + assignment
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
            val closeVariations = listOf(TicketAction.Type.AsEnum.CLOSE, TicketAction.Type.AsEnum.MASS_CLOSE)

            status?.run {{ t: Ticket -> t.status == value }}?.apply(functions::add)
            priority?.run {{ t: Ticket -> t.priority == value }}?.apply(functions::add)
            creator?.run {{ t: Ticket -> t.creator == value }}?.apply(functions::add)
            assigned?.run {{ t: Ticket -> t.assignedTo == value }}?.apply(functions::add)
            creationTime?.run {{ t: Ticket -> t.actions[0].timestamp >= value}}?.apply(functions::add)
            world?.run {{ t: Ticket -> (t.actions[0].location as? FromPlayer)?.world?.equals(value) ?: false }}?.apply(functions::add)
            closedBy?.run {{ t: Ticket -> t.actions.any { it.type.asEnum in closeVariations && it.user == value }}}?.apply(functions::add)
            lastClosedBy?.run {{ t: Ticket -> t.actions.lastOrNull { it.type.asEnum in closeVariations }?.run { user == value } ?: false }}?.apply(functions::add)

            keywords?.run {{ t: Ticket ->
                val comments = t.actions
                    .filter { it.type is TicketAction.Open || it.type is TicketAction.Comment || it.type is TicketAction.CloseWithComment }
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
            .sortedWith(compareByDescending(Ticket::id))
            .run { if (pageSize == 0 || isEmpty()) listOf(this) else chunked(pageSize) }
            .apply { maxPages = count() }

        val fixedPage = when {
            maxPages == 0 || page < 1 -> 1
            page in 1..maxPages -> page
            else -> maxPages
        }

        return CompletableFuture.completedFuture(
            DBResult(
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
            .map(Ticket::id)
            .toList()
            .let { CompletableFuture.completedFuture(it) }
    }

    override fun getTicketIDsWithUpdatesForAsync(creator: TicketCreator): CompletableFuture<List<Long>> {
        return ticketMap.values.toList()
            .asParallelStream()
            .filter { it.creatorStatusUpdate && it.creator == creator }
            .map(Ticket::id)
            .toList()
            .let { CompletableFuture.completedFuture(it) }
    }

    override fun getOwnedTicketIDsAsync(creator: TicketCreator): CompletableFuture<List<Long>> = CompletableFuture.supplyAsync {
        ticketMap.values.toList()
            .asParallelStream()
            .filter { it.creator == creator }
            .map(Ticket::id)
            .toList()
    }

    override fun getOpenTicketIDsAsync(): CompletableFuture<List<Long>> = CompletableFuture.supplyAsync {
        ticketMap.values.toList()
            .asParallelStream()
            .filter { it.status == Ticket.Status.OPEN }
            .map(Ticket::id)
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

    private fun sortActions(actions: List<TicketAction>) = actions.sortedBy(TicketAction::timestamp)
}

private fun Row.toAction(): TicketAction {
    return TicketAction(
        type = kotlin.run {
            val typeEnum = TicketAction.Type.AsEnum.valueOf(string(3))
            val msg = stringOrNull(5)

            when (typeEnum) {
                TicketAction.Type.AsEnum.ASSIGN -> TicketAction.Assign(msg?.asAssignmentType() ?: TicketAssignmentType.Nobody)
                TicketAction.Type.AsEnum.CLOSE -> TicketAction.CloseWithoutComment
                TicketAction.Type.AsEnum.COMMENT -> TicketAction.Comment(msg!!)
                TicketAction.Type.AsEnum.OPEN -> TicketAction.Open(msg!!)
                TicketAction.Type.AsEnum.REOPEN -> TicketAction.Reopen
                TicketAction.Type.AsEnum.SET_PRIORITY -> TicketAction.SetPriority(msg!!.toByte().toPriority())
                TicketAction.Type.AsEnum.MASS_CLOSE -> TicketAction.MassClose
                TicketAction.Type.AsEnum.CLOSE_WITH_COMMENT -> TicketAction.CloseWithComment(msg!!)
            }
        },
        user = string(4).asTicketCreator(),
        timestamp = long(6),
        location = kotlin.run {
            val x = intOrNull(9)

            if (x == null) FromConsole(server = stringOrNull(7))
            else FromPlayer(
                server = stringOrNull(7),
                world = string(8),
                x = int(9),
                y = int(10),
                z = int(11),
            )
        }
    )
}

private fun Row.toTicket(): Ticket {
    return Ticket(
        id = long(1),
        creator = string(2).asTicketCreator(),
        priority = byte(3).toPriority(),
        status = Ticket.Status.valueOf(string(4)),
        assignedTo = stringOrNull(5)?.asAssignmentType() ?: TicketAssignmentType.Nobody,
        creatorStatusUpdate = boolean(6),
    )
}

private fun TicketAction.Type.getMessage(): String? = when (this) {
    is TicketAction.Open -> message
    is TicketAction.Assign -> assignment.asString()
    is TicketAction.Comment -> comment
    is TicketAction.CloseWithComment -> comment
    is TicketAction.SetPriority -> priority.level.toString()
    else -> null
}