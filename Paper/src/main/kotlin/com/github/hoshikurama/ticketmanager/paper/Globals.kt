package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.common.PluginState
import com.github.hoshikurama.ticketmanager.common.TMLocale
import kotlinx.coroutines.Deferred
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import java.util.logging.Level

fun consoleLog(level: Level, message: String) = Bukkit.getLogger().log(level, ChatColor.stripColor(message))

internal val mainPlugin: TicketManagerPlugin
    get() = TicketManagerPlugin.plugin

internal val pluginState: Deferred<PluginState>
    get() = mainPlugin.configState


suspend fun pushMassNotify(permission: String, localeMsg: suspend (TMLocale) -> Component) {
    Bukkit.getConsoleSender().sendMessage(localeMsg(mainPlugin.configState.await().localeHandler.consoleLocale))

    Bukkit.getOnlinePlayers().asSequence()
        .filter { it.has(permission) }
        .forEach { localeMsg(it.toTMLocale()).run(it::sendMessage) }
}

internal fun Player.has(permission: String) = mainPlugin.perms.has(this, permission)

internal suspend fun Player.toTMLocale() = pluginState.await().localeHandler.getOrDefault(locale().toString())