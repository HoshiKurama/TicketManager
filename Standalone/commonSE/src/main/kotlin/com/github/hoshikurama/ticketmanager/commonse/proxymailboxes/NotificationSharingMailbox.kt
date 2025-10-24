package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.registry.messagesharing.MessageSharing
import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.commonse.commands.MessageNotification
import com.github.hoshikurama.tmcoroutine.TMCoroutine
import com.google.common.io.ByteStreams
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

class NotificationSharingMailbox(val messageSharing: MessageSharing) {
    private typealias T = MessageNotification<CommandSender.Info>
    private val outgoingChannelName = Server2Proxy.NotificationSharing.waterfallString()

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

    private fun encode(t: T): ByteArray = t.encodeForProxy()

    private fun decode(outputArray: ByteArray): T {
        val input = ByteStreams.newDataInput(outputArray)
            .apply { readUTF() } // Gets rid of server uuid

        return when (input.readUTF().run(MessageNotification.MessageType::valueOf)) {
            MessageNotification.MessageType.ASSIGN -> MessageNotification.Assign.decode(input)
            MessageNotification.MessageType.CLOSEWITHCOMMENT -> MessageNotification.CloseWithComment.decode(input)
            MessageNotification.MessageType.CLOSEWITHOUTCOMMENT -> MessageNotification.CloseWithoutComment.decode(input)
            MessageNotification.MessageType.MASSCLOSE -> MessageNotification.MassClose.decode(input)
            MessageNotification.MessageType.COMMENT -> MessageNotification.Comment.decode(input)
            MessageNotification.MessageType.CREATE -> MessageNotification.Create.decode(input)
            MessageNotification.MessageType.REOPEN -> MessageNotification.Reopen.decode(input)
            MessageNotification.MessageType.SETPRIORITY -> MessageNotification.SetPriority.decode(input)
        }
    }

    fun forward2Hub(t: T) {
        messageSharing.relay2Hub(encode(t), outgoingChannelName)
    }
}