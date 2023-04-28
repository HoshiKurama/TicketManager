package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.commonse.datas.GlobalState
import com.github.hoshikurama.ticketmanager.commonse.old.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.commonse.old.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.old.platform.PlayerJoinEvent
import com.github.hoshikurama.ticketmanager.commonse.ticket.Ticket
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.util.concurrent.ConcurrentHashMap

val proxyJoinMap = ConcurrentHashMap<String, Ticket.TicketLocation>()

class JoinEventImpl(
    globalState: GlobalState,
    instanceState: InstancePluginState,
    platformFunctions: PlatformFunctions,
) : PlayerJoinEvent(globalState, platformFunctions, instanceState), Listener {

    @EventHandler
    fun onPlayerJoinEvent(event: org.bukkit.event.player.PlayerJoinEvent) {
        val player = PaperPlayer(event.player, instanceState.localeHandler, instanceState.proxyServerName)
        val serverCount = Bukkit.getServer().onlinePlayers.size

        super.whenPlayerJoinsAsync(player, serverCount)

        val uuidString = event.player.uniqueId.toString()
        if (proxyJoinMap.containsKey(uuidString)) {
            platformFunctions.teleportToTicketLocSameServer(platformFunctions.buildPlayer(event.player.uniqueId, instanceState.localeHandler)!!, proxyJoinMap[uuidString]!!) // Teleport player to final location
            proxyJoinMap.remove(uuidString)
        }
    }
}