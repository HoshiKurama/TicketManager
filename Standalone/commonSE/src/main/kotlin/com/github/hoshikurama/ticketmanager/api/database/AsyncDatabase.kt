package com.github.hoshikurama.ticketmanager.api.database

import com.github.hoshikurama.ticketmanager.api.ticket.Creator
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import java.util.concurrent.CompletableFuture

/**
 * Defines how any database extension must operate with TicketManager.
 *
 * Functions will ALWAYS be called on a random thread in the Common ForkJoinPool. Thus, any implementation must
 * be thread-safe. It is also safe to block the thread. TicketManager is reload-safe, and thus any database extensions
 * must also be reload-safe via closeDatabase() and initializeDatabase().
 */
interface AsyncDatabase {

    // Individual property setters

    /**
     * Asynchronously set the assignment of a Ticket. This should have no other side effects.
     * @param ticketID ID of ticket to modify
     * @param assignment Assignment value. Null indicates an assignment of nobody
     * @return CompletableFuture indicating when the task is complete
     */
    fun setAssignmentAsync(ticketID: Long, assignment: String?): CompletableFuture<Void>

    /**
     * Asynchronously set the value indicating if the creator has seen the most recent action.
     * This should have no other side effects.
     * @param ticketID ID of ticket to modify
     * @param status true indicates the user has an update to read. False indicates otherwise.
     * @return CompletableFuture indicating when the task is complete
     */
    fun setCreatorStatusUpdateAsync(ticketID: Long, status: Boolean): CompletableFuture<Void>

    /**
     * Asynchronously change the ticket priority. This should have no other side effects.
     * @param ticketID ID of ticket to modify
     * @param priority Priority of ticket
     * @return CompletableFuture indicating when the task is complete
     */
    fun setPriorityAsync(ticketID: Long, priority: Ticket.Priority): CompletableFuture<Void>

    /**
     * Asynchronously set the OPEN or CLOSED status of a ticket. This should have no other side effects.
     * @param ticketID ID of ticket to modify
     * @param status Ticket Status (Open/Closed)
     * @return CompletableFuture indicating when the task is complete
     */
    fun setStatusAsync(ticketID: Long, status: Ticket.Status): CompletableFuture<Void>

    // Database Additions

    /**
     * Asynchronously append a Ticket.Action to a ticket. This should have no other side effects.
     * @param id ID of ticket to modify
     * @param action Action to append
     * @return CompletableFuture indicating when the task is complete
     */
    fun insertActionAsync(id: Long, action: Ticket.Action): CompletableFuture<Void>

    /**
     * Asynchronously add an initial ticket to the plugin. Tickets inserted with this function do not have a proper
     * id yet, which must be created here. Extensions are responsible for preventing ID collisions.
     * @param ticket Ticket to append
     * @return Uniquely assigned ticket ID
     */
    fun insertNewTicketAsync(ticket: Ticket): CompletableFuture<Long>

    // Get Ticket

    /**
     * Asynchronously retrieve a ticket if it exists.
     * @param id Desired ticket ID
     * @return Ticket if the id is found and null otherwise
     */
    fun getTicketOrNullAsync(id: Long): CompletableFuture<Ticket?>

    // Aggregate Operations

    /**
     * Asynchronously retrieve a paginated list of tickets with an open status. This is returned in the form of a Result object.
     * If the page size is 0, then the results will not be paginated.
     * @param page Requested page of pagination
     * @param pageSize Number of entries per page
     * @return See Result for specific returned information.
     * @see DBResult
     */
    fun getOpenTicketsAsync(page: Int, pageSize: Int): CompletableFuture<DBResult>

