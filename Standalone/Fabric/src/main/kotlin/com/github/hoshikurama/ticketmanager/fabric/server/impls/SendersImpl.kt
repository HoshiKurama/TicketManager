package com.github.hoshikurama.ticketmanager.fabric.server.impls

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.ticket.ActionLocation
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.kyori.adventure.text.Component
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

class FabricPlayer(
    internal val fPlayer: ServerPlayerEntity,
    override val serverName: String?,
) : CommandSender.OnlinePlayer {
    override val username: String = fPlayer.gameProfile.name
    override val uuid: UUID = fPlayer.uuid

    override fun getLocAsTicketLoc(): ActionLocation {
        return fPlayer.run { ActionLocation.FromPlayer(serverName, world.asString(), blockX, blockY, blockZ) }
    }

    override fun sendMessage(component: Component) = fPlayer.sendMessage(component)
}

class FabricConsole(
    override val serverName: String?,
    private val adventure: FabricServerAudiences,
): CommandSender.OnlineConsole {
    override fun sendMessage(component: Component) = adventure.console().sendMessage(component)
}