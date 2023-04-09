package com.github.hoshikurama.ticketmanager.commonse.old.database

import com.github.hoshikurama.ticketmanager.api.database.Option
import com.github.hoshikurama.ticketmanager.api.database.SearchConstraints
import com.github.hoshikurama.ticketmanager.api.ticket.Creator
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket

/* NOTE:
    Null Property: Property is NOT a search constraint
    Option Containing Null: null is the search constraint
 */
class SearchConstraintsImpl(
    override val creator: OptionImpl<Creator>? = null,
    override val assigned: OptionImpl<String?>? = null,
    override val priority: OptionImpl<Ticket.Priority>? = null,
    override val status: OptionImpl<Ticket.Status>? = null,
    override val closedBy: OptionImpl<Creator>? = null,
    override val lastClosedBy: OptionImpl<Creator>? = null,
    override val world: OptionImpl<String>? = null,
    override val creationTime: OptionImpl<Long>? = null,
    override val keywords: OptionImpl<List<String>>? = null,
) : SearchConstraints

@JvmInline
value class OptionImpl<T>(override val value: T) : Option<T>