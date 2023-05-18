package com.github.hoshikurama.ticketmanager.api.database

import com.github.hoshikurama.ticketmanager.api.ticket.TicketAssignmentType
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import com.github.hoshikurama.ticketmanager.api.ticket.TicketCreator

/**
 * Search Constraints are how TicketManager forwards search criteria to database extensions. Each property is encapsulated
 * with an "Option" wrapper, which makes two important distinctions:
 * - Null values
 * - Options containing null
 *
 * A null option is not part of a search. However, an Option containing null means the search should be performed on a null value.
 * For example, assigned = null means assignment is not a criteria. However, assigned = Option(null) means to search
 * for tickets assigned to nobody.
 *
 * @property creator search by ticket creator
 * @property assigned search by ticket assignment
 * @property priority search by ticket priority
 * @property status search by ticket status
 * @property closedBy search for tickets which have been closed by a certain entity at any point
 * @property lastClosedBy search for tickets in which that last close was by a certain entity
 * @property world search by tickets created in a certain world
 * @property creationTime search by tickets newer than a specific epoch time
 * @property keywords search by tickets with a particular keyword in any of its comments or in its opening statement.
 * @property requestedPage Page requested by user. Default to closest value if request falls outside of possible range.
 */

class SearchConstraints(
    val creator: Option<TicketCreator>? = null,
    val assigned: Option<TicketAssignmentType>? = null,
    val priority: Option<Ticket.Priority>? = null,
    val status: Option<Ticket.Status>? = null,
    val closedBy: Option<TicketCreator>? = null,
    val lastClosedBy: Option<TicketCreator>? = null,
    val world: Option<String>? = null,
    val creationTime: Option<Long>? = null,
    val keywords: Option<List<String>>? = null,
    val requestedPage: Int,
) {
    /**
     * Ticket searches can use up to 4 different symbols in searches. In practice, however, this is usually limited to only two.
     * As the name implies, this is the symbol a user enters and represents all possible types.
     */
    enum class Symbol {
        EQUALS, NOT_EQUALS, LESS_THAN, GREATER_THAN
    }
}

/**
 * Option is used exclusively by the SearchConstraints type. It allows for a differentiation between null values
 * (no search) and values which search for null. It also carries the appropriate symbol.
 * See SearchConstraints for more information
 * @see SearchConstraints
 */

class Option<T>(val symbol: SearchConstraints.Symbol, val value: T)

