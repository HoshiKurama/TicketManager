package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.platform.PlayerJoinEvent
import net.milkbowl.vault.permission.Permission
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class PaperJoinEvent(
    globalState: GlobalPluginState,
    instanceState: InstancePluginState,
    platformFunctions: PlatformFunctions,
    private val perms: Permission,
) : PlayerJoinEvent(globalState, platformFunctions, instanceState), Listener {

    @EventHandler
    fun onPlayerJoinEvent(event: org.bukkit.event.player.PlayerJoinEvent) {
        val player = PaperPlayer(event.player, perms, instanceState.localeHandler, instanceState.velocityServerName)
        super.whenPlayerJoins(player)
    }
}