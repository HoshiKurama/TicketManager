package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.LocaleHandler
import com.github.hoshikurama.ticketmanager.TMLocale
import com.github.hoshikurama.ticketmanager.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.platform.Console
import com.github.hoshikurama.ticketmanager.platform.Player
import com.github.hoshikurama.ticketmanager.ticket.BasicTicket
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.milkbowl.vault.permission.Permission

class SpigotPlayer(
    internal val sPlayer: org.bukkit.entity.Player,
    private val perms: Permission,
    private val adventure: BukkitAudiences,
    localeHandler: LocaleHandler,
) : Player(
    uniqueID = sPlayer.uniqueId,
    permissionGroups = perms.getPlayerGroups(sPlayer).toList(),
    name = sPlayer.name,
    locale = localeHandler.getOrDefault(sPlayer.locale),
) {
    override fun getTicketLocFromCurLoc(): BasicTicket.TicketLocation {
        return sPlayer.location.run { BasicTicket.TicketLocation(world!!.name, blockX, blockY, blockZ) }
    }

    override fun sendMessage(msg: String) {
        msg.parseMiniMessage().run(::sendMessage)
    }

    override fun sendMessage(component: Component) {
        adventure.player(sPlayer).sendMessage(component)
    }

    override fun has(permission: String): Boolean {
        return perms.has(sPlayer, permission)
    }
}

class SpigotConsole(
    private val adventure: BukkitAudiences,
    locale: TMLocale,
): Console(locale) {

    override fun sendMessage(msg: String) {
        msg.parseMiniMessage().run(::sendMessage)
    }

    override fun sendMessage(component: Component) {
        adventure.console().sendMessage(component)
    }
}