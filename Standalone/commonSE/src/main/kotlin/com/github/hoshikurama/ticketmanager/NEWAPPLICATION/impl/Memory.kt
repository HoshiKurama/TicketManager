package com.github.hoshikurama.ticketmanager.NEWAPPLICATION.impl

import com.github.hoshikurama.ticketmanager.api.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.database.DBResult
import com.github.hoshikurama.ticketmanager.api.database.SearchConstraints
import com.github.hoshikurama.ticketmanager.api.ticket.TicketAssignmentType
import com.github.hoshikurama.ticketmanager.api.ticket.TicketAction
import com.github.hoshikurama.ticketmanager.api.ticket.TicketCreator
import com.github.hoshikurama.ticketmanager.api.ticket.TicketInterface
import com.github.hoshikurama.ticketmanager.commonse.api.impl.database.misc.DBResultSTD
import com.github.hoshikurama.ticketmanager.commonse.api.impl.TicketSTD
import com.github.hoshikurama.ticketmanager.commonse.old.misc.*

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.notExists
// TODO FIX ANYTHING THAT USES ASSIGNMENT
//TODO: This will be moved to an extension
class Memory(
    private val filePath: String,
    private val backupFrequency: Long,
) : AsyncDatabase {

    private val ticketMap: ConcurrentHashMap<Long, TicketInterface>
    private val nextTicketID: AtomicLong
    private val fileIOOccurring = AtomicBoolean(false)

    private val backupScheduler = Executors.newScheduledThreadPool(1)

    private val gsonType = object : TypeToken<ConcurrentHashMap<Long, TicketSTD>>() {}.type!!
    private val customGson: Gson

    init {
        val path = Path.of("$filePath/TicketManager-Database8-Memory.ticketmanager")

        // Builds custom gson serializer for Creator
        val serializer = JsonSerializer<TicketCreator> { src, _, _ ->
            JsonObject().apply { addProperty("creator", src.asString()) }
        }
        val deserializer = JsonDeserializer { json, _, _ ->
            json.asJsonObject
                .get("creator")
                .asString
                .let { mapToCreatorOrNull(it) ?: throw Exception("Unsupported Creator Type: $it") }
        }
        customGson = GsonBuilder()
            .registerTypeAdapter(TicketCreator::class.java, serializer)
            .registerTypeAdapter(TicketCreator::class.java, deserializer)
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

    override fun setAssignmentAsync(ticketID: Long, assignment: TicketAssignmentType): CompletableFuture<Void> {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = TicketSTD(t.id, t.creator, t.priority, t.status, assignment, t.creatorStatusUpdate, t.actions)
        return CompletableFuture.completedFuture(null)
    }

    override fun setCreatorStatusUpdateAsync(ticketID: Long, status: Boolean): CompletableFuture<Void> {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = TicketSTD(t.id, t.creator, t.priority, t.status, t.assignedTo, status, t.actions)
        return CompletableFuture.completedFuture(null)
    }

    override fun setPriorityAsync(ticketID: Long, priority: TicketInterface.Priority): CompletableFuture<Void> {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = TicketSTD(t.id, t.creator, priority, t.status, t.assignedTo, t.creatorStatusUpdate, t.actions)
        return CompletableFuture.completedFuture(null)
    }

    override fun setStatusAsync(ticketID: Long, status: TicketInterface.Status): CompletableFuture<Void> {
        val t = ticketMap[ticketID]!!
        ticketMap[ticketID] = TicketSTD(t.id, t.creator, t.priority, status, t.assignedTo, t.creatorStatusUpdate, t.actions)
        return CompletableFuture.completedFuture(null)
    }

    override fun insertActionAsync(id: Long, action: TicketAction): CompletableFuture<Void> {
        ticketMap[id] = ticketMap[id]!! + action
        return CompletableFuture.completedFuture(null)
    }

    override fun insertNewTicketAsync(ticket: TicketInterface): CompletableFuture<Long> {
        val newID = nextTicketID.getAndIncrement()
        val newTicket = TicketSTD(newID, ticket.creator, ticket.priority, ticket.status, ticket.assignedTo, ticket.creatorStatusUpdate, ticket.actions)

        ticketMap[newID] = newTicket
        return CompletableFuture.completedFuture(newID)
    }

    private fun getTicketsAsync(ids: List<Long>): List<TicketInterface> {
        return ids.asParallelStream().map(ticketMap::get).filterNotNull().toList()
    }

    override fun getTicketOrNullAsync(id: Long): CompletableFuture<TicketInterface?> {
        return CompletableFuture.completedFuture(ticketMap[id])
    }

    override fun getOpenTicketsAsync(page: Int, pageSize: Int): CompletableFuture<DBResult> {
        return getTicketsFilteredBy(page, pageSize) { it.status == TicketInterface.Status.OPEN }
    }

    override fun getOpenTicketsAssignedToAsync(
        page: Int,
        pageSize: Int,
        assignment: TicketAssignmentType,
        unfixedGroupAssignment: List<String>
    ): CompletableFuture<DBResult> {
        val assignments = unfixedGroupAssignment.map { "::$it" } + assignment
        return getTicketsFilteredBy(page, pageSize) { it.status == TicketInterface.Status.OPEN && it.assignedTo in assignments }
    }

    override fun getOpenTicketsNotAssignedAsync(page: Int, pageSize: Int): CompletableFuture<DBResult> {
        return getTicketsFilteredBy(page, pageSize) { it.status == TicketInterface.Status.OPEN && it.assignedTo == null }
    }

    private fun getTicketsFilteredBy(page: Int, pageSize: Int, f: TicketPredicate): CompletableFuture<DBResult> = CompletableFuture.supplyAsync {
        val totalSize: Int
        val totalPages: Int

        val results = ticketMap.values.toList()
            .asParallelStream()
            .filter(f)
            .toList()
            .sortedWith(compareByDescending<TicketInterface> { it.priority.level }.thenByDescending { it.id })
            .apply { totalSize = count() }
            .run { if (pageSize == 0 || isEmpty()) listOf(this) else chunked(pageSize) }
            .apply { totalPages = count() }

        val fixedPage = when {
            totalPages == 0 || page < 1 -> 1
            page in 1..totalPages -> page
            else -> totalPages
        }

         DBResultSTD(
            filteredResults = results.getOrElse(fixedPage-1) { listOf() },
            totalPages = totalPages,
            totalResults = totalSize,
            returnedPage = fixedPage,
        )
    }

    override fun massCloseTicketsAsync(lowerBound: Long, upperBound: Long, actor: TicketCreator, ticketLoc: TicketInterface.CreationLocation): CompletableFuture<Void> = CompletableFuture.runAsync {
        val curTime = Instant.now().epochSecond

        (lowerBound..upperBound).asSequence()
            .asParallelStream()
            .mapNotNull { ticketMap[it] }
            .filter { it.status == TicketInterface.Status.OPEN }
            .toList()
            .forEach {
                val action = TicketSTD.ActionSTD(TicketSTD.ActionSTD.MassCloseSTD, actor, timestamp = curTime, location = ticketLoc)
                ticketMap[it.id] = TicketSTD(it.id, it.creator, it.priority, TicketInterface.Status.CLOSED, it.assignedTo, it.creatorStatusUpdate, it.actions + action)
            }
    }

    override fun countOpenTicketsAsync(): CompletableFuture<Long> = CompletableFuture.supplyAsync {
        ticketMap.values.toList()
            .asParallelStream()
            .filter { it.status == TicketInterface.Status.OPEN }
            .count()
    }

    override fun countOpenTicketsAssignedToAsync(
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): CompletableFuture<Long> = CompletableFuture.supplyAsync {
        val assignments = unfixedGroupAssignment.map { "::$it" } + assignment

        ticketMap.values.toList()
            .asParallelStream()
            .filter { it.status == TicketInterface.Status.OPEN && it.assignedTo in assignments }
            .count()
    }

    override fun searchDatabaseAsync(
        constraints: SearchConstraints,
        page: Int,
        pageSize: Int
    ): CompletableFuture<DBResult> = CompletableFuture.supplyAsync {
        val functions = mutableListOf<TicketPredicate>()

        constraints.run {
            // Builds Constraints
            val closeVariations = listOf(TicketInterface.Action.Type.TypeEnum.CLOSE, TicketInterface.Action.Type.TypeEnum.MASS_CLOSE)

            status?.run {{ t: TicketInterface -> t.status == value }}?.apply(functions::add)
            priority?.run {{ t: TicketInterface -> t.priority == value }}?.apply(functions::add)
            creator?.run {{ t: TicketInterface -> t.creator == value }}?.apply(functions::add)
            assigned?.run {{ t: TicketInterface -> t.assignedTo == value }}?.apply(functions::add)
            creationTime?.run {{ t: TicketInterface -> t.actions[0].timestamp >= value}}?.apply(functions::add)
            world?.run {{ t: TicketInterface -> t.actions[0].location.world?.equals(value) ?: false }}?.apply(functions::add)
            closedBy?.run {{ t: TicketInterface -> t.actions.any { it.type.getTypeEnum() in closeVariations && it.user == value }}}?.apply(functions::add)
            lastClosedBy?.run {{ t: TicketInterface -> t.actions.lastOrNull { it.type.getTypeEnum() in closeVariations }?.run { user == value } ?: false }}?.apply(functions::add)

            keywords?.run {{ t: TicketInterface ->
                val comments = t.actions
                    .filter { it.type is TicketInterface.Action.Type.OPEN || it.type is TicketInterface.Action.Type.COMMENT }
                    .map { it.type.getMessage()!! }
                value.map { w -> comments.any { it.lowercase().contains(w.lowercase()) } }
                    .all { it }
            }}?.apply(functions::add)
        }

        val combinedFunction = if (functions.isNotEmpty()) { t: TicketInterface -> functions.all { it(t) }}
        else { _: TicketInterface -> true }

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

        DBResultSTD(
            filteredResults = results.getOrElse(fixedPage-1) { listOf() },
            totalPages = maxPages,
            totalResults = totalSize,
            returnedPage = fixedPage,
        )
    }

    override fun getTicketIDsWithUpdatesAsync(): CompletableFuture<List<Long>> = CompletableFuture.supplyAsync {
        ticketMap.values.toList()
            .asParallelStream()
            .filter { it.creatorStatusUpdate }
            .map { it.id }
            .toList()
    }

    override fun getTicketIDsWithUpdatesForAsync(creator: TicketCreator): CompletableFuture<List<Long>> = CompletableFuture.supplyAsync {
        ticketMap.values.toList()
            .asParallelStream()
            .filter { it.creatorStatusUpdate && it.creator == creator }
            .map { it.id }
            .toList()
    }

    override fun getOwnedTicketIDsAsync(creator: TicketCreator): CompletableFuture<List<Long>> = CompletableFuture.supplyAsync {
        ticketMap.values.toList()
            .asParallelStream()
            .filter { it.creator equalTo creator }
            .map { it.id }
            .toList()
    }

    override fun closeDatabase() {
        runBlocking {
            if (fileIOOccurring.get()) {
                while (fileIOOccurring.get())
                    delay(100) // Plugin is either reloading or server is restarting. EXTREMELY IMPORTANT THIS FINISHES!
            } else {
                fileIOOccurring.set(true)
                writeDatabaseToFile()
                fileIOOccurring.set(false)
            }

            backupScheduler.shutdownNow() // Safe to do so now
        }
    }

    override fun initializeDatabase() {
        // Done on object instantiation
    }

    /*
    override suspend fun insertTicketForMigration(other: AsyncDatabase) {
        ticketMap.values.forEach { other.insertNewTicketAsync(it) }
    }

     */

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

private fun TicketInterface.Action.Type.getMessage(): String? = when (this) {
    is TicketInterface.Action.Type.OPEN -> message
    is TicketInterface.Action.Type.ASSIGN -> assignment
    is TicketInterface.Action.Type.COMMENT -> comment
    is TicketInterface.Action.Type.SET_PRIORITY -> priority.level.toString()
    else -> null
}