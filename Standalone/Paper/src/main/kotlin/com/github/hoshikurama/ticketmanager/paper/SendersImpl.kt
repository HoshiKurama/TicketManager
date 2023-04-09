package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.commonse.LocaleHandler
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.old.platform.Console
import com.github.hoshikurama.ticketmanager.commonse.old.platform.OnlinePlayer
import com.github.hoshikurama.ticketmanager.commonse.ticket.Ticket
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit

class PaperPlayer(
    internal val pPlayer: org.bukkit.entity.Player,
    localeHandler: LocaleHandler,
    serverName: String?,
) : OnlinePlayer(
    serverName = serverName,
    uniqueID = pPlayer.uniqueId,
    name = pPlayer.name,
    locale = localeHandler.getOrDefault(pPlayer.locale().toString()),
) {
    override fun getLocAsTicketLoc(): Ticket.TicketLocation {
        return pPlayer.location.run { Ticket.TicketLocation(serverName, world.name, blockX, blockY, blockZ) }
    }

    override fun sendMessage(component: Component) = pPlayer.sendMessage(component)
}

class PaperConsole(
    locale: TMLocale,
    serverName: String?,
) : Console(locale, serverName) {
    override fun sendMessage(component: Component) = Bukkit.getConsoleSender().sendMessage(component)
}