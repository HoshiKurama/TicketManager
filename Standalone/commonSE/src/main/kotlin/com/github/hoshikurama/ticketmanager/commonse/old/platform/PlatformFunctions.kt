package com.github.hoshikurama.ticketmanager.commonse.old.platform

import com.github.hoshikurama.ticketmanager.commonse.LocaleHandler
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.ticket.Ticket
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import java.util.*

interface PlatformFunctions {
    fun massNotify(localeHandler: LocaleHandler, permission: String, localeMsg: (TMLocale) -> Component)
    fun buildPlayer(uuid: UUID, localeHandler: LocaleHandler): OnlinePlayer?
    fun getAllOnlinePlayers(localeHandler: LocaleHandler): List<OnlinePlayer>
    fun offlinePlayerNameToUUIDOrNull(name: String): UUID?
    fun nameFromUUID(uuid: UUID): String
    fun teleportToTicketLocSameServer(player: OnlinePlayer, loc: Ticket.TicketLocation)
    fun teleportToTicketLocDiffServer(player: OnlinePlayer, loc: Ticket.TicketLocation)
    fun relayMessageToProxy(channel: String, encodedMessage: ByteArray)

    // Console Messages
    fun getConsoleAudience(): Audience
    fun pushInfoToConsole(message: String)
    fun pushWarningToConsole(message: String)
    fun pushErrorToConsole(message: String)

    // Tab Complete Functions:
    fun getOnlineSeenPlayerNames(sender: Sender): List<String>
    fun getWorldNames(): List<String>
}