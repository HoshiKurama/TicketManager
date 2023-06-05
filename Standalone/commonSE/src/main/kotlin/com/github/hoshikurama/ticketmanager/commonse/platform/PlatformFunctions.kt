package com.github.hoshikurama.ticketmanager.commonse.platform

import com.github.hoshikurama.ticketmanager.api.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.ticket.ActionLocation
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import java.util.*

typealias OnlinePlayer = CommandSender.Active.OnlinePlayer

interface PlatformFunctions {
    fun massNotify(permission: String, message: Component)
    fun buildPlayer(uuid: UUID): OnlinePlayer?
    fun getAllOnlinePlayers(): List<OnlinePlayer>
    fun offlinePlayerNameToUUIDOrNull(name: String): UUID?
    fun nameFromUUIDOrNull(uuid: UUID): String?
    fun teleportToTicketLocSameServer(player: OnlinePlayer, loc: ActionLocation.FromPlayer)
    fun teleportToTicketLocDiffServer(player: OnlinePlayer, loc: ActionLocation.FromPlayer)
    fun relayMessageToProxy(channel: String, encodedMessage: ByteArray)

    // Console Messages
    fun getConsoleAudience(): Audience
    fun pushInfoToConsole(message: String)
    fun pushWarningToConsole(message: String)
    fun pushErrorToConsole(message: String)

    // Tab Complete Functions:
    fun getOnlineSeenPlayerNames(sender: CommandSender.Active): List<String>
    fun getWorldNames(): List<String>
}