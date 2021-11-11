package com.github.hoshikurama.ticketmanager.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.*

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