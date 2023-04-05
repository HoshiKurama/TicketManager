package com.github.hoshikurama.ticketmanager.commonse.pipeline

import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.commonse.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.commonse.misc.TMCoroutine
import com.github.hoshikurama.ticketmanager.commonse.misc.pushErrors
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.platform.Sender
import com.github.hoshikurama.ticketmanager.commonse.ticket.User

abstract class HybridPipeline(
    val platform: PlatformFunctions,
    val instanceState: InstancePluginState,
    globalState: GlobalPluginState,
) : Pipeline {
    private val corePipeline = CorePipeline(platform, instanceState, globalState)

    override fun executeAsync(sender: Sender, args: List<String>) {
        TMCoroutine.runAsync {
            try {
                corePipeline.executeLogic(sender, args)?.run {
                    //  Send to other servers
                    platform.relayMessageToProxy("ticketmanager:inform_proxy", encodeForProxy())

                    // Send notification to current server
                    if (sendSenderMSG)
                        generateSenderMSG(sender.locale).run(sender::sendMessage)

                    if (sendCreatorMSG && creator is User) {
                        val creatorPlayer = platform.buildPlayer((creator as User).uuid, instanceState.localeHandler)

                        if (creatorPlayer != null && creatorPlayer.has(creatorAlertPerm) && !creatorPlayer.has(massNotifyPerm))
                            generateCreatorMSG(creatorPlayer.locale).run(creatorPlayer::sendMessage)
                    }

                    if (sendMassNotify)
                        platform.massNotify(instanceState.localeHandler, massNotifyPerm, generateMassNotify)
                }
            } catch (e: Exception) {
                pushErrors(platform, instanceState, e, TMLocale::consoleErrorCommandExecution)
                sender.sendMessage(sender.locale.warningsUnexpectedError)
            }
        }
    }
}