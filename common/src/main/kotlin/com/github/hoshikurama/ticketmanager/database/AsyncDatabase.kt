package com.github.hoshikurama.ticketmanager.database

import com.github.hoshikurama.ticketmanager.ticket.Creator
import com.github.hoshikurama.ticketmanager.ticket.Ticket
import java.util.concurrent.CompletableFuture

interface AsyncDatabase {
    val type: Type

    enum class Type {
        MEMORY, MYSQL, CACHED_H2, H2,
    }

    // Individual property setters
    fun setAssignment(ticketID: Long, assignment: String?)
    fun setCreatorStatusUpdate(ticketID: Long, status: Boolean)
    fun setPriority(ticketID: Long, priority: Ticket.Priority)
    fun setStatus(ticketID: Long, status: Ticket.Status)

    // Database Additions
    fun insertAction(id: Long, action: Ticket.Action)
    fun insertTicketAsync(ticket: Ticket): CompletableFuture<Long>

    // Get Ticket(s)
    fun getTicketsAsync(ids: List<Long>): CompletableFuture<List<Ticket>>
    fun getTicketOrNullAsync(id: Long): CompletableFuture<Ticket?>

    // Aggregate Operations
    fun getOpenTicketsAsync(page: Int, pageSize: Int): CompletableFuture<Result>
    fun getOpenTicketsAssignedToAsync(page: Int, pageSize: Int, assignment: String, unfixedGroupAssignment: List<String>): CompletableFuture<Result>
    fun getOpenTicketsNotAssignedAsync(page: Int, pageSize: Int): CompletableFuture<Result>
    fun massCloseTickets(lowerBound: Long, upperBound: Long, actor: Creator, ticketLoc:  Ticket.TicketLocation)

    // Counting
    fun countOpenTicketsAsync(): CompletableFuture<Long>
    fun countOpenTicketsAssignedToAsync(assignment: String, unfixedGroupAssignment: List<String>): CompletableFuture<Long>

    // Searching
    fun searchDatabaseAsync(constraints: SearchConstraint, page: Int, pageSize: Int): CompletableFuture<Result>

    // Other stuff I can't name right now
    fun getTicketIDsWithUpdatesAsync(): CompletableFuture<List<Long>>
    fun getTicketIDsWithUpdatesForAsync(creator: Creator): CompletableFuture<List<Long>>

    // Internal Database Functions
    fun closeDatabase()
    fun initializeDatabase()

    fun insertTicketForMigration(other: AsyncDatabase)
    fun migrateDatabase(
        to: Type,
        databaseBuilders: DatabaseBuilders,
        onBegin: () -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        CompletableFuture.runAsync(onBegin)

        try {
            when (to) {
                Type.MEMORY -> databaseBuilders.memoryBuilder
                Type.MYSQL -> databaseBuilders.mySQLBuilder
                Type.CACHED_H2 -> databaseBuilders.cachedH2Builder
                Type.H2 -> databaseBuilders.h2Builder
            }
                .let(DatabaseBuilder::build)
                .apply(AsyncDatabase::initializeDatabase)
                .apply(this::insertTicketForMigration)
                .apply(AsyncDatabase::closeDatabase)

        } catch (e: Exception) {
            CompletableFuture.runAsync { onError(e) }
        }

        CompletableFuture.runAsync(onComplete)
    }
}

data class Result(val filteredResults: List<Ticket>, val totalPages: Int, val totalResults: Int, val returnedPage: Int)