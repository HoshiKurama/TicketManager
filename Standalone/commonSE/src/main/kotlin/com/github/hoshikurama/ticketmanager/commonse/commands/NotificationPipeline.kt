package com.github.hoshikurama.ticketmanager.commonse.commands

import com.github.hoshikurama.ticketmanager.api.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.ticket.TicketCreator
import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.commonse.TMCoroutine
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.datas.ConfigState
import com.github.hoshikurama.ticketmanager.commonse.datas.GlobalState
import com.github.hoshikurama.ticketmanager.commonse.misc.pushErrors
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions



//TODO SPLIT PROXY FROM TICKETCREATOR
inline fun executeNotificationsAsync(
    platform: PlatformFunctions,
    configState: ConfigState,
    commandSender: CommandSender.Active,
    crossinline command: suspend () -> MessageNotification<CommandSender.Active>,
) {
    TMCoroutine.launchSupervised {
        try {
            command.invoke().run {
                //  Send to other servers if needed
                if (configState.enableProxyMode && configState.proxyServerName != null) {
                    platform.relayMessageToProxy(Server2Proxy.NotificationSharing.waterfallString(), encodeForProxy())
                }

                if (sendCreatorMSG && ticketCreator is TicketCreator.User) {
                    val creatorPlayer = platform.buildPlayer((ticketCreator as TicketCreator.User).uuid)

                    if (creatorPlayer != null && creatorPlayer.has(creatorAlertPerm) && !creatorPlayer.has(
                            massNotifyPerm
                        )
                    )
                        generateCreatorMSG().run(creatorPlayer::sendMessage)
                }

                if (sendMassNotify)
                    platform.massNotify(massNotifyPerm, generateMassNotify())
            }
        } catch (e: Exception) {
            pushErrors(platform, configState, e, TMLocale::consoleErrorCommandExecution)
            commandSender.sendMessage(GlobalState.activeLocale.warningsUnexpectedError)
        }
    }
}
