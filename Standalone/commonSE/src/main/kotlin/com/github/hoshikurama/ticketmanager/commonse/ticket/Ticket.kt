package com.github.hoshikurama.ticketmanager.commonse.ticket

import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import java.time.Instant
import java.util.*

class Ticket(
    val id: Long = -1L,                             // Ticket ID 1+... -1 placeholder during ticket creation
    val creator: Creator,
    val priority: Priority = Priority.NORMAL,       // Priority 1-5 or Lowest to Highest
    val status: Status = Status.OPEN,               // Status OPEN or CLOSED
    val assignedTo: String? = null,                 // Null if not assigned to anybody
    val creatorStatusUpdate: Boolean = false,       // Determines whether player should be notified
    val actions: List<Action> = listOf()
) {

    operator fun plus(actions: List<Action>): Ticket {
        return Ticket(id, creator, priority, status, assignedTo, creatorStatusUpdate, actions)
    }
    operator fun plus(actions: Action): Ticket {
        return Ticket(id, creator, priority, status, assignedTo, creatorStatusUpdate, this.actions + actions)
    }


    data class TicketLocation(val server: String?, val world: String?, val x: Int?, val y: Int?, val z: Int?) {
        override fun toString(): String = "${server ?: ""} ${world ?: ""} ${x ?: ""} ${y ?: ""} ${z ?: ""}".trimStart()
    }

    enum class Priority(val level: Byte) {
        LOWEST(1),
        LOW(2),
        NORMAL(3),
        HIGH(4),
        HIGHEST(5);

        fun toLocaledWord(locale: TMLocale) = when (this) {
            LOWEST -> locale.priorityLowest
            LOW -> locale.priorityLow
            NORMAL -> locale.priorityNormal
            HIGH -> locale.priorityHigh
            HIGHEST -> locale.priorityHighest
        }
    }

    enum class Status {
        OPEN, CLOSED;

        fun toLocaledWord(locale: TMLocale) = when (this) {
            OPEN -> locale.statusOpen
            CLOSED -> locale.statusClosed
        }
    }

    data class Action(val type: Type, val user: Creator, val location: TicketLocation, val message: String? = null, val timestamp: Long = Instant.now().epochSecond) {
        enum class Type {
            ASSIGN, CLOSE, COMMENT, OPEN, REOPEN, SET_PRIORITY, MASS_CLOSE
        }
    }
}




sealed interface Creator {
    override fun toString(): String
    infix fun equal(other: Creator) = other.toString() == this.toString()
}

class User(val uuid: UUID): Creator {

    override fun toString(): String = "USER.$uuid"
    override fun equals(other: Any?): Boolean {
        return if (other != null && other is User)
            this.uuid == other.uuid
        else false
    }
    override fun hashCode() = uuid.hashCode()
}

object Console : Creator {
    override fun toString(): String = "CONSOLE"
}

object Dummy : Creator {
    override fun toString(): String = throw Exception("Attempting to convert a dummy creator to a string!")
}

object InvalidUUID : Creator {
    override fun toString(): String = "INVALID_UUID"
}