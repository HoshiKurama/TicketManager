package com.github.hoshikurama.ticketmanager.paper.impls


import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.ticket.ActionLocation
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit

class PaperPlayer(
    internal val pPlayer: org.bukkit.entity.Player,
    override val serverName: String?,
) : CommandSender.OnlinePlayer {
    override val username = pPlayer.name
    override val uuid = pPlayer.uniqueId

    override fun getLocAsTicketLoc(): ActionLocation {
        return pPlayer.location.run { ActionLocation.FromPlayer(serverName, world.name, blockX, blockY, blockZ) }
    }

    override fun sendMessage(component: Component) = pPlayer.sendMessage(component)
}

class PaperConsole(
    override val serverName: String?,
) : CommandSender.OnlineConsole {
    override fun sendMessage(component: Component) = Bukkit.getConsoleSender().sendMessage(component)
}