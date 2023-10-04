package com.github.hoshikurama.ticketmanager.commonse

import com.github.hoshikurama.ticketmanager.api.CommandSender
import java.util.*

object CommandSenders {

    object Info {
        open class Player(override val username: String, override val uuid: UUID) : CommandSender.InfoPlayer
        open class Console : CommandSender.InfoConsole
    }

    object Active {
        abstract class Player(override val username: String, override val uuid: UUID) : CommandSender.OnlinePlayer
        abstract class Console : CommandSender.OnlineConsole
    }
}