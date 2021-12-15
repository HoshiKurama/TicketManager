package com.github.hoshikurama.ticketmanager.misc

import com.beust.klaxon.Klaxon
import com.github.hoshikurama.ticketmanager.pluginVersion
import java.net.URL

class UpdateChecker(val canCheck: Boolean) {
    val latestVersionOrNull = kotlin.run {
        if (!canCheck) return@run null

        val latestVersion = getLatestVersion() ?: return@run null
        if (pluginVersion == latestVersion) return@run null

        val curVersSplit = pluginVersion.split(".").map(String::toInt)
        val latestVersSplit = latestVersion.split(".").map(String::toInt)

        for (i in 0..latestVersSplit.lastIndex) {
            when {
                curVersSplit[i] > latestVersSplit[i] -> return@run null
                curVersSplit[i] < latestVersSplit[i] -> return@run latestVersion
                else -> continue // Equal
            }
        }

        return@run null
    }

    private fun getLatestVersion(): String? {
        return try {
            URL("https://api.github.com/repos/HoshiKurama/TicketManager/tags")
                .openStream()
                .let { Klaxon().parseArray<Release>(it) }
                ?.get(0)?.name
        } catch (e: Exception) { null }
    }
}

data class Release(
    val name: String,
    val zipball_url: String,
    val tarball_url: String,
    val commit: CommitInformation,
    val node_id: String,
)

data class CommitInformation(val sha: String, val url: String)