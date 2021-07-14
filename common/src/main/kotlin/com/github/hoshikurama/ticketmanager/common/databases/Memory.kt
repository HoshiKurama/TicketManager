package com.github.hoshikurama.ticketmanager.common.databases

import com.github.hoshikurama.ticketmanager.common.*
import com.github.hoshikurama.ticketmanager.common.ticket.BasicTicket
import com.github.hoshikurama.ticketmanager.common.ticket.FullTicket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.notExists

@OptIn(DelicateCoroutinesApi::class)
class Memory(
    private val filePath: String,
    backupFrequency: Long
) : Database {
    override val type = Database.Type.Memory

    private val mapMutex = ReadWriteMutex()
    private val ticketMap: MutableMap<Int, FullTicket>
    private val fileIOOccurring = MutexControlled(false)

    private val nextTicketID: IncrementalMutexController
    private val backupJob: Job

    init {
        val path = Path.of("$filePath/TicketManager-Database4-Memory.ticketmanager")

        if (path.exists()) {
            val encodedMap = Files.readString(path)
            ticketMap = Json.decodeFromString(encodedMap)

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

    override suspend fun getActionsAsFlow(ticketID: Int): Flow<FullTicket.Action> {
        return ticketMap[ticketID]!!.actions.asFlow()
    }

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

    override suspend fun getBasicTicket(ticketID: Int): BasicTicket? {
        return mapMutex.read.withLock { ticketMap[ticketID] }
    }

    override suspend fun addAction(ticketID: Int, action: FullTicket.Action) {
        mapMutex.write.withLock {
            val t = ticketMap[ticketID]!!
            ticketMap[ticketID] = FullTicket(t.id, t.creatorUUID, t.location, t.priority, t.status, t.assignedTo, t.creatorStatusUpdate, t.actions + action)
        }
    }

    override suspend fun addFullTicket(fullTicket: FullTicket) {
        val newID = nextTicketID.getAndIncrement()
        val newTicket = FullTicket(newID, fullTicket.creatorUUID, fullTicket.location, fullTicket.priority, fullTicket.status, fullTicket.assignedTo, fullTicket.creatorStatusUpdate, fullTicket.actions)

        mapMutex.write.withLock {
            ticketMap[newID] = newTicket
        }
    }

    override suspend fun addNewTicket(basicTicket: BasicTicket, context: CoroutineContext, message: String): Int {
        val id = nextTicketID.getAndIncrement()
        val action = FullTicket.Action(FullTicket.Action.Type.OPEN, basicTicket.creatorUUID, message)
        val fullTicket = FullTicket(id, basicTicket.creatorUUID, basicTicket.location, basicTicket.priority, basicTicket.status, basicTicket.assignedTo, basicTicket.creatorStatusUpdate, listOf(action))

        mapMutex.write.withLock {
            ticketMap[id] = fullTicket
        }

        return id
    }

    override suspend fun massCloseTickets(lowerBound: Int, upperBound: Int, uuid: UUID?, context: CoroutineContext) {
        val ticketsToChange = mutableListOf<FullTicket>()

        (lowerBound..upperBound).forEach { ticketID ->
            mapMutex.read.withLock {
                val ticket = ticketMap[ticketID]

                if (ticket != null && ticket.status == BasicTicket.Status.OPEN) {
                    ticketsToChange += ticket
                }
            }
        }

        val curTime = Instant.now().epochSecond
        val newTickets = ticketsToChange.map {
            val action = FullTicket.Action(FullTicket.Action.Type.MASS_CLOSE, uuid, timestamp = curTime)
            FullTicket(it.id, it.creatorUUID, it.location, it.priority, BasicTicket.Status.CLOSED, it.assignedTo, it.creatorStatusUpdate, it.actions + action)
        }

        mapMutex.write.withLock {
            newTickets.forEach {
                ticketMap[it.id] = it
            }
        }
    }

    override suspend fun getOpenIDPriorityPairs(): Flow<Pair<Int, Byte>> {
        val openTickets: Sequence<FullTicket>

        mapMutex.read.withLock {
            openTickets = ticketMap.asSequence()
                .map { it.value }
                .filter { it.status == BasicTicket.Status.OPEN }
        }

        return openTickets.map { it.id to it.priority.level }.asFlow()
    }

    override suspend fun getAssignedOpenIDPriorityPairs(
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): Flow<Pair<Int, Byte>> {
        val openTickets: Sequence<FullTicket>
        val assignments = unfixedGroupAssignment.map { "::$it" } + assignment

        mapMutex.read.withLock {
            openTickets = ticketMap.asSequence()
                .map { it.value }
                .filter { it.status == BasicTicket.Status.OPEN }
                .filter { it.assignedTo in assignments }
        }

        return openTickets.map { it.id to it.priority.level }.asFlow()
    }

    override suspend fun getIDsWithUpdates(): Flow<Int> {
        return mapMutex.read.withLock {
            ticketMap.asSequence()
                .map { it.value }
                .filter { it.creatorStatusUpdate }
                .map { it.id }
        }.asFlow()
    }

    override suspend fun getIDsWithUpdatesFor(uuid: UUID): Flow<Int> {
        return mapMutex.read.withLock {
            ticketMap.asSequence()
                .map { it.value }
                .filter { it.creatorUUID?.equals(uuid) == true }
                .filter { it.creatorStatusUpdate }
                .map { it.id }
        }.asFlow()
    }

    override suspend fun getBasicTickets(ids: List<Int>): Flow<BasicTicket> {
        return mapMutex.read.withLock {
            ids.mapNotNull { ticketMap[it] }
        }.asFlow()
    }

    override suspend fun getFullTicketsFromBasics(
        basicTickets: List<BasicTicket>,
        context: CoroutineContext
    ): Flow<FullTicket> {
        val ids = basicTickets.map { it.id }

        return getBasicTickets(ids).map { it as FullTicket }.toList().asFlow()
    }

    override suspend fun getFullTickets(ids: List<Int>, context: CoroutineContext): Flow<FullTicket> {
        return mapMutex.read.withLock {
            ids.mapNotNull { ticketMap[it] }
        }.asFlow()
    }

    override suspend fun searchDatabase(
        context: CoroutineContext,
        locale: TMLocale,
        mainTableConstraints: List<Pair<String, String?>>,
        searchFunction: (FullTicket) -> Boolean
    ): Flow<FullTicket> {
        val mainToFunction = mainTableConstraints.mapNotNull { (word, arg) ->
            when (word) {
                locale.searchAssigned -> { t: FullTicket -> t.assignedTo == arg }
                locale.searchCreator -> {
                    val uuid = arg?.run(UUID::fromString);
                    { t: FullTicket -> t.creatorUUID == uuid }
                }
                locale.searchPriority -> {
                    val priority = arg!!.toByte().run(::byteToPriority);
                    { t: FullTicket -> t.priority == priority }
                }
                locale.searchStatus -> {
                    val status = BasicTicket.Status.valueOf(arg!!);
                    { t: FullTicket -> t.status == status }
                }
                else -> null
            }
        }
        val newSearchFunction = { t: FullTicket -> mainToFunction.all { it(t) } && searchFunction(t) }

        return mapMutex.read.withLock {
            ticketMap.asSequence()
                .filter { newSearchFunction(it.value) }
                .map { it.value }
        }.asFlow()
    }

    override suspend fun closeDatabase() {
        while (fileIOOccurring.get()) delay(100)

        // Cancels GlobalScope backup job
        backupJob.cancel()

        fileIOOccurring.set(true)
        writeDatabaseToFileBlocking()
        fileIOOccurring.set(false)
    }

    override suspend fun initialiseDatabase() {
        // Done on object instantiation
    }

    override suspend fun updateNeeded(): Boolean {
        return false // Introduced as V4 db with TM5
    }

    override suspend fun migrateDatabase(
        context: CoroutineContext,
        to: Database.Type,
        mySQLBuilder: suspend () -> MySQL,
        sqLiteBuilder: suspend () -> SQLite,
        memoryBuilder: suspend () -> Memory,
        onBegin: suspend () -> Unit,
        onComplete: suspend () -> Unit
    ) {
        withContext(context) {
            launch { onBegin() }
        }
        when (to) {
            Database.Type.Memory -> return

            Database.Type.MySQL,
            Database.Type.SQLite -> {
                val otherDB = if (to == Database.Type.MySQL) mySQLBuilder() else sqLiteBuilder()
                otherDB.initialiseDatabase()

                mapMutex.read.withLock {
                    ticketMap.map { it.value }
                }
                    .map {
                        withContext(context) {
                            launch { otherDB.addFullTicket(it) }
                        }
                    }

                otherDB.closeDatabase()
            }
        }

        withContext(context) {
            launch { onComplete() }
        }
    }

    override suspend fun updateDatabase(
        context: CoroutineContext,
        onBegin: suspend () -> Unit,
        onComplete: suspend () -> Unit,
        offlinePlayerNameToUuidOrNull: (String) -> UUID?
    ) {
        // Not Applicable yet
    }

    private suspend fun writeDatabaseToFileBlocking() {
        val path = Path.of("$filePath/TicketManager-Database4-Memory.ticketmanager")
        if (path.notExists()) path.createFile()

        val encodedString: String
        mapMutex.read.withLock {
            encodedString = Json.encodeToString(ticketMap)
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