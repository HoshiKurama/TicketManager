package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.commonse.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.commonse.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.commonse.pipeline.HybridPipeline
import com.github.hoshikurama.ticketmanager.commonse.pipeline.Pipeline
import com.github.hoshikurama.ticketmanager.commonse.pipeline.PurePipeline
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.platform.Sender
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class CommandExecutorImpl(
    private val platform: PlatformFunctions,
    private val instanceState: InstancePluginState,
    private val globalState: GlobalPluginState,
) : CommandExecutor, Pipeline {

    private val purePipeline: PurePipeline = object : PurePipeline(platform, instanceState, globalState) {}
    private val hybridPipeline: HybridPipeline = object : HybridPipeline(platform, instanceState, globalState) {}

    override fun executeAsync(sender: Sender, args: List<String>) {
        if (instanceState.enableProxyMode) hybridPipeline.executeAsync(sender, args)
        else purePipeline.executeAsync(sender, args)
    }
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        val localeHandler = instanceState.localeHandler
        val agnosticSender: Sender =
            if (sender is org.bukkit.entity.Player) PaperPlayer(
                sender,
                localeHandler,
                instanceState.proxyServerName
            )
            else PaperConsole(
                localeHandler.consoleLocale,
                instanceState.proxyServerName
            )

        executeAsync(agnosticSender, args.toList())
        return false
    }
}