package com.github.hoshikurama.ticketmanager.api.commands

import com.github.hoshikurama.ticketmanager.api.ticket.TicketCreationLocation
import com.github.hoshikurama.ticketmanager.commonse.datas.GlobalState
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import net.kyori.adventure.text.Component
import net.luckperms.api.LuckPermsProvider
import java.util.*

/**
 * Represents anything in TicketManager that either has or is executing a command.
 * CommandCapable has two sub-interfaces:
 * - Info
 * - Active
 */
sealed interface CommandSender {

    /**
     * Represents information about a known command origin, like its name and type. Additionally,
     * implementors can serialize to a String. This is particularly useful for information sent
     * across a proxy where simplicity and bare information are required.
     *
     * For more complete functionality, see the CommandCapable.Active interface.
     */
    sealed interface Info : CommandSender {
        val username: String

        /**
         * Represents basic player information like username and uuid.
         */
        open class Player(final override val username: String, val uuid: UUID): Info

        /**
         * Represents basic information for a console sender.
         */
        open class Console() : Info {
            final override val username: String
                get() = GlobalState.activeLocale.consoleName
        }
    }


    /**
     * This interface represents an active connection to either a player or to Console.
     * Thus, implementers gain the following functionality:
     * - Sending messages in-game
     * - Acquiring an object representing in-world location (if applicable)
     * - Checking for permissions
     *
     * Implementers additionally may be treated as an info object and serialized for proxy usage.
     * However, only the information outlined in the CommandCapable.Info.X interface will be kept.
     */
    sealed interface Active : Info, CommandSender {
        val serverName: String?

        fun sendMessage(msg: String) = sendMessage(msg.parseMiniMessage())
        fun getLocAsTicketLoc(): TicketCreationLocation
        fun sendMessage(component: Component)
        fun has(permission: String): Boolean


        /**
         * Represents an Online Player. Implementations will be built during command execution.
         */
        abstract class OnlinePlayer(
            username: String,
            uuid: UUID,
            final override val serverName: String?,
        ): Active, Info.Player(username, uuid) {
            private val lpUser = LuckPermsProvider.get().userManager.getUser(uuid)!!
            val permissionGroups: List<String> = lpUser.getInheritedGroups(lpUser.queryOptions).map { it.name }

            final override fun has(permission: String): Boolean = lpUser.cachedData
                .permissionData
                .checkPermission(permission)
                .asBoolean()
        }

        /**
         * Actively represents Console. Implementations will be built during command execution.
         */
        abstract class OnlineConsole(final override val serverName: String?) : Active, Info.Console() {
            final override fun has(permission: String): Boolean = true
            final override fun getLocAsTicketLoc() = TicketCreationLocation.FromConsole(serverName)
        }
    }
}
