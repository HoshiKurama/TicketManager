package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.commonse.commands.MessageNotification
import com.github.hoshikurama.ticketmanager.commonse.messagesharingTEST.MessageSharing
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

/*
//TODO
    Issue: encode and decode data relies on TM:SE specific implementations that CANNOT be on the API.
    Solution: Intermediate Mailboxes
    - Extension does not directly read and write to typed mailbox.
    - Encoding Side:
        - TM:SE encodes the type properly to ByteArray
        - This gets relayed to hub
    - Decoding Side:
        - Extension forwards ByteArray to channel
        - TM:SE codes reads from this mailbox, decodes it, and forwards it to the typed mailbox
        - TM:SE reads this like normal

    - Create an interface for the API that instructs how to forward data around
    - TM:SE will create the listener for this mailbox and do its own stuff.
    - TM:Core will contain these intermediate mailboxes. Benefit too is they can stay on for entire plugin lifespan
 */