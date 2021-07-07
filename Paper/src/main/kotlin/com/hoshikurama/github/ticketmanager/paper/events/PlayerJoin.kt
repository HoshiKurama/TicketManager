package com.hoshikurama.github.ticketmanager.paper.events

import com.github.hoshikurama.componentDSL.formattedContent
import com.github.shynixn.mccoroutine.asyncDispatcher
import com.hoshikurama.github.ticketmanager.paper.has
import com.hoshikurama.github.ticketmanager.paper.mainPlugin
import com.hoshikurama.github.ticketmanager.paper.pluginState
import com.hoshikurama.github.ticketmanager.paper.toTMLocale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kyori.adventure.extra.kotlin.text
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerJoin : Listener  {

    @EventHandler
    suspend fun onPlayerJoin(event: PlayerJoinEvent) = coroutineScope {
        if (mainPlugin.pluginLocked.check()) return@coroutineScope

        val player = event.player

        withContext(mainPlugin.asyncDispatcher) {

            //Plugin Update Checking
            launch {
                val pluginUpdateStatus = pluginState.await().pluginUpdateAvailable.await()
                if (player.has("ticketmanager.notify.pluginUpdate") && pluginUpdateStatus != null) {
                    val sentMSG = player.toTMLocale().notifyPluginUpdate
                        .replace("%current%", pluginUpdateStatus.first)
                        .replace("%latest%", pluginUpdateStatus.second)
                    player.sendMessage(text { formattedContent(sentMSG) })
                }
            }

            // Unread Updates
            launch {
                if (player.has("ticketmanager.notify.unreadUpdates.onJoin")) {
                    pluginState.await().database.getTicketIDsWithUpdates(player.uniqueId)
                        .toList()
                        .run { if (size == 0) null else this }
                        ?.run {
                            val template = if (size == 1) player.toTMLocale().notifyUnreadUpdateSingle
                            else player.toTMLocale().notifyUnreadUpdateMulti
                            val tickets = this.joinToString(", ")


                            val sentMSG = template.replace("%num%", tickets)
                            player.sendMessage(text { formattedContent(sentMSG) })
                        }
                }
            }

            // View Open-Count and Assigned-Count Tickets
            launch {
                if (player.has("ticketmanager.notify.openTickets.onJoin")) {
                    val open = pluginState.await().database.getOpenTickets()
                    val assigned = pluginState.await().database.getOpenAssigned(player.name, mainPlugin.perms.getPlayerGroups(player).toList())

                    val sentMSG = player.toTMLocale().notifyOpenAssigned
                        .replace("%open%", "${open.count()}")
                        .replace("%assigned%", "${assigned.count()}")

                    player.sendMessage(text { formattedContent(sentMSG) })

                }
            }
        }
    }
}