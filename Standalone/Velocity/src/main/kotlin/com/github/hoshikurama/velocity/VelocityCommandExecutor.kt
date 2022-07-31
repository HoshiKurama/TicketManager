package com.github.hoshikurama.velocity

/*
NOTE: THIS IS FOR EVENTUAL PURE VELOCITY PLUGIN

import com.github.hoshikurama.ticketmanager.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.platform.CommandPipeline
import com.github.hoshikurama.ticketmanager.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.platform.Sender
import com.github.hoshikurama.ticketmanager.platform.TabComplete
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ConsoleCommandSource
import java.util.concurrent.CompletableFuture

class VelocityTabComplete(platform: PlatformFunctions): TabComplete(platform)

class VelocityCommands(
    platform: PlatformFunctions,
    instanceState: InstancePluginState,
    globalState: GlobalPluginState,
): SimpleCommand, CommandPipeline(platform, instanceState, globalState) {
    val tabComplete = VelocityTabComplete(platform)

    override fun suggestAsync(invocation: SimpleCommand.Invocation?): CompletableFuture<MutableList<String>> {
        if (invocation?.source() == null) return CompletableFuture.completedFuture(mutableListOf())

        return CompletableFuture.supplyAsync {
            val sender: Sender = invocation.source().let { source ->
                val localeHandler = instanceState.localeHandler

                when (source) {
                    is com.velocitypowered.api.proxy.Player -> VelocityPlayer.buildForSuggest(source, localeHandler)
                    is ConsoleCommandSource -> VelocityConsole(localeHandler.consoleLocale, source)
                    else -> throw Exception("Attempted sender is neither Player or Console")
                }
            }

            tabComplete.getReturnedTabs(sender, invocation.arguments().toList()).toMutableList()
        }
    }

    override fun execute(invocation: SimpleCommand.Invocation?) {
        if (invocation == null) return
    }
}
 */

