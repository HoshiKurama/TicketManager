package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.LocaleHandler
import com.github.hoshikurama.ticketmanager.TMLocale
import com.github.hoshikurama.ticketmanager.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.platform.Console
import com.github.hoshikurama.ticketmanager.platform.Player
import com.github.hoshikurama.ticketmanager.ticket.Ticket
import net.kyori.adventure.text.Component
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit

class PaperPlayer(
    internal val pPlayer: org.bukkit.entity.Player,
    private val perms: Permission,
    localeHandler: LocaleHandler,
    serverName: String?,
) : Player(
    serverName = serverName,
    uniqueID = pPlayer.uniqueId,
    permissionGroups = perms.getPlayerGroups(pPlayer).toList(),
    name = pPlayer.name,
    locale = localeHandler.getOrDefault(pPlayer.locale().toString()),
) {
    override fun getLocAsTicketLoc(): Ticket.TicketLocation {
       return pPlayer.location.run { Ticket.TicketLocation(serverName, world.name, blockX, blockY, blockZ) }
    }

    override fun sendMessage(msg: String) {
        msg.parseMiniMessage().run(::sendMessage)
    }

    override fun sendMessage(component: Component) {
        pPlayer.sendMessage(component)
    }

    override fun has(permission: String): Boolean {
        return perms.has(pPlayer, permission)
    }
}

class PaperConsole(
    locale: TMLocale,
    serverName: String?,
) : Console(locale, serverName) {

    override fun sendMessage(msg: String) {
        msg.parseMiniMessage().run(::sendMessage)
    }

    override fun sendMessage(component: Component) {
        Bukkit.getConsoleSender().sendMessage(component)
    }

    override fun getLocAsTicketLoc(): Ticket.TicketLocation {
        return Ticket.TicketLocation(serverName, null, null, null, null)
    }
}