package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.platform.PlayerJoinEvent
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.milkbowl.vault.permission.Permission
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class SpigotJoinEvent(
    globalState: GlobalPluginState,
    instanceState: InstancePluginState,
    platformFunctions: PlatformFunctions,
    private val perms: Permission,
    private val adventure: BukkitAudiences,
) : PlayerJoinEvent(globalState, platformFunctions, instanceState), Listener {

    @EventHandler
    fun onPlayerJoinEvent(event: org.bukkit.event.player.PlayerJoinEvent) {
        val player = SpigotPlayer(event.player, perms, adventure, instanceState.localeHandler)
        whenPlayerJoins(player)
    }
}