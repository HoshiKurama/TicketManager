package com.github.hoshikurama.ticketmanager.commonse.commands

import com.github.hoshikurama.ticketmanager.api.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.ticket.TicketCreator
import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.commonse.TMCoroutine
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.datas.ConfigState
import com.github.hoshikurama.ticketmanager.commonse.misc.pushErrors
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions

fun executeNotificationsAsync(
    platform: PlatformFunctions,
    configState: ConfigState,
    commandSender: CommandSender.Active,
    activeLocale: TMLocale,
    message: MessageNotification<CommandSender.Active>,
) {
    TMCoroutine.launchSupervised {
        try {
            message.run {
                //  Send to other servers if needed
                if (configState.enableProxyMode && configState.proxyServerName != null) {
                    platform.relayMessageToProxy(Server2Proxy.NotificationSharing.waterfallString(), encodeForProxy())
                }

                if (sendSenderMSG)
                    generateSenderMSG(activeLocale).run(commandSender::sendMessage)

                if (sendCreatorMSG && ticketCreator is TicketCreator.User) {
                    val creatorPlayer = platform.buildPlayer((ticketCreator as TicketCreator.User).uuid)

                    if (creatorPlayer != null && creatorPlayer.has(creatorAlertPerm) && !creatorPlayer.has(massNotifyPerm))
                        generateCreatorMSG(activeLocale).run(creatorPlayer::sendMessage)
                }



                if (sendMassNotify)
                    platform.massNotify(massNotifyPerm, generateMassNotify(activeLocale))
            }
        } catch (e: Exception) {
            pushErrors(platform, configState, activeLocale, e, TMLocale::consoleErrorCommandExecution)
            commandSender.sendMessage(activeLocale.warningsUnexpectedError)
        }
    }
}