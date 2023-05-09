package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.commonse.TMPlugin

class TMPluginImpl(
    private val paperPlugin: PaperPlugin
) : TMPlugin(
    buildPlatformFunctions = { PlatformFunctionsImpl(paperPlugin, it) },
    buildJoinEvent = { global, platform -> JoinEventImpl(global, platform) },
    cooldownAfterRemoval = {  }
) {

}

/*
class TMPluginImpl(
    private val paperPlugin: PaperPlugin,
) : TMPlugin(
    buildPlatformFunctions = { PlatformFunctionsImpl(paperPlugin, it) },
    buildPipeline = { platform, instance, global ->
        CommandExecutorImpl(
            platform,
            instance,
            global,
        )
    },
    buildTabComplete = { platform, instance -> TabCompleteImpl(platform, instance) },
    buildJoinEvent = { global, instance, platform -> JoinEventImpl(global, instance, platform) },
)  {
    private lateinit var metrics: Metrics

    private var proxy: Proxy? = null

    override fun performSyncBefore() {
        // Launch Metrics
        metrics = Metrics(paperPlugin, bukkitMetricsKey)
        metrics.addCustomChart(
            SingleLineChart("tickets_made") {
                globalPluginState.ticketCountMetrics.getAndSet(0)
            }
        )
        metrics.addCustomChart(
            SimplePie("database_type") {
                try { instancePluginState.database.type.name }
                catch (e: Exception) { "ERROR" }
            }
        )

        metrics.addCustomChart(SimplePie("plugin_platform") { "Paper" })
    }

    override fun performAsyncTaskTimer(frequency: Long, duration: TimeUnit, action: () -> Unit) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(paperPlugin, Runnable { action() }, 0, duration.toSeconds(frequency) * 20L)
    }

    override fun platformRunFirst() {
        TODO("Not yet implemented")
    }

    override fun platformRunSyncAfterCoreLaunch(
        cooldown: Cooldown?,
        activeLocale: TMLocale,
        database: AsyncDatabase,
        configState: ConfigState,
        joinEvent: com.github.hoshikurama.ticketmanager.commonse.platform.PlayerJoinEvent,
        lpGroupNames: List<String>,
        platformFunctions: PlatformFunctions
    ) {
        TODO("Not yet implemented")
    }

    override fun platformUpdateOnReload(
        cooldown: Cooldown?,
        activeLocale: TMLocale,
        database: AsyncDatabase,
        configState: ConfigState,
        joinEvent: com.github.hoshikurama.ticketmanager.commonse.platform.PlayerJoinEvent,
        lpGroupNames: List<String>,
        platformFunctions: PlatformFunctions
    ) {
        TODO("Not yet implemented")
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
                mySQLHost = getString("MySQL_Host"),
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
                enableProxyMode = getBoolean("Enable_Proxy"),
                proxyServerName = getString("Proxy_Server_Name"),
                autoUpdateConfig = getBoolean("Auto_Update_Config"),
                allowProxyUpdateChecks = getBoolean("Allow_Proxy_UpdateChecking"),
                proxyUpdateFrequency = getLong("Proxy_Update_Check_Frequency"),
                pluginUpdateFrequency = getLong("Plugin_Update_Check_Frequency"),
                forwardDiscordToProxy = getBoolean("Discord_Bot_On_Proxy"),
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

    override fun registerProcesses() {
        // Register Commands
        instancePluginState.localeHandler.getCommandBases().forEach {
            paperPlugin.getCommand(it)?.setExecutor(commandPipeline as CommandExecutor)
            // Remember to register any keyword in paper-plugin.yml
        }
        // Registers Tab Completion
        paperPlugin.server.pluginManager.registerEvents(tabComplete as Listener, paperPlugin)

        // Registers player join event
        paperPlugin.server.pluginManager.registerEvents(joinEvent as Listener, paperPlugin)

        // Register Velocity listeners if necessary
        if (instancePluginState.enableProxyMode) {
            proxy = Proxy(platformFunctions, instancePluginState)
            paperPlugin.server.messenger.registerOutgoingPluginChannel(paperPlugin, Server2Proxy.NotificationSharing.waterfallString())
            paperPlugin.server.messenger.registerIncomingPluginChannel(paperPlugin, Proxy2Server.NotificationSharing.waterfallString(), proxy!!)
            paperPlugin.server.messenger.registerOutgoingPluginChannel(paperPlugin, Server2Proxy.Teleport.waterfallString())
            paperPlugin.server.messenger.registerIncomingPluginChannel(paperPlugin, Proxy2Server.Teleport.waterfallString(), proxy!!)
            paperPlugin.server.messenger.registerOutgoingPluginChannel(paperPlugin, Server2Proxy.ProxyVersionRequest.waterfallString())
            paperPlugin.server.messenger.registerIncomingPluginChannel(paperPlugin, Proxy2Server.ProxyVersionRequest.waterfallString(), proxy!!)
            paperPlugin.server.messenger.registerOutgoingPluginChannel(paperPlugin, Server2Proxy.DiscordMessage.waterfallString())
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