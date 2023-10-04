package com.github.hoshikurama.ticketmanager.paper.hooks

import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.types.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.commonse.PlayerJoinExtensionHolder
import com.github.hoshikurama.ticketmanager.paper.impls.PaperPlayer
import com.github.hoshikurama.tmcore.TMCoroutine
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class JoinEventListener(
    private val config: Config,
    private val locale: Locale,
    private val permission: Permission,
    private val database: AsyncDatabase,
    private val platformFunctions: PlatformFunctions,
    private val extensions: PlayerJoinExtensionHolder
) : Listener {

    @EventHandler
    fun onPlayerJoinEvent(event: PlayerJoinEvent) {
        val player = PaperPlayer(event.player, config.proxyOptions?.serverName)

        extensions.asyncExtensions.forEach { extension ->
            TMCoroutine.Supervised.launch {
                extension.whenPlayerJoins(
                    player = player,
                    config = config,
                    database = database,
                    locale = locale,
                    permission = permission,
                    platformFunctions =  platformFunctions,
                )
            }
        }

        TMCoroutine.Supervised.launch {
            extensions.syncExtensions.forEach {
                try {
                    it.whenPlayerJoins(
                        player = player,
                        config = config,
                        database = database,
                        locale = locale,
                        permission = permission,
                        platformFunctions =  platformFunctions,
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}