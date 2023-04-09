package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.commonse.LocaleHandler
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.old.platform.OnlinePlayer
import com.github.hoshikurama.ticketmanager.commonse.old.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.old.platform.Sender
import com.github.hoshikurama.ticketmanager.commonse.ticket.Ticket
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.*
import java.util.logging.Level

class PlatformFunctionsImpl(
    private val adventure: BukkitAudiences,
    private val plugin: SpigotPlugin,
): PlatformFunctions {

    override fun massNotify(localeHandler: LocaleHandler, permission: String, localeMsg: (TMLocale) -> Component) {
        adventure.console().sendMessage(localeMsg(localeHandler.consoleLocale))

        Bukkit.getOnlinePlayers()
            .map { SpigotPlayer(it, adventure, localeHandler) }
            .filter { it.has(permission) }
            .forEach { localeMsg(it.locale).run(it::sendMessage) }
    }

    override fun buildPlayer(uuid: UUID, localeHandler: LocaleHandler): OnlinePlayer? {
        return Bukkit.getPlayer(uuid)?.run { SpigotPlayer(this, adventure, localeHandler) }
    }

    override fun getAllOnlinePlayers(localeHandler: LocaleHandler): List<OnlinePlayer> {
        return Bukkit.getOnlinePlayers().map { SpigotPlayer(it, adventure, localeHandler) }
    }

    override fun offlinePlayerNameToUUIDOrNull(name: String): UUID? {
        return Bukkit.getOfflinePlayers().firstOrNull { it.name?.equals(name) ?: false }
            ?.run { uniqueId }
    }

    override fun nameFromUUID(uuid: UUID): String {
        return uuid.run(Bukkit::getOfflinePlayer).name ?: "UUID"
    }

    override fun teleportToTicketLocSameServer(player: OnlinePlayer, loc: Ticket.TicketLocation) {
        val world = Bukkit.getWorld(loc.world!!)
        val spigotPlayer = player as SpigotPlayer

        world?.run {
            val location = Location(this, loc.x!!.toDouble(), loc.y!!.toDouble(), loc.z!!.toDouble())
            Bukkit.getScheduler().runTask(plugin, Runnable { spigotPlayer.sPlayer.teleport(location) })
        }
    }

    override fun teleportToTicketLocDiffServer(player: OnlinePlayer, loc: Ticket.TicketLocation) {
        throw Exception("Proxies with Spigot NOT Supported! Please use Paper for Proxy Support")
    }

    override fun relayMessageToProxy(channel: String, encodedMessage: ByteArray) {
        throw Exception("Proxies with Spigot NOT Supported! Please use Paper for Proxy Support")
    }

    override fun getConsoleAudience(): Audience {
        return adventure.console()
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

    override fun getOnlineSeenPlayerNames(sender: Sender): List<String> {
        return if (sender is SpigotPlayer) {
            val player = sender.sPlayer
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