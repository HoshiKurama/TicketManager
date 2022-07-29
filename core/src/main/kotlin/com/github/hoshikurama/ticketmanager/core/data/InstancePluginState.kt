package com.github.hoshikurama.ticketmanager.core.data

import com.github.hoshikurama.ticketmanager.common.UpdateChecker
import com.github.hoshikurama.ticketmanager.core.Discord
import com.github.hoshikurama.ticketmanager.core.LocaleHandler
import com.github.hoshikurama.ticketmanager.core.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.core.database.DatabaseBuilders
import java.util.concurrent.atomic.AtomicReference

class InstancePluginState(
    val database: AsyncDatabase,
    val cooldowns: Cooldown,
    val discord: Discord?,
    val databaseBuilders: DatabaseBuilders,
    val localeHandler: LocaleHandler,
    val pluginUpdate: AtomicReference<UpdateChecker>,

    val allowUnreadTicketUpdates: Boolean,
    val printModifiedStacktrace: Boolean,
    val printFullStacktrace: Boolean,
    val pluginUpdateFreq: Long,

    // Proxy
    val enableProxyMode: Boolean,
    val proxyServerName: String?,
    val allowProxyUpdatePings: Boolean,
    val cachedProxyUpdate: AtomicReference<Pair<String, String>?>,
)