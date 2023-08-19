package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.api.common.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.common.ticket.ActionLocation
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component

class SpigotPlayer(
    internal val sPlayer: org.bukkit.entity.Player,
    private val adventure: BukkitAudiences,
    override val serverName: String?,
) : CommandSender.Active.OnlinePlayer(sPlayer.name, sPlayer.uniqueId) {

    override fun getLocAsTicketLoc(): ActionLocation {
        return sPlayer.location.run { ActionLocation.FromPlayer(serverName, world!!.name, blockX, blockY, blockZ) }
    }

    override fun sendMessage(component: Component) = adventure.player(sPlayer).sendMessage(component)
}

class SpigotConsole(
    private val adventure: BukkitAudiences,
    override val serverName: String?,
) : CommandSender.Active.OnlineConsole() {
    override fun sendMessage(component: Component) = adventure.console().sendMessage(component)
}