package com.github.hoshikurama.ticketmanager.common.hooks.database

import com.github.hoshikurama.ticketmanager.common.database.Database
import com.github.hoshikurama.ticketmanager.common.database.MySQL
import com.github.hoshikurama.ticketmanager.common.hooks.internal.DatabaseBuilder
import kotlinx.coroutines.CoroutineDispatcher

class MySQLBuilder(
    private val host: String,
    private val port: String,
    private val databaseName: String,
    private val username: String,
    private val password: String,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBuilder {

    override suspend fun build(): Database? {
        return try { MySQL(host, port, databaseName, username, password, dispatcher) }
        catch (e: Exception) { null }
    }
}