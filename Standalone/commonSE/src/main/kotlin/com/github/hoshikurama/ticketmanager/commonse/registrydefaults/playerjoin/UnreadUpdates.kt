package com.github.hoshikurama.ticketmanager.commonse.registrydefaults.playerjoin

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.registry.playerjoin.PlayerJoinExtension
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.misc.templated

class UnreadUpdates : PlayerJoinExtension {

    override suspend fun whenPlayerJoins(
        player: CommandSender.OnlinePlayer,
        platformFunctions: PlatformFunctions,
        permission: Permission,
        database: AsyncDatabase,
        config: Config,
        locale: Locale
    ) {
        if (!permission.has(player, "ticketmanager.notify.unreadUpdates.onJoin")) return

        val ids = database.getTicketIDsWithUpdatesForAsync(player.asCreator())
        if (ids.isEmpty()) return

        val template =
            if (ids.size == 1) locale.notifyUnreadUpdateSingle
            else locale.notifyUnreadUpdateMulti
        val tickets = ids.joinToString(", ")

        template.parseMiniMessage("num" templated tickets)
            .run(player::sendMessage)
    }
}