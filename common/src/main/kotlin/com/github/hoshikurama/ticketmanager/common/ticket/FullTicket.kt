package com.github.hoshikurama.ticketmanager.common.ticket

import com.github.hoshikurama.ticketmanager.common.databases.Database
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.util.*

@Serializable
class FullTicket(
    override val id: Int,
    @Serializable(with = UUIDSerializer::class)
    override val creatorUUID: UUID?,
    override val location: BasicTicket.TicketLocation?,
    override val priority: BasicTicket.Priority,
    override val status: BasicTicket.Status,
    override val assignedTo: String?,
    override val creatorStatusUpdate: Boolean,
    val actions: List<Action>
) : BasicTicket {

    constructor(basicTicket: BasicTicket, actionsList: List<Action>) : this(
        id = basicTicket.id,
        creatorUUID = basicTicket.creatorUUID,
        location = basicTicket.location,
        priority = basicTicket.priority,
        status = basicTicket.status,
        assignedTo = basicTicket.assignedTo,
        creatorStatusUpdate = basicTicket.creatorStatusUpdate,
        actions = actionsList
    )

    @Serializable
    data class Action(
        val type: Type, @Serializable(with = UUIDSerializer::class) val user: UUID?, val message: String? = null, val timestamp: Long = Instant.now().epochSecond) {
        enum class Type {
            ASSIGN, CLOSE, COMMENT, OPEN, REOPEN, SET_PRIORITY, MASS_CLOSE
        }
    }

    override suspend fun toFullTicket(database: Database) = this
}

object UUIDSerializer : KSerializer<UUID?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID?) {
        val string = value?.toString() ?: "NULL"
        encoder.encodeString(string)
    }

    override fun deserialize(decoder: Decoder): UUID? {
        val string = decoder.decodeString()
        return if (string == "NULL") null else UUID.fromString(string)
    }
}