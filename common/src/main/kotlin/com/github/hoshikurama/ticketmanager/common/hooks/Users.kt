package com.github.hoshikurama.ticketmanager.common.hooks

import com.github.hoshikurama.ticketmanager.common.TMLocale
import com.github.hoshikurama.ticketmanager.common.ticket.BasicTicket
import net.kyori.adventure.text.Component
import java.util.*

sealed class Sender {
    abstract val name: String
    abstract val locale: TMLocale

    abstract fun sendMessage(msg: String)
    abstract fun sendMessage(component: Component)
    abstract fun has(permission: String): Boolean
}

abstract class Player : Sender() {
    abstract val uniqueID: UUID
    abstract val location: BasicTicket.TicketLocation
    abstract val permissionGroups: List<String>
}

abstract class Console : Sender() {
    override fun has(permission: String) = true
}

fun Sender.toUUIDOrNull() = if (this is Player) uniqueID else null