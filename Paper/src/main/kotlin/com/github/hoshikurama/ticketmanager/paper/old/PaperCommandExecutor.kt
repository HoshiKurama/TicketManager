package com.github.hoshikurama.ticketmanager.paper.old

import com.github.hoshikurama.ticketmanager.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.platform.CommandPipeline
import com.github.hoshikurama.ticketmanager.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.platform.Sender
import com.github.shynixn.mccoroutine.SuspendingCommandExecutor
import net.milkbowl.vault.permission.Permission
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

class PaperCommandExecutor(
    platform: PlatformFunctions,
    instanceState: InstancePluginState,
    globalState: GlobalPluginState,
    private val perms: Permission,
) : SuspendingCommandExecutor, CommandPipeline(platform, instanceState, globalState) {

    override suspend fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        val localeHandler = instanceState.localeHandler
        val agnosticSender: Sender =
            if (sender is org.bukkit.entity.Player) PaperPlayer(sender, perms, localeHandler)
            else PaperConsole(localeHandler.consoleLocale)

        return super.execute(agnosticSender, args.toList())
    }
}