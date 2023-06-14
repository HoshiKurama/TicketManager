package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.api.paper.TicketManagerDatabaseRegister
import com.github.hoshikurama.ticketmanager.common.Proxy2Server
import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.common.bukkitMetricsKey
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.TMPlugin
import com.github.hoshikurama.ticketmanager.commonse.commands.CommandTasks
import com.github.hoshikurama.ticketmanager.commonse.datas.ConfigState
import com.github.hoshikurama.ticketmanager.commonse.datas.Cooldown
import com.github.hoshikurama.ticketmanager.commonse.datas.GlobalState
import com.github.hoshikurama.ticketmanager.commonse.extensions.DatabaseManager
import com.github.hoshikurama.ticketmanager.commonse.misc.ConfigParameters
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.platform.events.EventBuilder
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import kotlinx.coroutines.runBlocking
import org.bstats.bukkit.Metrics
import org.bstats.charts.SimplePie
import org.bstats.charts.SingleLineChart
import org.bukkit.Bukkit
import org.bukkit.event.Listener
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

typealias TMPlayerJoinEvent = com.github.hoshikurama.ticketmanager.commonse.platform.PlayerJoinEvent
typealias BukkitPlayerJoinEvent = org.bukkit.event.player.PlayerJoinEvent

class TMPluginImpl(
    private val paperPlugin: PaperPlugin
) : TMPlugin(
    buildPlatformFunctions = { PlatformFunctionsImpl(paperPlugin, it) },
    buildJoinEvent = { config, platform, locale -> JoinEventImpl(locale, config, platform) },
    cooldownAfterRemoval = { it.run(Bukkit::getPlayer)?.run(CommandAPI::updateRequirements) },
    eventBuilder = EventBuilderImpl()
) {

    init {
        activeInstance = this
    }

    private lateinit var metrics: Metrics
    private var proxy: Proxy? = null

    override fun platformRunFirst() {
        // Launch Metrics
        metrics = Metrics(paperPlugin, bukkitMetricsKey)
        metrics.addCustomChart(
            SingleLineChart("tickets_made") {
                runBlocking {
                    GlobalState.ticketCounter.getAndReset()
                }
            }
        )
        metrics.addCustomChart(
            SimplePie("database_type") {
                GlobalState.databaseType?: "UNINITIALIZED"
            }
        )

        metrics.addCustomChart(SimplePie("plugin_platform") { "Paper" })

        // LuckPerms???
    }

    override fun platformRunSyncAfterCoreLaunch(
        cooldown: Cooldown?,
        activeLocale: TMLocale,
        configState: ConfigState,
        joinEvent: TMPlayerJoinEvent,
        lpGroupNames: List<String>,
        platformFunctions: PlatformFunctions,
        eventBuilder: EventBuilder
    ) {
        Bukkit.getServicesManager().register(TicketManagerDatabaseRegister::class.java, TicketManagerDatabaseRegister(DatabaseManager), paperPlugin, ServicePriority.Normal)
        nonCommandRegistrations(cooldown, activeLocale, configState, joinEvent, lpGroupNames, platformFunctions, eventBuilder)
        // Register Commands
        CommandAPI.onLoad(CommandAPIBukkitConfig(paperPlugin))
        CommandAPI.onEnable()
        CommandAPIRunner().generateCommands()
    }

    private fun nonCommandRegistrations(
        cooldown: Cooldown?,
        activeLocale: TMLocale,
        configState: ConfigState,
        joinEvent: TMPlayerJoinEvent,
        lpGroupNames: List<String>,
        platformFunctions: PlatformFunctions,
        eventBuilder: EventBuilder,
    ) {
        // Update Command References
        ReloadObjectCommand.cooldown = cooldown
        ReloadObjectCommand.locale = activeLocale
        ReloadObjectCommand.configState = configState
        ReloadObjectCommand.lpGroupNames = lpGroupNames
        ReloadObjectCommand.platform = platformFunctions
        ReloadObjectCommand.commandTasks = CommandTasks(
            eventBuilder = eventBuilder,
            configState = configState,
            platform = platformFunctions,
            locale = activeLocale,
        )

        // Register Proxy listeners if necessary
        if (configState.enableProxyMode) {
            proxy = Proxy(platformFunctions, configState, activeLocale)
            paperPlugin.server.messenger.registerOutgoingPluginChannel(paperPlugin, Server2Proxy.NotificationSharing.waterfallString())
            paperPlugin.server.messenger.registerIncomingPluginChannel(paperPlugin, Proxy2Server.NotificationSharing.waterfallString(), proxy!!)
            paperPlugin.server.messenger.registerOutgoingPluginChannel(paperPlugin, Server2Proxy.Teleport.waterfallString())
            paperPlugin.server.messenger.registerIncomingPluginChannel(paperPlugin, Proxy2Server.Teleport.waterfallString(), proxy!!)
            paperPlugin.server.messenger.registerOutgoingPluginChannel(paperPlugin, Server2Proxy.ProxyVersionRequest.waterfallString())
            paperPlugin.server.messenger.registerIncomingPluginChannel(paperPlugin, Proxy2Server.ProxyVersionRequest.waterfallString(), proxy!!)
            paperPlugin.server.messenger.registerOutgoingPluginChannel(paperPlugin, Server2Proxy.DiscordMessage.waterfallString())
        }

        // Registers player join event
        paperPlugin.server.pluginManager.registerEvents(joinEvent as Listener, paperPlugin)
    }

    override fun platformUpdateOnReload(
        cooldown: Cooldown?,
        activeLocale: TMLocale,
        configState: ConfigState,
        joinEvent: TMPlayerJoinEvent,
        lpGroupNames: List<String>,
        platformFunctions: PlatformFunctions,
        eventBuilder: EventBuilder,
    ) {
    // Unregistrations...
        // Unregister Player join event
        BukkitPlayerJoinEvent.getHandlerList().unregister(paperPlugin)

        // Unregister proxy events
        paperPlugin.server.messenger.unregisterIncomingPluginChannel(paperPlugin)
        paperPlugin.server.messenger.unregisterOutgoingPluginChannel(paperPlugin)

    // Registrations
        nonCommandRegistrations(cooldown, activeLocale, configState, joinEvent, lpGroupNames, platformFunctions, eventBuilder)
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
                pluginFolderPath = paperPlugin.dataFolder.toPath(),
                dbTypeAsStr = getString("Database_Mode"),
                allowCooldowns = getBoolean("Use_Cooldowns"),
                cooldownSeconds = getLong("Cooldown_Time"),
                localedColourCode = getString("Colour_Code"),
                selectedLocale = getString("Preferred_Locale"),
                allowUnreadTicketUpdates = getBoolean("Allow_Unread_Ticket_Updates"),
                checkForPluginUpdates = getBoolean("Allow_UpdateChecking"),
                printModifiedStacktrace = getBoolean("Print_Modified_Stacktrace"),
                printFullStacktrace = getBoolean("Print_Full_Stacktrace"),
                enableAdvancedVisualControl = getBoolean("Enable_Advanced_Visual_Control"),
                enableProxyMode = getBoolean("Enable_Proxy"),
                proxyServerName = getString("Proxy_Server_Name"),
                autoUpdateConfig = getBoolean("Auto_Update_Config"),
                allowProxyUpdateChecks = getBoolean("Allow_Proxy_UpdateChecking"),
                proxyUpdateFrequency = getLong("Proxy_Update_Check_Frequency"),
                pluginUpdateFrequency = getLong("Plugin_Update_Check_Frequency"),
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
            .run { get(paperPlugin) as File }
            .bufferedReader()
            .readLines()
    }

    override fun writeNewConfig(entries: List<String>) {
        val writer = JavaPlugin::class.java.getDeclaredField("configFile")
            .apply { isAccessible = true }
            .run { get(paperPlugin) as File }
            .bufferedWriter()

        entries.forEachIndexed { index, str ->
            writer.write(str)

            if (index != entries.lastIndex)
                writer.newLine()
        }
        writer.close()
    }
}