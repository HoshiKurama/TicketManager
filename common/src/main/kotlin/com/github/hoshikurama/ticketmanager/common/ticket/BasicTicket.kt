package com.github.hoshikurama.ticketmanager.common.ticket

import com.github.hoshikurama.ticketmanager.common.TMLocale
import com.github.hoshikurama.ticketmanager.common.databases.Database
import com.github.hoshikurama.ticketmanager.common.sortActions
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.coroutines.CoroutineContext

open class BasicTicket(
    val id: Int = -1,                               // Ticket ID 1+... -1 placeholder during ticket creation
    val creatorUUID: UUID?,                         // UUID if player, null if Console
    val location: TicketLocation?,                  // TicketLocation if player, null if Console
    val priority: Priority = Priority.NORMAL,       // Priority 1-5 or Lowest to Highest
    val status: Status = Status.OPEN,               // Status OPEN or CLOSED
    val assignedTo: String? = null,                 // Null if not assigned to anybody
    val creatorStatusUpdate: Boolean = false,       // Determines whether player should be notified
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

    @Serializable
    data class TicketLocation(val world: String, val x: Int, val y: Int, val z: Int) {
        override fun toString() = "$world $x $y $z"
    }

    suspend fun toFullTicketAsync(database: Database, context: CoroutineContext): Deferred<FullTicket> = withContext(context) {
        async {
            val sortedActions = database.getActionsAsFlow(id)
                .toList()
                .sortedWith(sortActions)

            FullTicket(this@BasicTicket, sortedActions)
        }
    }
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
