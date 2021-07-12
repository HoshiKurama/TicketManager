package com.github.hoshikurama.ticketmanager.common.databases

import com.github.hoshikurama.ticketmanager.common.TMLocale
import com.github.hoshikurama.ticketmanager.common.byteToPriority
import com.github.hoshikurama.ticketmanager.common.ticket.BasicTicket
import com.github.hoshikurama.ticketmanager.common.ticket.FullTicket
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.KeyValue
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class Redis(
    absoluteDataFolderPath: String,
    password: String,
    saveFrequency: Int
) : Database {
    private val client: RedisClient
    private val connection: StatefulRedisConnection<String, String>
    private val redis: RedisCoroutinesCommands<String, String>
    private val idAssigner: RedisIDAssignment

    override val type = Database.Type.Redis

    init {
        val redisUri = RedisURI.Builder.redis("localhost")
            .withPassword(password.toCharArray())
            .withDatabase(1)
            .build()
        client = RedisClient.create(redisUri)
        connection = client.connect()
        redis = connection.coroutines()

        idAssigner = RedisIDAssignment(redis)

        connection.sync().configSet("save", "$saveFrequency")
        connection.sync().configSet("dir", "$absoluteDataFolderPath/Redis")
        connection.sync().configRewrite()
    }

    @OptIn(DelicateCoroutinesApi::class)
    class RedisIDAssignment(private val cc: RedisCoroutinesCommands<String, String>) {
        private val channel = Channel<Int>()
        private var counter = 0

        init {
            GlobalScope.launch {
                counter = cc.keys("*")
                    .map { it.toInt() }
                    .toList()
                    .maxOrNull()!!


                while (true) {
                    counter++
                    channel.send(counter)
                }
            }
        }

        suspend fun getAndIncrement() = channel.receive()
    }

    internal object TicketHelper {
        private inline fun <reified T> kotlinDecode(json: String) = Json.decodeFromString<T>(json)
        private inline fun <reified T> kotlinEncode(t: T) = Json.encodeToString(t)

        private inline fun <reified T> T?.ifNotNullThen(f: T.() -> String) = if (this == null) "~NULL~" else f(this)
        private inline fun <reified T> String.rebuild(f: String.() -> T) = if (this == "~NULL~") null else f(this)

        // CREATOR_UUID
        fun creatorUuidAsString(uuid: UUID?) = uuid.ifNotNullThen(UUID::toString)
        fun stringToCreatorUUID(s: String) = s.rebuild(UUID::fromString)

        // PRIORITY
        fun priorityAsString(priority: BasicTicket.Priority) = priority.level.toString()
        fun stringToPriority(s: String) = byteToPriority(s.toByte())

        // STATUS
        fun statusAsString(status: BasicTicket.Status) = status.name
        fun stringToStatus(s: String) = BasicTicket.Status.valueOf(s)

        // ASSIGNED_TO
        fun assignmentAsString(assignment: String?) = assignment.ifNotNullThen { this }
        fun stringAsAssignment(s: String) = s.rebuild { this }

        // STATUS_UPDATE_FOR_CREATOR
        fun creatorUpdateAsString(creatorUpdate: Boolean) = if (creatorUpdate) "T" else "F"
        fun stringToCreatorUpdate(s: String) = s == "T"

        // LOCATION
        fun locationAsString(location: BasicTicket.TicketLocation?) = location.ifNotNullThen { kotlinEncode(this) }
        fun stringToLocation(s: String) : BasicTicket.TicketLocation? = s.rebuild { kotlinDecode(s) }

        // ACTIONS
        fun actionListAsString(list: List<FullTicket.Action>) = kotlinEncode(list)
        fun stringToActionList(s: String): List<FullTicket.Action> = kotlinDecode(s)
    }


    override suspend fun getActionsAsFlow(ticketID: Int): Flow<FullTicket.Action> = flow {
        redis.hget("$ticketID", "ACTIONS")
            ?.let(TicketHelper::stringToActionList)
            ?.forEach { emit(it) }
    }

    override suspend fun setAssignment(ticketID: Int, assignment: String?) {
        redis.hset("$ticketID", "ASSIGNED_TO", TicketHelper.assignmentAsString(assignment))
    }

    override suspend fun setCreatorStatusUpdate(ticketID: Int, status: Boolean) {
        redis.hset("$ticketID", "STATUS_UPDATE_FOR_CREATOR", TicketHelper.creatorUpdateAsString(status))
    }

    override suspend fun setPriority(ticketID: Int, priority: BasicTicket.Priority) {
        redis.hset("$ticketID", "PRIORITY", TicketHelper.priorityAsString(priority))
    }

    override suspend fun setStatus(ticketID: Int, status: BasicTicket.Status) {
        redis.hset("$ticketID", "STATUS", TicketHelper.statusAsString(status))
    }

    // NOTE: This function actually returns a FullTicket?
    override suspend fun getBasicTicket(ticketID: Int): BasicTicket? {
        val response = redis.hgetall("$ticketID")
            .toList()

        return if (response.isEmpty()) null else {
            BasicTicket(
                id = ticketID,
                creatorUUID = response.getValueFromKey("CREATOR_UUID").run(TicketHelper::stringToCreatorUUID),
                location = response.getValueFromKey("LOCATION").run(TicketHelper::stringToLocation),
                priority = response.getValueFromKey("PRIORITY").run(TicketHelper::stringToPriority),
                status = response.getValueFromKey("STATUS").run(TicketHelper::stringToStatus),
                assignedTo = response.getValueFromKey("ASSIGNED_TO").run(TicketHelper::stringAsAssignment),
                creatorStatusUpdate = response.getValueFromKey("STATUS_UPDATE_FOR_CREATOR").run(TicketHelper::stringToCreatorUpdate)
            )
        }
    }

    override suspend fun addAction(ticketID: Int, action: FullTicket.Action) {
        redis.hget("$ticketID", "ACTIONS")
            ?.run(TicketHelper::stringToActionList)
            ?.let { it + action }
            ?.run(TicketHelper::actionListAsString)
            ?.let { redis.hset("$ticketID", mapOf("ACTIONS" to it)) }
    }

    override suspend fun addFullTicket(fullTicket: FullTicket) {
        val ticketID = idAssigner.getAndIncrement()

        redis.hset("$ticketID",
            mapOf(
                "CREATOR_UUID" to fullTicket.creatorUUID.run(TicketHelper::creatorUuidAsString),
                "PRIORITY" to fullTicket.priority.run(TicketHelper::priorityAsString),
                "STATUS" to fullTicket.status.run(TicketHelper::statusAsString),
                "ASSIGNED_TO" to fullTicket.assignedTo.run(TicketHelper::assignmentAsString),
                "STATUS_UPDATE_FOR_CREATOR" to fullTicket.creatorStatusUpdate.run(TicketHelper::creatorUpdateAsString),
                "LOCATION" to fullTicket.location.run(TicketHelper::locationAsString),
                "ACTIONS" to fullTicket.actions.run(TicketHelper::actionListAsString),
            )
        )
    }

    override suspend fun addNewTicket(basicTicket: BasicTicket, context: CoroutineContext, message: String): Int {
        val actions = listOf(FullTicket.Action(FullTicket.Action.Type.OPEN, basicTicket.creatorUUID, message))
        val ticketID = idAssigner.getAndIncrement()

        withContext(context) {
            launch {
                redis.hset("$ticketID",
                    mapOf(
                        "CREATOR_UUID" to basicTicket.creatorUUID.run(TicketHelper::creatorUuidAsString),
                        "PRIORITY" to basicTicket.priority.run(TicketHelper::priorityAsString),
                        "STATUS" to basicTicket.status.run(TicketHelper::statusAsString),
                        "ASSIGNED_TO" to basicTicket.assignedTo.run(TicketHelper::assignmentAsString),
                        "STATUS_UPDATE_FOR_CREATOR" to basicTicket.creatorStatusUpdate.run(TicketHelper::creatorUpdateAsString),
                        "LOCATION" to basicTicket.location.run(TicketHelper::locationAsString),
                        "ACTIONS" to actions.run(TicketHelper::actionListAsString)
                    )
                )
            }
        }

        return ticketID
    }

    override suspend fun massCloseTickets(lowerBound: Int, upperBound: Int, uuid: UUID?, context: CoroutineContext) {
        (lowerBound..upperBound).forEach { id ->
            withContext(context) {
                launch {
                    val response = redis.hgetall("$id").toList()
                    if (response.isEmpty()) return@launch

                    val status = response.getValueFromKey("STATUS").run(TicketHelper::stringToStatus)
                    if (status != BasicTicket.Status.OPEN) return@launch

                    val actions = response.getValueFromKey("ACTIONS").run(TicketHelper::stringToActionList).toMutableList()
                    actions += FullTicket.Action(FullTicket.Action.Type.MASS_CLOSE, uuid)

                    redis.hset("$id",
                        mapOf(
                            "STATUS" to BasicTicket.Status.CLOSED.run(TicketHelper::statusAsString),
                            "ACTIONS" to actions.run(TicketHelper::actionListAsString),
                        )
                    )
                }
            }
        }
    }

    override suspend fun getOpenIDPriorityPairs(): Flow<Pair<Int, Byte>> = flow {
        redis.keys("*")
            .buffer(1000)
            .collect {
                val response = redis.hgetall(it).toList()
                val priority = response.getValueFromKey("PRIORITY").run(TicketHelper::stringToPriority)
                val status = response.getValueFromKey("STATUS").run(TicketHelper::stringToStatus)

                if (status == BasicTicket.Status.OPEN)
                    emit(it.toInt() to priority.level)
            }
    }

    override suspend fun getAssignedOpenIDPriorityPairs(
        assignment: String,
        unfixedGroupAssignment: List<String>
    ): Flow<Pair<Int, Byte>> = flow {
        val fixedAssignments = unfixedGroupAssignment.map { "::$it" }

        redis.keys("*")
            .buffer(1000)
            .collect {
                val response = redis.hgetall(it).toList()
                val priority = response.getValueFromKey("PRIORITY").run(TicketHelper::stringToPriority)
                val status = response.getValueFromKey("STATUS").run(TicketHelper::stringToStatus)
                val assigned = response.getValueFromKey("ASSIGNED_TO").run(TicketHelper::stringAsAssignment)

                if (status == BasicTicket.Status.OPEN && assigned?.run { it == assignment || it in fixedAssignments } == true)
                    emit(it.toInt() to priority.level)
            }
    }

    override suspend fun getIDsWithUpdates(): Flow<Int> = flow {
        redis.keys("*")
            .buffer(1000)
            .collect {
                val hasUpdate = redis.hget(it, "STATUS_UPDATE_FOR_CREATOR")!!.run(TicketHelper::stringToCreatorUpdate)

                if (hasUpdate)
                    emit(it.toInt())
            }
    }

    override suspend fun getIDsWithUpdatesFor(uuid: UUID): Flow<Int> = flow {
        redis.keys("*")
            .buffer(1000)
            .collect {
                val response = redis.hgetall(it).toList()
                val hasUpdate = response.getValueFromKey("STATUS_UPDATE_FOR_CREATOR").run(TicketHelper::stringToCreatorUpdate)
                val creatorUUID = response.getValueFromKey("CREATOR_UUID").run(TicketHelper::stringToCreatorUUID)

                if (creatorUUID?.run { it.equals(uuid) } == true && hasUpdate)
                    emit(it.toInt())
            }
    }

    override suspend fun getBasicTickets(ids: List<Int>): Flow<BasicTicket> = flow {
        ids.forEach { id ->
            val ticket = getBasicTicket(id)
            if (ticket != null) emit(ticket)
        }
    }

    // getBasicTicket returns FullTicket? Safe to type cast
    override suspend fun getFullTicketsFromBasics(
        basicTickets: List<BasicTicket>,
        context: CoroutineContext
    ): Flow<FullTicket> = flow {
        basicTickets.forEach {
            (it as? FullTicket)?.apply { emit(this) }
        }
    }

    override suspend fun getFullTickets(ids: List<Int>, context: CoroutineContext): Flow<FullTicket> = flow {
        ids.forEach {
            emit(getBasicTicket(it) as FullTicket)
        }
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

        return flow {
            redis.keys("*").collect { id ->
                val fullTicket = getBasicTicket(id.toInt()) as FullTicket

                if (newSearchFunction(fullTicket))
                    emit(fullTicket)
            }
        }
    }

    override suspend fun closeDatabase() {
        connection.close()
        client.shutdown()
    }

    override suspend fun initialiseDatabase() {
        // Database already initialized from instantiation
    }

    override suspend fun updateNeeded(): Boolean {
        return false // Introduced as V4 Database in TM5
    }

    override suspend fun migrateDatabase(
        context: CoroutineContext,
        to: Database.Type,
        onBegin: suspend () -> Unit,
        onComplete: suspend () -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun updateDatabase(
        context: CoroutineContext,
        onBegin: suspend () -> Unit,
        onComplete: suspend () -> Unit,
        offlinePlayerNameToUuidOrNull: (String) -> UUID?
    ) {
        // N/A (Introduced as V4 Database in TM5)
    }
}

private fun List<KeyValue<String,String>>.getValueFromKey(key: String) = first { it.key == key }.value!!