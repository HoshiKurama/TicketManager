package com.github.hoshikurama.ticketmanager.commonse.datas

import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class Cooldown(
    private val enabled: Boolean,
    private val duration: Long,
) {
    private val map = ConcurrentHashMap<UUID, Long>()

    fun checkAndSetAsync(uuid: UUID): Boolean {
        if (!enabled) return false

        val curTime = Instant.now().epochSecond
        val applies = map[uuid]?.let { it <= curTime } ?: false

        if (!applies) map[uuid] = duration + curTime
        return true
    }

    fun filterMapAsync() {
        val curTime = Instant.now().epochSecond
        map.filterValues { it > curTime }.keys.forEach(map::remove)
    }
}