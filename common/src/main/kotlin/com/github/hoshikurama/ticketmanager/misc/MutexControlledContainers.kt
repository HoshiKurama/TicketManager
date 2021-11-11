package com.github.hoshikurama.ticketmanager.misc

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MutexControlled<T>(private var t: T) {
    private val mutex = Mutex()

    suspend fun get(): T = mutex.withLock { t }
    suspend fun set(t: T) = mutex.withLock { this.t = t }
}

class IncrementalMutexController(private var n: Int) {
    private val mutex = Mutex()
    suspend fun getAndIncrement() = mutex.withLock { n.also { n++ } }
}