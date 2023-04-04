package com.github.hoshikurama.ticketmanager.commonse.platform

import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.ticket.Ticket
import com.github.hoshikurama.ticketmanager.commonse.ticket.User
import net.kyori.adventure.text.Component
import net.luckperms.api.LuckPermsProvider
import java.lang.Exception
import java.util.*

sealed class Sender(
    val name: String,
    val locale: TMLocale,
    val serverName: String?,
) {
    fun sendMessage(msg: String) = sendMessage(msg.parseMiniMessage())
    fun toUUIDOrNull() = if (this is OnlinePlayer) uniqueID else null

    abstract fun getLocAsTicketLoc(): Ticket.TicketLocation
    abstract fun sendMessage(component: Component)
    abstract fun has(permission: String): Boolean
}

abstract class OnlinePlayer(
    val uniqueID: UUID,
    name: String,
    locale: TMLocale,
    serverName: String?,
) : Sender(name, locale, serverName) {
    private val lpUser = LuckPermsProvider.get().userManager.getUser(uniqueID)!!
    val permissionGroups: List<String> = lpUser.getInheritedGroups(lpUser.queryOptions).map { it.name }
    final override fun has(permission: String): Boolean = lpUser.cachedData
        .permissionData
        .checkPermission(permission)
        .asBoolean()
}

abstract class Console(locale: TMLocale, serverName: String?) : Sender(locale.consoleName, locale, serverName) {
    final override fun has(permission: String): Boolean = true
    final override fun getLocAsTicketLoc(): Ticket.TicketLocation = Ticket.TicketLocation(serverName, null, null, null, null)
}
