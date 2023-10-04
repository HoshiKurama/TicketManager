package com.github.hoshikurama.ticketmanager.paper.hooks

import com.github.hoshikurama.ticketmanager.common.Proxy2Server
import com.github.hoshikurama.ticketmanager.common.randServerIdentifier
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.NotificationSharingChannel
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.PBEVersionChannel
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.ProxyJoinChannel
import com.github.hoshikurama.ticketmanager.commonse.utilities.notEquals
import com.github.hoshikurama.tmcore.TMCoroutine
import com.google.common.io.ByteStreams
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.*

class Proxy(
    private val notificationSharingChannel: NotificationSharingChannel,
    private val pbeVersionChannel: PBEVersionChannel,
    private val proxyJoinChannel: ProxyJoinChannel,
): PluginMessageListener {

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {


        when (channel) {
            Proxy2Server.NotificationSharing.name -> {
                val shouldSendMessage = ByteStreams.newDataInput(message)
                    .readUTF()
                    .run(UUID::fromString)
                    .notEquals(randServerIdentifier)

                if (shouldSendMessage) TMCoroutine.Global.launch {
                    notificationSharingChannel.answerFromPluginChannel(message)
                }
            }
            Proxy2Server.ProxyVersionRequest.name -> TMCoroutine.Global.launch {
                pbeVersionChannel.answerFromPluginChannel(message)
            }
            Proxy2Server.Teleport.name -> TMCoroutine.Global.launch {
                proxyJoinChannel.answerFromPluginChannel(message)
            }
        }
    }
}
/*
TODO: THIS GOES IN THE PROXY PLATFORM IMPLEMENTATION (Don't send message if not required)
// Filters out same server
if (input.readUTF().run(UUID::fromString).equals(randServerIdentifier)) return
*/