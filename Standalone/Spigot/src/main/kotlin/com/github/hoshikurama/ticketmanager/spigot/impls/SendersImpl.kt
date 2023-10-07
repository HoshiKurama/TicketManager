package com.github.hoshikurama.ticketmanager.spigot.impls

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.ticket.ActionLocation
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component

class SpigotPlayer(
    internal val sPlayer: org.bukkit.entity.Player,
    private val adventure: BukkitAudiences,
    override val serverName: String?,
): CommandSender.OnlinePlayer {
    override val username = sPlayer.name
    override val uuid = sPlayer.uniqueId

    override fun getLocAsTicketLoc(): ActionLocation {
        return sPlayer.location.run { ActionLocation.FromPlayer(serverName, world!!.name, blockX, blockY, blockZ) }
    }

    override fun sendMessage(component: Component) {
        adventure.player(sPlayer).sendMessage(component)
    }
}

class SpigotConsole(
    private val adventure: BukkitAudiences,
    override val serverName: String?,
): CommandSender.OnlineConsole {
    override fun sendMessage(component: Component) {
        adventure.console().sendMessage(component)
    }
}