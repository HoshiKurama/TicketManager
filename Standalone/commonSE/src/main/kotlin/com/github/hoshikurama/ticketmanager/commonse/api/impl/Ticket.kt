package com.github.hoshikurama.ticketmanager.commonse.api.impl

import com.github.hoshikurama.ticketmanager.api.ticket.Creator
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import java.time.Instant

class TicketSTD(
    override val id: Long = -1L,                                         // Ticket ID 1+... -1 placeholder during ticket creation
    override val creator: Creator,
    override val priority: Ticket.Priority = Ticket.Priority.NORMAL,     // Priority 1-5 or Lowest to Highest
    override val status: Ticket.Status = Ticket.Status.OPEN,             // Status OPEN or CLOSED
    override val assignedTo: String? = null,                             // Null if not assigned to anybody
    override val creatorStatusUpdate: Boolean = false,                   // Determines whether player should be notified
    override val actions: List<Ticket.Action> = listOf()
) : Ticket {

    override operator fun plus(actions: List<Ticket.Action>): Ticket {
        return TicketSTD(id, creator, priority, status, assignedTo, creatorStatusUpdate, actions)
    }
    override operator fun plus(action: Ticket.Action): Ticket {
        return TicketSTD(id, creator, priority, status, assignedTo, creatorStatusUpdate, this.actions + action)
    }

    data class PlayerTicketLocationSTD(
        override val server: String?,
        override val world: String,
        override val x: Int,
        override val y: Int,
        override val z: Int,
    ) : Ticket.CreationLocation.FromPlayer {
        override fun toString(): String = "${server ?: ""} $world $x $y $z".trimStart()
    }

    data class ConsoleTicketLocationSTD(override val server: String?) : Ticket.CreationLocation.FromConsole {
        override val world: String? = null
        override val x: Int? = null
        override val y: Int? = null
        override val z: Int? = null

        override fun toString(): String = "${server ?: ""} ${""} ${""} ${""} ${""}".trimStart()
    }

    data class ActionSTD(
        override val type: Ticket.Action.Type,
        override val user: Creator,
        override val location: Ticket.CreationLocation,
        override val timestamp: Long = Instant.now().epochSecond
    ) : Ticket.Action {

        @JvmInline
        value class AssignSTD(override val assignment: String?) : Ticket.Action.Type.ASSIGN
        @JvmInline value class CommentSTD(override val comment: String) : Ticket.Action.Type.COMMENT
        @JvmInline value class OpenSTD(override val message: String) : Ticket.Action.Type.OPEN
        @JvmInline value class SetPrioritySTD(override val priority: Ticket.Priority) : Ticket.Action.Type.SET_PRIORITY
        object CloseSTD : Ticket.Action.Type.CLOSE
        object ReopenSTD : Ticket.Action.Type.REOPEN
        object MassCloseSTD : Ticket.Action.Type.MASS_CLOSE
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