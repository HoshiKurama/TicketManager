package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.commonse.LocaleHandler
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.old.platform.Console
import com.github.hoshikurama.ticketmanager.commonse.old.platform.OnlinePlayer
import com.github.hoshikurama.ticketmanager.commonse.ticket.Ticket
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component

class SpigotPlayer(
    internal val sPlayer: org.bukkit.entity.Player,
    private val adventure: BukkitAudiences,
    localeHandler: LocaleHandler,
) : OnlinePlayer(
    uniqueID = sPlayer.uniqueId,
    name = sPlayer.name,
    locale = localeHandler.getOrDefault(sPlayer.locale),
    serverName = null,
) {
    override fun getLocAsTicketLoc(): Ticket.TicketLocation {
        return sPlayer.location.run { Ticket.TicketLocation(null, world?.name, blockX, blockY, blockZ) }
    }

    override fun sendMessage(component: Component) {
        adventure.player(sPlayer).sendMessage(component)
    }
}

class SpigotConsole(
    private val adventure: BukkitAudiences,
    locale: TMLocale,
): Console(locale, null) {

    override fun sendMessage(component: Component) {
        adventure.console().sendMessage(component)
    }
}