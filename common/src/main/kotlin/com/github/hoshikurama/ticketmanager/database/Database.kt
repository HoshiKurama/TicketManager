package com.github.hoshikurama.ticketmanager.database

import com.github.hoshikurama.ticketmanager.ticket.BasicTicket
import com.github.hoshikurama.ticketmanager.ticket.FullTicket
import java.util.*

interface Database {
    val type: Type


    enum class Type {
        MYSQL, SQLITE, MEMORY, CACHED_SQLITE,
    }

    // Individual property setters
    suspend fun setAssignment(ticketID: Int, assignment: String?)
    suspend fun setCreatorStatusUpdate(ticketID: Int, status: Boolean)
    suspend fun setPriority(ticketID: Int, priority: BasicTicket.Priority)
    suspend fun setStatus(ticketID: Int, status: BasicTicket.Status)

    // Database Additions
    suspend fun insertAction(id: Int, action: FullTicket.Action)
    suspend fun insertTicket(fullTicket: FullTicket): Int

    suspend fun getBasicTicketOrNull(ticketID: Int): BasicTicket? = getBasicTicketsOrNull(listOf(ticketID))?.firstOrNull()
    suspend fun getFullTicket(basicTicket: BasicTicket): FullTicket = getFullTickets(listOf(basicTicket)).first()
    suspend fun getBasicTicketsOrNull(ids: List<Int>): List<BasicTicket>?
    suspend fun getFullTickets(basicTickets: List<BasicTicket>): List<FullTicket>

    // Aggregate Operations
    suspend fun getOpenTickets(page: Int, pageSize: Int): Result<BasicTicket>
    suspend fun getOpenTicketsAssignedTo(page: Int, pageSize: Int, assignment: String, unfixedGroupAssignment: List<String>): Result<BasicTicket>
    suspend fun getOpenTicketsNotAssigned(page: Int, pageSize: Int): Result<BasicTicket>
    suspend fun massCloseTickets(lowerBound: Int, upperBound: Int, actor: UUID?)

    // Counting
    suspend fun countOpenTickets(): Int
    suspend fun countOpenTicketsAssignedTo(assignment: String, unfixedGroupAssignment: List<String>): Int

    // Searching
    suspend fun searchDatabase(constraints: SearchConstraint, page: Int, pageSize: Int): Result<FullTicket>

    // Other stuff I can't name right now
    suspend fun getTicketIDsWithUpdates(): List<Int>
    suspend fun getTicketIDsWithUpdatesFor(uuid: UUID): List<Int>

    // Internal Database Functions
    suspend fun closeDatabase()
    suspend fun initializeDatabase()
    suspend fun migrateDatabase(
        to: Type,
        databaseBuilders: DatabaseBuilders,
        onBegin: suspend () -> Unit,
        onComplete: suspend () -> Unit,
        onError: suspend (Exception) -> Unit,
    )
}

data class Result<T>(val filteredResults: List<T>, val totalPages: Int, val totalResults: Int, val returnedPage: Int)