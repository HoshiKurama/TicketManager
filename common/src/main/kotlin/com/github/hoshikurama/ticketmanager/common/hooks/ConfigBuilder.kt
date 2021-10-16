package com.github.hoshikurama.ticketmanager.common.hooks

import com.github.hoshikurama.ticketmanager.common.*
import com.github.hoshikurama.ticketmanager.common.database.Database
import com.github.hoshikurama.ticketmanager.common.hooks.database.MemoryBuilder
import com.github.hoshikurama.ticketmanager.common.hooks.database.MySQLBuilder
import com.github.hoshikurama.ticketmanager.common.hooks.database.SQLiteBuilder
import kotlinx.coroutines.*

object ConfigBuilder {
    suspend fun buildConfigState(
        mySQLBuilder: MySQLBuilder,
        sqliteBuilder: SQLiteBuilder,
        memoryBuilder: MemoryBuilder,
        attemptedDB: Database.Type,
        allowCooldowns: Boolean,
        cooldownSeconds: Long,
        localeHandlerColourCode: String,
        localeHandlerPreferredLocale: String,
        localeHandlerConsoleLocale: String,
        localeHandlerForceLocale: Boolean,
        allowUnreadTicketUpdates: Boolean,
        checkForPluginUpdates: Boolean,
        pluginVersion: String,
        enableDiscord: Boolean,
        DiscordNotifyOnAssign: Boolean,
        DiscordNotifyOnClose: Boolean,
        DiscordNotifyOnCloseAll: Boolean,
        DiscordNotifyOnComment: Boolean,
        DiscordNotifyOnCreate: Boolean,
        DiscordNotifyOnReopen: Boolean,
        DiscordNotifyOnPriorityChange: Boolean,
        DiscordToken: String,
        DiscordChannelID: Long,
        asyncDispatcher: CoroutineDispatcher,
        asyncScope: CoroutineScope,
    ) = coroutineScope {

        val cooldowns = Cooldown(allowCooldowns, cooldownSeconds)

        val deferredDatabase = async {
            try {
                when (attemptedDB) {
                    Database.Type.SQLite -> sqliteBuilder
                    Database.Type.MySQL -> mySQLBuilder
                    Database.Type.Memory -> memoryBuilder
                }
                    .build()!!
                    .apply { initialiseDatabase() }
            } catch (e: Exception) {
                sqliteBuilder.build().apply { initialiseDatabase() }
            }
        }

        val deferredLocaleHandler = async {
            try {
                LocaleHandler.buildLocalesAsync(
                    localeHandlerColourCode,
                    localeHandlerPreferredLocale,
                    localeHandlerConsoleLocale,
                    localeHandlerForceLocale
                )
            } catch (e: Exception) {
                LocaleHandler.buildLocalesAsync(
                    mainColourCode = "&3",
                    preferredLocale = "en_ca",
                    console_Locale = "en_ca",
                    forceLocale = false,
                )
            }
        }

        val deferredPluginUpdate = async {
            if (checkForPluginUpdates) {
                val latestVersion = UpdateChecker(91178).getLatestVersion()
                    .run { this ?: pluginVersion }

                if (pluginVersion == latestVersion) return@async null

                val curVersSplit = pluginVersion.split(".").map(String::toInt)
                val latestVersSplit = latestVersion.split(".").map(String::toInt)

                for (i in 0..latestVersSplit.lastIndex) {
                    if (curVersSplit[i] > latestVersSplit[i])
                        return@async null
                }
                return@async Pair(pluginVersion, latestVersion)
            }
            return@async null
        }

        val deferredDiscord = async {
            try {
                if (!enableDiscord) return@async null

                return@async Discord.create(
                    DiscordNotifyOnAssign,
                    DiscordNotifyOnClose,
                    DiscordNotifyOnCloseAll,
                    DiscordNotifyOnComment,
                    DiscordNotifyOnCreate,
                    DiscordNotifyOnReopen,
                    DiscordNotifyOnPriorityChange,
                    DiscordToken,
                    DiscordChannelID,
                    deferredLocaleHandler.await().consoleLocale,
                    asyncDispatcher,
                )

            } catch (e: Exception) {
                return@async null
            }
        }

        // Build final config object
        val configState = ConfigState(
            cooldowns,
            deferredDatabase.await(),
            deferredLocaleHandler.await(),
            allowUnreadTicketUpdates,
            deferredPluginUpdate,
            deferredDiscord.await(),
            mySQLBuilder,
            sqliteBuilder,
            memoryBuilder,
        )

        // Launch Discord coroutine login if requested
        configState.discord?.run {
            asyncScope.launch {
                try { login() }
                catch (e: Exception) { e.printStackTrace() }
            }
        }

        return@coroutineScope configState
    }
}