package com.github.hoshikurama.ticketmanager.paper.impls

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.ticket.ActionLocation
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.plugin.Plugin
import java.util.*

class PlatformFunctionsImpl(
    private val plugin: Plugin,
    private val permissions: Permission,
    config: Config,
) : PlatformFunctions {
    private val serverName = config.proxyOptions?.serverName

    override fun massNotify(permission: String, message: Component) {
        Bukkit.getConsoleSender().sendMessage(message)

        getAllOnlinePlayers()
            .filter { permissions.has(it, permission) }
            .forEach { it.sendMessage(message) }
    }

    override fun buildPlayer(uuid: UUID): CommandSender.OnlinePlayer? {
        return Bukkit.getPlayer(uuid)?.run { PaperPlayer(this, serverName) }
    }

    override fun getAllOnlinePlayers(): List<CommandSender.OnlinePlayer> {
        return Bukkit.getOnlinePlayers().map { PaperPlayer(it, serverName) }
    }

    override fun offlinePlayerNameToUUIDOrNull(name: String): UUID? {
        return Bukkit.getOfflinePlayers().firstOrNull { it.name?.equals(name) ?: false }
            ?.run { uniqueId }
    }

    override fun nameFromUUIDOrNull(uuid: UUID): String? {
        return uuid.run(Bukkit::getOfflinePlayer).name
    }

    override fun teleportToTicketLocSameServer(player: CommandSender.OnlinePlayer, loc: ActionLocation.FromPlayer) {
        val location = Location(Bukkit.getWorld(loc.world), loc.x.toDouble(), loc.y.toDouble(), loc.z.toDouble())
        Bukkit.getScheduler().runTask(plugin, Runnable { (player as PaperPlayer).pPlayer.teleport(location) })
    }

    override fun getConsoleAudience(): Audience {
        return Bukkit.getConsoleSender()
    }

    override fun pushInfoToConsole(message: String) {
        plugin.slF4JLogger.atInfo().setMessage(message).log()
    }

    override fun pushWarningToConsole(message: String) {
        plugin.slF4JLogger.atWarn().setMessage(message).log()
    }

    override fun pushErrorToConsole(message: String) {
        plugin.slF4JLogger.atError().setMessage(message).log()
    }

    override fun getOnlineSeenPlayerNames(sender: CommandSender.Active): List<String> {
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

