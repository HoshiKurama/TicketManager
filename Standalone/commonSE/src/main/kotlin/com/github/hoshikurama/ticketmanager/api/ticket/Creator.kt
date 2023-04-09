package com.github.hoshikurama.ticketmanager.api.ticket

import com.github.hoshikurama.ticketmanager.api.ticket.Creator.Console
import com.github.hoshikurama.ticketmanager.api.ticket.Creator.User
import java.util.*

/**
 * Abstractly represents, identifies, and compares entities which can create or modify a ticket. It is strictly used for
 * ticket action purposes.
 * @see User
 * @see Console
 */
sealed interface Creator {

    /**
     * @return Unique string-form storable on a database
     */
    fun asString(): String

    /**
     * Compares two creators using their toString function. This allows 2 different creator objects representing the same
     * person to remain equal.
     * @return Boolean indicating if creators are the same
     */
    infix fun equalTo(other: Creator?) = other != null && other.asString() == this.asString()

    /**
     * Represents a normal player on a Ticket/Action
     * @property uuid Player's unique ID on the server/network.
     */
    interface User : Creator {
        val uuid: UUID

        override fun asString(): String = "USER.$uuid"

        override fun equalTo(other: Creator?): Boolean {
            return if (other != null && other is User)
                this.uuid == other.uuid
            else false
        }

        /**
         * Internal means which should be used for Users in a HashMap.
         */
        fun hashCode2() = uuid.hashCode()
    }

    /**
     * Represents Console on a Ticket/Action
     */
    interface Console : Creator {
        override fun asString(): String = "CONSOLE"
    }

    /**
     * Represents anything other than a User or Console.
     */
    interface Other : Creator
}