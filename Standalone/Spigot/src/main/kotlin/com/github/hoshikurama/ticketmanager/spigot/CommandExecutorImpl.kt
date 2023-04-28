package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.commonse.datas.GlobalState
import com.github.hoshikurama.ticketmanager.commonse.old.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.commonse.old.pipeline.PurePipeline
import com.github.hoshikurama.ticketmanager.commonse.old.platform.PlatformFunctions
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class CommandExecutorImpl(
    platform: PlatformFunctions,
    instanceState: InstancePluginState,
    globalState: GlobalState,
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
            if (sender is org.bukkit.entity.Player) SpigotPlayer(sender, adventure, localeHandler)
            else SpigotConsole(adventure, localeHandler.consoleLocale)

        super.executeAsync(tmSender, args.toList())
        return false
    }
}