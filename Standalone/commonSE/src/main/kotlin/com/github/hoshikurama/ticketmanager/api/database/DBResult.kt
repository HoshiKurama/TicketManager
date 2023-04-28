package com.github.hoshikurama.ticketmanager.api.database

import com.github.hoshikurama.ticketmanager.api.ticket.Ticket

/**
 * For various reasons, certain AsyncDatabase function return a DBResult object containing various results.
 * In general, this is returned when the plugin expects numerous results to be returned, more than can be displayed
 * at once on a Minecraft instance's screen. Thus, the results are often chunked into pages and only the requested page is returned.
 * @property filteredResults Final list of tickets applicable to the called function
 * @property totalPages Number of pages found
 * @property totalResults Number of results found across all pages
 * @property returnedPage Page number of the filteredResults property
 */
@JvmRecord
data class DBResult(
    val filteredResults: List<Ticket>,
    val totalPages: Int,
    val totalResults: Int,
    val returnedPage: Int
)