package com.github.hoshikurama.ticketmanager.common

import com.github.hoshikurama.ticketmanager.common.database.Database
import com.github.hoshikurama.ticketmanager.common.hooks.database.MemoryBuilder
import com.github.hoshikurama.ticketmanager.common.hooks.database.MySQLBuilder
import com.github.hoshikurama.ticketmanager.common.hooks.database.SQLiteBuilder
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.*

class ConfigState(
    val cooldowns: Cooldown,
    val database: Database,
    val localeHandler: LocaleHandler,
    val allowUnreadTicketUpdates: Boolean,
    val pluginUpdateAvailable: Deferred<Pair<String, String>?>,
    val discord: Discord?,

    val mySQLBuilder: MySQLBuilder,
    val sqliteBuilder: SQLiteBuilder,
    val memoryBuilder: MemoryBuilder,
)

class Cooldown(
    private val enabled: Boolean,
    private val duration: Long,
) {
    private val mutex = Mutex()
    private val map = mutableMapOf<UUID, Long>()

    suspend fun checkAndSetAsync(uuid: UUID?): Boolean = mutex.withLock {
        if (!enabled || uuid == null) return@withLock false

        val curTime = Instant.now().epochSecond
        val applies = map[uuid]?.let { it <= curTime } ?: false

        if (applies) return@withLock true
        else {
            map[uuid] = duration + curTime
            return@withLock true
        }
    }

    suspend fun filterMapAsync() {
        mutex.withLock {
            map.forEach {
                if (it.value > Instant.now().epochSecond)
                    map.remove(it.key)
            }
        }
    }
}

fun test() {

}