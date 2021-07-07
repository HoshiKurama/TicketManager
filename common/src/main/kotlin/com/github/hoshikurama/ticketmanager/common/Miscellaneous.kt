package com.github.hoshikurama.ticketmanager.common

import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL
import java.util.*
import kotlin.coroutines.CoroutineContext

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

class NonBlockingSync<T>(
    private val context: CoroutineContext,
    private var t: T
) {
    suspend fun check() = withContext(context) { t }
    suspend fun set(v: T) = withContext(context) { t = v }
}