package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes

import com.github.hoshikurama.ticketmanager.api.registry.messagesharing.MessageSharing
import com.github.hoshikurama.ticketmanager.api.ticket.ActionLocation
import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.tmcoroutine.TMCoroutine
import com.google.common.io.ByteStreams
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import java.util.*

class TeleportJoinMailbox(private val messageSharing: MessageSharing) {
    private typealias T = Pair<UUID, ActionLocation.FromPlayer>
    private val outgoingChannelName = Server2Proxy.Teleport.waterfallString()

    private val channel = Channel<T>(capacity = Channel.RENDEZVOUS)
    val incomingMessages: ReceiveChannel<T> = channel // To enforce read-only (for listening to THIS mailbox)

    companion object {
        val Intermediary = Channel<ByteArray>()
    }

    init {
        TMCoroutine.Supervised.launch {
            for (incomingMSG in Intermediary) {
                channel.send(decode(incomingMSG))
            }
        }
    }

    private fun encode(t: T): ByteArray {
        return ByteStreams.newDataOutput().apply {
            writeUTF(t.second.server ?: "NULL")
            writeUTF(t.first.toString())
            writeUTF(t.second.world)
            writeInt(t.second.x)
            writeInt(t.second.y)
            writeInt(t.second.z)
        }.toByteArray()
    }

    private fun decode(outputArray: ByteArray): T {
        val input = ByteStreams.newDataInput(outputArray)
        val serverName = input.readUTF()
        val uuid = input.readUTF().run(UUID::fromString)
        val location = ActionLocation.FromPlayer(
            server = serverName,
            world = input.readUTF(),
            x = input.readInt(),
            y = input.readInt(),
            z = input.readInt(),
        )
        return uuid to location
    }

    fun forward2Hub(t: T) {
        messageSharing.relay2Hub(encode(t), outgoingChannelName)
    }
}