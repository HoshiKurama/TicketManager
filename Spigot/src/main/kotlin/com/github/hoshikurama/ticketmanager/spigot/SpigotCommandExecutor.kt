package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.platform.CommandPipeline
import com.github.hoshikurama.ticketmanager.platform.PlatformFunctions
import com.github.shynixn.mccoroutine.SuspendingCommandExecutor
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.milkbowl.vault.permission.Permission
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

class SpigotCommandExecutor(
    platform: PlatformFunctions,
    instanceState: InstancePluginState,
    globalState: GlobalPluginState,
    private val perms: Permission,
    private val adventure: BukkitAudiences,
) : CommandPipeline(platform, instanceState, globalState), SuspendingCommandExecutor {

    override suspend fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        val localeHandler = instanceState.localeHandler
        val tmSender =
            if (sender is org.bukkit.entity.Player) SpigotPlayer(sender, perms, adventure, localeHandler)
            else SpigotConsole(adventure, localeHandler.consoleLocale)

        return super.execute(tmSender, args.toList())
    }
}