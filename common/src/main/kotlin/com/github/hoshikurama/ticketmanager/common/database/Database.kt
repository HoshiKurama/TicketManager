package com.github.hoshikurama.ticketmanager.common.database

import com.github.hoshikurama.ticketmanager.common.TMLocale
import com.github.hoshikurama.ticketmanager.common.ticket.BasicTicket
import com.github.hoshikurama.ticketmanager.common.ticket.FullTicket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.util.*
import kotlin.coroutines.CoroutineContext

interface Database {
    val type: Type

    enum class Type {
        MySQL, SQLite, Memory
    }

    // Individual property getters
    suspend fun getActionsAsFlow(ticketID: Int): Flow<FullTicket.Action>

    // Individual property setters
    suspend fun setAssignment(ticketID: Int, assignment: String?)
    suspend fun setCreatorStatusUpdate(ticketID: Int, status: Boolean)
    suspend fun setPriority(ticketID: Int, priority: BasicTicket.Priority)
    suspend fun setStatus(ticketID: Int, status: BasicTicket.Status)

    // Get Specific Ticket Type
    suspend fun getBasicTicket(ticketID: Int): BasicTicket?

    // Database additions
    suspend fun addAction(ticketID: Int, action: FullTicket.Action)
    suspend fun addFullTicket(fullTicket: FullTicket)
    suspend fun addNewTicket(basicTicket: BasicTicket, scope: CoroutineScope, message: String): Int

    // Database removals
    suspend fun massCloseTickets(lowerBound: Int, upperBound: Int, uuid: UUID?, scope: CoroutineScope)

    // Collections of tickets
    suspend fun getOpenIDPriorityPairs(): Flow<Pair<Int, Byte>>
    suspend fun getAssignedOpenIDPriorityPairs(assignment: String, unfixedGroupAssignment: List<String>): Flow<Pair<Int, Byte>>
    suspend fun getUnassignedOpenIDPriorityPairs(): Flow<Pair<Int, Byte>>
    suspend fun getIDsWithUpdates(): Flow<Int>
    suspend fun getIDsWithUpdatesFor(uuid: UUID): Flow<Int>
    suspend fun getBasicTickets(ids: List<Int>): Flow<BasicTicket>
    suspend fun getFullTicketsFromBasics(basicTickets: List<BasicTicket>, context: CoroutineContext): Flow<FullTicket>
    suspend fun getFullTickets(ids: List<Int>, scope: CoroutineScope): Flow<FullTicket>

    // Database searching
    suspend fun searchDatabase(
        scope: CoroutineScope,
        locale: TMLocale,
        mainTableConstraints: List<Pair<String, String?>>,
        searchFunction: (FullTicket) -> Boolean
    ): Flow<FullTicket>

    // Internal Database Functions
    suspend fun closeDatabase()
    suspend fun initialiseDatabase()
    suspend fun updateNeeded(): Boolean
    suspend fun migrateDatabase(
        scope: CoroutineScope,
        to: Type,
        mySQLBuilder: suspend () -> MySQL?,
        sqLiteBuilder: suspend () -> SQLite,
        memoryBuilder: suspend () -> Memory?,
        onBegin: suspend () -> Unit,
        onComplete: suspend () -> Unit,
    )
    suspend fun updateDatabase(
        onBegin: suspend () -> Unit,
        onComplete: suspend () -> Unit,
        offlinePlayerNameToUuidOrNull: (String) -> UUID?
    )
}