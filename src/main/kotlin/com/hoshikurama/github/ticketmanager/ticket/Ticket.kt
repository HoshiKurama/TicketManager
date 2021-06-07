package com.hoshikurama.github.ticketmanager.ticket

import com.hoshikurama.github.ticketmanager.TMLocale
import org.bukkit.Bukkit
import org.bukkit.Location
import java.time.Instant
import java.util.*

class Ticket(
    val creatorUUID: UUID?,                         // UUID if player, null if Console
    val location: Location?,                        // Location if player, null if Console
    val actions: List<Action> = listOf(),           // List of actions
    val priority: Priority = Priority.NORMAL,       // Priority 1-5 or Lowest to Highest
    val status: Status = Status.OPEN,               // Status OPEN or CLOSED
    val assignedTo: String? = null,                 // Null if not assigned to anybody
    val statusUpdateForCreator: Boolean = false,    // Determines whether player should be notified
    val id: Int = -1,                               // Ticket ID 1+... -1 placeholder during ticket creation
    ) {

    enum class Priority(val level: Byte) {
        LOWEST(1), LOW(2), NORMAL(3), HIGH(4), HIGHEST(5)
    }
    enum class Status {
        OPEN, CLOSED
    }

    data class Action(val type: Type, val user: UUID?, val message: String? = null, val timestamp: Long = Instant.now().epochSecond) {
        enum class Type() {
            ASSIGN, CLOSE, COMMENT, OPEN, REOPEN, SET_PRIORITY, MASS_CLOSE
        }
    }
    data class Location(val world: String, val x: Int, val y: Int, val z: Int) {
        constructor(bukkitLoc: org.bukkit.Location) : this(bukkitLoc.world.name, bukkitLoc.blockX, bukkitLoc.blockY, bukkitLoc.blockZ)
        constructor(split: List<String>) : this(split[0], split[1].toInt(), split[2].toInt(), split[3].toInt())

        override fun toString() = "$world $x $y $z"
    }
}


fun UUID?.toName(locale: TMLocale): String {
    if (this == null) return locale.consoleName
    return this.run(Bukkit::getOfflinePlayer).name ?: "UUID"
}

fun Ticket.Priority.getColourCode() = when(this) {
    Ticket.Priority.LOWEST -> "&1"
    Ticket.Priority.LOW -> "&9"
    Ticket.Priority.NORMAL -> "&e"
    Ticket.Priority.HIGH -> "&c"
    Ticket.Priority.HIGHEST -> "&4"
}

fun Ticket.Priority.toColouredString(locale: TMLocale): String {
    val word = when (this) {
        Ticket.Priority.LOWEST -> locale.priorityLowest
        Ticket.Priority.LOW -> locale.priorityLow
        Ticket.Priority.NORMAL -> locale.priorityNormal
        Ticket.Priority.HIGH -> locale.priorityHigh
        Ticket.Priority.HIGHEST -> locale.priorityHighest
    }
    return word.let { this.getColourCode() + it }
}

fun Ticket.Status.toColouredString(locale: TMLocale) = when (this) {
    Ticket.Status.OPEN -> "&a${locale.statusOpen}"
    Ticket.Status.CLOSED -> "&c${locale.statusClosed}"
}

fun Ticket.Status.getColourCode() = when (this) {
    Ticket.Status.OPEN -> "&a"
    Ticket.Status.CLOSED -> "&c"
}

fun Ticket.Location.toBukkitLocationOrNull() =
    Bukkit.getWorld(world)?.let { Location(it, x.toDouble(), y.toDouble(), z.toDouble()) }