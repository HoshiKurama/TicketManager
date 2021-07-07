package com.hoshikurama.github.ticketmanager.common.ticket

import com.hoshikurama.github.ticketmanager.common.PluginState
import kotlinx.coroutines.Deferred
import java.util.*

class BasicTicket(
    val id: Int,
    val creatorUUID: UUID?,
    val location: Ticket.TicketLocation?,
    val priority: Ticket.Priority,
    val status: Ticket.Status,
    val assignedTo: String?,
    val statusUpdateForCreator: Boolean,

    val pluginState: PluginState,
) {
    companion object {
        suspend fun buildOrNull(pluginState: PluginState, id: Int) =
            pluginState.database.buildBasicTicket(id)
    }

    suspend fun setCreatorStatusUpdate(value: Boolean) =
        pluginState.database.setCreatorStatusUpdate(id, value)

    suspend fun setTicketPriority(value: Ticket.Priority) =
        pluginState.database.setPriority(id, value)

    suspend fun setTicketStatus(value: Ticket.Status) =
        pluginState.database.setStatus(id, value)

    suspend fun setAssignedTo(value: String?) =
        pluginState.database.setAssignment(id, value)

    suspend fun uuidMatches(other: UUID?) =
        creatorUUID?.equals(other) ?: (other == null)
}