package com.github.hoshikurama.ticketmanager.spigot.events

import com.github.hoshikurama.ticketmanager.spigot.*
import com.github.shynixn.mccoroutine.asyncDispatcher
import com.github.shynixn.mccoroutine.minecraftDispatcher
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerJoin : Listener  {

    @EventHandler
    suspend fun onPlayerJoin(event: PlayerJoinEvent) = withContext(mainPlugin.minecraftDispatcher) {
        if (mainPlugin.pluginState.pluginLocked.get()) return@withContext
        val player = event.player

        withContext(mainPlugin.asyncDispatcher) {

            //Plugin Update Checking
            launch {
                val pluginUpdateStatus = configState.pluginUpdateAvailable.await()
                if (player.has("ticketmanager.notify.pluginUpdate") && pluginUpdateStatus != null) {
                    player.toTMLocale().notifyPluginUpdate
                        .replace("%current%", pluginUpdateStatus.first)
                        .replace("%latest%", pluginUpdateStatus.second)
                        .run(::addColour)
                        .run(player::sendMessage)
                }
            }

            // Unread Updates
            launch {
                if (player.has("ticketmanager.notify.unreadUpdates.onJoin")) {
                    configState.database.getIDsWithUpdatesFor(player.uniqueId)
                        .toList()
                        .run { if (size == 0) null else this }
                        ?.run {
                            val template = if (size == 1) player.toTMLocale().notifyUnreadUpdateSingle
                            else player.toTMLocale().notifyUnreadUpdateMulti
                            val tickets = this.joinToString(", ")

                            template.replace("%num%", tickets)
                                .run(::addColour)
                                .run(player::sendMessage)
                        }
                }
            }

            // View Open-Count and Assigned-Count Tickets
            launch {
                if (player.has("ticketmanager.notify.openTickets.onJoin")) {
                    val open = configState.database.getOpenIDPriorityPairs()
                    val assigned = configState.database.getAssignedOpenIDPriorityPairs(player.name, mainPlugin.perms.getPlayerGroups(player).toList())

                    player.toTMLocale().notifyOpenAssigned
                        .replace("%open%", "${open.count()}")
                        .replace("%assigned%", "${assigned.count()}")
                        .run(::addColour)
                        .run(player::sendMessage)
                }
            }
        }
    }
}