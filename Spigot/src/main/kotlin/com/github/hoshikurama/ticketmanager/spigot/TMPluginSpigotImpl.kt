package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.TMPlugin
import com.github.hoshikurama.ticketmanager.metricsKey
import com.github.hoshikurama.ticketmanager.misc.ConfigParameters
import com.github.shynixn.mccoroutine.SuspendingCommandExecutor
import com.github.shynixn.mccoroutine.registerSuspendingEvents
import com.github.shynixn.mccoroutine.setSuspendingExecutor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import org.bukkit.command.TabCompleter
import org.bukkit.event.Listener
import java.io.File

class TMPluginSpigotImpl(
    private val spigotPlugin: SpigotPlugin,
    private val perms: Permission,
    private val adventure: BukkitAudiences,
    mainDispatcher: CoroutineDispatcher,
    asyncDispatcher: CoroutineDispatcher,
) : TMPlugin(
    mainDispatcher = mainDispatcher,
    asyncDispatcher = asyncDispatcher,
    platformFunctions = SpigotFunctions(perms, adventure),
    buildPipeline = { platform,instance,global -> SpigotCommandExecutor(platform, instance, global, perms, adventure) },
    buildTabComplete = { platform, instance -> SpigotTabComplete(platform, instance, perms, adventure) },
    buildJoinEvent = { global, instance -> SpigotJoinEvent(global, instance, perms, adventure) },
) {
    private lateinit var metrics: Metrics

    override fun performSyncBefore() {
        // Launch Metrics
        metrics = Metrics(spigotPlugin, metricsKey)
        metrics.addCustomChart(
            Metrics.SingleLineChart("tickets_made") {
                runBlocking {
                    val ticketCount = globalPluginState.ticketCountMetrics.get()
                    globalPluginState.ticketCountMetrics.set(0)
                    ticketCount
                }
            }
        )
        metrics.addCustomChart(
            Metrics.SimplePie("database_type") {
                runBlocking {
                    while (globalPluginState.pluginLocked.get()) delay(100L)
                    instancePluginState.database.type.name
                }
            }
        )

        metrics.addCustomChart(Metrics.SimplePie("plugin_platform") { "Spigot" })
    }

    override fun performAsyncTaskTimer(action: () -> Unit) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(spigotPlugin, Runnable { action() }, 120, 12000)
    }

    override fun configExists(): Boolean {
        return File(spigotPlugin.dataFolder, "config.yml").exists()
    }

    override fun generateConfig() {
        spigotPlugin.saveDefaultConfig()
    }

    override fun reloadConfig() {
        spigotPlugin.reloadConfig()
    }

    override fun readConfig(): ConfigParameters {
        return spigotPlugin.config.run {
            ConfigParameters(
                MySQLHost = getString("MySQL_Host"),
                mySQLPort = getString("MySQL_Port"),
                mySQLDBName = getString("MySQL_DBName"),
                mySQLUsername = getString("MySQL_Username"),
                mySQLPassword = getString("MySQL_Password"),
                pluginFolderPath = spigotPlugin.dataFolder.absolutePath,
                memoryFrequency = getLong("Memory_Backup_Frequency"),
                dbTypeAsStr = getString("Database_Mode"),
                allowCooldowns = getBoolean("Use_Cooldowns"),
                cooldownSeconds = getLong("Cooldown_Time"),
                localeHandlerColourCode = getString("Colour_Code"),
                localeHandlerPreferredLocale = getString("Preferred_Locale"),
                localeHandlerConsoleLocale = getString("Console_Locale"),
                localeHandlerForceLocale = getBoolean("Force_Locale"),
                allowUnreadTicketUpdates = getBoolean("Allow_Unread_Ticket_Updates"),
                checkForPluginUpdates = getBoolean("Allow_UpdateChecking"),
                enableDiscord = getBoolean("Use_Discord_Bot"),
                DiscordNotifyOnAssign = getBoolean("Discord_Notify_On_Assign"),
                DiscordNotifyOnClose = getBoolean("Discord_Notify_On_Close"),
                DiscordNotifyOnCloseAll = getBoolean("Discord_Notify_On_Close_All"),
                DiscordNotifyOnComment = getBoolean("Discord_Notify_On_Comment"),
                DiscordNotifyOnCreate = getBoolean("Discord_Notify_On_Create"),
                DiscordNotifyOnReopen = getBoolean("Discord_Notify_On_Reopen"),
                DiscordNotifyOnPriorityChange = getBoolean("Discord_Notify_On_Priority_Change"),
                DiscordToken = getString("Discord_Bot_Token"),
                DiscordChannelID = getLong("Discord_Channel_ID"),
                printModifiedStacktrace = getBoolean("Print_Modified_Stacktrace"),
                printFullStacktrace = getBoolean("Print_Full_Stacktrace"),
            )
        }
    }

    override fun registerProcesses() {
        instancePluginState.localeHandler.getCommandBases().forEach {
            spigotPlugin.getCommand(it)?.setSuspendingExecutor(commandPipeline as SuspendingCommandExecutor)
            spigotPlugin.getCommand(it)?.tabCompleter = tabComplete as TabCompleter
            // Remember to register any keyword in plugin.yml
        }

        // Registers play join event
        spigotPlugin.server.pluginManager.registerSuspendingEvents(joinEvent as Listener, spigotPlugin)
    }

    override fun unregisterProcesses() {
        // Removes current task timers
        spigotPlugin.server.scheduler.cancelTasks(spigotPlugin)

        // Note: Not able to unregister anything else since I lack any way to access the mcCoroutine variable
    }
}