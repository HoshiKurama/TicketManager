package com.hoshikurama.github.ticketmanager.ticket

import com.hoshikurama.github.ticketmanager.pluginState
import java.util.*

class TicketHandler(val id: Int) {
    internal val creatorUUID: UUID? by lazy {
        pluginState.database.getCreatorUUID(id)
    }

    internal val location: org.bukkit.Location? by lazy {
        pluginState.database.getLocation(id)
    }

    internal var statusUpdateForCreator: Boolean
        get() = statusUpdateForCreatorInternal
        set(v) = pluginState.database.setStatusUpdateForCreator(id, v)

    internal var priority: Ticket.Priority
        get() = priorityInternal
        set(v) = pluginState.database.setPriority(id, v)

    internal var status: Ticket.Status
        get() = statusInternal
        set(v) = pluginState.database.setStatus(id, v)

    internal var assignedTo: String?
        get() = assignedToInternal
        set(v) = pluginState.database.setAssignment(id, v)


    private val priorityInternal: Ticket.Priority by lazy {
        pluginState.database.getPriority(id)
    }
    private val statusInternal: Ticket.Status by lazy {
        pluginState.database.getStatus(id)
    }
    private val assignedToInternal by lazy {
        pluginState.database.getAssignment(id)
    }
    private val statusUpdateForCreatorInternal: Boolean by lazy {
        pluginState.database.getStatusUpdateForCreator(id)
    }

    fun UUIDMatches(uuid: UUID?) = creatorUUID?.equals(uuid) ?: (uuid == null)
}