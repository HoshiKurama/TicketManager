package com.github.hoshikurama.ticketmanager.commonse.platform

import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.ticket.Ticket
import net.kyori.adventure.text.Component
import java.util.*

sealed class Sender(
    val name: String,
    val locale: TMLocale,
    val serverName: String?,
) {
    fun sendMessage(msg: String) = sendMessage(msg.parseMiniMessage())
    abstract fun sendMessage(component: Component)
    abstract fun has(permission: String): Boolean

    fun toUUIDOrNull() = if (this is Player) uniqueID else null
    abstract fun getLocAsTicketLoc(): Ticket.TicketLocation
}

abstract class Player(
    val uniqueID: UUID,
    val permissionGroups: List<String>,
    name: String,
    locale: TMLocale,
    serverName: String?,
) : Sender(name, locale, serverName)

abstract class Console(locale: TMLocale, serverName: String?) : Sender(locale.consoleName, locale, serverName) {
    override fun has(permission: String): Boolean = true
    override fun getLocAsTicketLoc(): Ticket.TicketLocation = Ticket.TicketLocation(serverName, null, null, null, null)
}
