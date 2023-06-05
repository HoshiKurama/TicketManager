package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.implapi.ticket.TicketInterface
import com.github.hoshikurama.ticketmanager.commonse.LocaleHandler
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.commands.Console
import com.github.hoshikurama.ticketmanager.commonse.commands.OnlinePlayer
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
    override fun getLocAsTicketLoc(): TicketInterface.CreationLocation {
        return sPlayer.location.run { TicketInterface.TicketLocation(null, world?.name, blockX, blockY, blockZ) }
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