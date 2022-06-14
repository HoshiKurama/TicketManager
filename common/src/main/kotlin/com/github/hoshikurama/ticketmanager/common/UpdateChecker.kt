package com.github.hoshikurama.ticketmanager.common

import java.net.URL

class UpdateChecker(val canCheck: Boolean, private val location: Location) {
    val latestVersionIfNotLatest = kotlin.run {
        if (!canCheck) return@run null

        val latestVersion = getLatestVersion() ?: return@run null
        if (location.curVersion == latestVersion) return@run null

        val curVersSplit = location.curVersion.split(".").map(String::toInt)
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

    enum class Location(val link: String, val curVersion: String) {
        MAIN("https://api.github.com/repos/HoshiKurama/TicketManager/tags", mainPluginVersion),
        BRIDGE("https://api.github.com/repos/HoshiKurama/TicketManager-Bridge-Releases/tags", bridgePluginVersion),
    }

    private fun getLatestVersion(): String? {
        return try {
            val regex = "\"name\":\"[^,]*".toRegex()

            URL(location.link)
                .openStream()
                .bufferedReader()
                .readText()
                .let(regex::find)!!
                .value
                .substring(8) // "x.y.z"
                .replace("\"","")
        } catch (e: Exception) { null }
    }
}