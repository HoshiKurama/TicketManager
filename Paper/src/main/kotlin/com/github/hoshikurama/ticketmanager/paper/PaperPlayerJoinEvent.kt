package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.common.hooks.TMPlayerJoinEvent
import com.github.hoshikurama.ticketmanager.common.hooks.TicketManagerPlugin
import kotlinx.coroutines.coroutineScope
import net.milkbowl.vault.permission.Permission
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PaperPlayerJoinEvent(pluginData: TicketManagerPlugin<PaperPlugin>, private val perms: Permission) : TMPlayerJoinEvent<PaperPlugin>(pluginData), Listener {
    @EventHandler
    suspend fun onPlayerJoinEvent(event: PlayerJoinEvent) = coroutineScope {
        val player = PaperPlayer(event.player, pluginData, perms)

        whenPlayerJoins(player)
    }
}