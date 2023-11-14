package com.github.hoshikurama.ticketmanager.fabric.server.impls

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.ticket.ActionLocation
import com.github.hoshikurama.ticketmanager.fabric.server.PlayerNameUUIDStorage
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.kyori.adventure.text.Component
import net.minecraft.server.MinecraftServer
import java.util.*

class PlatformFunctionsImpl(
    private val audience: FabricServerAudiences,
    private val permissions: Permission,
    private val playerCache: PlayerNameUUIDStorage,
    private val minecraftServer: MinecraftServer,
    config: Config,
) : PlatformFunctions {
    private val serverName = config.proxyOptions?.serverName

    override fun buildPlayer(uuid: UUID): CommandSender.OnlinePlayer? {
        return FabricPlayer(minecraftServer.playerManager.getPlayer(uuid)!!, serverName)
    }

    override fun getAllOnlinePlayers(): List<CommandSender.OnlinePlayer> {
        return minecraftServer.playerManager.playerList.map { FabricPlayer(it, serverName) }
    }

    override fun getConsoleAudience(): Audience {
        return audience.console()
    }

    override fun getOnlineSeenPlayerNames(sender: CommandSender.Active): List<String> {
        return minecraftServer.playerNames.toList()
    }

    override fun getWorldNames(): List<String> {
        return minecraftServer.worlds.map { it.registryKey.registry.namespace } //TODO NOTE THIS IN DOCUMENTATION
    }

    override fun massNotify(permission: String, message: Component) {
        getConsoleAudience().sendMessage(message)

        getAllOnlinePlayers()
            .filter { permissions.has(it, permission) }
            .forEach { it.sendMessage(message) }
    }

    override fun nameFromUUIDOrNull(uuid: UUID): String? {
        return playerCache.nameOrNull(uuid)
    }

    override fun offlinePlayerNameToUUIDOrNull(name: String): UUID? {
        return playerCache.uuidOrNull(name)
    }

    override fun pushErrorToConsole(message: String) = println(message)
    override fun pushInfoToConsole(message: String) = println(message)
    override fun pushWarningToConsole(message: String) = println(message)

    override fun teleportToTicketLocDiffServer(player: CommandSender.OnlinePlayer, loc: ActionLocation.FromPlayer) {
        TODO("Not yet implemented")
    }

    override fun teleportToTicketLocSameServer(player: CommandSender.OnlinePlayer, loc: ActionLocation.FromPlayer) {
        TODO("Not yet implemented")
    }
}