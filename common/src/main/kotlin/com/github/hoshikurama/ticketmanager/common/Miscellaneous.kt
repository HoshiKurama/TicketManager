package com.github.hoshikurama.ticketmanager.common

import com.github.hoshikurama.ticketmanager.common.ticket.BasicTicket
import com.github.hoshikurama.ticketmanager.common.ticket.FullTicket
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.net.URL
import java.time.Instant
import java.util.*

class UpdateChecker(private val resourceID: Int) {
    fun getLatestVersion(): String? {
        var inputStream: InputStream? = null
        var scanner: Scanner? = null

        return try {
            inputStream = URL("https://api.spigotmc.org/legacy/update.php?resource=$resourceID").openStream()
            scanner = Scanner(inputStream!!)
            if (scanner.hasNext()) scanner.next() else null
        }
        catch (ignored: Exception) { null }
        finally {
            inputStream?.close()
            scanner?.close()
        }
    }
}


fun byteToPriority(byte: Byte) = when (byte.toInt()) {
    1 -> BasicTicket.Priority.LOWEST
    2 -> BasicTicket.Priority.LOW
    3 -> BasicTicket.Priority.NORMAL
    4 -> BasicTicket.Priority.HIGH
    5 -> BasicTicket.Priority.HIGHEST
    else -> BasicTicket.Priority.NORMAL
}

fun Long.toLargestRelativeTime(locale: TMLocale): String {
    val timeAgo = Instant.now().epochSecond - this

    return when {
        timeAgo >= 31556952L -> (timeAgo / 31556952L).toString() + locale.timeYears
        timeAgo >= 604800L ->(timeAgo / 604800L).toString() + locale.timeWeeks
        timeAgo >= 86400L ->(timeAgo / 86400L).toString() + locale.timeDays
        timeAgo >= 3600L ->(timeAgo / 3600L).toString() + locale.timeHours
        timeAgo >= 60L ->(timeAgo / 60L).toString() + locale.timeMinutes
        else -> timeAgo.toString() + locale.timeSeconds
    }
}

fun relTimeToEpochSecond(relTime: String, locale: TMLocale): Long {
    var seconds = 0L
    var index = 0
    val unprocessed = StringBuilder(relTime)

    while (unprocessed.isNotEmpty() && index != unprocessed.lastIndex + 1) {
        unprocessed[index].toString().toByteOrNull()
            // If number...
            ?.apply { index++ }
        // If not a number...
            ?: run {
                val number = if (index == 0) 0
                else unprocessed.substring(0, index).toLong()

                seconds += number * when (unprocessed[index].toString()) {
                    locale.searchTimeSecond -> 1L
                    locale.searchTimeMinute -> 60L
                    locale.searchTimeHour -> 3600L
                    locale.searchTimeDay -> 86400L
                    locale.searchTimeWeek -> 604800L
                    locale.searchTimeYear -> 31556952L
                    else -> 0L
                }

                unprocessed.delete(0, index+1)
                index = 0
            }
    }

    return Instant.now().epochSecond - seconds
}

val sortForList: Comparator<BasicTicket> = Comparator.comparing(BasicTicket::priority).reversed().thenComparing(Comparator.comparing(BasicTicket::id).reversed())

val sortActions: Comparator<FullTicket.Action> = Comparator.comparing(FullTicket.Action::timestamp)

fun <T> T.notEquals(t: T) = this != t


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