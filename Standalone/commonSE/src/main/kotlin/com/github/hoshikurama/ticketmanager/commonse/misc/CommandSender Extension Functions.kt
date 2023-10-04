package com.github.hoshikurama.ticketmanager.commonse.misc

import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.commonse.CommandSenders
import java.util.*
import com.github.hoshikurama.ticketmanager.api.CommandSender as CommandSenderAPI

fun InfoCSString.asCommandSender(): CommandSenderAPI.Info {
    val split = this.value.split(".")
    return when (split[0]) {
        "CSI_USER" -> CommandSenders.Info.Player(split[1], UUID.fromString(split[2]))
        "CSI_CONSOLE" -> CommandSenders.Info.Console()
        else -> throw Exception("Invalid type found when attempting to decode CommandCapable.Info! Value: ${split[0]}")
    }
}

fun CommandSenderAPI.getUsername(locale: Locale): String = when(this) {
    is CommandSenderAPI.Player -> username
    is CommandSenderAPI.Console -> locale.consoleName
    else -> throw Exception("Impossible")
}

@JvmInline
value class InfoCSString(val value: String)