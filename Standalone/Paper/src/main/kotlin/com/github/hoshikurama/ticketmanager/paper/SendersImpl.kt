package com.github.hoshikurama.ticketmanager.paper


import com.github.hoshikurama.ticketmanager.api.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.ticket.ActionLocation
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit

class PaperPlayer(
    internal val pPlayer: org.bukkit.entity.Player,
    override val serverName: String?,
) : CommandSender.Active.OnlinePlayer(pPlayer.name, pPlayer.uniqueId) {

    override fun getLocAsTicketLoc(): ActionLocation {
        return pPlayer.location.run { ActionLocation.FromPlayer(serverName, world.name, blockX, blockY, blockZ) }
    }

    override fun sendMessage(component: Component) = pPlayer.sendMessage(component)
}

class PaperConsole(
    override val serverName: String?,
) : CommandSender.Active.OnlineConsole {
    override fun sendMessage(component: Component) = Bukkit.getConsoleSender().sendMessage(component)
}