package com.github.hoshikurama.ticketmanager.velocity

/*
NOTE: THIS IS FOR EVENTUAL PURE VELOCITY PLUGIN

import com.github.hoshikurama.ticketmanager.LocaleHandler
import com.github.hoshikurama.ticketmanager.TMLocale
import com.github.hoshikurama.ticketmanager.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.misc.unwrapOrNull
import com.github.hoshikurama.ticketmanager.platform.Console
import com.github.hoshikurama.ticketmanager.platform.Player
import com.github.hoshikurama.ticketmanager.ticket.Ticket
import com.velocitypowered.api.proxy.server.RegisteredServer
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.context.DefaultContextKeys
import net.luckperms.api.context.ImmutableContextSet
import net.luckperms.api.model.user.User
import net.luckperms.api.node.NodeType
import net.luckperms.api.node.types.InheritanceNode
import java.util.*

class VelocityPlayer(
    internal val vPlayer: com.velocitypowered.api.proxy.Player,
    private val lpUser: User?,
    uniqueID: UUID,
    permissionGroup: List<String>,
    name: String,
    locale: TMLocale,
): Player(
    uniqueID = uniqueID,
    permissionGroups = permissionGroup,
    name = name,
    locale = locale,
) {
    companion object {
        fun build(vPlayer: com.velocitypowered.api.proxy.Player, localeHandler: LocaleHandler): VelocityPlayer {
            val luckperms = LuckPermsProvider.get()

            val lpUser: User = luckperms.getPlayerAdapter(com.velocitypowered.api.proxy.Player::class.java).getUser(vPlayer)
            val lpContext: ImmutableContextSet? = luckperms.contextManager.getContext(lpUser).unwrapOrNull()

            val curServerValue = lpContext?.getAnyValue(DefaultContextKeys.SERVER_KEY)?.unwrapOrNull()
            val curWorldValue = lpContext?.getAnyValue(DefaultContextKeys.WORLD_KEY)?.unwrapOrNull()

            val curServerGroups = lpUser.getNodes(NodeType.INHERITANCE)
                .filter { it.contexts.getAnyValue(DefaultContextKeys.SERVER_KEY).unwrapOrNull()?.equals(curServerValue) ?: false /* Filters out nodes for other servers */ }
                .filter { it.contexts.getAnyValue(DefaultContextKeys.WORLD_KEY).unwrapOrNull()?.equals(curWorldValue) ?: false /* Filters out nodes for other worlds */ }
                .map(InheritanceNode::getGroupName)

            val locale = localeHandler.getOrDefault(vPlayer.effectiveLocale?.toString() ?: "")

            return VelocityPlayer(vPlayer, lpUser, vPlayer.uniqueId, curServerGroups, vPlayer.username, locale)
        }

        fun buildForSuggest(vPlayer: com.velocitypowered.api.proxy.Player, localeHandler: LocaleHandler): VelocityPlayer {
            val locale = localeHandler.getOrDefault(vPlayer.effectiveLocale?.toString() ?: "")
            return VelocityPlayer(vPlayer, null, vPlayer.uniqueId, listOf(), "", locale)
        }
    }

    override fun getTicketLocFromCurLoc(): Ticket.TicketLocation = throw Exception("UNSUPPORTED OPERATION FOR VELOCITY")

    override fun sendMessage(msg: String) {
        msg.parseMiniMessage().run(::sendMessage)
    }

    override fun sendMessage(component: Component) {
        vPlayer.sendMessage(component)
    }

    override fun has(permission: String): Boolean = lpUser!!.cachedData.permissionData.checkPermission(permission).asBoolean()
}

 class VelocityConsole(locale: TMLocale, private val audience: Audience, private val server: RegisteredServer): Console(locale) {
     override fun getServerName(): String = server.serverInfo.name

     override fun sendMessage(msg: String) {
         msg.parseMiniMessage().run(::sendMessage)
     }

     override fun sendMessage(component: Component) {
         audience.sendMessage(component)
     }
 }
 */