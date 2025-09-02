package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.base

import com.github.hoshikurama.ticketmanager.api.registry.messagesharing.MessageSharing
import com.github.hoshikurama.tmcoroutine.TMCoroutine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Intended for when a single input and output are expected. See [ReceivingMailbox] for more information on the setup
 * itself.
 */
abstract class HandshakeMailbox<Input, Output> {
    protected abstract val messageSharing: MessageSharing
    protected abstract val outgoingChannelName: String
    protected abstract val apiChannelRef: ReceiveChannel<ByteArray>

    private val channel = Channel<Output>(capacity = Channel.RENDEZVOUS)

    init {
        TMCoroutine.Supervised.launch { // Note: Supervised good since new object made on each reload
            apiChannelRef?.let { channelRef ->
                for (incomingMSG in channelRef) {
                    channel.send(decodeOutput(incomingMSG))
                }
            }
        }
    }

    suspend fun request(input: Input): Output {
        messageSharing.relay2Hub(encodeInput(input), outgoingChannelName)
        return channel.receive()
    }

    protected abstract fun encodeInput(input: Input): ByteArray
    protected abstract fun decodeOutput(outputArray: ByteArray): Output
}