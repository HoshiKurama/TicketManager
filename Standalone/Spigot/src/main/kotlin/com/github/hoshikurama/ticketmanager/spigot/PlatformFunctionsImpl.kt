package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.api.common.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.common.ticket.ActionLocation
import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.commonse.misc.encodeRequestTP
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.luckperms.api.LuckPermsProvider
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.*
import java.util.logging.Level

typealias OnlinePlayer = CommandSender.Active.OnlinePlayer

class PlatformFunctionsImpl(
    private val adventure: BukkitAudiences,
    private val plugin: SpigotPlugin,
    private val serverName: String?,
): PlatformFunctions {

    override fun massNotify(permission: String, message: Component) {
        val lpUserAdapter = LuckPermsProvider.get().getPlayerAdapter(Player::class.java)
        adventure.console().sendMessage(message)

        Bukkit.getOnlinePlayers()
            .filter { lpUserAdapter.getPermissionData(it).checkPermission(permission).asBoolean() }
            .forEach { adventure.sender(it).sendMessage(message) }
    }

    override fun buildPlayer(uuid: UUID): OnlinePlayer? {
        return Bukkit.getPlayer(uuid)?.run { SpigotPlayer(this, adventure, serverName) }
    }

    override fun getAllOnlinePlayers(): List<OnlinePlayer> {
        return Bukkit.getOnlinePlayers().map { SpigotPlayer(it, adventure, serverName) }
    }

    override fun offlinePlayerNameToUUIDOrNull(name: String): UUID? {
        return Bukkit.getOfflinePlayers().firstOrNull { it.name?.equals(name) ?: false }
            ?.run { uniqueId }
    }

    override fun nameFromUUIDOrNull(uuid: UUID): String? {
        return uuid.run(Bukkit::getOfflinePlayer).name
    }

    override fun teleportToTicketLocSameServer(player: OnlinePlayer, loc: ActionLocation.FromPlayer) {
        val world = Bukkit.getWorld(loc.world)
        val spigotPlayer = player as SpigotPlayer

        world?.run {
            val location = Location(this, loc.x.toDouble(), loc.y.toDouble(), loc.z.toDouble())
            Bukkit.getScheduler().runTask(plugin, Runnable { spigotPlayer.sPlayer.teleport(location) })
        }
    }

    override fun teleportToTicketLocDiffServer(player: OnlinePlayer, loc: ActionLocation.FromPlayer) {
        plugin.server.sendPluginMessage(plugin, Server2Proxy.Teleport.waterfallString(), encodeRequestTP(player, loc))
    }

    override fun relayMessageToProxy(channel: String, encodedMessage: ByteArray) {
        plugin.server.sendPluginMessage(plugin, channel, encodedMessage)
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