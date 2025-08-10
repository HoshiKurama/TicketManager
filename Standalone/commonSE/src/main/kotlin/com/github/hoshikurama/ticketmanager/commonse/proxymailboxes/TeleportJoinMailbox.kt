package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes

import com.github.hoshikurama.ticketmanager.api.registry.messagesharing.MessageSharing
import com.github.hoshikurama.ticketmanager.api.ticket.ActionLocation
import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.base.ReceivingMailbox
import com.google.common.io.ByteStreams
import kotlinx.coroutines.channels.Channel
import java.util.*

class TeleportJoinMailbox(
    override val messageSharing: MessageSharing,
) : ReceivingMailbox<Pair<UUID, ActionLocation.FromPlayer>>() {
    override val outgoingChannelName = Server2Proxy.Teleport.waterfallString()
    override val apiChannelRef = Intermediary

    companion object {
        val Intermediary = Channel<ByteArray>()
    }

    override fun encode(t: Pair<UUID, ActionLocation.FromPlayer>): ByteArray {
        return ByteStreams.newDataOutput().apply {
            writeUTF(t.second.server ?: "NULL")
            writeUTF(t.first.toString())
            writeUTF(t.second.world)
            writeInt(t.second.x)
            writeInt(t.second.y)
            writeInt(t.second.z)
        }.toByteArray()
    }

    override fun decode(outputArray: ByteArray): Pair<UUID, ActionLocation.FromPlayer> {
        val input = ByteStreams.newDataInput(outputArray)
        val serverName = input.readUTF()
        val uuid = input.readUTF().run(UUID::fromString)
        val location = ActionLocation.FromPlayer(
            server = serverName,
            world = input.readUTF(),
            x = input.readInt(),
            y = input.readInt(),
            z = input.readInt(),
        )
        return uuid to location
    }
}