package com.github.hoshikurama.ticketmanager.misc

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Code from https://github.com/CruGlobal/android-gto-support/blob/47b44477e94e7d913de15066e3dd3eb8b54c4828/gto-support-kotlin-coroutines/src/main/java/org/ccci/gto/android/common/kotlin/coroutines/ReadWriteMutex.kt
interface ReadWriteMutex {
    val write: Mutex
    val read: Mutex
}

fun ReadWriteMutex(): ReadWriteMutex = ReadWriteMutexImpl()

internal class ReadWriteMutexImpl : ReadWriteMutex {
    private val stateMutex = Mutex()
    private val readerOwner = Any()
    internal var readers = 0L

    override val write = Mutex()
    override val read = object : Mutex {
        override suspend fun lock(owner: Any?) {
            stateMutex.withLock {
                check(readers < Long.MAX_VALUE) {
                    "Attempt to lock the read mutex more than ${Long.MAX_VALUE} times concurrently"
                }
                // first reader should lock the write mutex
                if (readers == 0L) write.lock(readerOwner)
                readers++
            }
        }

        override fun unlock(owner: Any?) {
            runBlocking {
                stateMutex.withLock {
                    check(readers > 0L) { "Attempt to unlock the read mutex when it wasn't locked" }
                    // release the write mutex lock when this is the last reader
                    if (--readers == 0L) write.unlock(readerOwner)
                }
            }
        }

        override val isLocked get() = TODO("Not supported")
        override val onLock get() = TODO("Not supported")
        override fun holdsLock(owner: Any) = TODO("Not supported")
        override fun tryLock(owner: Any?) = TODO("Not supported")
    }
}