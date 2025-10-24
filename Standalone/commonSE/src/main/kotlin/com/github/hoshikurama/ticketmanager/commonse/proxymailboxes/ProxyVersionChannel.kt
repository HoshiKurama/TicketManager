package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes

import com.github.hoshikurama.ticketmanager.api.registry.messagesharing.MessageSharing
import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.tmcoroutine.TMCoroutine
import com.google.common.io.ByteStreams
import kotlinx.coroutines.channels.Channel

class ProxyVersionChannel(private val messageSharing: MessageSharing) {
    private typealias Input = String
    private typealias Output = Pair<String, String>

    private val outgoingChannelName = Server2Proxy.ProxyVersionRequest.waterfallString()
    private val channel = Channel<Output>(capacity = Channel.RENDEZVOUS)

    companion object {
        val Intermediary = Channel<ByteArray>()
    }

    init {
        TMCoroutine.Supervised.launch { // Note: Supervised good since new object made on each reload
            for (incomingMSG in Intermediary) {
                channel.send(decodeOutput(incomingMSG))
            }
        }
    }

    private fun encodeInput(input: Input): ByteArray {
        return ByteStreams.newDataOutput()
            .apply { writeUTF(input) }
            .toByteArray()
    }

    private fun decodeOutput(outputArray: ByteArray): Output {
        val output = ByteStreams.newDataInput(outputArray)
        return output.readUTF() to output.readUTF()
    }

    suspend fun request(input: Input): Output {
        messageSharing.relay2Hub(encodeInput(input), outgoingChannelName)
        return channel.receive()
    }
}