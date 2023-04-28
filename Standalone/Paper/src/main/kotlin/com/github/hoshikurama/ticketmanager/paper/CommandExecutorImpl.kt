package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.commonse.datas.GlobalState
import com.github.hoshikurama.ticketmanager.commonse.old.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.commonse.old.pipeline.HybridPipeline
import com.github.hoshikurama.ticketmanager.commonse.old.pipeline.Pipeline
import com.github.hoshikurama.ticketmanager.commonse.old.pipeline.PurePipeline
import com.github.hoshikurama.ticketmanager.commonse.old.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.commands.CommandSender
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class CommandExecutorImpl(
    private val platform: PlatformFunctions,
    private val instanceState: InstancePluginState,
    private val globalState: GlobalState,
) : CommandExecutor, Pipeline {

    private val purePipeline: PurePipeline = object : PurePipeline(platform, instanceState, globalState) {}
    private val hybridPipeline: HybridPipeline = object : HybridPipeline(platform, instanceState, globalState) {}

    override fun executeAsync(sender: com.github.hoshikurama.ticketmanager.commonse.commands.CommandSender, args: List<String>) {
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
        val agnosticSender: com.github.hoshikurama.ticketmanager.commonse.commands.CommandSender =
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