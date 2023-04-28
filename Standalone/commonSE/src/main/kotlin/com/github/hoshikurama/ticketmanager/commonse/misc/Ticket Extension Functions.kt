package com.github.hoshikurama.ticketmanager.commonse.misc

import com.github.hoshikurama.ticketmanager.api.ticket.TicketAssignmentType
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import com.github.hoshikurama.ticketmanager.api.ticket.TicketCreator
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.datas.GlobalState
import java.util.*

// Ticket.Priority
internal fun Byte.toPriority(): Ticket.Priority = when (this.toInt()) {
    1 -> Ticket.Priority.LOWEST
    2 -> Ticket.Priority.LOW
    3 -> Ticket.Priority.NORMAL
    4 -> Ticket.Priority.HIGH
    5 -> Ticket.Priority.HIGHEST
    else -> throw Exception("Invalid Priority Level: $this")
}

internal fun Ticket.Priority.getHexColour(activeLocale: TMLocale): String = when (this) {
    Ticket.Priority.LOWEST -> activeLocale.priorityColourLowestHex
    Ticket.Priority.LOW -> activeLocale.priorityColourLowHex
    Ticket.Priority.NORMAL -> activeLocale.priorityColourNormalHex
    Ticket.Priority.HIGH -> activeLocale.priorityColourHighHex
    Ticket.Priority.HIGHEST -> activeLocale.priorityColourHighestHex
}

internal fun Ticket.Priority.toLocaledWord(activeLocale: TMLocale) = when (this) {
    Ticket.Priority.LOWEST -> activeLocale.priorityLowest
    Ticket.Priority.LOW -> activeLocale.priorityLow
    Ticket.Priority.NORMAL -> activeLocale.priorityNormal
    Ticket.Priority.HIGH -> activeLocale.priorityHigh
    Ticket.Priority.HIGHEST -> activeLocale.priorityHighest
}

// Ticket.Status
internal fun Ticket.Status.getHexColour(activeLocale: TMLocale): String = when (this) {
    Ticket.Status.OPEN -> activeLocale.statusColourOpenHex
    Ticket.Status.CLOSED -> activeLocale.statusColourClosedHex
}

internal fun Ticket.Status.toLocaledWord(activeLocale: TMLocale) = when (this) {
    Ticket.Status.OPEN ->  activeLocale.statusOpen
    Ticket.Status.CLOSED -> activeLocale.statusClosed
}

// AssignmentType
fun TicketAssignmentType.asString() = when (this) {
    is TicketAssignmentType.Console -> "CONSOLE"
    is TicketAssignmentType.Nobody -> "NOBODY"
    is TicketAssignmentType.Other -> "OTHER.$assignment"
}

fun TicketAssignmentType.toLocalizedName(activeLocale: TMLocale) = when(this) {
    is TicketAssignmentType.Other -> this.assignment
    TicketAssignmentType.Console -> activeLocale.consoleName
    TicketAssignmentType.Nobody -> activeLocale.miscNobody
}

fun String.asAssignmentType(): TicketAssignmentType {
    val split = split(".", limit = 2)
    return when (split[0]) {
        "CONSOLE" -> TicketAssignmentType.Console
        "NOBODY" -> TicketAssignmentType.Nobody
        "OTHER" -> TicketAssignmentType.Other(split[1])
        else -> throw Exception("Invalid Assignment Type String: $this")
    }
}

// TicketCreator
fun TicketCreator.asString(): String = when (this) {
    is TicketCreator.Console -> "CONSOLE"
    is TicketCreator.InvalidUUID -> "INVALID_UUID"
    is TicketCreator.User -> "USER.$uuid"
    is TicketCreator.DummyCreator -> "DUMMY"
}

fun String.asTicketCreator() : TicketCreator {
    val split = this.split(".", limit = 2)
    return when (split[0]) {
        "USER" -> split[1].run(UUID::fromString).run(TicketCreator::User)
        "CONSOLE" -> TicketCreator.Console
        "INVALID_UUID" -> TicketCreator.InvalidUUID
        else -> throw Exception("Invalid TicketCreator type: ${split[0]}")
    }
}