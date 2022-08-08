package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.commonse.LocaleHandler
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.misc.encodeRequestTP
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.platform.Player
import com.github.hoshikurama.ticketmanager.commonse.platform.Sender
import com.github.hoshikurama.ticketmanager.commonse.ticket.Ticket
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.Plugin
import java.util.*
import java.util.logging.Level

class PlatformFunctionsImpl(
    private val perms: Permission,
    private val plugin: Plugin,
    private val serverName: String?,
) : PlatformFunctions {

    override fun massNotify(localeHandler: LocaleHandler, permission: String, localeMsg: (TMLocale) -> Component) {
        Bukkit.getConsoleSender().sendMessage(localeMsg(localeHandler.consoleLocale))

        Bukkit.getOnlinePlayers()
            .filter { perms.has(it, permission) }
            .map { it to localeHandler.getOrDefault(it.locale().toString()) }
            .forEach { (p, locale) -> localeMsg(locale).run(p::sendMessage) }

    }

    override fun buildPlayer(uuid: UUID, localeHandler: LocaleHandler): Player? {
        return Bukkit.getPlayer(uuid)?.run { PaperPlayer(this, perms, localeHandler, serverName) }
    }

    override fun getAllOnlinePlayers(localeHandler: LocaleHandler): List<Player> {
        return Bukkit.getOnlinePlayers().map { PaperPlayer(it, perms, localeHandler, serverName) }
    }

    override fun stripColour(msg: String): String {
        return ChatColor.stripColor(msg)!!
    }

    override fun offlinePlayerNameToUUIDOrNull(name: String): UUID? {
        return Bukkit.getOfflinePlayers().firstOrNull { it.name?.equals(name) ?: false }
            ?.run { uniqueId }
    }

    override fun nameFromUUID(uuid: UUID): String {
        return uuid.run(Bukkit::getOfflinePlayer).name ?: "UUID"
    }

    override fun teleportToTicketLocSameServer(player: Player, loc: Ticket.TicketLocation) {
        val world = Bukkit.getWorld(loc.world!!)
        val paperPlayer = player as PaperPlayer

        world?.run {
            val location = Location(this, loc.x!!.toDouble(), loc.y!!.toDouble(), loc.z!!.toDouble())
            Bukkit.getScheduler().runTask(plugin, Runnable { paperPlayer.pPlayer.teleport(location) })
        }
    }

    override fun teleportToTicketLocDiffServer(player: Player, loc: Ticket.TicketLocation) {
        plugin.server.sendPluginMessage(plugin, "ticketmanager:server_to_proxy_tp", encodeRequestTP(player, loc))
    }

    override fun relayMessageToProxy(channel: String, encodedMessage: ByteArray) {
        plugin.server.sendPluginMessage(plugin, channel, encodedMessage)
    }

    override fun getConsoleAudience(): Audience {
        return Bukkit.getConsoleSender()
    }

    override fun pushInfoToConsole(message: String) {
        Bukkit.getLogger().log(Level.INFO, message)
    }

    override fun pushWarningToConsole(message: String) {
        Bukkit.getLogger().log(Level.WARNING, message)
    }

    override fun pushErrorToConsole(message: String) {
        Bukkit.getLogger().log(Level.SEVERE, message)
    }

    override fun getPermissionGroups(): List<String> {
        return perms.groups.toList()
    }

    override fun getOfflinePlayerNames(): List<String> {
        return Bukkit.getOfflinePlayers().mapNotNull(OfflinePlayer::getName)
    }

    override fun getOnlineSeenPlayerNames(sender: Sender): List<String> {
        return if (sender is PaperPlayer) {
            val player = sender.pPlayer
            Bukkit.getOnlinePlayers()
                .filter(player::canSee)
                .map { it.name }
        }
        else Bukkit.getOnlinePlayers().map { it.name }
    }

    override fun getWorldNames(): List<String> {
        return Bukkit.getWorlds().map { it.name }
    }
}

