package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.commonse.datas.GlobalPluginState
import com.github.hoshikurama.ticketmanager.commonse.old.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.commonse.old.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.old.platform.PlayerJoinEvent
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class JoinEventImpl(
    globalState: GlobalPluginState,
    instanceState: InstancePluginState,
    platformFunctions: PlatformFunctions,
    private val adventure: BukkitAudiences,
) : PlayerJoinEvent(globalState, platformFunctions, instanceState), Listener {

    @EventHandler
    fun onPlayerJoinEvent(event: org.bukkit.event.player.PlayerJoinEvent) {
        val player = SpigotPlayer(event.player, adventure, instanceState.localeHandler)
        whenPlayerJoinsAsync(player, 0)
    }
}