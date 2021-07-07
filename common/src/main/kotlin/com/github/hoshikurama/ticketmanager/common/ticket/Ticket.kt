package com.github.hoshikurama.ticketmanager.common.ticket

import com.github.hoshikurama.ticketmanager.common.TMLocale
import java.time.Instant
import java.util.*

class Ticket(
    val creatorUUID: UUID?,                         // UUID if player, null if Console
    val location: TicketLocation?,                  // TicketLocation if player, null if Console
    val actions: List<Action> = listOf(),           // List of actions
    val priority: Priority = Priority.NORMAL,       // Priority 1-5 or Lowest to Highest
    val status: Status = Status.OPEN,               // Status OPEN or CLOSED
    val assignedTo: String? = null,                 // Null if not assigned to anybody
    val statusUpdateForCreator: Boolean = false,    // Determines whether player should be notified
    val id: Int = -1,                               // Ticket ID 1+... -1 placeholder during ticket creation
) {

    enum class Priority(val level: Byte, val colourCode: String) {
        LOWEST(1, "&1"),
        LOW(2, "&9"),
        NORMAL(3, "&e"),
        HIGH(4, "&c"),
        HIGHEST(5, "&4")
    }
    enum class Status(val colourCode: String) {
        OPEN("&a"), CLOSED("&c")
    }

    data class Action(val type: Type, val user: UUID?, val message: String? = null, val timestamp: Long = Instant.now().epochSecond) {
        enum class Type {
            ASSIGN, CLOSE, COMMENT, OPEN, REOPEN, SET_PRIORITY, MASS_CLOSE
        }
    }
    data class TicketLocation(val world: String, val x: Int, val y: Int, val z: Int) {
        override fun toString() = "$world $x $y $z"
    }
}

fun Ticket.Priority.toLocaledWord(locale: TMLocale)= when (this) {
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