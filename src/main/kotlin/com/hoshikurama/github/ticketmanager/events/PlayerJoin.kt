package com.hoshikurama.github.ticketmanager.events

import com.hoshikurama.github.ticketmanager.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class PlayerJoin : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (anyLocksPresent()) return
        val player = event.player

        // Unread Updates
        if (player.has("ticketmanager.notify.unreadUpdates.onJoin")) {
            pluginState.database.getTicketIDsWithUpdates(player.uniqueId)
                .run { if (size == 0) null else this }
                ?.run {
                    val template =
                        if (size == 1) getLocale(player).notifyUnreadUpdateSingle
                        else getLocale(player).notifyUnreadUpdateMulti
                    val tickets = this.joinToString(", ")

                    template.replace("%num%", tickets)
                        .sendColouredMessageTo(player)
                }
        }

        // View Open-Count and Assigned-Count Tickets
        if (player.has("ticketmanager.notify.openTickets.onJoin")) {
            val open = pluginState.database.getOpen().size.toString()
            val assigned = pluginState.database.getOpenAssigned(player.name, mainPlugin.perms.getPlayerGroups(player).toList())
                    .count().toString()

            getLocale(player).run { notifyOpenAssigned }
                .replace("%open%", open)
                .replace("%assigned%", assigned)
                .sendColouredMessageTo(player)
        }
    }
}