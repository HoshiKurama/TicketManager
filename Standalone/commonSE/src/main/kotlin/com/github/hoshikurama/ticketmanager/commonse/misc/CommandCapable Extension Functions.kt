package com.github.hoshikurama.ticketmanager.commonse.misc

import com.github.hoshikurama.ticketmanager.api.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.ticket.TicketAssignmentType
import com.github.hoshikurama.ticketmanager.api.ticket.TicketCreator
import com.github.hoshikurama.ticketmanager.commonse.datas.GlobalState
import java.util.*

fun CommandSender.Info.asInfoString(): String = when (this) {
    is CommandSender.Info.Player,  -> "USER.$username.$uuid"
    is CommandSender.Info.Console -> "CONSOLE"
    else -> throw Exception()
// Note: the "else" is here because Kotlin is being dumb. Active objects are also Info objects
}

fun String.asCommandCapableInfo(): CommandSender.Info {
    val split = this.split(".")
    return when (split[0]) {
        "USER" -> CommandSender.Info.Player(split[1], UUID.fromString(split[2]))
        "CONSOLE" -> CommandSender.Info.Console()
        else -> throw Exception("Invalid type found when attempting to decode CommandCapable.Info! Value: ${split[0]}")
    }
}

fun CommandSender.Info.asCreator(): TicketCreator = when (this) {
    is CommandSender.Info.Console -> TicketCreator.Console
    is CommandSender.Info.Player -> TicketCreator.User(uuid)
    else -> throw Exception()
    // Note: the "else" is here because Kotlin is being dumb. Active objects are also Info objects
}

fun CommandSender.asTicketAssignmentType() = when(this) {
    is CommandSender.Active.OnlineConsole, is CommandSender.Info.Console -> TicketAssignmentType.Console
    is CommandSender.Active.OnlinePlayer -> TicketAssignmentType.Other(username) //Note STFU IntelliJ you stupid bitch...
    is CommandSender.Info.Player -> TicketAssignmentType.Other(username)
}