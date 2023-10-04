package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.api.common.ticket.ActionLocation
import com.github.hoshikurama.ticketmanager.commonse.oldDELETE.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.oldDELETE.PlayerJoinEvent
import com.github.hoshikurama.ticketmanager.commonse.oldDELETE.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.oldDELETE.datas.ConfigState
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import java.util.concurrent.ConcurrentHashMap

val proxyJoinMap = ConcurrentHashMap<String, ActionLocation.FromPlayer>()

class JoinEventImpl(
    activeLocale: TMLocale,
    configState: ConfigState,
    platformFunctions: PlatformFunctions,
    private val adventure: BukkitAudiences,
) : PlayerJoinEvent(platformFunctions, configState, activeLocale), Listener {

    @EventHandler
    fun onPlayerJoinEvent(event: org.bukkit.event.player.PlayerJoinEvent) {
        val player = SpigotPlayer(event.player, adventure, configState.proxyServerName)
        val serverCount = Bukkit.getServer().onlinePlayers.size

        super.whenPlayerJoinsAsync(player, serverCount)

        val uuidString = event.player.uniqueId.toString()
        if (proxyJoinMap.containsKey(uuidString)) {
            platformFunctions.teleportToTicketLocSameServer(platformFunctions.buildPlayer(event.player.uniqueId)!!, proxyJoinMap[uuidString]!!) // Teleport player to final location
            proxyJoinMap.remove(uuidString)
        }
    }
}