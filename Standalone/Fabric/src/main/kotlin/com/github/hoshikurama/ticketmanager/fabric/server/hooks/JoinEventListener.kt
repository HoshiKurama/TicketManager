package com.github.hoshikurama.ticketmanager.fabric.server.hooks

import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.commonse.PlayerJoinExtensionHolder
import com.github.hoshikurama.ticketmanager.fabric.server.impls.FabricPlayer
import com.github.hoshikurama.tmcoroutine.TMCoroutine
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

fun registerJoinEventListener(
    config: Config,
    locale: Locale,
    permission: Permission,
    database: AsyncDatabase,
    platformFunctions: PlatformFunctions,
    extensions: PlayerJoinExtensionHolder
) = ServerPlayConnectionEvents.INIT.register { handler, server ->
    val player = FabricPlayer(handler.player, config.proxyOptions?.serverName)

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