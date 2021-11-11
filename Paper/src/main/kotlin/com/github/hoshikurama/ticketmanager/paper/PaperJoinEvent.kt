package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.platform.PlayerJoinEvent
import kotlinx.coroutines.coroutineScope
import net.milkbowl.vault.permission.Permission
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class PaperJoinEvent(
    globalState: GlobalPluginState,
    instanceState: InstancePluginState,
    private val perms: Permission,
) : PlayerJoinEvent(globalState, instanceState), Listener {

    @EventHandler
    suspend fun onPlayerJoinEvent(event: org.bukkit.event.player.PlayerJoinEvent) = coroutineScope {
        val player = PaperPlayer(event.player, perms, instanceState.localeHandler)
        whenPlayerJoins(player)
    }
}