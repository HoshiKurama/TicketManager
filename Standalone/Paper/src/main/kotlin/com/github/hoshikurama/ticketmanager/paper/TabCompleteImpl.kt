package com.github.hoshikurama.ticketmanager.paper

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent
import com.github.hoshikurama.ticketmanager.commonse.old.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.commonse.old.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.commands.CommandSender
import com.github.hoshikurama.ticketmanager.commonse.old.platform.TabComplete
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class TabCompleteImpl(
    platform: PlatformFunctions,
    private val instanceState: InstancePluginState,
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
            val tmSender: CommandSender =
                if (sender is org.bukkit.entity.Player) PaperPlayer(sender, localeHandler, instanceState.proxyServerName)
                else PaperConsole(localeHandler.consoleLocale, instanceState.proxyServerName)

            event.completions = getReturnedTabs(tmSender, args)
        }
    }

    private fun String.isValidCommandStarter(): Boolean {
        return instanceState.localeHandler.getCommandBases()
            .any { startsWith("/$it ") }
    }
}