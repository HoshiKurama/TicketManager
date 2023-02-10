package com.github.hoshikurama.ticketmanager.commonse.platform

import com.github.hoshikurama.ticketmanager.commonse.LocaleHandler
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.ticket.Ticket
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import java.util.*

interface PlatformFunctions {
    fun massNotify(localeHandler: LocaleHandler, permission: String, localeMsg: (TMLocale) -> Component)
    fun buildPlayer(uuid: UUID, localeHandler: LocaleHandler): Player?
    fun getAllOnlinePlayers(localeHandler: LocaleHandler): List<Player>
    fun offlinePlayerNameToUUIDOrNull(name: String): UUID?
    fun nameFromUUID(uuid: UUID): String
    fun teleportToTicketLocSameServer(player: Player, loc: Ticket.TicketLocation)
    fun teleportToTicketLocDiffServer(player: Player, loc: Ticket.TicketLocation)
    fun relayMessageToProxy(channel: String, encodedMessage: ByteArray)

    // Console Messages
    fun getConsoleAudience(): Audience
    fun pushInfoToConsole(message: String)
    fun pushWarningToConsole(message: String)
    fun pushErrorToConsole(message: String)

    // Tab Complete Functions:
    fun getPermissionGroups(): List<String>
    fun getOfflinePlayerNames(): List<String>
    fun getOnlineSeenPlayerNames(sender: Sender): List<String>
    fun getWorldNames(): List<String>
}