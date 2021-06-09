package com.hoshikurama.github.ticketmanager

import com.hoshikurama.github.ticketmanager.ticket.Ticket
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.time.Instant
import java.util.*
import java.util.logging.Level
import kotlin.Comparator

internal val sortForList: Comparator<Ticket> = Comparator.comparing(Ticket::priority).reversed().thenComparing(Comparator.comparing(Ticket::id).reversed())

internal val mainPlugin: TicketManagerPlugin
    get() = TicketManagerPlugin.plugin

internal val pluginState: PluginState
    get() = mainPlugin.configState


fun CommandSender.sendPlatformMessage(
    component: net.md_5.bungee.api.chat.BaseComponent,
    serverType: PluginState.ServerType? = pluginState.serverType
    ) {
    when (serverType) {
        PluginState.ServerType.Paper -> sendMessage(component)
        PluginState.ServerType.Spigot -> spigot().sendMessage(component)
        else -> try {
            Class.forName("com.destroystokyo.paper.VersionHistoryManager\$VersionData")
            sendPlatformMessage(component, PluginState.ServerType.Paper)
        } catch (e: Exception) {
            sendPlatformMessage(component, PluginState.ServerType.Spigot)
        }
    }
}

fun Player.sendPlatformMessage(
    component: net.md_5.bungee.api.chat.BaseComponent,
    serverType: PluginState.ServerType? = pluginState.serverType
) {
    when (serverType) {
        PluginState.ServerType.Paper -> sendMessage(component)
        PluginState.ServerType.Spigot -> spigot().sendMessage(component)
        else -> try {
            Class.forName("com.destroystokyo.paper.VersionHistoryManager\$VersionData")
            sendPlatformMessage(component, PluginState.ServerType.Paper)
        } catch (e: Exception) {
            sendPlatformMessage(component, PluginState.ServerType.Spigot)
        }
    }
}

fun getUUUIDStringOrNull(playerName: String): String? {
    return when (pluginState.serverType) {
        PluginState.ServerType.Paper -> Bukkit.getPlayerUniqueId(playerName)?.toString()
        PluginState.ServerType.Spigot ->
            Bukkit.getOfflinePlayers()
                .asSequence()
                .filter { it.name?.equals(playerName) ?: false }
                .map { it.uniqueId.toString() }
                .firstOrNull()
    }
}

fun byteToPriority(byte: Byte) = when (byte.toInt()) {
    1 -> Ticket.Priority.LOWEST
    2 -> Ticket.Priority.LOW
    3 -> Ticket.Priority.NORMAL
    4 -> Ticket.Priority.HIGH
    5 -> Ticket.Priority.HIGHEST
    else -> Ticket.Priority.NORMAL
}

fun postModifiedStacktrace(e: Exception) {
    Bukkit.getOnlinePlayers().asSequence()
        .filter { it.has("ticketmanager.notify.warning") }
        .map { it to getLocale(it) }
        .forEach { p ->
            val sentComponent = TextComponent("")

            listOf(
                p.second.stacktraceLine1,
                p.second.stacktraceLine2.replace("%exception%", e.javaClass.simpleName),
                p.second.stacktraceLine3.replace("%message%", e.message ?: "?"),
                p.second.stacktraceLine4
            )
                .map(::toColour)
                .map(::TextComponent)
                .forEach { sentComponent.addExtra(it) }

            // Adds stacktrace entries
            e.stackTrace
                .filter { it.className.startsWith("com.hoshikurama.github.ticketmanager") }
                .map {
                    p.second.stacktraceEntry
                        .replace("%method%", it.methodName)
                        .replace("%file%", it.fileName ?: "?")
                        .replace("%line%", "${it.lineNumber}")
                }
                .map(::toColour)
                .map(::TextComponent)
                .forEach { sentComponent.addExtra(it) }

            p.first.sendPlatformMessage(sentComponent, null)
        }
}

internal fun anyLocksPresent() = mainPlugin.pluginLocked

internal fun getLocale(player: Player) = when (pluginState.serverType) {
    PluginState.ServerType.Paper -> pluginState.enabledLocales.getOrDefault(player.locale().toString())
    PluginState.ServerType.Spigot -> pluginState.enabledLocales.getOrDefault(player.locale)
}

internal fun getLocale(sender: CommandSender): TMLocale {
    return if (sender is Player) getLocale(sender)
           else pluginState.enabledLocales.consoleLocale
}

fun Player.has(permission: String): Boolean = mainPlugin.perms.has(this, permission)

fun CommandSender.has(permission: String): Boolean = if (this is Player) has(permission) else true

fun consoleLog(level: Level, message: String): Unit = Bukkit.getLogger().log(level, ChatColor.stripColor(message))

fun toColour(string: String): String = ChatColor.translateAlternateColorCodes('&', string)

fun pushMassNotify(permission: String, localeMsg: (TMLocale) -> String, level: Level = Level.INFO) {
    consoleLog(level, localeMsg(pluginState.enabledLocales.consoleLocale))

    Bukkit.getOnlinePlayers().asSequence()
        .filter { it.has(permission) }
        .forEach { getLocale(it).run(localeMsg).run(::toColour).run { it.sendMessage(this) } }
}

fun String.sendColouredMessageTo(player: Player) =
    toColour(this).run { player.sendMessage(this) }

fun String.sendColouredMessageTo(sender: CommandSender) =
    toColour(this).run { sender.sendMessage(this) }

fun stripColour(str: String): String {
    return ChatColor.stripColor(str).replace("&", "&&")
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

fun TextComponent.addViewTicketOnClick(id: Int, locale: TMLocale) {
    hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(locale.clickViewTicket))
    clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, locale.run { "/$commandBase $commandWordView $id" } )
}