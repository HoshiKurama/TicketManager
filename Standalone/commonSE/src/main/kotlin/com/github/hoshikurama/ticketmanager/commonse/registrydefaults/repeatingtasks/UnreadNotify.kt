package com.github.hoshikurama.ticketmanager.commonse.registrydefaults.repeatingtasks

import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.registry.repeatingtasks.RepeatingTaskExtension
import com.github.hoshikurama.ticketmanager.api.ticket.Creator
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.misc.templated
import kotlin.time.Duration.Companion.minutes

// Mass Unread Notify Period Task
class UnreadNotify : RepeatingTaskExtension {
    override val frequency = 10.minutes

    override suspend fun onRepeat(
        config: Config,
        locale: Locale,
        database: AsyncDatabase,
        permission: Permission,
        platformFunctions: PlatformFunctions
    ) {
        if (!config.allowUnreadTicketUpdates) return

        platformFunctions.getAllOnlinePlayers()
            .filter { permission.has(it, "ticketmanager.notify.unreadUpdates.scheduled") }
            .forEach {
                val ids = database.getOpenTicketIDsForUser(Creator.User(it.uuid))
                val tickets = ids.joinToString(", ")

                if (ids.isEmpty()) return

                val template = if (ids.size > 1) locale.notifyUnreadUpdateMulti
                else locale.notifyUnreadUpdateSingle

                template.parseMiniMessage("num" templated tickets)
                    .run(it::sendMessage)
            }
    }
}