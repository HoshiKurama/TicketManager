package com.github.hoshikurama.ticketmanager.common.ticket

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

    data class Action(val type: Type, val user:  UUID?, val message: String? = null, val timestamp: Long = Instant.now().epochSecond) {
        enum class Type {
            ASSIGN, CLOSE, COMMENT, OPEN, REOPEN, SET_PRIORITY, MASS_CLOSE
        }
    }
}