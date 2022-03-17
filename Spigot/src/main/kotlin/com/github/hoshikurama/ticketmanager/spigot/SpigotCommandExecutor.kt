package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.pipeline.PurePipeline
import com.github.hoshikurama.ticketmanager.platform.PlatformFunctions
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.milkbowl.vault.permission.Permission
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class SpigotCommandExecutor(
    platform: PlatformFunctions,
    instanceState: InstancePluginState,
    globalState: GlobalPluginState,
    private val perms: Permission,
    private val adventure: BukkitAudiences,
) : CommandExecutor, PurePipeline(platform, instanceState, globalState) {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        val localeHandler = instanceState.localeHandler
        val tmSender =
            if (sender is org.bukkit.entity.Player) SpigotPlayer(sender, perms, adventure, localeHandler)
            else SpigotConsole(adventure, localeHandler.consoleLocale)

        super.execute(tmSender, args.toList())
        return false
    }
}