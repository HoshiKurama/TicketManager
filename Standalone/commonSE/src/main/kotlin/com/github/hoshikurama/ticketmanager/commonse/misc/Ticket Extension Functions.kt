package com.github.hoshikurama.ticketmanager.commonse.misc

import com.github.hoshikurama.ticketmanager.api.common.ticket.Assignment
import com.github.hoshikurama.ticketmanager.api.common.ticket.Creator
import com.github.hoshikurama.ticketmanager.api.common.ticket.Ticket
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import java.util.*

private const val ASSIGNMENT_CONSOLE = "CONSOLE"
private const val ASSIGNMENT_NOBODY = "NOBODY"
private const val ASSIGNMENT_PLAYER = "PLAYER"
private const val ASSIGNMENT_GROUP = "GROUP"
private const val ASSIGNMENT_PHRASE = "PHRASE"
private const val CREATOR_USER = "USER"
private const val CREATOR_CONSOLE = "CONSOLE"
private const val CREATOR_INVALID_UUID = "INVALID_UUID"
private const val CREATOR_DUMMY = "DUMMY"


fun Byte.toPriority(): Ticket.Priority = when (this.toInt()) {
    1 -> Ticket.Priority.LOWEST
    2 -> Ticket.Priority.LOW
    3 -> Ticket.Priority.NORMAL
    4 -> Ticket.Priority.HIGH
    5 -> Ticket.Priority.HIGHEST
    else -> throw Exception("Invalid Priority Level: $this")
}

internal fun Ticket.Priority.asByte(): Byte = when (this) {
    Ticket.Priority.LOWEST -> 1
    Ticket.Priority.LOW -> 2
    Ticket.Priority.NORMAL -> 3
    Ticket.Priority.HIGH -> 4
    Ticket.Priority.HIGHEST -> 5
}


internal fun Ticket.Priority.getHexColour(activeLocale: TMLocale): String = when (this) {
    Ticket.Priority.LOWEST -> activeLocale.priorityColourLowestHex
    Ticket.Priority.LOW -> activeLocale.priorityColourLowHex
    Ticket.Priority.NORMAL -> activeLocale.priorityColourNormalHex
    Ticket.Priority.HIGH -> activeLocale.priorityColourHighHex
    Ticket.Priority.HIGHEST -> activeLocale.priorityColourHighestHex
}

fun Ticket.Status.getHexColour(activeLocale: TMLocale): String = when (this) {
    Ticket.Status.OPEN -> activeLocale.statusColourOpenHex
    Ticket.Status.CLOSED -> activeLocale.statusColourClosedHex
}


fun Ticket.Priority.toLocaledWord(activeLocale: TMLocale) = when (this) {
    Ticket.Priority.LOWEST -> activeLocale.priorityLowest
    Ticket.Priority.LOW -> activeLocale.priorityLow
    Ticket.Priority.NORMAL -> activeLocale.priorityNormal
    Ticket.Priority.HIGH -> activeLocale.priorityHigh
    Ticket.Priority.HIGHEST -> activeLocale.priorityHighest
}

internal fun Ticket.Status.toLocaledWord(activeLocale: TMLocale) = when (this) {
    Ticket.Status.OPEN ->  activeLocale.statusOpen
    Ticket.Status.CLOSED -> activeLocale.statusClosed
}


fun Assignment.asString() = when (this) {
    is Assignment.Console -> ASSIGNMENT_CONSOLE
    is Assignment.Nobody -> ASSIGNMENT_NOBODY
    is Assignment.Player -> "$ASSIGNMENT_PLAYER.$username"
    is Assignment.PermissionGroup -> "$ASSIGNMENT_GROUP.$permissionGroup"
    is Assignment.Phrase -> "$ASSIGNMENT_PHRASE.$phrase"
}

fun Creator.asString(): String = when (this) {
    is Creator.Console -> CREATOR_CONSOLE
    is Creator.UUIDNoMatch -> CREATOR_INVALID_UUID
    is Creator.User -> "$CREATOR_USER.$uuid"
    is Creator.DummyCreator -> CREATOR_DUMMY
}


fun Assignment.toLocalizedName(activeLocale: TMLocale) = when (this) {
    is Assignment.Player -> this.username
    is Assignment.Console -> activeLocale.consoleName
    is Assignment.Nobody -> activeLocale.miscNobody
    is Assignment.PermissionGroup -> this.permissionGroup
    is Assignment.Phrase -> this.phrase
}


fun AssignmentString.asAssignmentType(): Assignment {
    val split = value.split(".", limit = 2)
    return when (split[0]) {
        ASSIGNMENT_CONSOLE -> Assignment.Console
        ASSIGNMENT_NOBODY -> Assignment.Nobody
        ASSIGNMENT_PLAYER -> Assignment.Player(split[1])
        ASSIGNMENT_GROUP -> Assignment.PermissionGroup(split[1])
        ASSIGNMENT_PHRASE, "OTHER" -> Assignment.Phrase(split[1]) // note: "OTHER" is grandfathered from prev versions
        else -> throw Exception("Invalid Assignment Type String: $this")
    }
}

fun CreatorString.asTicketCreator() : Creator {
    val split = value.split(".", limit = 2)
    return when (split[0]) {
        CREATOR_CONSOLE -> Creator.Console
        CREATOR_DUMMY -> Creator.DummyCreator
        CREATOR_INVALID_UUID -> Creator.UUIDNoMatch
        CREATOR_USER -> split[1].run(UUID::fromString).run(Creator::User)
        else -> throw Exception("Invalid TicketCreator type: ${split[0]}")
    }
}

@JvmInline
value class CreatorString(val value: String)

@JvmInline
value class AssignmentString(val value: String)