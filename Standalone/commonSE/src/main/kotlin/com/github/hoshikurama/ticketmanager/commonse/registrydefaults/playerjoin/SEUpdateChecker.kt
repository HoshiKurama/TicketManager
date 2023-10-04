package com.github.hoshikurama.ticketmanager.commonse.registrydefaults.playerjoin

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.types.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.registry.playerjoin.PlayerJoinExtension
import com.github.hoshikurama.ticketmanager.common.mainPluginVersion
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.misc.templated
import java.net.URL
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource

class SEUpdateChecker: PlayerJoinExtension {
    @Volatile var latestVersion = grabLatestVersion()
    @Volatile var lastCheck = TimeSource.Monotonic.markNow()
    @Volatile var doesUpdateExist = doesNewUpdateExist()


    override suspend fun whenPlayerJoins(
        player: CommandSender.OnlinePlayer,
        platformFunctions: PlatformFunctions,
        permission: Permission,
        database: AsyncDatabase,
        config: Config,
        locale: Locale
    ) {
        if (!permission.has(player, "ticketmanager.notify.pluginUpdate")) return

        // Rewrites information after more than one hour and someone logs on.
        val curTime = TimeSource.Monotonic.markNow()
        if (curTime - lastCheck > 1.hours) {
            latestVersion = grabLatestVersion()
            doesUpdateExist = doesNewUpdateExist()
            lastCheck = curTime
        }

        if (!doesUpdateExist) return
        locale.notifyPluginUpdate.parseMiniMessage(
            "current" templated mainPluginVersion,
            "latest" templated latestVersion,
        ).run(player::sendMessage)
    }


    private fun grabLatestVersion(): String {
        val regex = "\"name\":\"[^,]*".toRegex()

        return URL("https://api.github.com/repos/HoshiKurama/TicketManager/tags")
            .openStream()
            .bufferedReader()
            .readText()
            .let(regex::find)!!
            .value
            .substring(8) // "x.y.z"
            .replace("\"","")
    }

    private fun doesNewUpdateExist(): Boolean {
        if (latestVersion == mainPluginVersion)
            return false

        val curVersSplit = mainPluginVersion.split(".").map(String::toInt)
        val latestVersSplit = latestVersion.split(".").map(String::toInt)

        for (i in 0 until 3) return when {
            curVersSplit[i] > latestVersSplit[i] -> false
            curVersSplit[i] < latestVersSplit[i] -> true
            else -> continue
        }

        return false // Must be a snapshot version
    }
}