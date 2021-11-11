package com.github.hoshikurama.ticketmanager.ticket

import java.util.*

open class BasicTicketImpl(
    override val id: Int = -1,
    override val creatorUUID: UUID?,
    override val location: BasicTicket.TicketLocation?,
    override val priority: BasicTicket.Priority = BasicTicket.Priority.NORMAL,
    override val status: BasicTicket.Status = BasicTicket.Status.OPEN,
    override val assignedTo: String? = null,
    override val creatorStatusUpdate: Boolean = false,
) : BasicTicket