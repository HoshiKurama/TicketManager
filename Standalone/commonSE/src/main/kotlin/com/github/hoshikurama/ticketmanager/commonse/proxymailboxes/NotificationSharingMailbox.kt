package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.registry.messagesharing.MessageSharing
import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.commonse.commands.MessageNotification
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.base.ReceivingMailbox
import com.google.common.io.ByteStreams
import kotlinx.coroutines.channels.Channel

class NotificationSharingMailbox(
    override val messageSharing: MessageSharing,
) : ReceivingMailbox<MessageNotification<CommandSender.Info>>() {
    override val outgoingChannelName = Server2Proxy.NotificationSharing.waterfallString()
    override val apiChannelRef = Intermediary

    companion object {
        val Intermediary = Channel<ByteArray>()
    }

    override fun encode(t: MessageNotification<CommandSender.Info>): ByteArray = t.encodeForProxy()

    override fun decode(outputArray: ByteArray): MessageNotification<CommandSender.Info> {
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
}