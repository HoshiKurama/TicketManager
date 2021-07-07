package com.hoshikurama.github.ticketmanager.paper.events

import com.github.shynixn.mccoroutine.SuspendingTabCompleter
import org.bukkit.command.Command
import org.bukkit.command.CommandSender

class TabComplete : SuspendingTabCompleter {

    override suspend fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        TODO("Not yet implemented")
    }
}