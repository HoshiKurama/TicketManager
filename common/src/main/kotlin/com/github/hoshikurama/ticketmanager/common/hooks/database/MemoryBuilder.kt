package com.github.hoshikurama.ticketmanager.common.hooks.database

import com.github.hoshikurama.ticketmanager.common.database.Database
import com.github.hoshikurama.ticketmanager.common.database.Memory
import com.github.hoshikurama.ticketmanager.common.hooks.internal.DatabaseBuilder
import com.github.hoshikurama.ticketmanager.common.tryOrNull

class MemoryBuilder(
    private val pathToFolder: String,
    private val backupFrequency: Long,
) : DatabaseBuilder {
    override suspend fun build(): Database? = tryOrNull { Memory(pathToFolder, backupFrequency) }
}