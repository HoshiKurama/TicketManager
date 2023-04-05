package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.common.bukkitMetricsKey
import com.github.hoshikurama.ticketmanager.commonse.TMPlugin
import com.github.hoshikurama.ticketmanager.commonse.misc.ConfigParameters
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bstats.bukkit.Metrics
import org.bstats.charts.SimplePie
import org.bstats.charts.SingleLineChart
import org.bukkit.Bukkit
import org.bukkit.command.CommandExecutor
import org.bukkit.command.TabCompleter
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.server.TabCompleteEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class TMPluginImpl(
    private val spigotPlugin: SpigotPlugin,
    private val adventure: BukkitAudiences,
) : TMPlugin(
    buildPlatformFunctions = { PlatformFunctionsImpl(adventure, spigotPlugin) },
    buildPipeline = { platform,instance,global -> CommandExecutorImpl(platform, instance, global, adventure) },
    buildTabComplete = { platform, instance -> TabCompleteImpl(platform, instance, adventure) },
    buildJoinEvent = { global, instance, platform -> JoinEventImpl(global, instance, platform, adventure) },
) {
    private lateinit var metrics: Metrics

    override fun performSyncBefore() {
        // Launch Metrics
        metrics = Metrics(spigotPlugin, bukkitMetricsKey)
        metrics.addCustomChart(
            SingleLineChart("tickets_made") {
                globalPluginState.ticketCountMetrics.getAndSet(0)
            }
        )
        metrics.addCustomChart(
            SimplePie("database_type") {
                instancePluginState.database.type.name
            }
        )

        metrics.addCustomChart(SimplePie("plugin_platform") { "Spigot" })
    }

    override fun performAsyncTaskTimer(frequency: Long, duration: TimeUnit, action: () -> Unit) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(spigotPlugin, Runnable { action() }, 0, duration.toSeconds(frequency) * 20L)
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
                mySQLHost = getString("MySQL_Host"),
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
                discordNotifyOnAssign = getBoolean("Discord_Notify_On_Assign"),
                discordNotifyOnClose = getBoolean("Discord_Notify_On_Close"),
                discordNotifyOnCloseAll = getBoolean("Discord_Notify_On_Close_All"),
                discordNotifyOnComment = getBoolean("Discord_Notify_On_Comment"),
                discordNotifyOnCreate = getBoolean("Discord_Notify_On_Create"),
                discordNotifyOnReopen = getBoolean("Discord_Notify_On_Reopen"),
                discordNotifyOnPriorityChange = getBoolean("Discord_Notify_On_Priority_Change"),
                discordToken = getString("Discord_Bot_Token"),
                discordChannelID = getLong("Discord_Channel_ID"),
                printModifiedStacktrace = getBoolean("Print_Modified_Stacktrace"),
                printFullStacktrace = getBoolean("Print_Full_Stacktrace"),
                enableAdvancedVisualControl = getBoolean("Enable_Advanced_Visual_Control"),
                enableProxyMode = false,
                proxyServerName = "",
                autoUpdateConfig = getBoolean("Auto_Update_Config"),
                allowProxyUpdateChecks = false,
                proxyUpdateFrequency = 0,
                pluginUpdateFrequency = getLong("Plugin_Update_Check_Frequency"),
                forwardDiscordToProxy = false,
            )
        }
    }

    override fun loadInternalConfig(): List<String> {
        return this::class.java.classLoader
            .getResourceAsStream("config.yml")
            ?.let(InputStream::reader)
            ?.let(InputStreamReader::readLines) ?: emptyList()
    }

    override fun loadPlayerConfig(): List<String> {
        return JavaPlugin::class.java.getDeclaredField("configFile")
            .apply { isAccessible = true }
            .run { get(spigotPlugin) as File }
            .bufferedReader()
            .readLines()
    }

    override fun writeNewConfig(entries: List<String>) {
        val writer = JavaPlugin::class.java.getDeclaredField("configFile")
            .apply { isAccessible = true }
            .run { get(spigotPlugin) as File }
            .bufferedWriter()

        entries.forEachIndexed { index, str ->
            writer.write(str)

            if (index != entries.lastIndex)
                writer.newLine()
        }
        writer.close()
    }

    override fun registerProcesses() {
        instancePluginState.localeHandler.getCommandBases().forEach {
            spigotPlugin.getCommand(it)?.setExecutor(commandPipeline as CommandExecutor)
            spigotPlugin.getCommand(it)?.tabCompleter = tabComplete as TabCompleter
            // Remember to register any keyword in plugin.yml
        }

        // Registers play join event
        spigotPlugin.server.pluginManager.registerEvents(joinEvent as Listener, spigotPlugin)
    }

    override fun unregisterProcesses() {
        // Removes current task timers
        spigotPlugin.server.scheduler.cancelTasks(spigotPlugin)

        // Unregisters listeners
        TabCompleteEvent.getHandlerList().unregister(spigotPlugin)
        PlayerJoinEvent.getHandlerList().unregister(spigotPlugin)
    }
}