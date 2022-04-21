package com.github.hoshikurama.ticketmanager.velocity

import com.github.hoshikurama.ticketmanager.pluginVersion
import com.google.common.io.ByteStreams
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import java.util.*

@Plugin(
    id = "ticketmanager",
    name = "TicketManager",
    version = pluginVersion,
    description = "Advanced Ticket Management Solution",
    authors = ["HoshiKurama"],
)
class TMPluginVelocityImpl @Inject constructor(private val server: ProxyServer) { //, private val logger: Logger, @DataDirectory dataDirectory: Path
    private val incomingMessage = MinecraftChannelIdentifier.create("ticketmanager", "inform_proxy")
    private val outgoingMessage = MinecraftChannelIdentifier.create("ticketmanager", "relayed_message")

    private val serverToProxyTeleport = MinecraftChannelIdentifier.create("ticketmanager", "server_to_proxy_tp")
    private val proxyToServerTeleport = MinecraftChannelIdentifier.create("ticketmanager", "proxy_to_server_tp")

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        server.channelRegistrar.register(incomingMessage, outgoingMessage, serverToProxyTeleport, proxyToServerTeleport)
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        server.channelRegistrar.unregister(incomingMessage, outgoingMessage, serverToProxyTeleport, proxyToServerTeleport)
    }

    @Subscribe
    fun onMessage(event: PluginMessageEvent) {
        when (event.identifier) {

            incomingMessage ->
                server.allServers
                    .filter { it.playersConnected.isNotEmpty() }
                    .forEach { it.sendPluginMessage(outgoingMessage, event.data) }

            serverToProxyTeleport -> {
                val input =  ByteStreams.newDataInput(event.data)

                val serverName = input.readUTF()
                val uuid = UUID.fromString(input.readUTF())
                val targetServer = server.allServers.firstOrNull { it.serverInfo.name == serverName }



                if (targetServer != null) {
                    targetServer.sendPluginMessage(proxyToServerTeleport, event.data)                     // Sends data to server
                    server.getPlayer(uuid).get().createConnectionRequest(targetServer).fireAndForget()    // Teleport player
                }
            }
        }
    }
}