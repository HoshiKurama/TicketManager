package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.api.ticket.Creator
import com.github.hoshikurama.ticketmanager.common.Proxy2Server
import com.github.hoshikurama.ticketmanager.common.ProxyUpdate
import com.github.hoshikurama.ticketmanager.common.randServerIdentifier
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.commands.MessageNotification
import com.github.hoshikurama.ticketmanager.commonse.datas.ConfigState
import com.github.hoshikurama.ticketmanager.commonse.misc.decodeRequestTP
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import com.google.common.io.ByteStreams
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.*

class Proxy(
    private val platform: PlatformFunctions,
    private val configState: ConfigState,
    private val activeLocale: TMLocale,
): PluginMessageListener {

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        when (channel) {

            Proxy2Server.NotificationSharing.name -> {
                @Suppress("UnstableApiUsage")
                val input = ByteStreams.newDataInput(message)

                // Filters out same server
                if (input.readUTF().run(UUID::fromString).equals(randServerIdentifier)) return

                val notification = when (input.readUTF().run(MessageNotification.MessageType::valueOf)) {
                    MessageNotification.MessageType.ASSIGN -> MessageNotification.Assign.decode(input)
                    MessageNotification.MessageType.CLOSEWITHCOMMENT -> MessageNotification.CloseWithComment.decode(
                        input
                    )
                    MessageNotification.MessageType.CLOSEWITHOUTCOMMENT -> MessageNotification.CloseWithoutComment.decode(
                        input
                    )
                    MessageNotification.MessageType.MASSCLOSE -> MessageNotification.MassClose.decode(input)
                    MessageNotification.MessageType.COMMENT -> MessageNotification.Comment.decode(input)
                    MessageNotification.MessageType.CREATE -> MessageNotification.Create.decode(input)
                    MessageNotification.MessageType.REOPEN -> MessageNotification.Reopen.decode(input)
                    MessageNotification.MessageType.SETPRIORITY -> MessageNotification.SetPriority.decode(input)
                }

                notification.run {
                    if (sendCreatorMSG && ticketCreator is Creator.User) {
                        val creatorPlayer = platform.buildPlayer((ticketCreator as Creator.User).uuid)

                        if (creatorPlayer != null && creatorPlayer.has(creatorAlertPerm) && !creatorPlayer.has(massNotifyPerm))
                            generateCreatorMSG(activeLocale).run(creatorPlayer::sendMessage)
                    }

                    if (sendMassNotify)
                        platform.massNotify(massNotifyPerm, generateMassNotify(activeLocale))
                }
            }

            Proxy2Server.Teleport.name -> {
                val (uuid, location) = decodeRequestTP(message)
                proxyJoinMap[uuid.toString()] = location
            }

            Proxy2Server.ProxyVersionRequest.name -> {
                val (curVer, latestVer) = ProxyUpdate.decodeServerMsg(message)
                configState.proxyUpdate = curVer to latestVer
            }
        }
    }
}