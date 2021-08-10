package com.github.hoshikurama.ticketmanager.common.hooks.internal

import com.github.hoshikurama.ticketmanager.common.database.Database

interface DatabaseBuilder {
    suspend fun build(): Database?
}