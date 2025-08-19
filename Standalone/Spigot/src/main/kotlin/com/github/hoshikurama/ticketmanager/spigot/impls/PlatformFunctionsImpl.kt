package com.github.hoshikurama.ticketmanager.spigot.impls

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.ticket.ActionLocation
import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.commonse.misc.encodeRequestTP
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.plugin.Plugin
import java.util.*
import java.util.logging.Level

class PlatformFunctionsImpl(
    private val adventure: BukkitAudiences,
    private val plugin: Plugin,
    private val permissions: Permission,
    config: Config,
): PlatformFunctions {
    private val serverName = config.proxyOptions?.serverName

    override fun massNotify(permission: String, message: Component) {
        adventure.console().sendMessage(message)

        getAllOnlinePlayers()
            .filter { permissions.has(it, permission) }
            .forEach { it.sendMessage(message) }
    }

    override fun buildPlayer(uuid: UUID): CommandSender.OnlinePlayer? {
        return Bukkit.getPlayer(uuid)?.run { SpigotPlayer(this, adventure, serverName) }
    }

    override fun getAllOnlinePlayers(): List<CommandSender.OnlinePlayer> {
        return Bukkit.getOnlinePlayers().map { SpigotPlayer(it, adventure, serverName) }
    }

    override fun offlinePlayerNameToUUIDOrNull(name: String): UUID? {
        return Bukkit.getOfflinePlayers().firstOrNull { it.name?.equals(name) ?: false }
            ?.run { uniqueId }
    }

    override fun nameFromUUIDOrNull(uuid: UUID): String? {
        return uuid.run(Bukkit::getOfflinePlayer).name
    }

    override fun teleportToTicketLocSameServer(player: CommandSender.OnlinePlayer, loc: ActionLocation.FromPlayer) {
        val world = Bukkit.getWorld(loc.world)
        val spigotPlayer = player as SpigotPlayer

        world?.run {
            val location = Location(this, loc.x.toDouble(), loc.y.toDouble(), loc.z.toDouble())
            Bukkit.getScheduler().runTask(plugin, Runnable { spigotPlayer.sPlayer.teleport(location) })
        }
    }

    override fun teleportToTicketLocDiffServer(player: CommandSender.OnlinePlayer, loc: ActionLocation.FromPlayer) {
        plugin.server.sendPluginMessage(plugin, Server2Proxy.Teleport.waterfallString(), encodeRequestTP(player, loc))
    }

    override fun getConsoleAudience(): Audience {
        return adventure.console()
    }

    override fun pushInfoToConsole(message: String) {
        plugin.logger.log(Level.INFO, message)
    }

    override fun pushWarningToConsole(message: String) {
        plugin.logger.log(Level.WARNING, message)
    }

    override fun pushErrorToConsole(message: String) {
        plugin.logger.log(Level.SEVERE, message)
    }

    override fun getOnlineSeenPlayerNames(sender: CommandSender.Active): List<String> {
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