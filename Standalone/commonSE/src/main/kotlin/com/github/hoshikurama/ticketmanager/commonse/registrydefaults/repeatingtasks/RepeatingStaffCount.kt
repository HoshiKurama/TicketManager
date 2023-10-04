package com.github.hoshikurama.ticketmanager.commonse.registrydefaults.repeatingtasks

import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.types.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.registry.repeatingtasks.RepeatingTaskExtension
import com.github.hoshikurama.ticketmanager.api.ticket.Assignment
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.misc.templated
import kotlin.time.Duration.Companion.minutes

// Open and Assigned Notify
class RepeatingStaffCount : RepeatingTaskExtension {
    override val frequency = 10.minutes

    override suspend fun onRepeat(
        config: Config,
        locale: Locale,
        database: AsyncDatabase,
        permission: Permission,
        platformFunctions: PlatformFunctions
    ) {
        val (openTickets, _, openCount, _) = database.getOpenTicketsAsync(1, 0)

        platformFunctions.getAllOnlinePlayers()
            .filter { permission.has(it, "ticketmanager.notify.openTickets.scheduled") }
            .forEach { player ->
                val groups = permission.groupNamesOf(player)
                    .map(Assignment::PermissionGroup)

                val assignedCount = openTickets.count {
                    it.assignedTo == Assignment.Player(player.username)
                            || it.assignedTo in groups
                }

                if (assignedCount != 0) {
                    locale.notifyOpenAssigned.parseMiniMessage(
                        "open" templated "$openCount",
                        "assigned" templated "$assignedCount"
                    ).run(player::sendMessage)
                }
            }
    }
}