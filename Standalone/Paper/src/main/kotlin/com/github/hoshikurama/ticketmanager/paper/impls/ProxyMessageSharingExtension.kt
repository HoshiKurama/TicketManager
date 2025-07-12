package com.github.hoshikurama.ticketmanager.paper.impls

import com.github.hoshikurama.ticketmanager.common.Proxy2Server
import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.common.randServerIdentifier
import com.github.hoshikurama.ticketmanager.commonse.messagesharingTEST.MessageSharing
import com.github.hoshikurama.ticketmanager.commonse.messagesharingTEST.MessageSharingExtension
import com.github.hoshikurama.ticketmanager.commonse.utilities.notEquals
import com.github.hoshikurama.tmcoroutine.TMCoroutine
import com.google.common.io.ByteStreams
import kotlinx.coroutines.channels.SendChannel
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.UUID
import java.util.function.Consumer

class ProxyMessageSharingExtension(private val plugin: Plugin) : MessageSharingExtension, MessageSharing {

    override fun relay2Hub(data: ByteArray, channelName: String) {
        Bukkit.getScheduler().runTask(plugin, Consumer {
            plugin.server.sendPluginMessage(plugin, Server2Proxy.NotificationSharing.waterfallString(), data)
        })
    }

    override suspend fun unload(trueShutdown: Boolean) {
        val unregister = {
            plugin.server.messenger.unregisterIncomingPluginChannel(plugin)
            plugin.server.messenger.unregisterOutgoingPluginChannel(plugin)
        }

        if (trueShutdown) unregister()
        else plugin.runTask(unregister)
    }

    override fun load(
        teleportJoinChannel: SendChannel<ByteArray>,
        notificationSharingChannel: SendChannel<ByteArray>,
        pbeVersionChannel: SendChannel<ByteArray>
    ): MessageSharing {

        // Generate listener for forwarding to intermediaries
        val listener = PluginMessageListener { channel, player, message ->
            when (channel.split(":", limit = 2)[1]) {
                Proxy2Server.NotificationSharing.name -> {
                    val shouldSendMessage = ByteStreams.newDataInput(message)
                        .readUTF()
                        .run(UUID::fromString)
                        .notEquals(randServerIdentifier)

                    if (shouldSendMessage) TMCoroutine.Global.launch {
                        notificationSharingChannel.send(message)
                    }
                }

                Proxy2Server.Teleport.name -> TMCoroutine.Global.launch {
                    teleportJoinChannel.send(message)
                }

                Proxy2Server.ProxyVersionRequest.name -> TMCoroutine.Global.launch {
                    pbeVersionChannel.send(message)
                }
            }
        }

        // Register plugin channels
        plugin.runTask {
            plugin.server.messenger.run {
                registerOutgoingPluginChannel(plugin, Server2Proxy.NotificationSharing.waterfallString())
                registerIncomingPluginChannel(plugin, Proxy2Server.NotificationSharing.waterfallString(), listener)
                registerOutgoingPluginChannel(plugin, Server2Proxy.Teleport.waterfallString())
                registerIncomingPluginChannel(plugin, Proxy2Server.Teleport.waterfallString(), listener)
                registerOutgoingPluginChannel(plugin, Server2Proxy.ProxyVersionRequest.waterfallString())
                registerIncomingPluginChannel(plugin, Proxy2Server.ProxyVersionRequest.waterfallString(), listener)
            }
        }

        return this
    }
}