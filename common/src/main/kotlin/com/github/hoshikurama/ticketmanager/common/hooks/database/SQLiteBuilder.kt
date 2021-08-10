package com.github.hoshikurama.ticketmanager.common.hooks.database


import com.github.hoshikurama.ticketmanager.common.database.Database
import com.github.hoshikurama.ticketmanager.common.database.SQLite
import com.github.hoshikurama.ticketmanager.common.hooks.internal.DatabaseBuilder

class SQLiteBuilder(private val pathToFolder: String) : DatabaseBuilder {
    override suspend fun build(): Database = SQLite(pathToFolder)
}