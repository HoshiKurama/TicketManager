package com.github.hoshikurama.ticketmanager.waterfall

import com.github.hoshikurama.ticketmanager.common.UpdateChecker
import com.github.hoshikurama.ticketmanager.common.bridgePluginVersion
import com.github.hoshikurama.ticketmanager.common.waterfallBridgeKey
import com.google.common.io.ByteStreams
import net.md_5.bungee.api.event.PluginMessageEvent
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.event.EventHandler
import org.bstats.bungeecord.Metrics
import java.util.*
import java.util.logging.Level

@Suppress("Unused")
class WaterfallBridge : Plugin(), Listener {
    lateinit var metrics: Metrics
    override fun onEnable() {
        proxy.registerChannel("ticketmanager:inform_proxy")
        proxy.registerChannel("ticketmanager:relayed_message")
        proxy.registerChannel("ticketmanager:server_to_proxy_tp")
        proxy.registerChannel("ticketmanager:proxy_to_server_tp")

        this.proxy.pluginManager.registerListener(this, this)

        // Initialize Metrics
        metrics = Metrics(this, waterfallBridgeKey)

        // Version check
        UpdateChecker(true, UpdateChecker.Location.BRIDGE).latestVersionIfNotLatest?.let {
            logger.log(Level.INFO, "[TicketManager] A new bridge update is available!\n Latest: $it\n Current: $bridgePluginVersion")
        }
    }

    override fun onDisable() {
        proxy.unregisterChannel("ticketmanager:inform_proxy")
        proxy.unregisterChannel("ticketmanager:relayed_message")
        proxy.unregisterChannel("ticketmanager:server_to_proxy_tp")
        proxy.unregisterChannel("ticketmanager:proxy_to_server_tp")
    }

    @EventHandler
    fun onMessage(event: PluginMessageEvent) {
        when (event.tag) {

            "ticketmanager:inform_proxy" ->
                this.proxy.serversCopy
                    .map { it.value }
                    .filter { it.players.isNotEmpty() }
                    .forEach { it.sendData("ticketmanager:relayed_message", event.data) }

            "ticketmanager:server_to_proxy_tp" -> {
                val input =  ByteStreams.newDataInput(event.data)

                val serverName = input.readUTF()
                val uuid = UUID.fromString(input.readUTF())
                val targetServer = proxy.serversCopy[serverName]

                if (targetServer != null) {
                    targetServer.sendData("ticketmanager:proxy_to_server_tp", event.data)    // Sends data to server
                    proxy.getPlayer(uuid).connect(targetServer)                                     // Teleport player
                }
            }
        }
    }
}