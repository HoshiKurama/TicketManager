package com.github.hoshikurama.ticketmanager.paper

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent
import com.github.hoshikurama.ticketmanager.common.hooks.Sender
import com.github.hoshikurama.ticketmanager.common.hooks.TabComplete
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class PaperTabComplete(
    private val pluginData: PaperTicketManagerPlugin,
    private val perms: Permission
) : TabComplete(), Listener {

    @EventHandler
    fun onTabCompleteAsync(event: AsyncTabCompleteEvent) {
        if (event.buffer.startsWith("/ticket ")) {
            val args = event.buffer
                .replace(" +".toRegex(), " ")
                .split(" ")
                .run { subList(1, this.size) }

            val sender = event.sender
            val tmSender: Sender =
                if (sender is Player) PaperPlayer(sender, pluginData, perms)
                else PaperConsole(pluginData.configState.localeHandler.consoleLocale)

            event.completions = getReturnedTabs(tmSender, args)
        }
    }

    override fun getPermissionGroups(): List<String> = perms.groups.toList()

    override fun getOfflinePlayerNames(): List<String> = Bukkit.getOfflinePlayers().mapNotNull { it.name }

    override fun getOnlineSeenPlayerNames(sender: Sender): List<String> {
        return if (sender is PaperPlayer) {
            val player = sender.pPlayer
            Bukkit.getOnlinePlayers()
                .filter(player::canSee)
                .map { it.name }
        }
        else Bukkit.getOnlinePlayers().map { it.name }
    }

    override fun getWorldNames(): List<String> = Bukkit.getWorlds().map { it.name }
}