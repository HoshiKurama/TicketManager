package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.TMPlugin
import com.github.hoshikurama.ticketmanager.metricsKey
import com.github.hoshikurama.ticketmanager.misc.ConfigParameters
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.milkbowl.vault.permission.Permission
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

class TMPluginSpigotImpl(
    private val spigotPlugin: SpigotPlugin,
    private val perms: Permission,
    private val adventure: BukkitAudiences,
) : TMPlugin(
    buildPlatformFunctions = { SpigotFunctions(perms, adventure, spigotPlugin) },
    buildPipeline = { platform,instance,global -> SpigotCommandExecutor(platform, instance, global, perms, adventure) },
    buildTabComplete = { platform, instance -> SpigotTabComplete(platform, instance, perms, adventure) },
    buildJoinEvent = { global, instance, platform -> SpigotJoinEvent(global, instance, platform, perms, adventure) },
) {
    private lateinit var metrics: Metrics

    override fun performSyncBefore() {
        // Launch Metrics
        metrics = Metrics(spigotPlugin, metricsKey)
        metrics.addCustomChart(
            Metrics.SingleLineChart("tickets_made") {
                val ticketCount = globalPluginState.ticketCountMetrics.get()
                globalPluginState.ticketCountMetrics.set(0)
                ticketCount
            }
        )
        metrics.addCustomChart(
            Metrics.SimplePie("database_type") {
                instancePluginState.database.type.name
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
                enableAdvancedVisualControl = getBoolean("Enable_Advanced_Visual_Control"),
                enableProxyMode = false,
                proxyServerName = "",
                autoUpdateConfig = getBoolean("Auto_Update_Config"),
            )
        }
    }

    override fun loadInternalConfig(): List<String> {
        return this::class.java.classLoader
            .getResourceAsStream("config.yml")
            .let(InputStream::reader)
            .let(InputStreamReader::readLines)
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