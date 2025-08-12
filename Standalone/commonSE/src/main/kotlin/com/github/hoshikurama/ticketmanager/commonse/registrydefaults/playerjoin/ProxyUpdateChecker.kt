package com.github.hoshikurama.ticketmanager.commonse.registrydefaults.playerjoin

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.registry.playerjoin.PlayerJoinExtension
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.misc.templated
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.ProxyVersionChannel
import kotlin.math.min
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeSource

class ProxyUpdateChecker(private val proxyVersionChannel: ProxyVersionChannel) : PlayerJoinExtension {
    @Volatile var lastCheck = TimeSource.Monotonic.markNow()
    @Volatile var doesUpdateExist: Boolean? = null
    @Volatile var curVersion: String? = null
    @Volatile var latestVersion: String? = null

    override suspend fun whenPlayerJoins(
        player: CommandSender.OnlinePlayer,
        platformFunctions: PlatformFunctions,
        permission: Permission,
        database: AsyncDatabase,
        config: Config,
        locale: Locale
    ) {
        if (!permission.has(player, "ticketmanager.notify.proxyUpdate")) return

        // Rewrites information after more than one hour and someone logs on.
        val curTime = TimeSource.Monotonic.markNow()
        val serverName = config.proxyOptions?.serverName ?: "NULL"

        if (curTime - lastCheck > 1.hours || curVersion == null || latestVersion == null) {
            val (cur, latest) = proxyVersionChannel.request(serverName)
            curVersion = cur
            latestVersion = latest
            lastCheck = curTime
            doesUpdateExist = doesNewUpdateExist()
        }

        if (doesUpdateExist == false) return
        locale.notifyProxyUpdate.parseMiniMessage(
            "current" templated curVersion!!,
            "latest" templated latestVersion!!,
        ).run(player::sendMessage)
    }

    private fun doesNewUpdateExist(): Boolean {
        if (latestVersion == curVersion)
            return false

        val curVersSplit = curVersion!!.split(".").map(String::toInt)
        val latestVersSplit = latestVersion!!.split(".").map(String::toInt)

        for (i in 0..<min(curVersSplit.size, latestVersSplit.size)) return when {
            curVersSplit[i] > latestVersSplit[i] -> false
            curVersSplit[i] < latestVersSplit[i] -> true
            else -> continue
        }

        return false // Either snapshot or unable to determine
    }
}