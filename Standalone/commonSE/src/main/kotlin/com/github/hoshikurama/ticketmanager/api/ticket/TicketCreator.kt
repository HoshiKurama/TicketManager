package com.github.hoshikurama.ticketmanager.api.ticket
import java.util.*


/**
 * Abstractly represents, identifies, and compares entities which can create or modify a ticket. It is strictly used for
 * ticket action purposes.
 * @see User
 * @see Console
 */
sealed interface TicketCreator {

    /**
     * Normal player on a Ticket/Action
     * @property uuid Player's unique ID on the server/network.
     */
    class User(val uuid: UUID) : TicketCreator {

        /**
         * Checks if two user instances are the same. Users equal if they hold the same uuid.
         */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other != null && other is User && uuid == other.uuid
        }
        // Internal code used for HashMap on User
        override fun hashCode(): Int = uuid.hashCode()
    }

    /**
     * Console on a Ticket/Action
     */
    object Console : TicketCreator

    /**
     * Internal dummy value used when TicketManager is unable to find an accurate User or Console object
     */
    object InvalidUUID : TicketCreator

    /**
     * Ticket type is used for types which contain an instance of TicketCreator but is not
     * applicable. For example, events fired contain the ticket creator, but the mass-close command
     * targets many tickets. Thus, a dummy creator is used.
     */
    object DummyCreator : TicketCreator
}
