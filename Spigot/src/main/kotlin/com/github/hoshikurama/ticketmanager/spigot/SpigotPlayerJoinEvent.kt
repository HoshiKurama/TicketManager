package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.common.hooks.TMPlayerJoinEvent
import com.github.hoshikurama.ticketmanager.common.hooks.TicketManagerPlugin
import kotlinx.coroutines.coroutineScope
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.milkbowl.vault.permission.Permission
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class SpigotPlayerJoinEvent(
    pluginData: TicketManagerPlugin<SpigotPlugin>,
    private val perms: Permission,
    private val adventure: BukkitAudiences,
) : TMPlayerJoinEvent<SpigotPlugin>(pluginData), Listener {

    @EventHandler
    suspend fun onPlayerJoinEvent(event: PlayerJoinEvent) = coroutineScope {
        val player = SpigotPlayer(event.player, pluginData, perms, adventure)

        whenPlayerJoins(player)
    }
}