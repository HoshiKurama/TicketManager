package com.github.hoshikurama.ticketmanager.core.database.impl

import com.github.hoshikurama.ticketmanager.core.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.core.database.Result
import com.github.hoshikurama.ticketmanager.core.database.SearchConstraint
import com.github.hoshikurama.ticketmanager.core.misc.*
import com.github.hoshikurama.ticketmanager.core.ticket.Creator
import com.github.hoshikurama.ticketmanager.core.ticket.Ticket
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.io.BufferedWriter
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.notExists

@OptIn(ExperimentalPathApi::class)
class Memory(
    private val filePath: String,
    private val backupFrequency: Long,
) : AsyncDatabase {

    override val type: AsyncDatabase.Type = AsyncDatabase.Type.MEMORY

    private val ticketMap: ConcurrentHashMap<Long, Ticket>
    private val nextTicketID: AtomicLong
    private val fileIOOccurring = AtomicBoolean(false)

    private val backupScheduler = Executors.newScheduledThreadPool(1)

    private val gsonType = object : TypeToken<ConcurrentHashMap<Long, Ticket>>() {}.type!!
    private val customGson: Gson

    init {
        val path = Path.of("$filePath/TicketManager-Database8-Memory.ticketmanager")

        // Builds custom gson serializer for Creator
        val serializer = JsonSerializer<Creator> { src, _, _ ->
            JsonObject().apply { addProperty("creator", src.toString()) }
        }
        val deserializer = JsonDeserializer { json, _, _ ->
            json.asJsonObject
                .get("creator")
                .asString
                .let { mapToCreatorOrNull(it) ?: throw Exception("Unsupported Creator Type: $it") }
        }
        customGson = GsonBuilder()
            .registerTypeAdapter(Creator::class.java, serializer)
            .registerTypeAdapter(Creator::class.java, deserializer)
            .create()

        // File logic
        if (path.exists()) {
            val encodedMap = Files.readString(path)
            ticketMap = customGson.fromJson(encodedMap, gsonType)

            val highestID = ticketMap.maxByOrNull { it.key }?.key ?: 0L
            nextTicketID = AtomicLong(highestID + 1L)
        } else {
            ticketMap = ConcurrentHashMap()
            nextTicketID = AtomicLong(1)
        }

        // Launches backup system for duration of database
        fun scheduleBackup() {
            if (!fileIOOccurring.get()) {
                fileIOOccurring.set(true)
                writeDatabaseToFile()
                fileIOOccurring.set(false)

                backupScheduler.schedule(::scheduleBackup, backupFrequency, TimeUnit.SECONDS)
            }
        }

        backupScheduler.schedule(::scheduleBackup, backupFrequency, TimeUnit.SECONDS)
    }

    override fun setAssignment(ticketID: Long, assignment: String?) {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = Ticket(t.id, t.creator, t.priority, t.status, assignment, t.creatorStatusUpdate, t.actions)
    }

    override fun setCreatorStatusUpdate(ticketID: Long, status: Boolean) {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = Ticket(t.id, t.creator, t.priority, t.status, t.assignedTo, status, t.actions)
    }

    override fun setPriority(ticketID: Long, priority: Ticket.Priority) {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = Ticket(t.id, t.creator, priority, t.status, t.assignedTo, t.creatorStatusUpdate, t.actions)
    }

    override fun setStatus(ticketID: Long, status: Ticket.Status) {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = Ticket(t.id, t.creator, t.priority, status, t.assignedTo, t.creatorStatusUpdate, t.actions)

    }

    override fun insertAction(id: Long, action: Ticket.Action) {
        ticketMap[id] = ticketMap[id]!! + action
    }

    override fun insertTicketAsync(ticket: Ticket): CompletableFuture<Long> {
        val newID = nextTicketID.getAndIncrement()
        val newTicket = Ticket(newID, ticket.creator, ticket.priority, ticket.status, ticket.assignedTo, ticket.creatorStatusUpdate, ticket.actions)

        ticketMap[newID] = newTicket
        return CompletableFuture.completedFuture(newID)
    }

    override fun getTicketsAsync(ids: List<Long>): CompletableFuture<List<Ticket>> {
        val tickets = ids.asParallelStream().map(ticketMap::get).filterNotNull().toList()
        return CompletableFuture.completedFuture(tickets)

    }

    override fun getTicketOrNullAsync(id: Long): CompletableFuture<Ticket?> {
        return CompletableFuture.completedFuture(ticketMap[id])
    }

    override fun getOpenTicketsAsync(page: Int, pageSize: Int): CompletableFuture<Result> {
        return getTicketsFilteredBy(page, pageSize) { it.status == Ticket.Status.OPEN }
    }

    override fun getOpenTicketsAssignedToAsync(
        page: Int,
        pageSize: Int,
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): CompletableFuture<Result> {
        val assignments = unfixedGroupAssignment.map { "::$it" } + assignment
        return getTicketsFilteredBy(page, pageSize) { it.status == Ticket.Status.OPEN && it.assignedTo in assignments }
    }

    override fun getOpenTicketsNotAssignedAsync(page: Int, pageSize: Int): CompletableFuture<Result> {
        return getTicketsFilteredBy(page, pageSize) { it.status == Ticket.Status.OPEN && it.assignedTo == null }
    }

    private fun getTicketsFilteredBy(page: Int, pageSize: Int, f: TicketPredicate): CompletableFuture<Result> {
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
            Result(
                filteredResults = results.getOrElse(fixedPage-1) { listOf() },
                totalPages = totalPages,
                totalResults = totalSize,
                returnedPage = fixedPage,
            )
        )
    }

    override fun massCloseTickets(lowerBound: Long, upperBound: Long, actor: Creator, ticketLoc: Ticket.TicketLocation) {
        val curTime = Instant.now().epochSecond

        (lowerBound..upperBound).asSequence()
            .asParallelStream()
            .mapNotNull { ticketMap[it] }
            .filter { it.status == Ticket.Status.OPEN }
            .toList()
            .forEach {
                val action = Ticket.Action(Ticket.Action.Type.MASS_CLOSE, actor, timestamp = curTime, location = ticketLoc)
                ticketMap[it.id] = Ticket(it.id, it.creator, it.priority, Ticket.Status.CLOSED, it.assignedTo, it.creatorStatusUpdate, it.actions + action)
            }
    }

    override fun countOpenTicketsAsync(): CompletableFuture<Long> {
        val count = ticketMap.values.toList()
            .asParallelStream()
            .filter { it.status == Ticket.Status.OPEN }
            .count()

        return CompletableFuture.completedFuture(count)
    }

    override fun countOpenTicketsAssignedToAsync(
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): CompletableFuture<Long> {
        val assignments = unfixedGroupAssignment.map { "::$it" } + assignment
        val count = ticketMap.values.toList()
            .asParallelStream()
            .filter { it.status == Ticket.Status.OPEN && it.assignedTo in assignments }
            .count()

        return CompletableFuture.completedFuture(count)
    }

    override fun searchDatabaseAsync(
        constraints: SearchConstraint,
        page: Int,
        pageSize: Int
    ): CompletableFuture<Result> {
        val functions = mutableListOf<TicketPredicate>()

        constraints.run {
            // Builds Constraints
            val closeVariations = listOf(Ticket.Action.Type.CLOSE, Ticket.Action.Type.MASS_CLOSE)

            status?.run {{ t: Ticket -> t.status == value }}?.apply(functions::add)
            priority?.run {{ t: Ticket -> t.priority == value }}?.apply(functions::add)
            creator?.run {{ t: Ticket -> t.creator == value }}?.apply(functions::add)
            assigned?.run {{ t: Ticket -> t.assignedTo == value }}?.apply(functions::add)
            creationTime?.run {{ t: Ticket -> t.actions[0].timestamp >= value}}?.apply(functions::add)
            world?.run {{ t: Ticket -> t.actions[0].location.world?.equals(value) ?: false }}?.apply(functions::add)
            closedBy?.run {{ t: Ticket -> t.actions.any { it.type in closeVariations && it.user == value }}}?.apply(functions::add)
            lastClosedBy?.run {{ t: Ticket -> t.actions.lastOrNull { it.type in closeVariations }?.run { user == value } ?: false }}?.apply(functions::add)

            keywords?.run {{ t: Ticket ->
                val comments = t.actions
                    .filter { it.type == Ticket.Action.Type.OPEN || it.type == Ticket.Action.Type.COMMENT }
                    .map { it.message!! }
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
            Result(
                filteredResults = results.getOrElse(fixedPage-1) { listOf() },
                totalPages = maxPages,
                totalResults = totalSize,
                returnedPage = fixedPage,
            )
        )
    }

    override fun getTicketIDsWithUpdatesAsync(): CompletableFuture<List<Long>> {
        val values = ticketMap.values.toList()
            .asParallelStream()
            .filter { it.creatorStatusUpdate }
            .map { it.id }
            .toList()

        return CompletableFuture.completedFuture(values)
    }

    override fun getTicketIDsWithUpdatesForAsync(creator: Creator): CompletableFuture<List<Long>> {
        val values = ticketMap.values.toList()
            .asParallelStream()
            .filter { it.creatorStatusUpdate && it.creator == creator }
            .map { it.id }
            .toList()

        return CompletableFuture.completedFuture(values)
    }

    override fun closeDatabase() {

        if (fileIOOccurring.get()) {
            while (fileIOOccurring.get())
                Thread.sleep(100) // Plugin is either reloading or server is restarting. It's fine to sleep this one thread. EXTREMELY IMPORTANT THIS FINISHES!
        } else {
            fileIOOccurring.set(true)
            writeDatabaseToFile()
            fileIOOccurring.set(false)
        }

        backupScheduler.shutdownNow() // Safe to do so now
    }

    override fun initializeDatabase() {
        // Done on object instantiation
    }

    override fun insertTicketForMigration(other: AsyncDatabase) {
        ticketMap.values.forEach(other::insertTicketAsync)
    }

    private fun writeDatabaseToFile() {
        val path = Path.of("$filePath/TicketManager-Database8-Memory.ticketmanager")
        if (path.notExists()) path.createFile()

        val encodedString: String = customGson.toJson(ticketMap, gsonType)

        var writer: BufferedWriter? = null
        try {
            writer = Files.newBufferedWriter(path, Charset.forName("UTF-8"))
            writer.write(encodedString)
        } finally {
            writer?.close()
        }
    }
}