package com.github.hoshikurama.ticketmanager.common.databases

import com.github.hoshikurama.ticketmanager.common.ticket.BasicTicket
import com.github.hoshikurama.ticketmanager.common.ticket.Ticket
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import java.util.*

class MySQL(
    host: String,
    port: String,
    dbName: String,
    username: String,
    password: String,
) : Database {
    override val type: Database.Type
        get() = TODO("Not yet implemented")

    override suspend fun getAssignmentOrNull(ticketID: Int): Deferred<String?> {
        TODO("Not yet implemented")
    }

    override suspend fun getCreatorUUIDOrNull(ticketID: Int): Deferred<UUID?> {
        TODO("Not yet implemented")
    }

    override suspend fun getTicketLocationOrNull(ticketID: Int): Deferred<Ticket.TicketLocation?> {
        TODO("Not yet implemented")
    }

    override suspend fun getPriorityOrNull(ticketID: Int): Deferred<Ticket.Priority?> {
        TODO("Not yet implemented")
    }

    override suspend fun getStatusOrNull(ticketID: Int): Deferred<Ticket.Status?> {
        TODO("Not yet implemented")
    }

    override suspend fun getCreatorStatusUpdateOrNull(ticketID: Int): Deferred<Boolean?> {
        TODO("Not yet implemented")
    }

    override suspend fun setAssignment(ticketID: Int, assignment: String?) {
        TODO("Not yet implemented")
    }

    override suspend fun setPriority(ticketID: Int, priority: Ticket.Priority) {
        TODO("Not yet implemented")
    }

    override suspend fun setStatus(ticketID: Int, status: Ticket.Status) {
        TODO("Not yet implemented")
    }

    override suspend fun setCreatorStatusUpdate(ticketID: Int, status: Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun buildBasicTicket(id: Int): BasicTicket? {
        TODO("Not yet implemented")
    }

    override suspend fun addAction(ticketID: Int, action: Ticket.Action) {
        TODO("Not yet implemented")
    }

    override suspend fun addTicket(ticket: Ticket, action: Ticket.Action): Deferred<Int> {
        TODO("Not yet implemented")
    }

    override suspend fun getOpenTickets(): Flow<Ticket> {
        TODO("Not yet implemented")
    }

    override suspend fun getOpenAssigned(assignment: String, groupAssignment: List<String>): Flow<Ticket> {
        TODO("Not yet implemented")
    }

    override suspend fun getTicketOrNull(ID: Int): Deferred<Ticket?> {
        TODO("Not yet implemented")
    }

    override suspend fun getTicketIDsWithUpdates(): Flow<Pair<UUID, Int>> {
        TODO("Not yet implemented")
    }

    override suspend fun getTicketIDsWithUpdates(uuid: UUID): Flow<Int> {
        TODO("Not yet implemented")
    }

    override suspend fun isValidID(ticketID: Int): Deferred<Boolean> {
        TODO("Not yet implemented")
    }

    override suspend fun massCloseTickets(lowerBound: Int, upperBound: Int, uuid: UUID?) {
        TODO("Not yet implemented")
    }

    override suspend fun searchDatabase(searchFunction: (Ticket) -> Boolean): Flow<Ticket> {
        TODO("Not yet implemented")
    }

    override suspend fun closeDatabase() {
        TODO("Not yet implemented")
    }

    override suspend fun createDatabasesIfNeeded() {
        TODO("Not yet implemented")
    }

    override suspend fun migrateDatabase(to: Database.Type) {
        TODO("Not yet implemented")
    }

    override suspend fun updateNeeded(): Deferred<Boolean> {
        TODO("Not yet implemented")
    }

    override suspend fun updateDatabase() {
        TODO("Not yet implemented")
    }

}