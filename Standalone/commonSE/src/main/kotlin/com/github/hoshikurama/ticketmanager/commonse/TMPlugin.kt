package com.github.hoshikurama.ticketmanager.commonse

import com.github.hoshikurama.ticketmanager.common.ProxyUpdate
import com.github.hoshikurama.ticketmanager.common.UpdateChecker
import com.github.hoshikurama.ticketmanager.common.discord.Discord
import com.github.hoshikurama.ticketmanager.common.mainPluginVersion
import com.github.hoshikurama.ticketmanager.common.supportedLocales
import com.github.hoshikurama.ticketmanager.commonse.data.Cooldown
import com.github.hoshikurama.ticketmanager.commonse.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.commonse.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.commonse.database.*
import com.github.hoshikurama.ticketmanager.commonse.misc.*
import com.github.hoshikurama.ticketmanager.commonse.pipeline.Pipeline
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.platform.PlayerJoinEvent
import com.github.hoshikurama.ticketmanager.commonse.platform.TabComplete
import com.github.hoshikurama.ticketmanager.commonse.ticket.User
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.exists

abstract class TMPlugin(
    private val buildPlatformFunctions: (String?) -> PlatformFunctions,
    private val buildJoinEvent: (GlobalPluginState, InstancePluginState, PlatformFunctions) -> PlayerJoinEvent,
    private val buildTabComplete: (PlatformFunctions, InstancePluginState) -> TabComplete,
    private val buildPipeline: (PlatformFunctions, InstancePluginState, GlobalPluginState) -> Pipeline,
) {
    protected val globalPluginState = GlobalPluginState()

    protected lateinit var instancePluginState: InstancePluginState private set
    protected lateinit var platformFunctions: PlatformFunctions private set
    protected lateinit var tabComplete: TabComplete private set
    protected lateinit var joinEvent: PlayerJoinEvent private set
    protected lateinit var commandPipeline: Pipeline private set

    companion object {
       internal lateinit var activeInstance: TMPlugin
       private set
    }

    abstract fun performSyncBefore()
    abstract fun performAsyncTaskTimer(frequency: Long, duration: TimeUnit, action: () -> Unit)

    // Config Functions
    abstract fun configExists(): Boolean
    abstract fun generateConfig()
    abstract fun reloadConfig()
    abstract fun readConfig(): ConfigParameters

    // Config update functions
    abstract fun loadInternalConfig(): List<String>
    abstract fun loadPlayerConfig(): List<String>
    abstract fun writeNewConfig(entries: List<String>)

    // Registration Related Functions
    abstract fun registerProcesses()
    abstract fun unregisterProcesses()


    // Non-Abstract Functions
    fun enableTicketManager() {
        performSyncBefore()
        activeInstance = this
        CompletableFuture.runAsync(::initializeData) // Also registers
        performAsyncTaskTimer(10, TimeUnit.MINUTES, ::periodicTasks)
    }

    private fun updatePluginUpdateChecker() {
        val newChecker = UpdateChecker(true, UpdateChecker.Location.MAIN)
        instancePluginState.pluginUpdate.set(newChecker)
    }

    private fun updateProxyUpdateChecker() {
        // ASSUMES PROXY SERVER NAME IS NOT NULL AND CAN CHECK FOR UPDATES
        val message = ProxyUpdate.encodeProxyMsg(instancePluginState.proxyServerName!!)
        platformFunctions.relayMessageToProxy("ticketmanager:s2p_proxy_update", message)
    }

    private fun periodicTasks() {
        if (globalPluginState.pluginLocked.get()) return

        CompletableFuture.runAsync(instancePluginState.cooldowns::filterMapAsync)

        // Mass Unread Notify
        CompletableFuture.runAsync {
            if (!instancePluginState.allowUnreadTicketUpdates) return@runAsync

            platformFunctions.getAllOnlinePlayers(instancePluginState.localeHandler)
                .filter { it.has("ticketmanager.notify.unreadUpdates.scheduled") }
                .forEach {
                    instancePluginState.database.getTicketIDsWithUpdatesForAsync(User(it.uniqueID)).thenAcceptAsync { ids ->
                        val tickets = ids.joinToString(", ")

                        if (ids.isEmpty()) return@thenAcceptAsync

                        val template = if (ids.size > 1) it.locale.notifyUnreadUpdateMulti
                        else it.locale.notifyUnreadUpdateSingle

                        template.parseMiniMessage("num" templated tickets).run(it::sendMessage)
                    }
                        .exceptionallyAsync { e -> pushScheduledMessage(e as Exception) }
                }
        }

        // Open and Assigned Notify
        instancePluginState.database.getOpenTicketsAsync(1, 0).thenApplyAsync { (openTickets, _, openCount,_) ->
            platformFunctions.getAllOnlinePlayers(instancePluginState.localeHandler)
                .filter { it.has("ticketmanager.notify.openTickets.scheduled") }
                .forEach { p ->
                    val groups = p.permissionGroups.map { "::$it" }
                    val assignedCount = openTickets.count { it.assignedTo == p.name || it.assignedTo in groups }

                    if (assignedCount != 0)
                        p.locale.notifyOpenAssigned.parseMiniMessage(
                            "open" templated "$openCount",
                            "assigned" templated "$assignedCount"
                        ).run(p::sendMessage)
                }
        }.exceptionallyAsync { e -> pushScheduledMessage(e as Exception) }
    }

    fun disablePlugin() {
        globalPluginState.pluginLocked.set(true)
        instancePluginState.database.closeDatabase()
    }

    fun initializeData() {
        CompletableFuture.runAsync {
            globalPluginState.pluginLocked.set(true)
            val errorsToPushOnLocalization = mutableListOf<() -> Unit>()

            // Generate config file if not found
            if (!configExists()) {
                generateConfig()
                errorsToPushOnLocalization.add {
                    platformFunctions.massNotify(instancePluginState.localeHandler, "ticketmanager.notify.warning") {
                        it.warningsNoConfig.parseMiniMessage()
                    }
                }
            }
            reloadConfig()

            // Builds associated objects from config
            readConfig().let { c ->
                // List of errors
                val errors = mutableListOf<Pair<String, String>>()
                fun <T> T.addToErrors(location: String, toString: (T) -> String): T = this.also { errors.add(location to toString(it)) }


                // Updates config file if requested
                if (c.autoUpdateConfig ?: true.addToErrors("Auto_Update_Config", Boolean::toString)) {
                    com.github.hoshikurama.ticketmanager.common.updateConfig(::loadPlayerConfig, ::loadInternalConfig, ::writeNewConfig)
                }

                // Generates Advanced Visual Control files
                try {
                    if (c.enableAdvancedVisualControl == true) {
                        File("${c.pluginFolderPath}/locales").let { if (!it.exists()) it.mkdir() }

                        supportedLocales
                            .filterNot { Paths.get("${c.pluginFolderPath}/locales/$it.yml").exists() }
                            .forEach {
                                this@TMPlugin::class.java.classLoader
                                    .getResourceAsStream("locales/visual/$it.yml")!!
                                    .use { input -> Files.copy(input, Paths.get("${c.pluginFolderPath}/locales/$it.yml")) }
                            }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Proxy Stuff
                val enableProxyMode = c.enableProxyMode ?: false.addToErrors("Enable_Proxy", Boolean::toString)
                val serverName = c.proxyServerName.let { if (it == null) null.addToErrors("Proxy_Server_Name") { "null" } else it.takeIf(String::isNotBlank) }
                val allowProxyUpdatePings = c.allowProxyUpdateChecks ?: true.addToErrors("Allow_Proxy_UpdateChecking", Boolean::toString)
                val proxyUpdateFreq = c.proxyUpdateFrequency ?: 1L.addToErrors("Proxy_Update_Check_Frequency", Long::toString)

                // Generate platform functions
                platformFunctions = buildPlatformFunctions(serverName)

                // Builds LocaleHandler object
                val localeHandler = kotlin.run {
                    val colourCode = c.localeHandlerColourCode ?: "&3".addToErrors("Colour_Code") { it }
                    val preferredLocale = c.localeHandlerPreferredLocale ?: "en_ca".addToErrors("Preferred_Locale") { it }
                    val consoleLocale = c.localeHandlerConsoleLocale ?: "en_ca".addToErrors("Console_Locale") { it }
                    val forceLocale = c.localeHandlerForceLocale ?: false.addToErrors("Force_Locale", Boolean::toString)
                    val enableAdvancedVisualControl = c.enableAdvancedVisualControl ?: false.addToErrors("Enable_Advanced_Visual_Control", Boolean::toString)
                    LocaleHandler.buildLocales(
                        colourCode,
                        preferredLocale.lowercase(),
                        consoleLocale.lowercase(),
                        forceLocale,
                        c.pluginFolderPath,
                        enableAdvancedVisualControl
                    )
                }

                // Builds DatabaseBuilders object
                val databaseBuilders = DatabaseBuilders(
                    mySQLBuilder = kotlin.run {
                        val port = c.mySQLPort ?: "_".addToErrors("MySQL_Port") { it }
                        val host = c.mySQLHost ?: "_".addToErrors("MySQL_Host") { it }
                        val dbName = c.mySQLDBName ?: "_".addToErrors("MySQL_DBName") { it }
                        val username = c.mySQLUsername ?: "_".addToErrors("MySQL_Username") { it }
                        val password = c.mySQLPassword ?: "_".addToErrors("MySQL_Password") { it }
                        MySQLBuilder(host, port, dbName, username, password)
                    },
                    memoryBuilder = kotlin.run {
                        val backupFrequency = c.memoryFrequency ?: 600L.addToErrors("Memory_Backup_Frequency", Long::toString)
                        MemoryBuilder(c.pluginFolderPath, backupFrequency)
                    },
                    cachedH2Builder = CachedH2Builder(c.pluginFolderPath),
                    h2Builder = H2Builder(c.pluginFolderPath)
                )

                // Builds Database object
                val database = kotlin.run {
                    val type =
                        try { AsyncDatabase.Type.valueOf(c.dbTypeAsStr!!.uppercase()) }
                        catch (e: Exception) { AsyncDatabase.Type.CACHED_H2.addToErrors("Database_Mode", AsyncDatabase.Type::name) }

                    try {
                        when (type) {
                            AsyncDatabase.Type.MEMORY -> databaseBuilders.memoryBuilder
                            AsyncDatabase.Type.MYSQL -> databaseBuilders.mySQLBuilder
                            AsyncDatabase.Type.CACHED_H2 -> databaseBuilders.cachedH2Builder
                            AsyncDatabase.Type.H2 -> databaseBuilders.h2Builder
                        }
                            .run(DatabaseBuilder::build)
                            .apply(AsyncDatabase::initializeDatabase)

                    } catch (e: Exception) {
                        errorsToPushOnLocalization.add { pushErrors(platformFunctions, instancePluginState, e, TMLocale::consoleErrorBadDatabase) }
                        databaseBuilders.cachedH2Builder.build().apply { initializeDatabase() }
                    }
                }

                // Builds Discord-Related Objects
                val enableDiscord = c.enableDiscord ?: false.addToErrors("Use_Discord_Bot", Boolean::toString)
                val enableProxyDiscord = (c.forwardDiscordToProxy ?: false.addToErrors("Discord_Bot_On_Proxy", Boolean::toString)) && enableDiscord
                val discordSettings = Discord.Settings(
                    notifyOnAssign = c.discordNotifyOnAssign ?: true.addToErrors("Discord_Notify_On_Assign", Boolean::toString),
                    notifyOnClose = c.discordNotifyOnClose ?: true.addToErrors("Discord_Notify_On_Close", Boolean::toString),
                    notifyOnCloseAll = c.discordNotifyOnCloseAll ?: true.addToErrors("Discord_Notify_On_Close_All", Boolean::toString),
                    notifyOnComment = c.discordNotifyOnComment ?: true.addToErrors("Discord_Notify_On_Comment", Boolean::toString),
                    notifyOnCreate = c.discordNotifyOnCreate ?: true.addToErrors("Discord_Notify_On_Create", Boolean::toString),
                    notifyOnReopen = c.discordNotifyOnReopen ?: true.addToErrors("Discord_Notify_On_Reopen", Boolean::toString),
                    notifyOnPriorityChange = c.discordNotifyOnPriorityChange ?: true.addToErrors("Discord_Notify_On_Priority_Change", Boolean::toString),
                    forwardToProxy = enableProxyDiscord
                )
                val discord = if (enableDiscord && !enableProxyDiscord) {
                    try {
                        Discord(
                            token = c.discordToken ?: "0".addToErrors("Discord_Bot_Token") { it },
                            channelID = c.discordChannelID ?: (-1L).addToErrors("Discord_Channel_ID", Long::toString),
                        )
                    } catch (e: Exception) {
                        errorsToPushOnLocalization.add { pushErrors(platformFunctions, instancePluginState, e, TMLocale::consoleErrorBadDiscord) }
                        null
                    }
                } else null

                // Builds Cooldowns
                val cooldowns = Cooldown(
                    enabled = c.allowCooldowns ?: false.addToErrors("Use_Cooldowns", Boolean::toString),
                    duration = c.cooldownSeconds ?: (0L).addToErrors("Cooldown_Time", Long::toString),
                )

                // Other config items
                val allowUnreadTicketUpdates = c.allowUnreadTicketUpdates ?: true.addToErrors("Allow_Unread_Ticket_Updates", Boolean::toString)
                val updateChecker = UpdateChecker(canCheck = c.checkForPluginUpdates ?: true.addToErrors("Allow_UpdateChecking", Boolean::toString), UpdateChecker.Location.MAIN)
                val printModifiedStacktrace = c.printModifiedStacktrace ?: true.addToErrors("Print_Modified_Stacktrace", Boolean::toString)
                val printFullStacktrace = c.printFullStacktrace ?: false.addToErrors("Print_Full_Stacktrace", Boolean::toString)
                val pluginUpdateFreq = c.pluginUpdateFrequency ?: 1L.addToErrors("Plugin_Update_Check_Frequency", Long::toString)

                // Builds and assigns final objects
                instancePluginState = InstancePluginState(
                    database = database,
                    cooldowns = cooldowns,
                    discord = discord,
                    discordSettings = discordSettings,
                    databaseBuilders = databaseBuilders,
                    localeHandler = localeHandler,
                    pluginUpdate = AtomicReference(updateChecker),
                    allowUnreadTicketUpdates = allowUnreadTicketUpdates,
                    printModifiedStacktrace = printModifiedStacktrace,
                    printFullStacktrace = printFullStacktrace,
                    enableProxyMode = enableProxyMode,
                    proxyServerName = serverName,
                    allowProxyUpdatePings = allowProxyUpdatePings,
                    cachedProxyUpdate = AtomicReference(),
                )
                tabComplete = buildTabComplete(platformFunctions, instancePluginState)
                commandPipeline = buildPipeline(platformFunctions, instancePluginState, globalPluginState)
                joinEvent = buildJoinEvent(globalPluginState, instancePluginState, platformFunctions)
                registerProcesses()

                // Print warnings
                CompletableFuture.runAsync {
                    errors.forEach { (node, default) ->
                        localeHandler.consoleLocale.consoleWarningInvalidConfigNode
                            .replaceFirst("%node%", node)
                            .replaceFirst("%default%", default)
                            .run(platformFunctions::pushWarningToConsole)
                    }
                    errorsToPushOnLocalization.forEach { it() }

                    // Notification stating something went wrong during startup
                    if (errors.size > 0) {
                        platformFunctions.massNotify(localeHandler, "ticketmanager.notify.warning") {
                            it.warningsInvalidConfig.parseMiniMessage("count" templated "${errors.size}")
                        }
                    }

                    platformFunctions.pushInfoToConsole(instancePluginState.localeHandler.consoleLocale.consoleInitializationComplete)
                }

                // Additional startup procedures
                instancePluginState.run {

                    // Place update is available into Console
                    if (pluginUpdate.get().canCheck && pluginUpdate.get().latestVersionIfNotLatest != null) {
                        localeHandler.consoleLocale.notifyPluginUpdate.parseMiniMessage(
                            "current" templated mainPluginVersion,
                            "latest" templated pluginUpdate.get().latestVersionIfNotLatest!!
                        ).run(platformFunctions.getConsoleAudience()::sendMessage)
                    }

                    // Launch plugin update checking
                    if (pluginUpdate.get().canCheck)
                        performAsyncTaskTimer(pluginUpdateFreq, TimeUnit.HOURS, ::updatePluginUpdateChecker)

                    // Proxy update checking...
                    if (proxyServerName != null && allowProxyUpdatePings && enableProxyMode) {
                        updateProxyUpdateChecker()                                                                  // Perform initial proxy update check
                        performAsyncTaskTimer(proxyUpdateFreq, TimeUnit.HOURS, ::updateProxyUpdateChecker)          // Launch proxy update checking
                    }
                }
            }

            globalPluginState.pluginLocked.set(false)
        }.exceptionallyAsync { it.printStackTrace(); null; }
    }

    private fun pushScheduledMessage(exception: Exception): Void? {
        pushErrors(platformFunctions, instancePluginState, exception, TMLocale::consoleErrorScheduledNotifications)
        return null
    }
}