package com.github.hoshikurama.ticketmanager.commonse.old.platform

import com.github.hoshikurama.ticketmanager.common.ProxyUpdate
import com.github.hoshikurama.ticketmanager.common.mainPluginVersion
import com.github.hoshikurama.ticketmanager.commonse.datas.GlobalPluginState
import com.github.hoshikurama.ticketmanager.commonse.old.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.commonse.old.misc.TMCoroutine
import com.github.hoshikurama.ticketmanager.commonse.old.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.old.misc.pushErrors
import com.github.hoshikurama.ticketmanager.commonse.old.misc.templated
import com.github.hoshikurama.ticketmanager.commonse.ticket.User
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

abstract class PlayerJoinEvent(
    private val globalPluginState: GlobalPluginState,
    protected val platformFunctions: PlatformFunctions,
    protected val instanceState: InstancePluginState,
) {

    fun whenPlayerJoinsAsync(player: OnlinePlayer, serverCount: Int) {
        if (globalPluginState.pluginLocked.get()) return
        try {
            TMCoroutine.runAsync {

                // Plugin Update Checking
                kotlin.run {
                    if (instanceState.pluginUpdate.get().canCheck) {
                        val newerVersion = instanceState.pluginUpdate.get().latestVersionIfNotLatest
                            ?: return@run // Only present if newer version is available and plugin can check
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

                    val ids = instanceState.database.getTicketIDsWithUpdatesForAsync(User(player.uniqueID))
                    if (ids.isEmpty()) return@run

                    val template =
                        if (ids.size == 1) player.locale.notifyUnreadUpdateSingle else player.locale.notifyUnreadUpdateMulti
                    val tickets = ids.joinToString(", ")

                    template.parseMiniMessage("num" templated tickets).run(player::sendMessage)
                }

                // View Open-Count and Assigned-Count Tickets
                coroutineScope {
                    if (!player.has("ticketmanager.notify.openTickets.onJoin")) return@coroutineScope

                    val openCF = async { instanceState.database.countOpenTicketsAsync() }
                    val assignedCF = async {
                        instanceState.database.countOpenTicketsAssignedToAsync(
                            player.name,
                            player.permissionGroups
                        )
                    }

                    if (openCF.await() != 0L)
                        player.locale.notifyOpenAssigned.parseMiniMessage(
                            "open" templated openCF.await().toString(),
                            "assigned" templated assignedCF.await().toString()
                        ).run(player::sendMessage)
                }
            }
        } catch (e: Exception) {
            pushErrors(platformFunctions, instanceState, e) { "An error occurred when a player joined!" }
            //TODO: LOCALIZE THIS EVENTUALLY
        }
    }
}