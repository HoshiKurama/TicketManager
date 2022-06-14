package com.github.hoshikurama.ticketmanager.core.data

import com.github.hoshikurama.ticketmanager.common.UpdateChecker
import com.github.hoshikurama.ticketmanager.core.Discord
import com.github.hoshikurama.ticketmanager.core.LocaleHandler
import com.github.hoshikurama.ticketmanager.core.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.core.database.DatabaseBuilders

class InstancePluginState(
    val database: AsyncDatabase,
    val cooldowns: Cooldown,
    val discord: Discord?,
    val databaseBuilders: DatabaseBuilders,
    val localeHandler: LocaleHandler,

    val allowUnreadTicketUpdates: Boolean,
    val pluginUpdateChecker: UpdateChecker,
    val printModifiedStacktrace: Boolean,
    val printFullStacktrace: Boolean,

    val enableProxyMode: Boolean,
    val proxyServerName: String?,
)