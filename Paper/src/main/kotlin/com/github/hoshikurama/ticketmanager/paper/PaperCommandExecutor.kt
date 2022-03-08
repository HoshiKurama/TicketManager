package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.pipeline.PurePipeline
import com.github.hoshikurama.ticketmanager.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.platform.Sender
import net.milkbowl.vault.permission.Permission
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class PaperCommandExecutor(
    platform: PlatformFunctions,
    instanceState: InstancePluginState,
    globalState: GlobalPluginState,
    private val perms: Permission,
) : CommandExecutor, PurePipeline(platform, instanceState, globalState) {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        val localeHandler = instanceState.localeHandler
        val agnosticSender: Sender =
            if (sender is org.bukkit.entity.Player) PaperPlayer(sender, perms, localeHandler)
            else PaperConsole(localeHandler.consoleLocale)

        super.execute(agnosticSender, args.toList())
        return false
    }
}