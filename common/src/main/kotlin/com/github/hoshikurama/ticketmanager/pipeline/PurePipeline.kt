package com.github.hoshikurama.ticketmanager.pipeline

import com.github.hoshikurama.ticketmanager.TMLocale
import com.github.hoshikurama.ticketmanager.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.misc.pushErrors
import com.github.hoshikurama.ticketmanager.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.platform.Sender
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
            .thenApplyAsync { params ->
                params?.run {
                    if (sendSenderMSG)
                        senderLambda!!(sender.locale)
                            .run(sender::sendMessage)

                    if (sendCreatorMSG)
                        creator?.let { creatorLambda!!(it.locale) }
                            ?.run(creator::sendMessage)

                    if (sendMassNotifyMSG)
                        platform.massNotify(instanceState.localeHandler, massNotifyPerm, massNotifyLambda!!)
                }
            }
            .exceptionallyAsync {
                pushErrors(platform, instanceState, it as Exception, TMLocale::consoleErrorCommandExecution)
                sender.sendMessage(sender.locale.warningsUnexpectedError)
            }
    }
}