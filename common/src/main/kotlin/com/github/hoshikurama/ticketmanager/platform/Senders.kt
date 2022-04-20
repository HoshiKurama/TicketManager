package com.github.hoshikurama.ticketmanager.platform

import com.github.hoshikurama.ticketmanager.TMLocale
import com.github.hoshikurama.ticketmanager.ticket.Ticket
import net.kyori.adventure.text.Component
import java.util.*

sealed class Sender(
    val name: String,
    val locale: TMLocale,
    val serverName: String?,
) {
    abstract fun sendMessage(msg: String)
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
}
