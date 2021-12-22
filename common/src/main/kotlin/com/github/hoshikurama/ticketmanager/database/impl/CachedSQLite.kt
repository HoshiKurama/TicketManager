@file:Suppress("DuplicatedCode")

package com.github.hoshikurama.ticketmanager.database.impl

import com.github.hoshikurama.ticketmanager.database.Database
import com.github.hoshikurama.ticketmanager.database.DatabaseBuilders
import com.github.hoshikurama.ticketmanager.database.Result
import com.github.hoshikurama.ticketmanager.database.SearchConstraint
import com.github.hoshikurama.ticketmanager.misc.*
import com.github.hoshikurama.ticketmanager.misc.TypeSafeStream.Companion.asTypeSafeStream
import com.github.hoshikurama.ticketmanager.ticket.BasicTicket
import com.github.hoshikurama.ticketmanager.ticket.BasicTicketImpl
import com.github.hoshikurama.ticketmanager.ticket.FullTicket
import com.github.hoshikurama.ticketmanager.ticket.plus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotliquery.Connection
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.using
import java.sql.DriverManager
import java.time.Instant
import java.util.*
import kotlin.collections.set

class CachedSQLite(
    absoluteDataFolderPath: String,
    private val asyncDispatcher: CoroutineDispatcher,
) : Database {
    override val type = Database.Type.CACHED_SQLITE

    // Cache Data
    private val mapMutex = ReadWriteMutex()
    private val ticketMap = mutableMapOf<Int, FullTicket>()
    private lateinit var nextTicketID: IncrementalMutexController

    // SQLite Data
    private val url: String = "jdbc:sqlite:$absoluteDataFolderPath/TicketManager-SQLite.db"
    private fun getSession() = Session(Connection(DriverManager.getConnection(url)))

    private val sqlWriteQueue = Channel<(Session) -> Unit>(1_000)

    private val asyncScope: CoroutineScope
        get() = CoroutineScope(asyncDispatcher)


    private suspend fun ticketCopies() = mapMutex.read.withLock { ticketMap.values.toList() }

    override suspend fun setAssignment(ticketID: Int, assignment: String?) {
        mapMutex.write.withLock {
            val t = ticketMap[ticketID]!!
            ticketMap[ticketID] = FullTicket(t.id, t.creatorUUID, t.location, t.priority, t.status, assignment, t.creatorStatusUpdate, t.actions)
        }

        sqlWriteQueue.send { it.update(queryOf("UPDATE TicketManager_V4_Tickets SET ASSIGNED_TO = ? WHERE ID = $ticketID;", assignment)) }
    }

    override suspend fun setCreatorStatusUpdate(ticketID: Int, status: Boolean) {
        mapMutex.write.withLock {
            val t = ticketMap[ticketID]!!
            ticketMap[ticketID] = FullTicket(t.id, t.creatorUUID, t.location, t.priority, t.status, t.assignedTo, status, t.actions)
        }

        sqlWriteQueue.send { it.update(queryOf("UPDATE TicketManager_V4_Tickets SET STATUS_UPDATE_FOR_CREATOR = ? WHERE ID = $ticketID;", status)) }
    }

    override suspend fun setPriority(ticketID: Int, priority: BasicTicket.Priority) {
        mapMutex.write.withLock {
            val t = ticketMap[ticketID]!!
            ticketMap[ticketID] = FullTicket(t.id, t.creatorUUID, t.location, priority, t.status, t.assignedTo, t.creatorStatusUpdate, t.actions)
        }

        sqlWriteQueue.send { it.update(queryOf("UPDATE TicketManager_V4_Tickets SET PRIORITY = ? WHERE ID = $ticketID;", priority.level)) }
    }

    override suspend fun setStatus(ticketID: Int, status: BasicTicket.Status) {
        mapMutex.write.withLock {
            val t = ticketMap[ticketID]!!
            ticketMap[ticketID] = FullTicket(t.id, t.creatorUUID, t.location, t.priority, status, t.assignedTo, t.creatorStatusUpdate, t.actions)
        }

        sqlWriteQueue.send { it.update(queryOf("UPDATE TicketManager_V4_Tickets SET STATUS = ? WHERE ID = $ticketID;", status.name)) }
    }

    override suspend fun insertAction(id: Int, action: FullTicket.Action) {
        mapMutex.write.withLock {
            ticketMap[id] = ticketMap[id]!! + action
        }

        sqlWriteQueue.send {
            it.update(
                queryOf("INSERT INTO TicketManager_V4_Actions (TICKET_ID,ACTION_TYPE,CREATOR_UUID,MESSAGE,TIMESTAMP) VALUES (?,?,?,?,?);",
                    id,
                    action.type.name,
                    action.user?.toString(),
                    action.message,
                    action.timestamp
                )
            )
        }
    }

    override suspend fun insertTicket(fullTicket: FullTicket): Int {
        val newID = nextTicketID.getAndIncrement()
        val newTicket = FullTicket(newID, fullTicket.creatorUUID, fullTicket.location, fullTicket.priority, fullTicket.status, fullTicket.assignedTo, fullTicket.creatorStatusUpdate, fullTicket.actions)

        mapMutex.write.withLock {
            ticketMap[newID] = newTicket
        }

        // Writes ticket
        sqlWriteQueue.send {
            it.update(
                queryOf("INSERT INTO TicketManager_V4_Tickets (ID, CREATOR_UUID, PRIORITY, STATUS, ASSIGNED_TO, STATUS_UPDATE_FOR_CREATOR, LOCATION) VALUES (?,?,?,?,?,?,?);",
                    newID,
                    newTicket.creatorUUID,
                    newTicket.priority.level,
                    newTicket.status.name,
                    newTicket.assignedTo,
                    newTicket.creatorStatusUpdate,
                    newTicket.location?.toString()
                )
            )
        }

        // Writes Actions
        fullTicket.actions.forEach { action ->
            sqlWriteQueue.send {
                it.update(
                    queryOf("INSERT INTO TicketManager_V4_Actions (TICKET_ID,ACTION_TYPE,CREATOR_UUID,MESSAGE,TIMESTAMP) VALUES (?,?,?,?,?);",
                        newID,
                        action.type.name,
                        action.user?.toString(),
                        action.message,
                        action.timestamp
                    )
                )
            }
        }

        return newID
    }

    override suspend fun getBasicTicketsOrNull(ids: List<Int>): List<BasicTicket> {
        return mapMutex.read.withLock { ids.asTypeSafeStream().map(ticketMap::get) }.filterNotNull().toList()
    }

    override suspend fun getFullTickets(basicTickets: List<BasicTicket>): List<FullTicket> {
        return basicTickets.map { it as FullTicket }
    }

    override suspend fun getOpenTickets(page: Int, pageSize: Int): Result<BasicTicket> {
        return getTicketsFilteredBy(page, pageSize) { it.status == BasicTicket.Status.OPEN }
    }

    override suspend fun getOpenTicketsAssignedTo(
        page: Int,
        pageSize: Int,
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): Result<BasicTicket> {
        val assignments = unfixedGroupAssignment.map { "::$it" } + assignment
        return getTicketsFilteredBy(page, pageSize) { it.status == BasicTicket.Status.OPEN && it.assignedTo in assignments }
    }

    override suspend fun getOpenTicketsNotAssigned(page: Int, pageSize: Int): Result<BasicTicket> {
        return getTicketsFilteredBy(page, pageSize) { it.status == BasicTicket.Status.OPEN && it.assignedTo == null }
    }

    private suspend fun getTicketsFilteredBy(page: Int, pageSize: Int, f: FullTicketPredicate): Result<BasicTicket> {
        val totalSize: Int
        val totalPages: Int

        val results = ticketCopies()
            .asParallelStream()
            .filter(f)
            .toList()
            .sortedWith(compareByDescending<FullTicket> { it.priority.level }.thenByDescending { it.id })
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
        val curTime = Instant.now().epochSecond
        mapMutex.read.lock()

        // Writes to ticketMap
        val changes = (lowerBound..upperBound).asSequence()
            .asParallelStream()
            .map { ticketMap[it] }
            .filterNotNull()
            .filter { it.status == BasicTicket.Status.OPEN }
            .toList()
            .onEach {
                val action = FullTicket.Action(FullTicket.Action.Type.MASS_CLOSE, actor, timestamp = curTime)
                ticketMap[it.id] = FullTicket(it.id, it.creatorUUID, it.location, it.priority, BasicTicket.Status.CLOSED, it.assignedTo, it.creatorStatusUpdate, it.actions + action)
            }

        mapMutex.read.unlock()

        // SQL stuff
        sqlWriteQueue.send { session ->
            val now = Instant.now().epochSecond

            session.update(queryOf("UPDATE TicketManager_V4_Tickets SET STATUS = ? WHERE ID IN (${changes.joinToString(", ")});", BasicTicket.Status.CLOSED.name))

            changes.forEach {
                session.update(
                    queryOf("INSERT INTO TicketManager_V4_Actions (TICKET_ID,ACTION_TYPE,CREATOR_UUID,MESSAGE,TIMESTAMP) VALUES (?,?,?,?,?);",
                        it,
                        FullTicket.Action.Type.MASS_CLOSE.name,
                        actor?.toString(),
                        null,
                        now,
                    )
                )
            }
        }
    }

    override suspend fun countOpenTickets(): Int {
        return ticketCopies().asParallelStream()
            .filter { it.status == BasicTicket.Status.OPEN }
            .toList()
            .count()
    }

    override suspend fun countOpenTicketsAssignedTo(assignment: String, unfixedGroupAssignment: List<String>): Int {
        val assignments = unfixedGroupAssignment.map { "::$it" } + assignment
        return ticketCopies().asParallelStream()
            .filter { it.status == BasicTicket.Status.OPEN && it.assignedTo in assignments }
            .toList()
            .count()
    }

    override suspend fun searchDatabase(
        constraints: SearchConstraint,
        page: Int,
        pageSize: Int
    ): Result<FullTicket> {
        val functions = mutableListOf<FullTicketPredicate>()

        constraints.run {
            // Builds Constraints
            val closeVariations = listOf(FullTicket.Action.Type.CLOSE, FullTicket.Action.Type.MASS_CLOSE)

            status?.run {{ t: FullTicket -> t.status == value }}?.apply(functions::add)
            priority?.run {{ t: FullTicket -> t.priority == value }}?.apply(functions::add)
            creator?.run {{ t: FullTicket -> t.creatorUUID == value }}?.apply(functions::add)
            assigned?.run {{ t: FullTicket -> t.assignedTo == value }}?.apply(functions::add)
            creationTime?.run {{ t: FullTicket -> t.actions[0].timestamp >= value}}?.apply(functions::add)
            world?.run {{ t: FullTicket -> t.location?.world?.equals(value) ?: false }}?.apply(functions::add)
            closedBy?.run {{ t: FullTicket -> t.actions.any { it.type in closeVariations && it.user == value }}}?.apply(functions::add)
            lastClosedBy?.run {{ t: FullTicket -> t.actions.lastOrNull { it.type in closeVariations }?.run { user == value } ?: false }}?.apply(functions::add)

            keywords?.run {{ t: FullTicket ->
                val comments = t.actions
                    .filter { it.type == FullTicket.Action.Type.OPEN || it.type == FullTicket.Action.Type.COMMENT }
                    .map { it.message!! }
                value.map { w -> comments.any { it.lowercase().contains(w.lowercase()) } }
                    .all { it }
            }}?.apply(functions::add)
        }

        val combinedFunction = if (functions.isNotEmpty()) { t: FullTicket -> functions.all { it(t) }}
        else { _: FullTicket -> true }

        val totalSize: Int
        val maxPages: Int
        val results = ticketCopies().asParallelStream()
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

        return Result(
            filteredResults = results.getOrElse(fixedPage-1) { listOf() },
            totalPages = maxPages,
            totalResults = totalSize,
            returnedPage = fixedPage,
        )
    }

    override suspend fun getTicketIDsWithUpdates(): List<Int> {
        return ticketCopies().asParallelStream()
            .filter { it.creatorStatusUpdate }
            .map { it.id }
            .toList()
    }

    override suspend fun getTicketIDsWithUpdatesFor(uuid: UUID): List<Int> {
        return ticketCopies().asParallelStream()
            .filter { it.creatorStatusUpdate && it.creatorUUID?.equals(uuid) == true }
            .map { it.id }
            .toList()
    }

    override suspend fun closeDatabase() {
        sqlWriteQueue.close()
    }

    override suspend fun initializeDatabase() {
        // Creates table if it doesn't exist
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

        // Loads tickets to memory
        val basicTickets = using(getSession()) { session ->
            session.run(
                queryOf("SELECT * FROM TicketManager_V4_Tickets;")
                    .map {
                        BasicTicketImpl(
                            id = it.int(1),
                            creatorUUID = it.stringOrNull(2)?.let(UUID::fromString),
                            priority = byteToPriority(it.byte(3)),
                            status = BasicTicket.Status.valueOf(it.string(4)),
                            assignedTo = it.stringOrNull(5),
                            creatorStatusUpdate = it.boolean(6),
                            location = it.stringOrNull(7)?.split(" ")?.let { l ->
                                BasicTicket.TicketLocation(
                                    world = l[0],
                                    x = l[1].toInt(),
                                    y = l[2].toInt(),
                                    z = l[3].toInt()
                                )
                            }
                        )
                    }
                    .asList
            )
        }
        val actions = using(getSession()) { session ->
            session.run(
                queryOf("SELECT TICKET_ID, ACTION_TYPE, CREATOR_UUID, MESSAGE, TIMESTAMP FROM TicketManager_V4_Actions;")
                    .map { r ->
                        r.int(1) to FullTicket.Action(
                            type = FullTicket.Action.Type.valueOf(r.string(2)),
                            user = r.stringOrNull(3)?.let { UUID.fromString(it) },
                            message = r.stringOrNull(4),
                            timestamp = r.long(5)
                        )
                    }
                    .asList
            )
        }

        // Combine actions and basic tickets to FullTickets and adds those to ticket map
        actions.groupBy({ it.first }, { it.second })
            .mapValues { it.value.sortedBy(FullTicket.Action::timestamp) }
            .run { basicTickets.map { it + get(it.id)!! } }
            .forEach { ticketMap[it.id] = it } // No sync needed as action is linear

        nextTicketID = IncrementalMutexController((ticketMap.keys.maxOrNull() ?: 0) + 1)

        // Launches async coroutine to linearly process SQL writes to the database
        asyncScope.launch {
            for (instructions in sqlWriteQueue) {
                using(getSession()) {
                    instructions(it)
                }
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

        try {
            when (to) {
                Database.Type.CACHED_SQLITE -> return@coroutineScope
                Database.Type.SQLITE -> return@coroutineScope // Backed by the same SQLite file already

                Database.Type.MEMORY -> {
                    val otherDB = databaseBuilders.memoryBuilder.build()
                        .also { it.initializeDatabase() }

                    mapMutex.read.withLock { ticketMap.values.toList() }
                        .parallelFlowForEach(otherDB::insertTicket)
                    otherDB.closeDatabase()
                }

                Database.Type.MYSQL -> {
                    val otherDB = databaseBuilders.mySQLBuilder.build()
                        .also { it.initializeDatabase() }

                    mapMutex.read.withLock { ticketMap.values.toList() }
                        .parallelFlowForEach(otherDB::insertTicket)
                    otherDB.closeDatabase()
                }
            }
            onComplete()
        } catch (e: Exception) {
            launch { onError(e) }
        }
    }

    private fun tableExists(table: String): Boolean {
        return using(getSession().connection.underlying.metaData.getTables(null, null, table, null)) {
            while (it.next())
                if (it.getString("TABLE_NAME")?.lowercase()?.equals(table.lowercase()) == true) return@using true
            return@using false
        }
    }
}