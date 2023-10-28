package com.github.hoshikurama.ticketmanager.commonse.registrydefaults.playerjoin

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.registry.playerjoin.PlayerJoinExtension
import com.github.hoshikurama.ticketmanager.api.ticket.Assignment
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.misc.templated
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

// View Open-Count and Assigned-Count Tickets
class StaffCount : PlayerJoinExtension {

    override suspend fun whenPlayerJoins(
        player: CommandSender.OnlinePlayer,
        platformFunctions: PlatformFunctions,
        permission: Permission,
        database: AsyncDatabase,
        config: Config,
        locale: Locale
    ): Unit = coroutineScope {
        if (!permission.has(player, "ticketmanager.notify.openTickets.onJoin"))
            return@coroutineScope

        val openDeferred = async { database.countOpenTicketsAsync() }
        val assignedDeferred = async {
            val assignments = permission
                .groupNamesOf(player)
                .map(Assignment::PermissionGroup) + Assignment.Player(player.username)
            database.countOpenTicketsAssignedToAsync(assignments)
        }

        if (openDeferred.await() != 0L) {
            locale.notifyOpenAssigned.parseMiniMessage(
                "open" templated openDeferred.await().toString(),
                "assigned" templated assignedDeferred.await().toString()
            ).run(player::sendMessage)
        }
    }
}