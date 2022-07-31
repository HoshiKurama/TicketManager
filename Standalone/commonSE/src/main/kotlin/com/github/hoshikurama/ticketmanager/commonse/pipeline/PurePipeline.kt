package com.github.hoshikurama.ticketmanager.commonse.pipeline

import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.commonse.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.commonse.misc.pushErrors
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.platform.Sender
import com.github.hoshikurama.ticketmanager.commonse.ticket.User
import java.util.concurrent.CompletableFuture

abstract class PurePipeline(
    val platform: PlatformFunctions,
    val instanceState: InstancePluginState,
    globalState: GlobalPluginState,
) : Pipeline {
    private val corePipeline = CorePipeline(platform, instanceState, globalState)

    override fun execute(sender: Sender, args: List<String>) {
        CompletableFuture.supplyAsync { corePipeline.executeLogic(sender, args) }
            .thenComposeAsync { it }
            .thenApplyAsync { notification ->
                notification?.run {

                    if (sendSenderMSG)
                        generateSenderMSG(sender.locale).run(sender::sendMessage)

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
            .exceptionallyAsync {
                pushErrors(platform, instanceState, it as Exception, TMLocale::consoleErrorCommandExecution)
                sender.sendMessage(sender.locale.warningsUnexpectedError)
            }
    }
}