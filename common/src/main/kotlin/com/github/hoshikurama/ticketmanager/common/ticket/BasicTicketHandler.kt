package com.github.hoshikurama.ticketmanager.common.ticket

import com.github.hoshikurama.ticketmanager.common.PluginState
import com.github.hoshikurama.ticketmanager.common.databases.Database
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.*

class BasicTicketHandler(
    id: Int,
    creatorUUID: UUID?,
    location: TicketLocation?,
    priority: Priority,
    status: Status,
    assignedTo: String?,
    creatorStatusUpdate: Boolean,
    val database: Database,

) : BasicTicket(id, creatorUUID, location, priority, status, assignedTo, creatorStatusUpdate) {

    constructor(database: Database, basicTicket: BasicTicket): this(
        basicTicket.id,
        basicTicket.creatorUUID,
        basicTicket.location,
        basicTicket.priority,
        basicTicket.status,
        basicTicket.assignedTo,
        basicTicket.creatorStatusUpdate,
        database
    )

    companion object {
        suspend fun buildHandlerAsync(database: Database, id: Int) = coroutineScope {
            async {
                val basicTicket = database.getBasicTicketAsync(id)
                basicTicket.await()?.run { BasicTicketHandler(database, this) }
            }
        }
    }

    suspend fun setCreatorStatusUpdate(value: Boolean) =
        database.setCreatorStatusUpdateAsync(id, value)

    suspend fun setTicketPriority(value: Priority) =
        database.setPriorityAsync(id, value)

    suspend fun setTicketStatus(value: Status) =
        database.setStatusAsync(id, value)

    suspend fun setAssignedTo(value: String?) =
        database.setAssignmentAsync(id, value)

    suspend fun toFullTicketAsync() = super.toFullTicketAsync(database)
}