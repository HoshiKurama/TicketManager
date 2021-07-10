package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.common.PluginState
import com.github.hoshikurama.ticketmanager.common.TMLocale
import com.github.shynixn.mccoroutine.asyncDispatcher
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*
import java.util.logging.Level
import kotlin.coroutines.CoroutineContext

fun consoleLog(level: Level, message: String) = Bukkit.getLogger().log(level, ChatColor.stripColor(message))

internal val mainPlugin: TicketManagerPlugin
    get() = TicketManagerPlugin.plugin

internal val pluginState: PluginState
    get() = mainPlugin.configState

internal val asyncContext: CoroutineContext
    get() = mainPlugin.asyncDispatcher


internal fun pushMassNotify(permission: String, localeMsg: (TMLocale) -> Component) {
    Bukkit.getConsoleSender().sendMessage(localeMsg(mainPlugin.configState.localeHandler.consoleLocale))

    Bukkit.getOnlinePlayers().asSequence()
        .filter { it.has(permission) }
        .forEach { localeMsg(it.toTMLocale()).run(it::sendMessage) }
}

internal fun Player.has(permission: String) = mainPlugin.perms.has(this, permission)
internal fun CommandSender.has(permission: String): Boolean = if (this is Player) has(permission) else true

internal fun Player.toTMLocale() = pluginState.localeHandler.getOrDefault(locale().toString())
internal fun CommandSender.toTMLocale() = if (this is Player) toTMLocale() else pluginState.localeHandler.consoleLocale

internal fun CommandSender.toUUIDOrNull() = if (this is Player) this.uniqueId else null

fun UUID?.toName(locale: TMLocale): String {
    if (this == null) return locale.consoleName
    return this.run(Bukkit::getOfflinePlayer).name ?: "UUID"
}