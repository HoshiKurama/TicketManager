package com.github.hoshikurama.ticketmanager.commonse.registrydefaults.playerjoin

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.registry.playerjoin.PlayerJoinExtension
import com.github.hoshikurama.ticketmanager.api.ticket.ActionLocation
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.TeleportJoinMailbox
import com.github.hoshikurama.tmcoroutine.TMCoroutine
import kotlinx.coroutines.delay
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

class ProxyTeleport(private val teleportJoinMailbox: TeleportJoinMailbox) : PlayerJoinExtension {
    private val uuidLocation = ConcurrentHashMap<UUID, ActionLocation.FromPlayer>()

    init {
        TMCoroutine.Supervised.launch {
            for ((uuid, location) in teleportJoinMailbox.incomingMessages) {
                uuidLocation[uuid] = location

                // Remove in the event they don't make it to the server
                TMCoroutine.Supervised.launch {
                    delay(1.minutes)
                    uuidLocation.remove(uuid)
                }
            }
        }
    }

    override suspend fun whenPlayerJoins(
        player: CommandSender.OnlinePlayer,
        platformFunctions: PlatformFunctions,
        permission: Permission,
        database: AsyncDatabase,
        config: Config,
        locale: Locale
    ) {
        if (!uuidLocation.containsKey(player.uuid)) return
        platformFunctions.teleportToTicketLocSameServer(player, uuidLocation[player.uuid]!!)
        uuidLocation.remove(player.uuid)
    }
}