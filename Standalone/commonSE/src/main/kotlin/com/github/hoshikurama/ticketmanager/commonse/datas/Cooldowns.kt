package com.github.hoshikurama.ticketmanager.commonse.datas

import com.github.hoshikurama.ticketmanager.commonse.TMCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ForkJoinPool
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class Cooldown(
    private val duration: Long,
    private val runAfterRemoval: (suspend (UUID) -> Unit)? = null,
) {
    private val map = ConcurrentHashMap<UUID, Pair<Long, Job>>()

    private fun underCooldown(uuid: UUID) = map[uuid]?.first?.let { it <= Instant.now().epochSecond } ?: false

    fun notUnderCooldown(uuid: UUID) = !underCooldown(uuid)

    fun add(uuid: UUID) {
        map[uuid] = (Instant.now().epochSecond + duration) to TMCoroutine.launchGlobal {
            delay(duration.toDuration(DurationUnit.SECONDS).inWholeMilliseconds)

            if (!isActive) return@launchGlobal
            map.remove(uuid)
            runAfterRemoval?.let { it(uuid) }
        }
    }

    internal fun shutdown() {
        map.forEachValue(ForkJoinPool.getCommonPoolParallelism().toLong()) { it.second.cancel() }
        map.clear()
    }
}

