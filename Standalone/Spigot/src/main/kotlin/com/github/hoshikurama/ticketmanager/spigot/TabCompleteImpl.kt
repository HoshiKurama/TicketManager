package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.commonse.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.platform.TabComplete
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.milkbowl.vault.permission.Permission
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class TabCompleteImpl(
    platform: PlatformFunctions,
    private val instanceState: InstancePluginState,
    private val perms: Permission,
    private val adventure: BukkitAudiences,
) : TabComplete(platform), TabCompleter {

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        val tmSender =
            if (sender is org.bukkit.entity.Player) SpigotPlayer(sender, perms, adventure, instanceState.localeHandler)
            else SpigotConsole(adventure, instanceState.localeHandler.consoleLocale)

        return getReturnedTabs(tmSender, args.toList()).toMutableList()
    }
}