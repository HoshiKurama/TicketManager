package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.misc.decodeRequestTP
import com.github.hoshikurama.ticketmanager.pipeline.Notification
import com.github.hoshikurama.ticketmanager.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.randServerIdentifier
import com.github.hoshikurama.ticketmanager.ticket.User
import com.google.common.io.ByteStreams
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.*

class Proxy(
    private val platform: PlatformFunctions,
    private val instanceState: InstancePluginState
): PluginMessageListener {

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {

        when (channel) {

            "ticketmanager:relayed_message" -> {
                @Suppress("UnstableApiUsage")
                val input = ByteStreams.newDataInput(message)

                // Filters out same server
                if (input.readUTF().run(UUID::fromString).equals(randServerIdentifier)) return

                val notification = when (input.readUTF().run(Notification.MessageType::valueOf)) {
                    Notification.MessageType.ASSIGN -> Notification.Assign.fromByteArray(input)
                    Notification.MessageType.CLOSEWITHCOMMENT -> Notification.CloseWithComment.fromByteArray(input)
                    Notification.MessageType.CLOSEWITHOUTCOMMENT -> Notification.CloseWithoutComment.fromByteArray(input)
                    Notification.MessageType.MASSCLOSE -> Notification.MassClose.fromByteArray(input)
                    Notification.MessageType.COMMENT -> Notification.Comment.fromByteArray(input)
                    Notification.MessageType.CREATE -> Notification.Create.fromByteArray(input)
                    Notification.MessageType.REOPEN -> Notification.Reopen.fromByteArray(input)
                    Notification.MessageType.SETPRIORITY -> Notification.SetPriority.fromByteArray(input)
                }

                notification.run {
                    if (sendCreatorMSG && creator is User) {
                        val creatorPlayer = platform.buildPlayer((creator as User).uuid, instanceState.localeHandler)

                        if (creatorPlayer != null && creatorPlayer.has(creatorAlertPerm) && !creatorPlayer.has(massNotifyPerm))
                            generateCreatorMSG(creatorPlayer.locale).run(creatorPlayer::sendMessage)
                    }

                    if (sendMassNotify) {
                        platform.massNotify(instanceState.localeHandler, massNotifyPerm, generateMassNotify)
                    }
                }
            }

            "ticketmanager:proxy_to_server_tp" -> {
                val (uuid, location) = decodeRequestTP(message)
                proxyJoinMap[uuid.toString()] = location
            }
        }
    }
}