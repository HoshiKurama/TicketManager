package com.github.hoshikurama.ticketmanager.common.databases

import com.github.hoshikurama.ticketmanager.common.ticket.BasicTicket
import com.github.hoshikurama.ticketmanager.common.ticket.FullTicket
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

    override suspend fun getActionsAsFlow(ticketID: Int): Flow<Pair<Int, FullTicket.Action>> {
        TODO("Not yet implemented")
    }

    override suspend fun setAssignmentAsync(ticketID: Int, assignment: String?) {
        TODO("Not yet implemented")
    }

    override suspend fun setCreatorStatusUpdateAsync(ticketID: Int, status: Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun setPriorityAsync(ticketID: Int, priority: BasicTicket.Priority) {
        TODO("Not yet implemented")
    }

    override suspend fun setStatusAsync(ticketID: Int, status: BasicTicket.Status) {
        TODO("Not yet implemented")
    }

    override suspend fun getBasicTicketAsync(ticketID: Int): Deferred<BasicTicket?> {
        TODO("Not yet implemented")
    }

    override suspend fun addAction(ticketID: Int, action: FullTicket.Action) {
        TODO("Not yet implemented")
    }

    override suspend fun addFullTicket(fullTicket: FullTicket) {
        TODO("Not yet implemented")
    }

    override suspend fun addNewTicketAsync(basicTicket: BasicTicket, message: String): Deferred<Int> {
        TODO("Not yet implemented")
    }

    override suspend fun massCloseTickets(lowerBound: Int, upperBound: Int, uuid: UUID?) {
        TODO("Not yet implemented")
    }

    override suspend fun getBasicOpenAsFlow(): Flow<BasicTicket> {
        TODO("Not yet implemented")
    }

    override suspend fun getBasicOpenAssignedAsFlow(
        assignment: String,
        groupAssignment: List<String>
    ): Flow<BasicTicket> {
        TODO("Not yet implemented")
    }

    override suspend fun getBasicsWithUpdatesAsFlow(): Flow<BasicTicket> {
        TODO("Not yet implemented")
    }

    override suspend fun getFullOpenAsFlow(): Flow<FullTicket> {
        TODO("Not yet implemented")
    }

    override suspend fun getFullOpenAssignedAsFlow(
        assignment: String,
        groupAssignment: List<String>
    ): Flow<FullTicket> {
        TODO("Not yet implemented")
    }

    override suspend fun getIDsWithUpdatesAsFlowFor(uuid: UUID): Flow<Int> {
        TODO("Not yet implemented")
    }

    override suspend fun searchDatabase(searchFunction: (FullTicket) -> Boolean): Flow<FullTicket> {
        TODO("Not yet implemented")
    }

    override suspend fun closeDatabase() {
        TODO("Not yet implemented")
    }

    override suspend fun initialiseDatabase() {
        TODO("Not yet implemented")
    }

    override suspend fun updateNeededAsync(): Deferred<Boolean> {
        TODO("Not yet implemented")
    }

    override suspend fun migrateDatabase(
        to: Database.Type,
        onBegin: suspend () -> Unit,
        onComplete: suspend () -> Unit
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun updateDatabase(
        onBegin: suspend () -> Unit,
        onComplete: suspend () -> Unit,
        offlinePlayerNameToUuidOrNull: (String) -> UUID?
    ) {
        TODO("Not yet implemented")
    }
}