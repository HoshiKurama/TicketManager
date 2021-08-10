package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.componentDSL.formattedContent
import com.github.hoshikurama.ticketmanager.common.ConfigState
import com.github.hoshikurama.ticketmanager.common.database.Database
import com.github.hoshikurama.ticketmanager.common.hooks.ConfigBuilder
import com.github.hoshikurama.ticketmanager.common.hooks.TicketManagerPlugin
import com.github.hoshikurama.ticketmanager.common.hooks.database.MemoryBuilder
import com.github.hoshikurama.ticketmanager.common.hooks.database.MySQLBuilder
import com.github.hoshikurama.ticketmanager.common.hooks.database.SQLiteBuilder
import com.github.hoshikurama.ticketmanager.common.tryOrNull
import com.github.shynixn.mccoroutine.registerSuspendingEvents
import com.github.shynixn.mccoroutine.setSuspendingExecutor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kyori.adventure.extra.kotlin.text
import java.io.File

class PaperTicketManagerPlugin(
    mainPlugin: PaperPlugin,
    mainDispatcher: CoroutineDispatcher,
    asyncDispatcher: CoroutineDispatcher,
) : TicketManagerPlugin<PaperPlugin>(
    mainPlugin,
    mainDispatcher,
    asyncDispatcher,
) {

    override suspend fun loadConfig(): ConfigState {
        // Creates config file if not found
        if (!File(mainPlugin.dataFolder, "config.yml").exists()) {
            mainPlugin.saveDefaultConfig()

            // Notifies users config was generated after plugin state init
            asyncScope.launch {
                while (!isConfigStateInitialized())
                    delay(100L)
                commandPipeline.pushMassNotify("ticketmanager.notify.warning") { text { formattedContent(it.warningsNoConfig) } }
            }
        }

        // Config file now generated
        mainPlugin.reloadConfig()

        return mainPlugin.config.run {
            ConfigBuilder.buildConfigState(
                mySQLBuilder = MySQLBuilder(
                    getString("MySQL_Host")!!,
                    getString("MySQL_Port")!!,
                    getString("MySQL_DBName")!!,
                    getString("MySQL_Username")!!,
                    getString("MySQL_Password")!!,
                    asyncDispatcher,
                ),
                sqliteBuilder = SQLiteBuilder(mainPlugin.dataFolder.absolutePath),
                memoryBuilder = MemoryBuilder(
                    pathToFolder = mainPlugin.dataFolder.absolutePath,
                    backupFrequency = getLong("Memory_Backup_Frequency", 600)
                ),
                attemptedDB = getString("Database_Mode", "SQLite")!!
                    .let { tryOrNull { Database.Type.valueOf(it) } ?: Database.Type.SQLite },
                allowCooldowns = getBoolean("Use_Cooldowns", false),
                cooldownSeconds = getLong("Cooldown_Time", 0L),
                localeHandlerColourCode = getString("Colour_Code", "&3")!!,
                localeHandlerPreferredLocale = getString("Preferred_Locale", "en_ca")!!,
                localeHandlerConsoleLocale = getString("Console_Locale", "en_ca")!!,
                localeHandlerForceLocale = getBoolean("Force_Locale", false),
                allowUnreadTicketUpdates = getBoolean("Allow_Unread_Ticket_Updates", true),
                checkForPluginUpdates = getBoolean("Allow_UpdateChecking", true),
                pluginVersion = mainPlugin.description.version,
                enableDiscord = getBoolean("Use_Discord_Bot", false),
                DiscordNotifyOnAssign = getBoolean("Discord_Notify_On_Assign", false),
                DiscordNotifyOnClose = getBoolean("Discord_Notify_On_Close", false),
                DiscordNotifyOnCloseAll = getBoolean("Discord_Notify_On_Close_All", false),
                DiscordNotifyOnComment = getBoolean("Discord_Notify_On_Comment", false),
                DiscordNotifyOnCreate = getBoolean("Discord_Notify_On_Create", false),
                DiscordNotifyOnReopen = getBoolean("Discord_Notify_On_Reopen", false),
                DiscordNotifyOnPriorityChange = getBoolean("Discord_Notify_On_Priority_Change", false),
                DiscordToken = getString("Discord_Bot_Token", "")!!,
                DiscordChannelID = getLong("Discord_Channel_ID", -1),
                asyncDispatcher = asyncDispatcher,
                asyncScope = asyncScope
            )
        }
    }

    override suspend fun performRegistration() {
        // Register events and commands
        configState.localeHandler.getCommandBases().forEach {
            mainPlugin.getCommand(it)!!.setSuspendingExecutor(commandPipeline as PaperCommandPipeline)
            mainPlugin.server.pluginManager.registerEvents(PaperTabComplete(this, mainPlugin.perms), mainPlugin)
            // Remember to register any keyword in plugin.yml
        }
        // Registers player join event
        mainPlugin.server.pluginManager.registerSuspendingEvents(PaperPlayerJoinEvent(this, mainPlugin.perms), mainPlugin)
    }
}