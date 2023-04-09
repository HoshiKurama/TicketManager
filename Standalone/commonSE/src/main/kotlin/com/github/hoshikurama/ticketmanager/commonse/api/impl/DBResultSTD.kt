package com.github.hoshikurama.ticketmanager.commonse.api.impl

import com.github.hoshikurama.ticketmanager.api.database.DBResult
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket

data class DBResultSTD(
    override val filteredResults: List<Ticket>,
    override val totalPages: Int,
    override val totalResults: Int,
    override val returnedPage: Int
) : DBResult