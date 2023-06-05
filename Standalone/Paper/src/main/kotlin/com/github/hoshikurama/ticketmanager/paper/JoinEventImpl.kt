package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.implapi.ticket.TicketCreationLocation
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.datas.ConfigState
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.platform.PlayerJoinEvent
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.util.concurrent.ConcurrentHashMap

val proxyJoinMap = ConcurrentHashMap<String, TicketCreationLocation.FromPlayer>()

class JoinEventImpl(
    activeLocale: TMLocale,
    configState: ConfigState,
    platformFunctions: PlatformFunctions,
) : PlayerJoinEvent(platformFunctions, configState, activeLocale), Listener {

    @EventHandler
    fun onPlayerJoinEvent(event: org.bukkit.event.player.PlayerJoinEvent) {
        val player = PaperPlayer(event.player, configState.proxyServerName)
        val serverCount = Bukkit.getServer().onlinePlayers.size

        super.whenPlayerJoinsAsync(player, serverCount)

        val uuidString = event.player.uniqueId.toString()
        if (proxyJoinMap.containsKey(uuidString)) {
            platformFunctions.teleportToTicketLocSameServer(platformFunctions.buildPlayer(event.player.uniqueId)!!, proxyJoinMap[uuidString]!!) // Teleport player to final location
            proxyJoinMap.remove(uuidString)
        }
    }
}