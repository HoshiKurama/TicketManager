package com.github.hoshikurama.ticketmanager.common.ticket

import com.github.hoshikurama.ticketmanager.common.databases.Database
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.CoroutineContext

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
        suspend fun buildHandlerAsync(database: Database, id: Int, context: CoroutineContext) = withContext(context) {
            async {
                val basicTicket = database.getBasicTicket(id)
                basicTicket?.run { BasicTicketHandler(database, this) }
            }
        }
    }

    suspend fun setCreatorStatusUpdate(value: Boolean) =
        database.setCreatorStatusUpdate(id, value)

    suspend fun setTicketPriority(value: Priority) =
        database.setPriority(id, value)

    suspend fun setTicketStatus(value: Status) =
        database.setStatus(id, value)

    suspend fun setAssignedTo(value: String?) =
        database.setAssignment(id, value)

    suspend fun toFullTicketAsync(context: CoroutineContext) = super.toFullTicketAsync(database, context)
}