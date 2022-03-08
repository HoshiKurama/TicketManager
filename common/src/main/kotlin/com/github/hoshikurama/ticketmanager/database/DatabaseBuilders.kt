package com.github.hoshikurama.ticketmanager.database

import com.github.hoshikurama.ticketmanager.database.impl.AsyncMemory
import com.github.hoshikurama.ticketmanager.database.impl.AsyncMySQL

interface DatabaseBuilder {
    fun build(): AsyncDatabase
}

/*
class MemoryBuilder(
    private val pathToFolder: String,
    private val backupFrequency: Long,
) : DatabaseBuilder {
    override suspend fun build() = Memory(pathToFolder, backupFrequency)
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

 */

class MySQLBuilder(
    private val host: String,
    private val port: String,
    private val databaseName: String,
    private val username: String,
    private val password: String,
) : DatabaseBuilder {
    override fun build() = AsyncMySQL(host, port, databaseName, username, password)
}

class MemoryBuilder(
    private val pathToFolder: String,
    private val backupFrequency: Long,
) : DatabaseBuilder {
    override fun build() = AsyncMemory(pathToFolder, backupFrequency)
}

class DatabaseBuilders(val mySQLBuilder: MySQLBuilder, val memoryBuilder: MemoryBuilder)
