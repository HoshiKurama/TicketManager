package com.github.hoshikurama.ticketmanager.paper.old

import com.github.hoshikurama.componentDSL.formattedContent
import com.github.hoshikurama.ticketmanager.LocaleHandler
import com.github.hoshikurama.ticketmanager.TMLocale
import com.github.hoshikurama.ticketmanager.platform.Console
import com.github.hoshikurama.ticketmanager.platform.Player
import com.github.hoshikurama.ticketmanager.ticket.BasicTicket
import net.kyori.adventure.extra.kotlin.text
import net.kyori.adventure.text.Component
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit

class PaperPlayer(
    internal val pPlayer: org.bukkit.entity.Player,
    private val perms: Permission,
    localeHandler: LocaleHandler
) : Player(
    uniqueID = pPlayer.uniqueId,
    permissionGroups = perms.getPlayerGroups(pPlayer).toList(),
    name = pPlayer.name,
    locale = localeHandler.getOrDefault(pPlayer.locale().toString()),
) {
    override fun getTicketLocFromCurLoc(): BasicTicket.TicketLocation {
       return pPlayer.location.run { BasicTicket.TicketLocation(world.name, blockX, blockY, blockZ) }
    }

    override fun sendMessage(msg: String) {
        text { formattedContent(msg) }
            .run(::sendMessage)
    }

    override fun sendMessage(component: Component) {
        pPlayer.sendMessage(component)
    }

    override fun has(permission: String): Boolean {
        return perms.has(pPlayer, permission)
    }
}

class PaperConsole(
    locale: TMLocale
) : Console(locale) {
    override fun sendMessage(msg: String) {
        text { formattedContent(msg) }
            .run(::sendMessage)
    }

    override fun sendMessage(component: Component) {
        Bukkit.getConsoleSender().sendMessage(component)
    }
}