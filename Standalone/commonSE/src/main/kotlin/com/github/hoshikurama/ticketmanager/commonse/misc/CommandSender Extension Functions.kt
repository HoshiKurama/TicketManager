package com.github.hoshikurama.ticketmanager.commonse.misc

import com.github.hoshikurama.ticketmanager.api.common.commands.CommandSender
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import java.util.*

fun InfoCSString.asCommandSender(): CommandSender.Info {
    val split = this.value.split(".")
    return when (split[0]) {
        "CSI_USER" -> CommandSender.Info.Player(split[1], UUID.fromString(split[2]))
        "CSI_CONSOLE" -> CommandSender.Info.Console()
        else -> throw Exception("Invalid type found when attempting to decode CommandCapable.Info! Value: ${split[0]}")
    }
}

fun CommandSender.getUsername(locale: TMLocale): String = when(this) {
    is CommandSender.Active.OnlineConsole,
    is CommandSender.Info.Console -> locale.consoleName
    is CommandSender.Active.OnlinePlayer -> username
    is CommandSender.Info.Player -> username
}

@JvmInline
value class InfoCSString(val value: String)