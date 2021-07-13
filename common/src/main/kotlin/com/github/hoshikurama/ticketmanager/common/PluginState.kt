package com.github.hoshikurama.ticketmanager.common

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PluginState {
    val jobCount = MutexControlled(0)
    val pluginLocked = MutexControlled(true)
    val ticketCountMetrics = MutexControlled(0)
}

class MutexControlled<T>(private var t: T) {
    private val mutex = Mutex()

    suspend fun get(): T = mutex.withLock { t }
    suspend fun set(t: T) = mutex.withLock { this.t = t }
}