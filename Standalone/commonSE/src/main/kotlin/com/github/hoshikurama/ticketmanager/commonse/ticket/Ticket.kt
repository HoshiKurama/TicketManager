package com.github.hoshikurama.ticketmanager.commonse.ticket

import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.apiTesting.Creator
import com.github.hoshikurama.ticketmanager.commonse.apiTesting.Ticket
import java.time.Instant

class TicketImpl(
    override val id: Long = -1L,                                          // Ticket ID 1+... -1 placeholder during ticket creation
    override val creator: Creator,
    override val priority: Ticket.Priority = Ticket.Priority.NORMAL,     // Priority 1-5 or Lowest to Highest
    override val status: Ticket.Status = Ticket.Status.OPEN,             // Status OPEN or CLOSED
    override val assignedTo: String? = null,                             // Null if not assigned to anybody
    override val creatorStatusUpdate: Boolean = false,                   // Determines whether player should be notified
    override val actions: List<ActionImpl> = listOf()
) : Ticket {

    override operator fun plus(actions: List<Ticket.Action>): Ticket {
        return TicketImpl(id, creator, priority, status, assignedTo, creatorStatusUpdate, actions)
    }
    override operator fun plus(action: Ticket.Action): Ticket {
        return TicketImpl(id, creator, priority, status, assignedTo, creatorStatusUpdate, this.actions + action)
    }

    @JvmRecord
    data class PlayerTicketLocationImpl(
        override val server: String?,
        override val world: String,
        override val x: Int,
        override val y: Int,
        override val z: Int,
    ) : Ticket.CreationLocation.FromPlayer {
        override fun toString(): String = "${server ?: ""} $world $x $y $z".trimStart()
    }

    data class ConsoleTicketLocationImpl(override val server: String?) : Ticket.CreationLocation.FromConsole {
        override val world: String? = null
        override val x: Int? = null
        override val y: Int? = null
        override val z: Int? = null

        override fun toString(): String = "${server ?: ""} ${""} ${""} ${""} ${""}".trimStart()
    }

    data class ActionImpl(
        override val type: Ticket.Action.Type,
        override val user: Creator,
        override val location: Ticket.CreationLocation,
        override val timestamp: Long = Instant.now().epochSecond
    ) : Ticket.Action {

        @JvmInline
        value class ASSIGNImpl(override val assignment: String?) : Ticket.Action.Type.ASSIGN
        @JvmInline value class COMMENTImpl(override val comment: String) : Ticket.Action.Type.COMMENT
        @JvmInline value class OPENImpl(override val message: String) : Ticket.Action.Type.OPEN
        @JvmInline value class SETPRIORITYImpl(override val priority: Ticket.Priority) : Ticket.Action.Type.SET_PRIORITY
        object CLOSEImpl : Ticket.Action.Type.CLOSE
        object REOPENImpl : Ticket.Action.Type.REOPEN
        object MASSCLOSEImpl : Ticket.Action.Type.MASS_CLOSE
    }
}

fun Ticket.Priority.toLocaledWord(locale: TMLocale) = when (this) {
    Ticket.Priority.LOWEST -> locale.priorityLowest
    Ticket.Priority.LOW -> locale.priorityLow
    Ticket.Priority.NORMAL -> locale.priorityNormal
    Ticket.Priority.HIGH -> locale.priorityHigh
    Ticket.Priority.HIGHEST -> locale.priorityHighest
}

fun Ticket.Status.toLocaledWord(locale: TMLocale) = when (this) {
    Ticket.Status.OPEN -> locale.statusOpen
    Ticket.Status.CLOSED -> locale.statusClosed
}