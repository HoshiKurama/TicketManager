package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.core.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.core.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.core.pipeline.HybridPipeline
import com.github.hoshikurama.ticketmanager.core.pipeline.Pipeline
import com.github.hoshikurama.ticketmanager.core.pipeline.PurePipeline
import com.github.hoshikurama.ticketmanager.core.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.core.platform.Sender
import net.milkbowl.vault.permission.Permission
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class PaperCommandExecutor(
    private val platform: PlatformFunctions,
    private val instanceState: InstancePluginState,
    private val globalState: GlobalPluginState,
    private val perms: Permission,
) : CommandExecutor, Pipeline {

    private val purePipeline: PurePipeline = object : PurePipeline(platform, instanceState, globalState) {}
    private val hybridPipeline: HybridPipeline = object : HybridPipeline(platform, instanceState, globalState) {}

    override fun execute(sender: Sender, args: List<String>) {
        if (instanceState.enableProxyMode) hybridPipeline.execute(sender, args)
        else purePipeline.execute(sender, args)
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
                perms,
                localeHandler,
                instanceState.proxyServerName
            )
            else PaperConsole(
                localeHandler.consoleLocale,
                instanceState.proxyServerName
            )

        execute(agnosticSender, args.toList())
        return false
    }
}