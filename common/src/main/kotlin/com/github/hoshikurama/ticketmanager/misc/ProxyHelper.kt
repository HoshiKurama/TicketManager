package com.github.hoshikurama.ticketmanager.misc

import com.github.hoshikurama.ticketmanager.platform.Player
import com.github.hoshikurama.ticketmanager.ticket.Ticket
import com.google.common.io.ByteStreams
import java.util.*

fun encodeRequestTP(player: Player, location: Ticket.TicketLocation): ByteArray {
    val output = ByteStreams.newDataOutput()

    output.writeUTF(location.server!!)
    output.writeUTF(player.uniqueID.toString())
    output.writeUTF(location.world!!)
    output.writeInt(location.x!!)
    output.writeInt(location.y!!)
    output.writeInt(location.z!!)

    return output.toByteArray()
}

fun decodeRequestTP(array: ByteArray): Pair<UUID, Ticket.TicketLocation> {
    val input = ByteStreams.newDataInput(array)

    val server = input.readUTF()
    val uuid = UUID.fromString(input.readUTF())
    val world = input.readUTF()
    val x = input.readInt()
    val y = input.readInt()
    val z = input.readInt()

    return uuid to Ticket.TicketLocation(server, world, x, y, z)
}