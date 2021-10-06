package com.github.hoshikurama.ticketmanager.common.ticket

import com.github.hoshikurama.ticketmanager.common.database.Database
import com.github.hoshikurama.ticketmanager.common.sortActions
import java.util.*

open class ConcreteBasicTicket(
    override val id: Int = -1,
    override val creatorUUID: UUID?,
    override val location: BasicTicket.TicketLocation?,
    override val priority: BasicTicket.Priority = BasicTicket.Priority.NORMAL,
    override val status: BasicTicket.Status = BasicTicket.Status.OPEN,
    override val assignedTo: String? = null,
    override val creatorStatusUpdate: Boolean = false,
) : BasicTicket {

    override suspend fun toFullTicket(database: Database): FullTicket {
        val sortedActions = database.getActions(id)
            .toList()
            .sortedWith(sortActions)

        return FullTicket(this, sortedActions)
    }
}