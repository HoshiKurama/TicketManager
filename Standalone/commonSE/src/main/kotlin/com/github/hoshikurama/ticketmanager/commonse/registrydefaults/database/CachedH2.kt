package com.github.hoshikurama.ticketmanager.commonse.registrydefaults.database

import com.github.hoshikurama.ticketmanager.api.registry.database.types.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.database.utils.DBResult
import com.github.hoshikurama.ticketmanager.api.registry.database.utils.SearchConstraints
import com.github.hoshikurama.ticketmanager.api.ticket.*
import com.github.hoshikurama.ticketmanager.commonse.misc.*
import com.github.hoshikurama.ticketmanager.commonse.utilities.asParallelStream
import com.github.hoshikurama.ticketmanager.commonse.utilities.mapNotNull
import com.github.hoshikurama.ticketmanager.commonse.utilities.notEquals
import com.github.hoshikurama.tmcore.TMCoroutine
import kotlinx.coroutines.runBlocking
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
        val fixedURL = "jdbc:h2:file:$absoluteDataFolderPath/TicketManager-H2-V10.db"
            .replace("C:", "")
            .replace("\\", "/")

        sqlPool = JdbcConnectionPool.create(fixedURL,"","")
        sqlPool.maxConnections = 3
    }

    private inline fun sendQuery(crossinline f: Session.() -> Unit) = TMCoroutine.Supervised.launch {
        using(sessionOf(sqlPool)) { f(it) }
    }

    override fun closeDatabase() {
        sqlPool.dispose()
    }

    override suspend fun countOpenTicketsAssignedToAsync(assignments: List<Assignment>): Long {
        return ticketMap.values.asParallelStream()
            .filter { it.status == Ticket.Status.OPEN }
            .filter { it.assignedTo in assignments }
            .count()
    }

    override suspend fun countOpenTicketsAsync(): Long {
        return ticketMap.values.asParallelStream()
            .filter { it.status == Ticket.Status.OPEN }
            .count()
    }

    override suspend fun getOpenTicketIDsAsync(): List<Long> {
        return ticketMap.values.asParallelStream()
            .filter { it.status == Ticket.Status.OPEN }
            .map(Ticket::id)
            .toList()
    }

    override suspend fun getOpenTicketIDsForUser(creator: Creator): List<Long> {
        return ticketMap.values.asParallelStream()
            .filter { it.creator == creator }
            .filter { it.status == Ticket.Status.OPEN }
            .map(Ticket::id)
            .toList()
    }

    override suspend fun getOpenTicketsAssignedToAsync(
        page: Int,
        pageSize: Int,
        assignments: List<Assignment>
    ): DBResult {
        return getTicketsFilteredBy(page, pageSize) { it.status == Ticket.Status.OPEN && it.assignedTo in assignments }
    }

    private fun getTicketsFilteredBy(page: Int, pageSize: Int, f: TicketPredicate): DBResult {
        val totalSize: Int
        val totalPages: Int

        val results = ticketMap.values
            .asParallelStream()
            .filter(f)
            .toList()
            .sortedWith(compareByDescending<Ticket> { it.priority.asByte() }.thenByDescending { it.id })
            .apply { totalSize = count() }
            .run { if (pageSize == 0 || isEmpty()) listOf(this) else chunked(pageSize) }
            .apply { totalPages = count() }

        val fixedPage = when {
            totalPages == 0 || page < 1 -> 1
            page in 1..totalPages -> page
            else -> totalPages
        }

        return DBResult(
            filteredResults = results.getOrElse(fixedPage-1) { listOf() },
            totalPages = totalPages,
            totalResults = totalSize,
            returnedPage = fixedPage,
        )
    }

    override suspend fun getOpenTicketsAsync(page: Int, pageSize: Int): DBResult {
        return getTicketsFilteredBy(page, pageSize) { it.status == Ticket.Status.OPEN }
    }

    override suspend fun getOpenTicketsNotAssignedAsync(page: Int, pageSize: Int): DBResult {
        return getTicketsFilteredBy(page, pageSize) { it.status == Ticket.Status.OPEN && it.assignedTo == Assignment.Nobody }
    }

    override suspend fun getOwnedTicketIDsAsync(creator: Creator): List<Long> {
        return ticketMap.values.asParallelStream()
            .filter { it.creator == creator }
            .map(Ticket::id)
            .toList()
    }

    override suspend fun getTicketIDsWithUpdatesAsync(): List<Long> {
        return ticketMap.values.asParallelStream()
            .filter { it.creatorStatusUpdate }
            .map(Ticket::id)
            .toList()
    }

    override suspend fun getTicketIDsWithUpdatesForAsync(creator: Creator): List<Long> {
        return ticketMap.values.asParallelStream()
            .filter { it.creatorStatusUpdate && it.creator == creator }
            .map(Ticket::id)
            .toList()
    }

    override suspend fun getTicketOrNullAsync(id: Long): Ticket? {
        return ticketMap[id]
    }

    override fun initializeDatabase() {
        // This part doesn't submit to the work-stealing pool since it's important this executes before continuing
        // Creates table if it doesn't exist
        using(sessionOf(sqlPool)) {
            // Ticket Table
            it.execute(queryOf("""
                create table if not exists TicketManager_v10_Tickets
                (
                    ID                        NUMBER GENERATED BY DEFAULT AS IDENTITY (START WITH 1) PRIMARY KEY,
                    CREATOR                   VARCHAR_IGNORECASE(70)    not null,
                    PRIORITY                  TINYINT                   not null,
                    STATUS                    VARCHAR_IGNORECASE(10)    not null,
                    ASSIGNED_TO               VARCHAR_IGNORECASE(255)   not null,
                    STATUS_UPDATE_FOR_CREATOR BOOLEAN                   not null
                );
                """.replace("\n", "").trimIndent()))

            it.execute(queryOf("""create unique index if not exists INDEX_ID on TicketManager_V10_Tickets (ID);"""))
            it.execute(queryOf("""create index if not exists INDEX_STATUS_UPDATE_FOR_CREATOR on TicketManager_V10_Tickets (STATUS_UPDATE_FOR_CREATOR);"""))
            it.execute(queryOf("""create index if not exists INDEX_STATUS on TicketManager_V10_Tickets (STATUS);"""))


            // Actions Table
            it.execute(queryOf("""
                CREATE TABLE IF NOT EXISTS TicketManager_v10_Actions
                (
                    ACTION_ID       NUMBER GENERATED BY DEFAULT AS IDENTITY (START WITH 1) PRIMARY KEY,
                    TICKET_ID       BIGINT                  NOT NULL,
                    ACTION_TYPE     VARCHAR_IGNORECASE(20)  NOT NULL,
                    CREATOR         VARCHAR_IGNORECASE(70)  NOT NULL,
                    MESSAGE         LONGVARCHAR,
                    EPOCH_TIME      BIGINT                  NOT NULL,
                    SERVER          VARCHAR(100),
                    WORLD           VARCHAR(100),
                    WORLD_X         INT,
                    WORLD_Y         INT,
                    WORLD_Z         INT
                );""".replace("\n", "").trimIndent()))

            it.execute(queryOf("""CREATE INDEX IF NOT EXISTS INDEX_TICKET_ID ON TicketManager_V10_Actions (TICKET_ID);"""))
        }

        // Loads tickets to memory
        val basicTicketsCF = CompletableFuture.supplyAsync {
            using(sessionOf(sqlPool)) { session ->
                session.run(queryOf("SELECT * FROM TicketManager_V10_Tickets;")
                    .map {it.toTicket() }.asList
                )
            }
        }

        val actionsCF = CompletableFuture.supplyAsync {
            using(sessionOf(sqlPool)) { session ->
                session.run(queryOf("SELECT * FROM TicketManager_V10_Actions;")
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

    override suspend fun insertActionAsync(id: Long, action: Action) {
        ticketMap[id] = ticketMap[id]!! + action

        sendQuery {
            execute(
                queryOf("INSERT INTO TicketManager_V10_Actions (TICKET_ID, ACTION_TYPE, CREATOR, MESSAGE, EPOCH_TIME, SERVER, WORLD, WORLD_X, WORLD_Y, WORLD_Z) VALUES (?,?,?,?,?,?,?,?,?,?);",
                    id,
                    action.getEnumForDB().name,
                    action.user.asString(),
                    action.getMessage(),
                    action.timestamp,
                    action.location.server,
                    action.location.let { if (it is ActionLocation.FromPlayer) it.world else null },
                    action.location.let { if (it is ActionLocation.FromPlayer) it.x else null },
                    action.location.let { if (it is ActionLocation.FromPlayer) it.y else null },
                    action.location.let { if (it is ActionLocation.FromPlayer) it.z else null },
                )
            )
        }
    }

    override suspend fun insertNewTicketAsync(ticket: Ticket): Long {
        val newID = nextTicketID.getAndIncrement()
        val newTicket = Ticket(newID, ticket.creator, ticket.priority, ticket.status, ticket.assignedTo, ticket.creatorStatusUpdate, ticket.actions)
        ticketMap[newID] = newTicket

        // Writes ticket
        sendQuery {
            update(
                queryOf("INSERT INTO TicketManager_V10_Tickets (ID, CREATOR, PRIORITY, STATUS, ASSIGNED_TO, STATUS_UPDATE_FOR_CREATOR) VALUES (?,?,?,?,?,?);",
                    newTicket.id,
                    newTicket.creator.asString(),
                    newTicket.priority.asByte(),
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
                    queryOf("INSERT INTO TicketManager_V10_Actions (TICKET_ID, ACTION_TYPE, CREATOR, MESSAGE, EPOCH_TIME, SERVER, WORLD, WORLD_X, WORLD_Y, WORLD_Z) VALUES (?,?,?,?,?,?,?,?,?,?);",
                        newTicket.id,
                        it.getEnumForDB().name,
                        it.user.asString(),
                        it.getMessage(),
                        it.timestamp,
                        it.location.server,
                        it.location.let { if (it is ActionLocation.FromPlayer) it.world else null },
                        it.location.let { if (it is ActionLocation.FromPlayer) it.x else null },
                        it.location.let { if (it is ActionLocation.FromPlayer) it.y else null },
                        it.location.let { if (it is ActionLocation.FromPlayer) it.z else null },
                    )
                )
            }
        }

        return newID
    }

    override suspend fun massCloseTicketsAsync(
        lowerBound: Long,
        upperBound: Long,
        actor: Creator,
        ticketLoc: ActionLocation
    ) {
        val curTime = Instant.now().epochSecond

        // Memory Operations
        val ticketStream = (lowerBound..upperBound).asSequence()
            .asParallelStream()
            .mapNotNull { ticketMap[it] }
            .filter { it.status == Ticket.Status.OPEN }
            .map {
                // Side effects occur here intentionally
                val action = ActionInfo(actor, ticketLoc, curTime).MassClose()
                val newTicket = Ticket(it.id, it.creator, it.priority, Ticket.Status.CLOSED, it.assignedTo,
                    it.creatorStatusUpdate, it.actions + action
                )
                ticketMap[it.id] = newTicket
                newTicket
            }


        // SQL operations
        val ticketIds = ticketStream.map(Ticket::id).toList()
        val action = ActionInfo(
            user = actor,
            location = ticketLoc,
        ).MassClose()

        sendQuery { update(queryOf("UPDATE TicketManager_V10_Tickets SET STATUS = ? WHERE ID IN (${ticketIds.joinToString(", ")});", Ticket.Status.CLOSED.name)) }
        sendQuery { ticketIds.forEach { runBlocking { insertActionAsync(it, action) } } }
    }

    override suspend fun searchDatabaseAsync(constraints: SearchConstraints, pageSize: Int): DBResult {
        val functions = mutableListOf<TicketPredicate>()

        constraints.run {
            // Builds Constraints
            val closeVariations = listOf(ActionAsEnum.CLOSE, ActionAsEnum.MASS_CLOSE)

            status?.run {
                when (symbol) {
                    SearchConstraints.Symbol.EQUALS -> { t: Ticket -> t.status == value }
                    SearchConstraints.Symbol.NOT_EQUALS -> { t: Ticket -> t.status != value }
                    else -> throw Exception("Invalid type attempted in status search: ${symbol.name}")
                }
            }?.apply(functions::add)

            priority?.run {
                when (symbol) {
                    SearchConstraints.Symbol.EQUALS -> { t: Ticket -> t.priority == value }
                    SearchConstraints.Symbol.NOT_EQUALS -> { t: Ticket -> t.priority != value }
                    SearchConstraints.Symbol.GREATER_THAN -> { t: Ticket -> t.priority.asByte() > value.asByte() }
                    SearchConstraints.Symbol.LESS_THAN -> { t: Ticket -> t.priority.asByte() < value.asByte() }
                }
            }?.apply(functions::add)

            creator?.run {
                when (symbol) {
                    SearchConstraints.Symbol.EQUALS -> { t: Ticket -> t.creator == value }
                    SearchConstraints.Symbol.NOT_EQUALS -> { t: Ticket -> t.creator != value }
                    else -> throw Exception("Invalid type attempted in creator search: ${symbol.name}")
                }
            }?.apply(functions::add)

            assigned?.run {
                when (symbol) {
                    SearchConstraints.Symbol.EQUALS -> { t: Ticket -> t.assignedTo == value }
                    SearchConstraints.Symbol.NOT_EQUALS -> { t: Ticket -> t.assignedTo != value }
                    else -> throw Exception("Invalid type attempted in assignment search: ${symbol.name}")
                }
            }?.apply(functions::add)

            creationTime?.run {
                when (symbol) {
                    SearchConstraints.Symbol.LESS_THAN -> { t: Ticket -> t.actions[0].timestamp >= value}
                    SearchConstraints.Symbol.GREATER_THAN -> { t: Ticket -> t.actions[0].timestamp <= value}
                    else -> throw Exception("Invalid type attempted in creation time search: ${symbol.name}")
                }
            }?.apply(functions::add)

            world?.run {
                when (symbol) {
                    SearchConstraints.Symbol.EQUALS -> { t: Ticket -> (t.actions[0].location as? ActionLocation.FromPlayer)?.world?.equals(value) ?: false }
                    SearchConstraints.Symbol.NOT_EQUALS -> { t: Ticket -> (t.actions[0].location as? ActionLocation.FromPlayer)?.world?.notEquals(value) ?: true }
                    else -> throw Exception("Invalid type attempted in world search: ${symbol.name}")
                }
            }?.apply(functions::add)

            closedBy?.run {
                when (symbol) {
                    SearchConstraints.Symbol.EQUALS -> { t: Ticket -> t.actions.any { it.getEnumForDB() in closeVariations && it.user == value }}
                    SearchConstraints.Symbol.NOT_EQUALS -> { t: Ticket -> t.actions.none { it.getEnumForDB() in closeVariations && it.user == value }}
                    else -> throw Exception("Invalid type attempted in closedBy search: ${symbol.name}")
                }
            }?.apply(functions::add)

            lastClosedBy?.run {
                when (symbol) {
                    SearchConstraints.Symbol.EQUALS -> { t: Ticket -> t.actions.lastOrNull { it.getEnumForDB() in closeVariations }?.run { user == value } ?: false }
                    SearchConstraints.Symbol.NOT_EQUALS -> { t: Ticket -> t.actions.lastOrNull { it.getEnumForDB() in closeVariations }?.run { user != value } ?: true }
                    else -> throw Exception("Invalid type attempted in lastClosedBy search: ${symbol.name}")
                }
            }?.apply(functions::add)

            keywords?.run {
                when (symbol) {
                    SearchConstraints.Symbol.EQUALS -> { t: Ticket ->
                        val comments = t.actions
                            .filter { it is ActionInfo.Open || it is ActionInfo.Comment || it is ActionInfo.CloseWithComment }
                            .map { it.getMessage()!! }
                        value.map { w -> comments.any { it.lowercase().contains(w.lowercase()) } }
                            .all { it }
                    }
                    SearchConstraints.Symbol.NOT_EQUALS -> { t: Ticket ->
                        val comments = t.actions
                            .filter { it is ActionInfo.Open || it is ActionInfo.Comment || it is ActionInfo.CloseWithComment }
                            .map { it.getMessage()!! }
                        value.map { w -> comments.none { it.lowercase().contains(w.lowercase()) } }
                            .all { it }
                    }
                    else -> throw Exception("Invalid type attempted in  search: ${symbol.name}")
                }
            }?.apply(functions::add)
        }

        val combinedFunction = if (functions.isNotEmpty()) { t: Ticket -> functions.all { it(t) }}
        else { _: Ticket -> true }

        val totalSize: Int
        val maxPages: Int
        val results = ticketMap.values.asParallelStream()
            .filter(combinedFunction)
            .toList()
            .apply { totalSize = count() }
            .sortedWith(compareByDescending(Ticket::id))
            .run { if (pageSize == 0 || isEmpty()) listOf(this) else chunked(pageSize) }
            .apply { maxPages = count() }

        val fixedPage = when {
            maxPages == 0 || constraints.requestedPage < 1 -> 1
            constraints.requestedPage in 1..maxPages -> constraints.requestedPage
            else -> maxPages
        }

        return DBResult(
            filteredResults = results.getOrElse(fixedPage-1) { listOf() },
            totalPages = maxPages,
            totalResults = totalSize,
            returnedPage = fixedPage,
        )
    }

    override suspend fun setAssignmentAsync(ticketID: Long, assignment: Assignment) {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = Ticket(t.id, t.creator, t.priority, t.status, assignment, t.creatorStatusUpdate, t.actions)

        sendQuery { update(queryOf("UPDATE TicketManager_V10_Tickets SET ASSIGNED_TO = ? WHERE ID = ?;", assignment.asString(), ticketID)) }

    }

    override suspend fun setCreatorStatusUpdateAsync(ticketID: Long, status: Boolean) {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = Ticket(t.id, t.creator, t.priority, t.status, t.assignedTo, status, t.actions)

        sendQuery { update(queryOf("UPDATE TicketManager_V10_Tickets SET STATUS_UPDATE_FOR_CREATOR = ? WHERE ID = ?;", status, ticketID)) }
    }

    override suspend fun setPriorityAsync(ticketID: Long, priority: Ticket.Priority) {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = Ticket(t.id, t.creator, priority, t.status, t.assignedTo, t.creatorStatusUpdate, t.actions)

        sendQuery { update(queryOf("UPDATE TicketManager_V10_Tickets SET PRIORITY = ? WHERE ID = ?;", priority.asByte(), ticketID)) }
    }

    override suspend fun setStatusAsync(ticketID: Long, status: Ticket.Status) {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = Ticket(t.id, t.creator, t.priority, status, t.assignedTo, t.creatorStatusUpdate, t.actions)

        sendQuery { update(queryOf("UPDATE TicketManager_V10_Tickets SET STATUS = ? WHERE ID = ?;", status.name, ticketID)) }
    }

    private fun sortActions(actions: List<Action>) = actions.sortedBy(Action::timestamp)
}

private fun Row.toAction(): Action {

    val actionInfo = ActionInfo(
        user = CreatorString(string(4)).asTicketCreator(),
        timestamp = long(6),
        location = kotlin.run {
            val x = intOrNull(9)

            if (x == null) ActionLocation.FromConsole(server = stringOrNull(7))
            else ActionLocation.FromPlayer(
                server = stringOrNull(7),
                world = string(8),
                x = int(9),
                y = int(10),
                z = int(11),
            )
        }
    )

    val msg = stringOrNull(5)
    return when (ActionAsEnum.valueOf(string(3))) {
        ActionAsEnum.OPEN -> actionInfo.Open(msg!!)
        ActionAsEnum.COMMENT -> actionInfo.Comment(msg!!)
        ActionAsEnum.CLOSE -> actionInfo.CloseWithoutComment()
        ActionAsEnum.CLOSE_WITH_COMMENT -> actionInfo.CloseWithComment(msg!!)
        ActionAsEnum.ASSIGN -> actionInfo.Assign(msg?.run(::AssignmentString)?.asAssignmentType() ?: Assignment.Nobody)
        ActionAsEnum.REOPEN -> actionInfo.Reopen()
        ActionAsEnum.SET_PRIORITY -> actionInfo.SetPriority(msg!!.toByte().toPriority())
        ActionAsEnum.MASS_CLOSE -> actionInfo.MassClose()
    }
}

private fun Row.toTicket(): Ticket {
    return Ticket(
        id = long(1),
        creator = CreatorString(string(2)).asTicketCreator(),
        priority = byte(3).toPriority(),
        status = Ticket.Status.valueOf(string(4)),
        assignedTo = stringOrNull(5)?.run(::AssignmentString)?.asAssignmentType() ?: Assignment.Nobody,
        creatorStatusUpdate = boolean(6),
        actions = emptyList<Action>()
    )
}

internal fun Action.getMessage(): String? = when (this) {
    is ActionInfo.Assign -> assignment.asString()
    is ActionInfo.CloseWithComment -> comment
    is ActionInfo.Open -> message
    is ActionInfo.Comment -> comment
    is ActionInfo.SetPriority -> priority.asByte().toString()
    else -> null
}

internal enum class ActionAsEnum {
    ASSIGN, CLOSE, CLOSE_WITH_COMMENT, COMMENT, OPEN, REOPEN, SET_PRIORITY, MASS_CLOSE
}

internal fun Action.getEnumForDB(): ActionAsEnum = when (this) {
    is ActionInfo.Assign -> ActionAsEnum.ASSIGN
    is ActionInfo.CloseWithComment -> ActionAsEnum.CLOSE_WITH_COMMENT
    is ActionInfo.CloseWithoutComment -> ActionAsEnum.CLOSE
    is ActionInfo.Comment -> ActionAsEnum.COMMENT
    is ActionInfo.MassClose -> ActionAsEnum.MASS_CLOSE
    is ActionInfo.Open -> ActionAsEnum.OPEN
    is ActionInfo.Reopen -> ActionAsEnum.REOPEN
    is ActionInfo.SetPriority -> ActionAsEnum.SET_PRIORITY
}