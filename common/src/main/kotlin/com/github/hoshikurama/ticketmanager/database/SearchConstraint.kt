package com.github.hoshikurama.ticketmanager.database

import com.github.hoshikurama.ticketmanager.ticket.BasicTicket
import java.util.*

/* NOTE:
    Null Property: Property is NOT a search constraint
    Option Containing Null: null is the search constraint
 */
class SearchConstraint(
    var creator: Option<UUID?>? = null,
    var assigned: Option<String?>? = null,
    var priority: Option<BasicTicket.Priority>? = null,
    var status: Option<BasicTicket.Status>? = null,
    var closedBy: Option<UUID?>? = null,
    var lastClosedBy: Option<UUID?>? = null,
    var world: Option<String>? = null,
    var creationTime: Option<Long>? = null,
    var keywords: Option<List<String>>? = null,
)

class Option<T>(val value: T)