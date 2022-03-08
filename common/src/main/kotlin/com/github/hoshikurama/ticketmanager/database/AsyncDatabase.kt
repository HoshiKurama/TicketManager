package com.github.hoshikurama.ticketmanager.database

import com.github.hoshikurama.ticketmanager.ticket.Creator
import com.github.hoshikurama.ticketmanager.ticket.Ticket
import java.util.concurrent.CompletableFuture

interface AsyncDatabase {
    val type: Type

    enum class Type {
        MEMORY, MYSQL, SQLITE, CACHED_SQLITE
    }

    // Individual property setters
    fun setAssignment(ticketID: Int, assignment: String?)
    fun setCreatorStatusUpdate(ticketID: Int, status: Boolean)
    fun setPriority(ticketID: Int, priority: Ticket.Priority)
    fun setStatus(ticketID: Int, status: Ticket.Status)

    // Database Additions
    fun insertAction(id: Int, action: Ticket.Action)
    fun insertTicketAsync(ticket: Ticket): CompletableFuture<Int>

    // Get Ticket(s)
    fun getTicketsAsync(ids: List<Int>): CompletableFuture<List<Ticket>>
    fun getTicketOrNullAsync(id: Int): CompletableFuture<Ticket?>

    // Aggregate Operations
    fun getOpenTicketsAsync(page: Int, pageSize: Int): CompletableFuture<Result>
    fun getOpenTicketsAssignedToAsync(page: Int, pageSize: Int, assignment: String, unfixedGroupAssignment: List<String>): CompletableFuture<Result>
    fun getOpenTicketsNotAssignedAsync(page: Int, pageSize: Int): CompletableFuture<Result>
    fun massCloseTickets(lowerBound: Int, upperBound: Int, actor: Creator, ticketLoc:  Ticket.TicketLocation)

    // Counting
    fun countOpenTicketsAsync(): CompletableFuture<Int>
    fun countOpenTicketsAssignedToAsync(assignment: String, unfixedGroupAssignment: List<String>): CompletableFuture<Int>

    // Searching
    fun searchDatabaseAsync(constraints: SearchConstraint, page: Int, pageSize: Int): CompletableFuture<Result>

    // Other stuff I can't name right now
    fun getTicketIDsWithUpdatesAsync(): CompletableFuture<List<Int>>
    fun getTicketIDsWithUpdatesForAsync(creator: Creator): CompletableFuture<List<Int>>

    // Internal Database Functions
    fun closeDatabase()
    fun initializeDatabase()
    fun migrateDatabase(
        to: Type,
        databaseBuilders: DatabaseBuilders,
        onBegin: () -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit,
    )
}

data class Result(val filteredResults: List<Ticket>, val totalPages: Int, val totalResults: Int, val returnedPage: Int)