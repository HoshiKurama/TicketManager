package com.github.hoshikurama.ticketmanager.common.ticket

import com.github.hoshikurama.ticketmanager.common.database.Database

class BasicTicketHandler(
    private val basicTicket: BasicTicket,
    val database: Database,
) : BasicTicket by basicTicket {

    suspend fun setCreatorStatusUpdate(value: Boolean) =
        database.setCreatorStatusUpdate(id, value)

    suspend fun setTicketPriority(value: BasicTicket.Priority) =
        database.setPriority(id, value)

    suspend fun setTicketStatus(value: BasicTicket.Status) =
        database.setStatus(id, value)

    suspend fun setAssignedTo(value: String?) =
        database.setAssignment(id, value)

    suspend fun toFullTicket() = basicTicket.toFullTicket(database)

    companion object {
        suspend fun buildHandler(database: Database, id: Int): BasicTicketHandler? {
            val basicTicket = database.getBasicTicket(id)
            return basicTicket?.let { BasicTicketHandler(it, database) }
        }
    }
}