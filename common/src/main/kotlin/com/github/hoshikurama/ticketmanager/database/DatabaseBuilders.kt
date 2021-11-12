package com.github.hoshikurama.ticketmanager.database

import com.github.hoshikurama.ticketmanager.database.impl.CachedSQLite
import com.github.hoshikurama.ticketmanager.database.impl.Memory
import com.github.hoshikurama.ticketmanager.database.impl.MySQL
import com.github.hoshikurama.ticketmanager.database.impl.SQLite
import kotlinx.coroutines.CoroutineDispatcher

interface DatabaseBuilder {
    suspend fun build(): Database
}

class MemoryBuilder(
    private val pathToFolder: String,
    private val backupFrequency: Long,
) : DatabaseBuilder {
    override suspend fun build() = Memory(pathToFolder, backupFrequency)
}

class MySQLBuilder(
    private val host: String,
    private val port: String,
    private val databaseName: String,
    private val username: String,
    private val password: String,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBuilder {
    override suspend fun build() = MySQL(host, port, databaseName, username, password, dispatcher)
}

class SQLiteBuilder(private val pathToFolder: String) : DatabaseBuilder {
    override suspend fun build() = SQLite(pathToFolder)
}

class CachedSQLiteBuilder(
    private val pathToFolder: String,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBuilder {
    override suspend fun build() = CachedSQLite(pathToFolder, dispatcher)
}

class DatabaseBuilders(val memoryBuilder: MemoryBuilder, val sqLiteBuilder: SQLiteBuilder, val mySQLBuilder: MySQLBuilder, val cachedSQLiteBuilder: CachedSQLiteBuilder)