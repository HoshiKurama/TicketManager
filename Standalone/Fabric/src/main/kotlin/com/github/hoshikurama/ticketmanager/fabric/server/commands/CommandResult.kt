package com.github.hoshikurama.ticketmanager.fabric.server.commands

import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import net.kyori.adventure.text.Component

sealed interface CommandResult
class Error(val errorComponent: Component): CommandResult
class Success(val ticket: Ticket): CommandResult