package com.github.hoshikurama.velocity

/*
NOTE: THIS IS FOR EVENTUAL PURE VELOCITY PLUGIN

import com.github.hoshikurama.ticketmanager.LocaleHandler
import com.github.hoshikurama.ticketmanager.TMLocale
import com.github.hoshikurama.ticketmanager.misc.unwrapOrNull
import com.github.hoshikurama.ticketmanager.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.platform.Player
import com.github.hoshikurama.ticketmanager.platform.Sender
import com.github.hoshikurama.ticketmanager.ticket.Ticket
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import java.util.*

class VelocityPlatformFunctions(
    private val server: ProxyServer,
) : PlatformFunctions {
    override fun buildPlayer(uuid: UUID, localeHandler: LocaleHandler): Player? {
        return server.getPlayer(uuid).unwrapOrNull()?.let { VelocityPlayer.build(it, localeHandler) }
    }

    override fun getAllOnlinePlayers(localeHandler: LocaleHandler): List<Player> {
        return server.allServers
            .flatMap { it.playersConnected }
            .map { VelocityPlayer.build(it, localeHandler) }
    }


}
 */