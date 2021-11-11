package com.github.hoshikurama.ticketmanager.platform


import com.github.hoshikurama.ticketmanager.LocaleHandler
import com.github.hoshikurama.ticketmanager.TMLocale
import com.github.hoshikurama.ticketmanager.ticket.BasicTicket
import net.kyori.adventure.text.Component
import java.util.*

interface PlatformFunctions {
    fun massNotify(localeHandler: LocaleHandler, permission: String, localeMsg: (TMLocale) -> Component)
    fun buildPlayer(uuid: UUID, localeHandler: LocaleHandler): Player?
    fun getOnlinePlayers(localeHandler: LocaleHandler): List<Player>
    fun stripColour(msg: String):  String
    fun offlinePlayerNameToUUIDOrNull(name: String): UUID?
    fun nameFromUUID(uuid: UUID): String
    fun teleportToTicketLocation(player: Player, loc: BasicTicket.TicketLocation)
    suspend fun postModifiedStacktrace(e: Exception, localeHandler: LocaleHandler)

    // Console Messages
    fun pushInfoToConsole(message: String)
    fun pushWarningToConsole(message: String)
    fun pushErrorToConsole(message: String)

    // Tab Complete Functions:
    fun getPermissionGroups(): List<String>
    fun getOfflinePlayerNames(): List<String>
    fun getOnlineSeenPlayerNames(sender: Sender): List<String>
    fun getWorldNames(): List<String>
}