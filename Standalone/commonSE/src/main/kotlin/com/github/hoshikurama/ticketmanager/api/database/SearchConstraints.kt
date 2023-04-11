package com.github.hoshikurama.ticketmanager.api.database

import com.github.hoshikurama.ticketmanager.api.ticket.Creator
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket

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
 */

interface SearchConstraints {
    val creator: Option<Creator>?
    val assigned: Option<String?>?
    val priority: Option<Ticket.Priority>?
    val status: Option<Ticket.Status>?
    val closedBy: Option<Creator>?
    val lastClosedBy: Option<Creator>?
    val world: Option<String>?
    val creationTime: Option<Long>?
    val keywords: Option<List<String>>?
}

/**
 * Option is used exclusively by the SearchConstraints type. It allows for a differentiation between null values (no search)
 * and values which search for null. See SearchConstraints for more information
 * @see SearchConstraints
 */

interface Option<T> {
    val value: T
}