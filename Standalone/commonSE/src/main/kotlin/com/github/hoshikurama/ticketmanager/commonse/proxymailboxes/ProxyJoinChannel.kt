package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes

import com.github.hoshikurama.ticketmanager.api.ticket.ActionLocation
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.base.AbstractForwardingMailbox
import com.google.common.io.ByteStreams
import java.util.*

abstract class ProxyJoinChannel : AbstractForwardingMailbox<Pair<UUID, ActionLocation.FromPlayer>>() {
    // Note: weird order is for compatibility with PBE 3

    final override fun decode(outputArray: ByteArray): Pair<UUID, ActionLocation.FromPlayer> {
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

    final override fun encode(t: Pair<UUID, ActionLocation.FromPlayer>): ByteArray {
        return ByteStreams.newDataOutput().apply {
            writeUTF(t.second.server ?: "NULL")
            writeUTF(t.first.toString())
            writeUTF(t.second.world)
            writeInt(t.second.x)
            writeInt(t.second.y)
            writeInt(t.second.z)
        }.toByteArray()
    }
}