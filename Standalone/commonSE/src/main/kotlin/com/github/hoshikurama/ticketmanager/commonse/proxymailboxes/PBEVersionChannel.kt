package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes

import com.github.hoshikurama.ticketmanager.api.registry.messagesharing.MessageSharing
import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.base.HandshakeMailbox
import com.google.common.io.ByteStreams
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

class PBEVersionChannel(
    override val messageSharing: MessageSharing
) : HandshakeMailbox<String, String>() {
    override val outgoingChannelName = Server2Proxy.ProxyVersionRequest.waterfallString()
    override val apiChannelRef: ReceiveChannel<ByteArray> = Intermediary

    companion object {
        val Intermediary = Channel<ByteArray>()
    }

    override fun encodeInput(input: String): ByteArray {
        return ByteStreams.newDataOutput()
            .apply { writeUTF(input) }
            .toByteArray()
    }

    override fun decodeOutput(outputArray: ByteArray): String {
        return ByteStreams.newDataInput(outputArray).readUTF()
    }
}