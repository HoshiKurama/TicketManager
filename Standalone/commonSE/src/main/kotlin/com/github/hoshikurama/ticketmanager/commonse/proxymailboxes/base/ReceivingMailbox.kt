package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.base

import com.github.hoshikurama.ticketmanager.commonse.messagesharingTEST.MessageSharing
import com.github.hoshikurama.tmcoroutine.TMCoroutine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Intended for Mailboxes that listen for data from its associated intermediary core channel, decode it, and place it into
 * this mailbox for listening. The uplink is for uploading the same data type to the hub connection.
 */
abstract class ReceivingMailbox<T> {
    protected abstract val messageSharing: MessageSharing
    protected abstract val outgoingChannelName: String
    protected abstract val apiChannelRef: ReceiveChannel<ByteArray>

    private val channel = Channel<T>(capacity = Channel.RENDEZVOUS)
    val incomingMessages: ReceiveChannel<T> = channel // To enforce read-only (for listening to THIS mailbox)

    init {
        TMCoroutine.Supervised.launch {
            for (incomingMSG in apiChannelRef) {
                channel.send(decode(incomingMSG))
            }
        }
    }

    fun forward2Hub(t: T) {
        messageSharing.relay2Hub(encode(t), outgoingChannelName)
    }

    protected abstract fun encode(t: T): ByteArray
    protected abstract fun decode(outputArray: ByteArray): T

}