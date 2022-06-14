package com.github.hoshikurama.ticketmanager.core.platform

import com.github.hoshikurama.ticketmanager.common.mainPluginVersion
import com.github.hoshikurama.ticketmanager.core.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.core.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.core.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.core.misc.pushErrors
import com.github.hoshikurama.ticketmanager.core.misc.templated
import com.github.hoshikurama.ticketmanager.core.ticket.User
import java.util.concurrent.CompletableFuture

abstract class PlayerJoinEvent(
    protected val globalPluginState: GlobalPluginState,
    protected val platformFunctions: PlatformFunctions,
    protected val instanceState: InstancePluginState,
) {

    fun whenPlayerJoins(player: Player) {
        if (globalPluginState.pluginLocked.get()) return

        CompletableFuture.runAsync {
            // Plugin Update Checking
            kotlin.run {
                if (instanceState.pluginUpdateChecker.canCheck) {
                    val newerVersion = instanceState.pluginUpdateChecker.latestVersionOrNull ?: return@run // Only present if newer version is available and plugin can check
                    if (!player.has("ticketmanager.notify.pluginUpdate")) return@run

                    player.locale.notifyPluginUpdate.parseMiniMessage(
                        "current" templated mainPluginVersion,
                        "latest" templated newerVersion,
                    ).run(player::sendMessage)
                }
            }

            // Unread Updates
            kotlin.run {
                if (!player.has("ticketmanager.notify.unreadUpdates.onJoin")) return@run

                instanceState.database.getTicketIDsWithUpdatesForAsync(User(player.uniqueID)).thenAcceptAsync { ids ->
                    if (ids.isEmpty()) return@thenAcceptAsync

                    val template = if (ids.size == 1) player.locale.notifyUnreadUpdateSingle else player.locale.notifyUnreadUpdateMulti
                    val tickets = ids.joinToString(", ")

                    template.parseMiniMessage("num" templated tickets).run(player::sendMessage)
                }
            }

            // View Open-Count and Assigned-Count Tickets
            kotlin.run {
                if (!player.has("ticketmanager.notify.openTickets.onJoin")) return@run

                val openCF = instanceState.database.countOpenTicketsAsync()
                val assignedCF = instanceState.database.countOpenTicketsAssignedToAsync(player.name, player.permissionGroups)

                CompletableFuture.allOf(openCF, assignedCF).thenAcceptAsync {
                    val open = openCF.join()
                    val assigned = assignedCF.join()

                    if (open != 0L)
                        player.locale.notifyOpenAssigned.parseMiniMessage(
                            "open" templated open.toString(),
                            "assigned" templated assigned.toString()
                        ).run(player::sendMessage)
                }
            }
        }.exceptionallyAsync {
            (it as? Exception)?.let { e ->
                pushErrors(platformFunctions, instanceState, e) { "An error occurred when a player joined!" } //TODO: LOCALIZE THIS MESSAGE IN 8.1
            }
            null
        }
    }
}