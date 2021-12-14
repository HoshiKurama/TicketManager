package com.github.hoshikurama.ticketmanager.database.impl

import com.github.hoshikurama.ticketmanager.TMLocale
import com.github.hoshikurama.ticketmanager.database.Database
import com.github.hoshikurama.ticketmanager.database.DatabaseBuilders
import com.github.hoshikurama.ticketmanager.database.Result
import com.github.hoshikurama.ticketmanager.database.SearchConstraint
import com.github.hoshikurama.ticketmanager.misc.*
import com.github.hoshikurama.ticketmanager.misc.TypeSafeStream.Companion.asTypeSafeStream
import com.github.hoshikurama.ticketmanager.ticket.BasicTicket
import com.github.hoshikurama.ticketmanager.ticket.FullTicket
import com.github.hoshikurama.ticketmanager.ticket.plus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.withLock
import java.io.BufferedWriter
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.notExists

@OptIn(DelicateCoroutinesApi::class)
class Memory(
    private val filePath: String,
    backupFrequency: Long
) : Database {

    override val type = Database.Type.MEMORY
    private val mapMutex = ReadWriteMutex()
    private val ticketMap: MutableMap<Int, FullTicket>
    private val fileIOOccurring = MutexControlled(false)

    private val nextTicketID: IncrementalMutexController
    private val backupJob: Job

    private val gsonType = object : TypeToken<MutableMap<Int, FullTicket>>() {}.type!!

    init {
        val path = Path.of("$filePath/TicketManager-Database4-Memory.ticketmanager")

        if (path.exists()) {

            val encodedMap = Files.readString(path)
            ticketMap = Gson().fromJson(encodedMap, gsonType)

            val highestID = ticketMap.maxByOrNull { it.key }?.key ?: 0
            nextTicketID = IncrementalMutexController(highestID + 1)
        } else {
            ticketMap = mutableMapOf()
            nextTicketID = IncrementalMutexController(1)
        }

        // Launches backup system for duration of database
        backupJob = GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                delay(1000L * backupFrequency)

                if (!fileIOOccurring.get()) {
                    fileIOOccurring.set(true)
                    writeDatabaseToFileBlocking()
                    fileIOOccurring.set(false)
                }
            }
        }
    }

    suspend fun ticketCopies() = mapMutex.read.withLock { ticketMap.values.toList() }

    override suspend fun setAssignment(ticketID: Int, assignment: String?) {
        mapMutex.write.withLock {
            val t = ticketMap[ticketID]!!
            ticketMap[ticketID] = FullTicket(t.id, t.creatorUUID, t.location, t.priority, t.status, assignment, t.creatorStatusUpdate, t.actions)
        }
    }

    override suspend fun setCreatorStatusUpdate(ticketID: Int, status: Boolean) {
        mapMutex.write.withLock {
            val t = ticketMap[ticketID]!!
            ticketMap[ticketID] = FullTicket(t.id, t.creatorUUID, t.location, t.priority, t.status, t.assignedTo, status, t.actions)
        }
    }

    override suspend fun setPriority(ticketID: Int, priority: BasicTicket.Priority) {
        mapMutex.write.withLock {
            val t = ticketMap[ticketID]!!
            ticketMap[ticketID] = FullTicket(t.id, t.creatorUUID, t.location, priority, t.status, t.assignedTo, t.creatorStatusUpdate, t.actions)
        }
    }

    override suspend fun setStatus(ticketID: Int, status: BasicTicket.Status) {
        mapMutex.write.withLock {
            val t = ticketMap[ticketID]!!
            ticketMap[ticketID] = FullTicket(t.id, t.creatorUUID, t.location, t.priority, status, t.assignedTo, t.creatorStatusUpdate, t.actions)
        }
    }

    override suspend fun insertAction(id: Int, action: FullTicket.Action) {
        mapMutex.write.withLock {
            ticketMap[id] = ticketMap[id]!! + action
        }
    }

    override suspend fun insertTicket(fullTicket: FullTicket): Int {
        val newID = nextTicketID.getAndIncrement()
        val newTicket = FullTicket(newID, fullTicket.creatorUUID, fullTicket.location, fullTicket.priority, fullTicket.status, fullTicket.assignedTo, fullTicket.creatorStatusUpdate, fullTicket.actions)

        mapMutex.write.withLock {
            ticketMap[newID] = newTicket
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
        mapMutex.write.lock()

        (lowerBound..upperBound).asSequence()
            .asParallelStream()
            .map { ticketMap[it] }
            .filter { it != null  }
            .filter { it!!.status == BasicTicket.Status.OPEN }
            .toList()
            .forEach {
                val action = FullTicket.Action(FullTicket.Action.Type.MASS_CLOSE, actor, timestamp = curTime)
                ticketMap[it!!.id] = FullTicket(it.id, it.creatorUUID, it.location, it.priority, BasicTicket.Status.CLOSED, it.assignedTo, it.creatorStatusUpdate, it.actions + action)
            }
        mapMutex.write.unlock()
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
        locale: TMLocale,
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

    override suspend fun initializeDatabase() {
        // Done on object instantiation
    }

    override suspend fun closeDatabase() {
        while (fileIOOccurring.get()) delay(100)

        // Cancels GlobalScope backup job
        backupJob.cancel()

        fileIOOccurring.set(true)
        writeDatabaseToFileBlocking()
        fileIOOccurring.set(false)
    }

    override suspend fun migrateDatabase(
        to: Database.Type,
        databaseBuilders: DatabaseBuilders,
        onBegin: suspend () -> Unit,
        onComplete: suspend () -> Unit,
        onError: suspend (Exception) -> Unit,
    ) = coroutineScope {
        launch { onBegin() }

        try {
            when (to) {
                Database.Type.MEMORY -> return@coroutineScope

                Database.Type.MYSQL -> {
                    val otherDB = databaseBuilders.mySQLBuilder.build()
                        .also { it.initializeDatabase() }

                    mapMutex.read.withLock { ticketMap.values }
                        .asFlow()
                        .buffer(10_000)
                        .onEach(otherDB::insertTicket)
                    otherDB.closeDatabase()
                }

                Database.Type.SQLITE, Database.Type.CACHED_SQLITE -> {
                    val otherDB = databaseBuilders.sqLiteBuilder.build()
                        .also { it.initializeDatabase() }

                    mapMutex.read.withLock { ticketMap.values }
                        .forEach { otherDB.insertTicket(it) }
                    otherDB.closeDatabase()
                }
            }
            onComplete()
        } catch (e: Exception) {
            launch { onError(e) }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun writeDatabaseToFileBlocking() {
        val path = Path.of("$filePath/TicketManager-Database4-Memory.ticketmanager")
        if (path.notExists()) path.createFile()

        val encodedString: String
        mapMutex.read.withLock {
            encodedString = Gson().toJson(ticketMap, gsonType)
        }

        var writer: BufferedWriter? = null
        try {
            writer = Files.newBufferedWriter(path, Charset.forName("UTF-8"))

            writer.write(encodedString)
        } finally {
            writer?.close()
        }
    }
}