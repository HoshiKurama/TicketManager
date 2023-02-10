package com.github.hoshikurama.ticketmanager.sponge

import com.github.hoshikurama.ticketmanager.commonse.LocaleHandler
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.platform.Player
import com.github.hoshikurama.ticketmanager.commonse.platform.Console
import com.github.hoshikurama.ticketmanager.commonse.ticket.Ticket
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.luckperms.api.LuckPerms
import net.luckperms.api.node.NodeType
import net.luckperms.api.node.types.InheritanceNode
import org.spongepowered.api.Sponge
import org.spongepowered.plugin.builtin.jvm.Plugin
import java.util.logging.Logger

class SpongePlayer(
    private val sPlayer: org.spongepowered.api.entity.living.player.Player,
    private val lp: LuckPerms,
    localeHandler: LocaleHandler,
    serverName: String?,
) : Player(
    name = sPlayer.name(),
    serverName = serverName,
    uniqueID = sPlayer.uniqueId(),
    locale = localeHandler.getOrDefault(sPlayer.locale().toString()),
    permissionGroups = lp.toLPPlayerOnline(sPlayer)
        .getNodes(NodeType.INHERITANCE)
        .map(InheritanceNode::getGroupName),
) {

    override fun sendMessage(msg: String) {
        msg.parseMiniMessage().run(::sendMessage)
    }

    override fun sendMessage(component: Component) {
        sPlayer.sendMessage(component)
    }

    override fun has(permission: String): Boolean {
        return lp.toLPPlayerOnline(sPlayer).cachedData.permissionData.checkPermission(permission).asBoolean()
    }

    override fun getLocAsTicketLoc(): Ticket.TicketLocation {
        return sPlayer.location().run { Ticket.TicketLocation(serverName, "world", blockX(), blockY(), blockZ()) } // No multi-world support on Sponge
    }

    class SpongeConsole(
        locale: TMLocale,
    ) : Console(locale, "world") {
        override fun sendMessage(msg: String) {
            msg.parseMiniMessage().run(::sendMessage)
        }

        override fun sendMessage(component: Component) {
            Sponge.systemSubject().sendMessage(component)
        }

        override fun getLocAsTicketLoc(): Ticket.TicketLocation {
            return Ticket.TicketLocation(serverName, null, null, null, null)
        }
    }
}