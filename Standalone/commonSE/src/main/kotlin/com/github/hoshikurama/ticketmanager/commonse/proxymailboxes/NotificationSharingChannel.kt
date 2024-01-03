package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.commonse.commands.MessageNotification
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.base.AbstractForwardingMailbox
import com.google.common.io.ByteStreams

abstract class NotificationSharingChannel : AbstractForwardingMailbox<MessageNotification<CommandSender.Info>>() {

    final override fun encode(t: MessageNotification<CommandSender.Info>): ByteArray {
        return t.encodeForProxy()
    }

    final override fun decode(outputArray: ByteArray): MessageNotification<CommandSender.Info> {
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