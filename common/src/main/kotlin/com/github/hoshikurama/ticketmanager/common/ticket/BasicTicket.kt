package com.github.hoshikurama.ticketmanager.common.ticket

import com.github.hoshikurama.ticketmanager.common.TMLocale
import com.github.hoshikurama.ticketmanager.common.databases.Database
import kotlinx.serialization.Serializable
import java.util.*

interface BasicTicket {
    val id: Int                             // Ticket ID 1+... -1 placeholder during ticket creation
    val creatorUUID: UUID?                  // UUID if player, null if Console
    val location: TicketLocation?           // TicketLocation if player, null if Console
    val priority: Priority                  // Priority 1-5 or Lowest to Highest
    val status: Status                      // Status OPEN or CLOSED
    val assignedTo: String?                 // Null if not assigned to anybody
    val creatorStatusUpdate: Boolean        // Determines whether player should be notified

    @Serializable
    data class TicketLocation(val world: String, val x: Int, val y: Int, val z: Int) {
        override fun toString() = "$world $x $y $z"
    }

    @Serializable
    enum class Priority(val level: Byte, val colourCode: String) {
        LOWEST(1, "&1"),
        LOW(2, "&9"),
        NORMAL(3, "&e"),
        HIGH(4, "&c"),
        HIGHEST(5, "&4")
    }

    @Serializable
    enum class Status(val colourCode: String) {
        OPEN("&a"), CLOSED("&c")
    }

    suspend fun toFullTicket(database: Database): FullTicket
}


fun BasicTicket.Priority.toLocaledWord(locale: TMLocale) = when (this) {
    BasicTicket.Priority.LOWEST -> locale.priorityLowest
    BasicTicket.Priority.LOW -> locale.priorityLow
    BasicTicket.Priority.NORMAL -> locale.priorityNormal
    BasicTicket.Priority.HIGH -> locale.priorityHigh
    BasicTicket.Priority.HIGHEST -> locale.priorityHighest
}

fun BasicTicket.Status.toLocaledWord(locale: TMLocale) = when (this) {
    BasicTicket.Status.OPEN -> locale.statusOpen
    BasicTicket.Status.CLOSED -> locale.statusClosed
}

fun BasicTicket.uuidMatches(other: UUID?) =
    creatorUUID?.equals(other) ?: (other == null)

fun String.toTicketLocation() = split(" ")
    .let { BasicTicket.TicketLocation(it[0], it[1].toInt(), it[2].toInt(), it[3].toInt()) }