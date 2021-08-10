package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.common.hooks.Sender
import com.github.hoshikurama.ticketmanager.common.hooks.TabComplete
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class SpigotTabComplete(
    private val pluginData: SpigotTicketManagerPlugin,
    private val perms: Permission,
    private val adventure: BukkitAudiences,
) : TabComplete(), TabCompleter {

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        val tmSender =
            if (sender is org.bukkit.entity.Player) SpigotPlayer(sender, pluginData, perms, adventure)
            else SpigotConsole(pluginData.configState.localeHandler.consoleLocale, adventure)

        return getReturnedTabs(tmSender, args.toList()).toMutableList()
    }


    override fun getPermissionGroups(): List<String> = perms.groups.toList()

    override fun getOfflinePlayerNames(): List<String> = Bukkit.getOfflinePlayers().mapNotNull { it.name }

    override fun getOnlineSeenPlayerNames(sender: Sender): List<String> {
        return if (sender is SpigotPlayer) {
            val player = sender.sPlayer
            Bukkit.getOnlinePlayers()
                .filter(player::canSee)
                .map { it.name }
        }
        else Bukkit.getOnlinePlayers().map { it.name }
    }

    override fun getWorldNames(): List<String> = Bukkit.getWorlds().map { it.name }
}