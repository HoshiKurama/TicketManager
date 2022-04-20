package com.github.hoshikurama.ticketmanager.velocity

import com.github.hoshikurama.ticketmanager.pluginVersion
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier

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

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        server.channelRegistrar.register(incomingMessage)
        server.channelRegistrar.register(outgoingMessage)
    }

    @Subscribe
    fun onMessage(event: PluginMessageEvent) {
        if (event.identifier == incomingMessage) {
            server.allServers.parallelStream()
                .filter { it.playersConnected.isNotEmpty() }
                .forEach { it.sendPluginMessage(outgoingMessage, event.data) }
        }
    }
}