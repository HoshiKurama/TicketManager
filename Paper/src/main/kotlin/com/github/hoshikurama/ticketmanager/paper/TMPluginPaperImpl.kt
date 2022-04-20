package com.github.hoshikurama.ticketmanager.paper

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent
import com.github.hoshikurama.ticketmanager.TMPlugin
import com.github.hoshikurama.ticketmanager.metricsKey
import com.github.hoshikurama.ticketmanager.misc.ConfigParameters
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import org.bukkit.command.CommandExecutor
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import java.io.File


class TMPluginPaperImpl(
    private val paperPlugin: PaperPlugin,
    private val perms: Permission,
) : TMPlugin(
    buildPlatformFunctions = { PaperFunctions(perms, paperPlugin, it) },
    buildPipeline = { platform, instance, global -> PaperCommandExecutor(platform, instance, global, perms) },
    buildTabComplete = { platform, instance -> PaperTabComplete(platform, instance, perms) },
    buildJoinEvent = { global, instance, platform -> PaperJoinEvent(global, instance, platform, perms) },
)  {
    private lateinit var metrics: Metrics

    private var proxy: VelocityProxy? = null

    override fun performSyncBefore() {
        // Launch Metrics
        metrics = Metrics(paperPlugin, metricsKey)
        metrics.addCustomChart(
            Metrics.SingleLineChart("tickets_made") {
                globalPluginState.ticketCountMetrics.getAndSet(0)
            }
        )
        metrics.addCustomChart(
            Metrics.SimplePie("database_type") {
                try { instancePluginState.database.type.name }
                catch (e: Exception) { "ERROR" }
            }
        )

        metrics.addCustomChart(Metrics.SimplePie("plugin_platform") { "Paper" })
    }

    override fun performAsyncTaskTimer(action: () -> Unit) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(paperPlugin, Runnable { action() }, 120, 12000)
    }

    override fun configExists(): Boolean {
        return File(paperPlugin.dataFolder, "config.yml").exists()
    }

    override fun generateConfig() {
        paperPlugin.saveDefaultConfig()
    }

    override fun reloadConfig() {
        paperPlugin.reloadConfig()
    }

    override fun readConfig(): ConfigParameters {
        return paperPlugin.config.run {
            ConfigParameters(
                MySQLHost = getString("MySQL_Host"),
                mySQLPort = getString("MySQL_Port"),
                mySQLDBName = getString("MySQL_DBName"),
                mySQLUsername = getString("MySQL_Username"),
                mySQLPassword = getString("MySQL_Password"),
                pluginFolderPath = paperPlugin.dataFolder.absolutePath,
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
                enableAdvancedVisualControl = getBoolean("Enable_Advanced_Visual_Control"),
                enableVelocity = getBoolean("Enable_Velocity"),
                velocityServerName = getString("Velocity_Server_Name"),
            )
        }
    }

    override fun registerProcesses() {
        // Register Commands
        instancePluginState.localeHandler.getCommandBases().forEach {
            paperPlugin.getCommand(it)?.setExecutor(commandPipeline as CommandExecutor)
            // Remember to register any keyword in plugin.yml
        }
        // Registers Tab Completion
        paperPlugin.server.pluginManager.registerEvents(tabComplete as Listener, paperPlugin)

        // Registers player join event
        paperPlugin.server.pluginManager.registerEvents(joinEvent as Listener, paperPlugin)

        // Register Velocity listeners if necessary
        if (instancePluginState.enableVelocity) {
            proxy = VelocityProxy(platformFunctions, instancePluginState)
            paperPlugin.server.messenger.registerOutgoingPluginChannel(paperPlugin, "ticketmanager:inform_proxy")
            paperPlugin.server.messenger.registerIncomingPluginChannel(paperPlugin, "ticketmanager:relayed_message", proxy!!)
        }
    }

    override fun unregisterProcesses() {
        // Removes current task timers
        paperPlugin.server.scheduler.cancelTasks(paperPlugin)

        // Unregisters events
        AsyncTabCompleteEvent.getHandlerList().unregister(paperPlugin)
        PlayerJoinEvent.getHandlerList().unregister(paperPlugin)

        // Unregister proxy events
        paperPlugin.server.messenger.unregisterIncomingPluginChannel(paperPlugin)
        paperPlugin.server.messenger.unregisterOutgoingPluginChannel(paperPlugin)
    }
}