    /**
     * Asynchronously retrieve a paginated list of tickets which have an open status and a specific assignment.
     * This is returned in the form of a Result object. Users wishing to search for tickets assigned to nobody should
     * use the next function instead.
     * If the page size is 0, then the results will not be paginated.
     * @param page Requested page of pagination
     * @param pageSize Number of entries per page
     * @param assignment Desired assignment to player or string.
     * @param unfixedGroupAssignment Desired assignment for any group names. Useful for when searching for tickets
     * assigned to a user via directly or by their permission group.
     * @return See Result for specific returned information.
     * @see DBResult
     */
    fun getOpenTicketsAssignedToAsync(page: Int, pageSize: Int, assignment: String, unfixedGroupAssignment: List<String>): CompletableFuture<DBResult>

    /**
     * Asynchronously retrieve a paginated list of tickets which have an open status and assigned to nobody.
     * This is returned in the form of a Result object.
     * If the page size is 0, then the results will not be paginated.
     * @param page Requested page of pagination
     * @param pageSize Number of entries per page
     * @return See Result for specific returned information.
     * @see DBResult
     */
    fun getOpenTicketsNotAssignedAsync(page: Int, pageSize: Int): CompletableFuture<DBResult>

    /**
     * Asynchronously close all tickets between a lower and upper bound inclusive. Extensions have the following obligation
     * for each ticket:
     * - Set status to close
     * - insert a Mass-close action. The actor and ticket location are provided to create this entry.
     * @param lowerBound Lower bound inclusive
     * @param upperBound Upper bound inclusive
     * @param actor Creator who initiated the ticket modification
     * @param ticketLoc Location where Creator made the ticket modification
     * @return CompletableFuture indicating when action is complete.
     */
    fun massCloseTicketsAsync(lowerBound: Long, upperBound: Long, actor: Creator, ticketLoc: Ticket.CreationLocation): CompletableFuture<Void>

    // Counting
    /**
     * Asynchronously acquire the number of currently open tickets.
     */
    fun countOpenTicketsAsync(): CompletableFuture<Long>

    /**
     * Asynchronously acquire the number of open tickets assigned to a particular user or to a set of permission groups
     * @param assignment Desired assignment to player or string.
     * @param unfixedGroupAssignment Desired assignment for any group names. Useful for when searching for tickets
     * assigned to a user via directly or by their permission group.
     */
    fun countOpenTicketsAssignedToAsync(assignment: String, unfixedGroupAssignment: List<String>): CompletableFuture<Long>

    // Searching
    /**
     * Asynchronously search through the entire database for all tickets which meet particular search constraints.
     * @param constraints See SearchConstraints for more information
     * @param page Requested results page number
     * @param pageSize Size of each page of tickets
     * @return See Result for more information
     * @see SearchConstraints
     * @see DBResult
     */
    fun searchDatabaseAsync(constraints: SearchConstraints, page: Int, pageSize: Int): CompletableFuture<DBResult>

    // ID Acquisition
    /**
     * Asynchronously retrieve all ticket IDs for any ticket that the creator has not viewed the most recent update.
     * @return list of ticket IDs
     */
    fun getTicketIDsWithUpdatesAsync(): CompletableFuture<List<Long>>

    /**
     * Asynchronously retrieve all ticket IDs where a particular user has not viewed the most recent update.
     * @param creator creator of the tickets
     * @return list of ticket IDs
     */
    fun getTicketIDsWithUpdatesForAsync(creator: Creator): CompletableFuture<List<Long>>

    /**
     * Asynchronously retrieve all ticket IDs of tickets owned by a particular Creator
     * @param creator Ticket creator
     * @return list of ticket IDs
     */
    fun getOwnedTicketIDsAsync(creator: Creator): CompletableFuture<List<Long>>


    // Internal Database Functions
    /**
     * Shuts down the database. This function runs on the Common ForkJoinPool as a blocking call during server shutdown
     * and plugin reloading. Do not let this function complete until the database is fully shut down.
     */
    fun closeDatabase()

    /**
     * Initializes or re-initializes the database. This function runs on the Common ForkJoinPool as a blocking call
     * during server shutdown and plugin reloading. Do not let this function complete until the database is ready for use.
     */
    fun initializeDatabase()
}

interface DBResult {
    val filteredResults: List<Ticket>
    val totalPages: Int
    val totalResults: Int
    val returnedPage: Int
}

