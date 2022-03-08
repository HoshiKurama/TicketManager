package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.LocaleHandler
import com.github.hoshikurama.ticketmanager.TMLocale
import com.github.hoshikurama.ticketmanager.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.platform.Player
import com.github.hoshikurama.ticketmanager.platform.Sender
import com.github.hoshikurama.ticketmanager.ticket.Ticket
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import java.util.*
import java.util.logging.Level

class PaperFunctions(private val perms: Permission) : PlatformFunctions {

    override fun massNotify(localeHandler: LocaleHandler, permission: String, localeMsg: (TMLocale) -> Component) {
        Bukkit.getConsoleSender().sendMessage(localeMsg(localeHandler.consoleLocale))

        Bukkit.getOnlinePlayers()
            .filter { perms.has(it, permission) }
            .map { it to localeHandler.getOrDefault(it.locale().toString()) }
            .forEach { (p, locale) -> localeMsg(locale).run(p::sendMessage) }

    }

    override fun buildPlayer(uuid: UUID, localeHandler: LocaleHandler): Player? {
        return Bukkit.getPlayer(uuid)?.run { PaperPlayer(this, perms, localeHandler) }
    }

    override fun getPlayersOnAllServers(localeHandler: LocaleHandler): List<Player> {
        return Bukkit.getOnlinePlayers().map { PaperPlayer(it, perms, localeHandler) }
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

    override fun teleportToTicketLocation(player: Player, loc: Ticket.TicketLocation) {
        val world = Bukkit.getWorld(loc.world!!)
        val paperPlayer = player as PaperPlayer
        //TODO THROWING CALLING SYNC METHOD ASYNCLY
        world?.run {
            val location = Location(this, loc.x!!.toDouble(), loc.y!!.toDouble(), loc.z!!.toDouble())
            paperPlayer.pPlayer.teleport(location)
        }
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