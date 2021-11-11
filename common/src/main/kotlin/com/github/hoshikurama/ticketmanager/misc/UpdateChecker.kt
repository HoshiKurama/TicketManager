package com.github.hoshikurama.ticketmanager.misc

import com.github.hoshikurama.ticketmanager.pluginVersion
import java.io.InputStream
import java.net.URL
import java.util.*

class UpdateChecker(val canCheck: Boolean) {
    val latestVersionOrNull: String? by lazy {
        if (!canCheck) return@lazy null

        val latestVersion = getLatestVersion() ?: return@lazy null
        if (pluginVersion == latestVersion) return@lazy null

        val curVersSplit = pluginVersion.split(".").map(String::toInt)
        val latestVersSplit = latestVersion.split(".").map(String::toInt)

        for (i in 0..latestVersSplit.lastIndex) {
            if (curVersSplit[i] > latestVersSplit[i])
                return@lazy null
        }

        // If code reaches here, the latest version is higher than current version
        latestVersion
    }

    private fun getLatestVersion(): String? {
        var inputStream: InputStream? = null
        var scanner: Scanner? = null

        return try {
            inputStream = URL("https://api.spigotmc.org/legacy/update.php?resource=91178").openStream()
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