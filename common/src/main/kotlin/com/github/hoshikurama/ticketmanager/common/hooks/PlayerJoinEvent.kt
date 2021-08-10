package com.github.hoshikurama.ticketmanager.common.hooks

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

abstract class TMPlayerJoinEvent<T>(val pluginData: TicketManagerPlugin<T>) {

    suspend fun whenPlayerJoins(player: Player) = coroutineScope {
        if (pluginData.pluginLocked.get()) return@coroutineScope

        //Plugin Update Checking
        launch {
            val pluginUpdateStatus = pluginData.configState.pluginUpdateAvailable.await()
            if (player.has("ticketmanager.notify.pluginUpdate") && pluginUpdateStatus != null) {
                player.locale.notifyPluginUpdate
                    .replace("%current%", pluginUpdateStatus.first)
                    .replace("%latest%", pluginUpdateStatus.second)
                    .run(player::sendMessage)
            }
        }

        // Unread Updates
        launch {
            if (!player.has("ticketmanager.notify.unreadUpdates.onJoin")) return@launch
            pluginData.configState.database.getIDsWithUpdatesFor(player.uniqueID)
                .toList()
                .run { if (size == 0) null else this }
                ?.run {
                    val template = if (size == 1) player.locale.notifyUnreadUpdateSingle
                    else player.locale.notifyUnreadUpdateMulti
                    val tickets = this.joinToString(", ")

                    template.replace("%num%", tickets)
                        .run(player::sendMessage)
                }
        }

        // View Open-Count and Assigned-Count Tickets
        launch {
            if (!player.has("ticketmanager.notify.openTickets.onJoin")) return@launch
            val open = pluginData.configState.database.getOpenIDPriorityPairs()
            val assigned = pluginData.configState.database.getAssignedOpenIDPriorityPairs(player.name, player.permissionGroups)

            player.locale.notifyOpenAssigned
                .replace("%open%", "${open.count()}")
                .replace("%assigned%", "${assigned.count()}")
                .run(player::sendMessage)
        }
    }
}