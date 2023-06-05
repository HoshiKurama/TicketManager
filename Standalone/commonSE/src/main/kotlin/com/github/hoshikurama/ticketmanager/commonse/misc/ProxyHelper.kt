package com.github.hoshikurama.ticketmanager.commonse.misc

import com.github.hoshikurama.ticketmanager.api.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.ticket.ActionLocation
import com.google.common.io.ByteStreams
import java.util.*

fun encodeRequestTP(player: CommandSender.Active.OnlinePlayer, location: ActionLocation.FromPlayer): ByteArray {
    val output = ByteStreams.newDataOutput()

    output.writeUTF(location.server!!)
    output.writeUTF(player.uuid.toString())
    output.writeUTF(location.world)
    output.writeInt(location.x)
    output.writeInt(location.y)
    output.writeInt(location.z)

    return output.toByteArray()
}

fun decodeRequestTP(array: ByteArray): Pair<UUID, ActionLocation.FromPlayer> {
    val input = ByteStreams.newDataInput(array)

    val server = input.readUTF()
    val uuid = UUID.fromString(input.readUTF())
    val world = input.readUTF()
    val x = input.readInt()
    val y = input.readInt()
    val z = input.readInt()

    return uuid to ActionLocation.FromPlayer(server, world, x, y, z)
}