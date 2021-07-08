package com.github.hoshikurama.ticketmanager.common.databases

import com.github.hoshikurama.ticketmanager.common.ticket.BasicTicket
import com.github.hoshikurama.ticketmanager.common.ticket.FullTicket
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import java.util.*

interface Database {
    val type: Type

    enum class Type {
        MySQL, SQLite
    }

    // Individual property getters
    suspend fun getActionsAsFlow(ticketID: Int): Flow<Pair<Int, FullTicket.Action>>

    // Individual property setters
    suspend fun setAssignmentAsync(ticketID: Int, assignment: String?)
    suspend fun setCreatorStatusUpdateAsync(ticketID: Int, status: Boolean)
    suspend fun setPriorityAsync(ticketID: Int, priority: BasicTicket.Priority)
    suspend fun setStatusAsync(ticketID: Int, status: BasicTicket.Status)

    // Get Specific Ticket Type
    suspend fun getBasicTicketAsync(ticketID: Int): Deferred<BasicTicket?>

    // Database additions
    suspend fun addAction(ticketID: Int, action: FullTicket.Action)
    suspend fun addFullTicket(fullTicket: FullTicket)
    suspend fun addNewTicketAsync(basicTicket: BasicTicket, message: String): Deferred<Int>

    // Database removals
    suspend fun massCloseTickets(lowerBound: Int, upperBound: Int, uuid: UUID?)

    // Collections of tickets
    suspend fun getBasicOpenAsFlow(): Flow<BasicTicket>
    suspend fun getBasicOpenAssignedAsFlow(assignment: String, groupAssignment: List<String>): Flow<BasicTicket>
    suspend fun getBasicsWithUpdatesAsFlow(): Flow<BasicTicket>
    suspend fun getFullOpenAsFlow(): Flow<FullTicket>
    suspend fun getFullOpenAssignedAsFlow(assignment: String, groupAssignment: List<String>): Flow<FullTicket>
    suspend fun getIDsWithUpdatesAsFlowFor(uuid: UUID): Flow<Int>

    // Database searching
    suspend fun searchDatabase(searchFunction: (FullTicket) -> Boolean): Flow<FullTicket>

    // Internal Database Functions
    suspend fun closeDatabase()
    suspend fun initialiseDatabase()
    suspend fun updateNeededAsync(): Deferred<Boolean>
    suspend fun migrateDatabase(
        to: Type,
        onBegin: suspend () -> Unit,
        onComplete: suspend () -> Unit,
    )
    suspend fun updateDatabase(
        onBegin: suspend () -> Unit,
        onComplete: suspend () -> Unit,
        offlinePlayerNameToUuidOrNull: (String) -> UUID?
    )
}