package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.base

import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.interfaces.ForwardingMailbox
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

abstract class AbstractForwardingMailbox<T> : ForwardingMailbox<T> {
    private val forwardingChannel = Channel<T>(capacity = Channel.RENDEZVOUS)

    final override val channelListener: ReceiveChannel<T>
        get() = forwardingChannel

    final override fun forward(t: T) = encode(t).run(::relayToProxy)

    suspend fun answerFromPluginChannel(input: ByteArray) {
        forwardingChannel.send(decode(input))
    }

    protected abstract fun encode(t: T): ByteArray
    protected abstract fun decode(outputArray: ByteArray): T
    protected abstract fun relayToProxy(inputArray: ByteArray)
}