package com.github.hoshikurama.ticketmanager.paper


import com.github.hoshikurama.ticketmanager.api.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.ticket.TicketCreationLocation
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit

class PaperPlayer(
    internal val pPlayer: org.bukkit.entity.Player,
    serverName: String?,
) : CommandSender.Active.OnlinePlayer(
    serverName = serverName,
    uuid = pPlayer.uniqueId,
    username = pPlayer.name,
) {
    override fun getLocAsTicketLoc(): TicketCreationLocation {
        return pPlayer.location.run { TicketCreationLocation.FromPlayer(serverName, world.name, blockX, blockY, blockZ) }
    }

    override fun sendMessage(component: Component) = pPlayer.sendMessage(component)
}

class PaperConsole(
    serverName: String?,
) : CommandSender.Active.OnlineConsole(serverName) {
    override fun sendMessage(component: Component) = Bukkit.getConsoleSender().sendMessage(component)
}