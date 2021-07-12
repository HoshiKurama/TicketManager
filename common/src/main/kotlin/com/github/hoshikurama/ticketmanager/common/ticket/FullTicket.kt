package com.github.hoshikurama.ticketmanager.common.ticket

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.util.*

class FullTicket(
    id: Int = -1,                               // Ticket ID 1+... -1 placeholder during ticket creation
    creatorUUID: UUID?,                         // UUID if player, null if Console
    location: TicketLocation?,                  // TicketLocation if player, null if Console
    val actions: List<Action> = listOf(),       // List of actions
    priority: Priority = Priority.NORMAL,       // Priority 1-5 or Lowest to Highest
    status: Status = Status.OPEN,               // Status OPEN or CLOSED
    assignedTo: String? = null,                 // Null if not assigned to anybody
    creatorStatusUpdate: Boolean = false,        // Determines whether player should be notified
) : BasicTicket(id, creatorUUID, location, priority, status, assignedTo, creatorStatusUpdate) {

    constructor(basicTicket: BasicTicket, actions: List<Action>): this(
        basicTicket.id,
        basicTicket.creatorUUID,
        basicTicket.location,
        actions,
        basicTicket.priority,
        basicTicket.status,
        basicTicket.assignedTo,
        basicTicket.creatorStatusUpdate
    )

    @Serializable
    data class Action(val type: Type, val user: @Serializable(with = UUIDSerializer::class) UUID?, val message: String? = null, val timestamp: Long = Instant.now().epochSecond) {
        enum class Type {
            ASSIGN, CLOSE, COMMENT, OPEN, REOPEN, SET_PRIORITY, MASS_CLOSE
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = UUID::class)
object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}