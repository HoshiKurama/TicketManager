package com.github.hoshikurama.ticketmanager.common.databases

import com.github.hoshikurama.ticketmanager.common.ticket.BasicTicket
import com.github.hoshikurama.ticketmanager.common.ticket.Ticket
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import java.util.*

interface Database {
    val type: Type

    enum class Type {
        MySQL, SQLite
    }

    // Individual things
    suspend fun getAssignmentOrNull(ticketID: Int): Deferred<String?>
    suspend fun getCreatorUUIDOrNull(ticketID: Int): Deferred<UUID?>
    suspend fun getTicketLocationOrNull(ticketID: Int): Deferred<Ticket.TicketLocation?>
    suspend fun getPriorityOrNull(ticketID: Int): Deferred<Ticket.Priority?>
    suspend fun getStatusOrNull(ticketID: Int): Deferred<Ticket.Status?>
    suspend fun getCreatorStatusUpdateOrNull(ticketID: Int): Deferred<Boolean?>
    suspend fun setAssignment(ticketID: Int, assignment: String?)
    suspend fun setPriority(ticketID: Int, priority: Ticket.Priority)
    suspend fun setStatus(ticketID: Int, status: Ticket.Status)
    suspend fun setCreatorStatusUpdate(ticketID: Int, status: Boolean)
    suspend fun buildBasicTicket(id: Int): BasicTicket?

    // More specific Ticket actions
    suspend fun addAction(ticketID: Int, action: Ticket.Action)
    suspend fun addTicket(ticket: Ticket, action: Ticket.Action): Deferred<Int>
    suspend fun getOpenTickets(): Flow<Ticket>
    suspend fun getOpenAssigned(assignment: String, groupAssignment: List<String>): Flow<Ticket>
    suspend fun getTicketOrNull(ID: Int): Deferred<Ticket?>
    suspend fun getTicketIDsWithUpdates(): Flow<Pair<UUID, Int>>
    suspend fun getTicketIDsWithUpdates(uuid: UUID): Flow<Int>
    suspend fun isValidID(ticketID: Int): Deferred<Boolean>
    suspend fun massCloseTickets(lowerBound: Int, upperBound: Int, uuid: UUID?)
    suspend fun searchDatabase(searchFunction: (Ticket) -> Boolean): Flow<Ticket>

    // Database Modifications
    suspend fun closeDatabase()
    suspend fun createDatabasesIfNeeded()
    suspend fun migrateDatabase(to: Type)
    suspend fun updateNeeded(): Deferred<Boolean>
    suspend fun updateDatabase()
}