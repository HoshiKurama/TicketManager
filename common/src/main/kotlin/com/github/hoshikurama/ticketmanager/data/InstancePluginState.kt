package com.github.hoshikurama.ticketmanager.data

import com.github.hoshikurama.ticketmanager.Discord
import com.github.hoshikurama.ticketmanager.LocaleHandler
import com.github.hoshikurama.ticketmanager.database.Database
import com.github.hoshikurama.ticketmanager.database.DatabaseBuilders
import com.github.hoshikurama.ticketmanager.misc.UpdateChecker

class InstancePluginState(
    val database: Database,
    val cooldowns: Cooldown,
    val discord: Discord?,
    val databaseBuilders: DatabaseBuilders,
    val localeHandler: LocaleHandler,

    val allowUnreadTicketUpdates: Boolean,
    val pluginUpdateChecker: UpdateChecker,
    val printModifiedStacktrace: Boolean,
    val printFullStacktrace: Boolean,
)