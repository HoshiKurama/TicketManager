package com.hoshikurama.github.ticketmanager.databases

import com.hoshikurama.github.ticketmanager.ticket.Ticket
import java.util.*

interface Database {
    enum class Types {
        MySQL, SQLite
    }

    val type: Types

    // Individual things
    fun getAssignment(ticketID: Int): String?
    fun getCreatorUUID(ticketID: Int): UUID?
    fun getLocation(ticketID: Int): org.bukkit.Location?
    fun getPriority(ticketID: Int): Ticket.Priority
    fun getStatus(ticketID: Int): Ticket.Status
    fun getStatusUpdateForCreator(ticketID: Int): Boolean
    fun setAssignment(ticketID: Int, assignment: String?)
    fun setPriority(ticketID: Int, priority: Ticket.Priority)
    fun setStatus(ticketID: Int, status: Ticket.Status)
    fun setStatusUpdateForCreator(ticketID: Int, status: Boolean)

    // More specific Ticket actions
    fun addAction(ticketID: Int, action: Ticket.Action)
    fun addTicket(ticket: Ticket, action: Ticket.Action): Int
    fun getOpen(): List<Ticket>
    fun getOpenAssigned(assignment: String, groupAssignment: List<String>): List<Ticket>
    fun getTicket(ID: Int): Ticket?
    fun getTicketIDsWithUpdates(): List<Pair<UUID, Int>>
    fun getTicketIDsWithUpdates(uuid: UUID): List<Int>
    fun isValidID(ticketID: Int): Boolean
    fun massCloseTickets(lowerBound: Int, upperBound: Int, uuid: UUID?)
    fun searchDB(params: Map<String, String>): List<Ticket>

    // Database Modifications
    fun closeDatabase()
    fun createDatabasesIfNeeded()
    fun migrateDatabase(targetType: Types)
    fun updateNeeded(): Boolean
    fun updateDatabase()
}