package com.github.hoshikurama.ticketmanager.database

import com.github.hoshikurama.ticketmanager.ticket.Creator
import com.github.hoshikurama.ticketmanager.ticket.Ticket

/* NOTE:
    Null Property: Property is NOT a search constraint
    Option Containing Null: null is the search constraint
 */
class SearchConstraint(
    var creator: Option<Creator>? = null,
    var assigned: Option<String?>? = null,
    var priority: Option<Ticket.Priority>? = null,
    var status: Option<Ticket.Status>? = null,
    var closedBy: Option<Creator>? = null,
    var lastClosedBy: Option<Creator>? = null,
    var world: Option<String>? = null,
    var creationTime: Option<Long>? = null,
    var keywords: Option<List<String>>? = null,
)

class Option<T>(val value: T)