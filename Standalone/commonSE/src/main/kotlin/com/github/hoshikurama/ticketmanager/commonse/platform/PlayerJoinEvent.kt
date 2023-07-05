package com.github.hoshikurama.ticketmanager.commonse.platform

import com.github.hoshikurama.ticketmanager.api.common.TMCoroutine
import com.github.hoshikurama.ticketmanager.api.common.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.common.ticket.Assignment
import com.github.hoshikurama.ticketmanager.common.ProxyUpdate
import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.common.mainPluginVersion
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.datas.ConfigState
import com.github.hoshikurama.ticketmanager.commonse.datas.GlobalState
import com.github.hoshikurama.ticketmanager.commonse.extensions.DatabaseManager
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.misc.pushErrors
import com.github.hoshikurama.ticketmanager.commonse.misc.templated
import kotlinx.coroutines.async

abstract class PlayerJoinEvent(
    protected val platformFunctions: PlatformFunctions,
    protected val configState: ConfigState,
    protected val locale: TMLocale,
) {

    fun whenPlayerJoinsAsync(player: CommandSender.Active.OnlinePlayer, serverCount: Int) {
        if (GlobalState.isPluginLocked) return
        try {
            TMCoroutine.launchSupervised ignored@ {

                // Plugin Update Checking
                TMCoroutine.launchSupervised {
                    if (configState.pluginUpdate.canCheck) {
                        val newerVersion = configState.pluginUpdate.latestVersionIfNotLatest
                            ?: return@launchSupervised // Only present if newer version is available and plugin can check
                        if (!player.has("ticketmanager.notify.pluginUpdate")) return@launchSupervised

                        locale.notifyPluginUpdate.parseMiniMessage(
                            "current" templated mainPluginVersion,
                            "latest" templated newerVersion,
                        ).run(player::sendMessage)
                    }
                }

                // Proxy update message
                TMCoroutine.launchSupervised {
                    if (player.has("ticketmanager.notify.proxyUpdate")
                        && configState.enableProxyMode
                        && configState.allowProxyUpdatePings
                        && configState.proxyServerName != null
                        && configState.proxyUpdate != null
                    ) {
                        // Helps with startup...
                        if (serverCount <= 1) {
                            val message = ProxyUpdate.encodeProxyMsg(configState.proxyServerName)
                            platformFunctions.relayMessageToProxy(Server2Proxy.ProxyVersionRequest.waterfallString(), message)
                        }

                        val (curVer, latestVer) = configState.proxyUpdate!!
                        locale.notifyProxyUpdate.parseMiniMessage(
                            "current" templated curVer,
                            "latest" templated latestVer,
                        ).run(player::sendMessage)
                    }
                }

                // Unread Updates
                TMCoroutine.launchSupervised {
                    if (!player.has("ticketmanager.notify.unreadUpdates.onJoin")) return@launchSupervised

                    val ids = DatabaseManager.activeDatabase.getTicketIDsWithUpdatesForAsync(player.asCreator())
                    if (ids.isEmpty()) return@launchSupervised

                    val template =
                        if (ids.size == 1) locale.notifyUnreadUpdateSingle else locale.notifyUnreadUpdateMulti
                    val tickets = ids.joinToString(", ")

                    template.parseMiniMessage("num" templated tickets).run(player::sendMessage)
                }

                // View Open-Count and Assigned-Count Tickets
                TMCoroutine.launchSupervised {
                    if (!player.has("ticketmanager.notify.openTickets.onJoin")) return@launchSupervised

                    val openCF = async { DatabaseManager.activeDatabase.countOpenTicketsAsync() }
                    val assignedCF = async {
                        DatabaseManager.activeDatabase.countOpenTicketsAssignedToAsync(
                            player.permissionGroups.map(Assignment::PermissionGroup) + Assignment.Player(player.username)
                        )
                    }

                    if (openCF.await() != 0L)
                        locale.notifyOpenAssigned.parseMiniMessage(
                            "open" templated openCF.await().toString(),
                            "assigned" templated assignedCF.await().toString()
                        ).run(player::sendMessage)
                }
            }
        } catch (e: Exception) {
            pushErrors(platformFunctions, configState, locale, e) { "An error occurred when a player joined!" }
        }
    }
}