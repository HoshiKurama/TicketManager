package com.github.hoshikurama.ticketmanager.commonse.data

import com.github.hoshikurama.ticketmanager.common.UpdateChecker
import com.github.hoshikurama.ticketmanager.common.discord.Discord
import com.github.hoshikurama.ticketmanager.commonse.LocaleHandler
import com.github.hoshikurama.ticketmanager.commonse.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.commonse.database.DatabaseBuilders
import java.util.concurrent.atomic.AtomicReference

class InstancePluginState(
    val database: AsyncDatabase,
    val cooldowns: Cooldown,
    val discord: Discord?,
    val discordSettings: Discord.Settings,
    val databaseBuilders: DatabaseBuilders,
    val localeHandler: LocaleHandler,
    val pluginUpdate: AtomicReference<UpdateChecker>,

    val allowUnreadTicketUpdates: Boolean,
    val printModifiedStacktrace: Boolean,
    val printFullStacktrace: Boolean,

    // Proxy
    val enableProxyMode: Boolean,
    val proxyServerName: String?,
    val allowProxyUpdatePings: Boolean,
    val cachedProxyUpdate: AtomicReference<Pair<String, String>?>,
)