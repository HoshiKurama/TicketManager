package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.base

import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.interfaces.HandshakeMailbox
import kotlinx.coroutines.channels.Channel

abstract class AbstractHandshakeMailbox<Input, Output> : HandshakeMailbox<Input, Output> {
    private val returningChannel = Channel<Output>(capacity = Channel.RENDEZVOUS)

    override suspend fun request(input: Input): Output {
        encodeInput(input).run(::relayToProxy)
        return returningChannel.receive()
    }

    /**
     * Incoming plugin channel calls this to forward responses to the requester
     */
    suspend fun answerFromPluginChannel(outputArray: ByteArray) {
        returningChannel.send(decodeOutput(outputArray))
    }

    protected abstract fun encodeInput(input: Input): ByteArray
    protected abstract fun decodeOutput(outputArray: ByteArray): Output
    protected abstract fun relayToProxy(inputArray: ByteArray)
}