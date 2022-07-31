package com.github.hoshikurama.ticketmanager.commonse.platform

import com.github.hoshikurama.ticketmanager.common.ProxyUpdate
import com.github.hoshikurama.ticketmanager.common.mainPluginVersion
import com.github.hoshikurama.ticketmanager.commonse.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.commonse.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.misc.pushErrors
import com.github.hoshikurama.ticketmanager.commonse.misc.templated
import com.github.hoshikurama.ticketmanager.commonse.ticket.User
import java.util.concurrent.CompletableFuture

abstract class PlayerJoinEvent(
    private val globalPluginState: GlobalPluginState,
    protected val platformFunctions: PlatformFunctions,
    protected val instanceState: InstancePluginState,
) {

    fun whenPlayerJoins(player: Player, serverCount: Int) {
        if (globalPluginState.pluginLocked.get()) return

        CompletableFuture.runAsync {

            // Plugin Update Checking
            kotlin.run {
                if (instanceState.pluginUpdate.get().canCheck) {
                    val newerVersion = instanceState.pluginUpdate.get().latestVersionIfNotLatest ?: return@run // Only present if newer version is available and plugin can check
                    if (!player.has("ticketmanager.notify.pluginUpdate")) return@run

                    player.locale.notifyPluginUpdate.parseMiniMessage(
                        "current" templated mainPluginVersion,
                        "latest" templated newerVersion,
                    ).run(player::sendMessage)
                }
            }

            // Proxy update message
            kotlin.run {
                if (player.has("ticketmanager.notify.proxyUpdate")
                    && instanceState.enableProxyMode
                    && instanceState.allowProxyUpdatePings
                    && instanceState.proxyServerName != null
                    && instanceState.cachedProxyUpdate.get() != null
                ) {
                    // Helps with startup...
                    if (serverCount <= 1) {
                        val message = ProxyUpdate.encodeProxyMsg(instanceState.proxyServerName)
                        platformFunctions.relayMessageToProxy("ticketmanager:s2p_proxy_update", message)
                    }

                    val (curVer, latestVer) = instanceState.cachedProxyUpdate.get()!!
                    player.locale.notifyProxyUpdate.parseMiniMessage(
                        "current" templated curVer,
                        "latest" templated latestVer,
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
                pushErrors(platformFunctions, instanceState, e) { "An error occurred when a player joined!" } //TODO: LOCALIZE THIS EVENTUALLY
            }
            null
        }
    }
}