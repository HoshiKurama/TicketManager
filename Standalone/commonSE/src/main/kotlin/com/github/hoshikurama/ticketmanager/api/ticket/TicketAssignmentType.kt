package com.github.hoshikurama.ticketmanager.api.ticket

/**
 * Represents the 3 distinctions TicketManager makes for ticket assignments:
 * - Console
 * - Nobody
 * - Other (Players, Permission Groups, & Phrases)
 *
 * Developers should not implement this interface as the behaviour is not overridable.
 * Please use it to facilitate your decision in how you store ticket assignments.
 */
sealed class TicketAssignmentType {

    /**
     * Represents anything other than Nobody or Console. More specifically, this will be a phrase assignment,
     * which may or may not represent a player or permission group.
     */
    class Other(val assignment: String) : TicketAssignmentType()
    object Nobody : TicketAssignmentType()
    object Console : TicketAssignmentType()

     override fun equals(other: Any?): Boolean {
        return when (this) {
            is Nobody, is Console  -> this === other
            is Other -> other != null && other is Other && this.assignment == other.assignment
        }
    }
}