package com.github.hoshikurama.ticketmanager.paper

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent
import com.github.hoshikurama.ticketmanager.core.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.core.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.core.platform.Sender
import com.github.hoshikurama.ticketmanager.core.platform.TabComplete

import net.milkbowl.vault.permission.Permission
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class PaperTabComplete(
    platform: PlatformFunctions,
    private val instanceState: InstancePluginState,
    private val perms: Permission,
) : TabComplete(platform), Listener {

    @EventHandler
    fun onTabCompleteAsync(event: AsyncTabCompleteEvent) {

        if (event.buffer.isValidCommandStarter()) {
            val args = event.buffer
                .replace(" +".toRegex(), " ")
                .split(" ")
                .run { subList(1, this.size) }

            val sender = event.sender
            val localeHandler = instanceState.localeHandler
            val tmSender: Sender =
                if (sender is org.bukkit.entity.Player) PaperPlayer(sender, perms, localeHandler, instanceState.proxyServerName)
                else PaperConsole(localeHandler.consoleLocale, instanceState.proxyServerName)

            event.completions = getReturnedTabs(tmSender, args)
        }
    }

    private fun String.isValidCommandStarter(): Boolean {
        return instanceState.localeHandler.getCommandBases()
            .any { startsWith("/$it ") }
    }
}