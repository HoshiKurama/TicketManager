package com.github.hoshikurama.ticketmanager.commonse.database

import com.github.hoshikurama.ticketmanager.commonse.apiTesting.Creator
import com.github.hoshikurama.ticketmanager.commonse.apiTesting.Ticket
import com.github.hoshikurama.ticketmanager.commonse.misc.TMCoroutine

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
    suspend fun insertTicketAsync(ticket: Ticket): Long

    // Get Ticket(s)
    suspend fun getTicketsAsync(ids: List<Long>): List<Ticket>
    suspend fun getTicketOrNullAsync(id: Long): Ticket?

    // Aggregate Operations
    suspend fun getOpenTicketsAsync(page: Int, pageSize: Int): Result
    suspend fun getOpenTicketsAssignedToAsync(page: Int, pageSize: Int, assignment: String, unfixedGroupAssignment: List<String>): Result
    suspend fun getOpenTicketsNotAssignedAsync(page: Int, pageSize: Int): Result
    fun massCloseTickets(lowerBound: Long, upperBound: Long, actor: Creator, ticketLoc:  Ticket.CreationLocation)

    // Counting
    suspend fun countOpenTicketsAsync(): Long
    suspend fun countOpenTicketsAssignedToAsync(assignment: String, unfixedGroupAssignment: List<String>): Long

    // Searching
    suspend fun searchDatabaseAsync(constraints: SearchConstraint, page: Int, pageSize: Int): Result

    // Other stuff I can't name right now
    suspend fun getTicketIDsWithUpdatesAsync(): List<Long>
    suspend fun getTicketIDsWithUpdatesForAsync(creator: Creator): List<Long>

    // Internal Database Functions
    suspend fun closeDatabase()
    fun initializeDatabase()

    suspend fun insertTicketForMigration(other: AsyncDatabase)
    suspend fun migrateDatabase(
        to: Type,
        databaseBuilders: DatabaseBuilders,
        onBegin: () -> Unit,
        onComplete: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        onBegin()

        try {
            when (to) {
                Type.MEMORY -> databaseBuilders.memoryBuilder
                Type.MYSQL -> databaseBuilders.mySQLBuilder
                Type.CACHED_H2 -> databaseBuilders.cachedH2Builder
                Type.H2 -> databaseBuilders.h2Builder
            }
                .let(DatabaseBuilder::build)
                .apply(AsyncDatabase::initializeDatabase)
                .apply { insertTicketForMigration(this) }
                .apply { closeDatabase() }

        } catch (e: Exception) {
            TMCoroutine.runAsync { onError(e) }
        }
        onComplete()
    }
}

data class Result(val filteredResults: List<Ticket>, val totalPages: Int, val totalResults: Int, val returnedPage: Int)