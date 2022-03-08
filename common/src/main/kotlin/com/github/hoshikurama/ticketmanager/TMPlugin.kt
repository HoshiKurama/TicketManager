package com.github.hoshikurama.ticketmanager

import com.github.hoshikurama.ticketmanager.data.Cooldown
import com.github.hoshikurama.ticketmanager.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.database.DatabaseBuilders
import com.github.hoshikurama.ticketmanager.database.MemoryBuilder
import com.github.hoshikurama.ticketmanager.database.MySQLBuilder
import com.github.hoshikurama.ticketmanager.database.impl.AsyncMemory
import com.github.hoshikurama.ticketmanager.database.impl.AsyncMySQL
import com.github.hoshikurama.ticketmanager.misc.*
import com.github.hoshikurama.ticketmanager.pipeline.Pipeline
import com.github.hoshikurama.ticketmanager.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.platform.PlayerJoinEvent
import com.github.hoshikurama.ticketmanager.platform.TabComplete
import com.github.hoshikurama.ticketmanager.ticket.User
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists

abstract class TMPlugin(
    private val platformFunctions: PlatformFunctions,
    private val buildJoinEvent: (GlobalPluginState, InstancePluginState) -> PlayerJoinEvent,
    private val buildTabComplete: (PlatformFunctions, InstancePluginState) -> TabComplete,
    private val buildPipeline: (PlatformFunctions, InstancePluginState, GlobalPluginState) -> Pipeline,
) {
    protected val globalPluginState = GlobalPluginState()

    protected lateinit var instancePluginState: InstancePluginState private set
    protected lateinit var tabComplete: TabComplete private set
    protected lateinit var joinEvent: PlayerJoinEvent private set
    protected lateinit var commandPipeline: Pipeline private set

    companion object {
       internal lateinit var activeInstance: TMPlugin
       private set
    }

    abstract fun performSyncBefore()
    abstract fun performAsyncTaskTimer(action: () -> Unit)

    // Config Functions
    abstract fun configExists(): Boolean
    abstract fun generateConfig()
    abstract fun reloadConfig()
    abstract fun readConfig(): ConfigParameters

    // Registration Related Functions
    abstract fun registerProcesses()
    abstract fun unregisterProcesses()


    // Non-Abstract Functions
    fun enableTicketManager() {
        performSyncBefore()
        activeInstance = this
        CompletableFuture.runAsync(::initializeData) // Also registers
        performAsyncTaskTimer { CompletableFuture.runAsync(::periodicTasks) }
    }

    private fun periodicTasks() {
        if (globalPluginState.pluginLocked.get()) return

        CompletableFuture.runAsync(instancePluginState.cooldowns::filterMapAsync)

        // Mass Unread Notify
        CompletableFuture.runAsync {
            if (!instancePluginState.allowUnreadTicketUpdates) return@runAsync

            platformFunctions.getPlayersOnAllServers(instancePluginState.localeHandler)
                .filter { it.has("ticketmanager.notify.unreadUpdates.scheduled") }
                .forEach {
                    instancePluginState.database.getTicketIDsWithUpdatesForAsync(User(it.uniqueID)).thenAcceptAsync { ids ->
                        val tickets = ids.joinToString(", ")

                        if (ids.isEmpty()) return@thenAcceptAsync

                        val template = if (ids.size > 1) it.locale.notifyUnreadUpdateMulti
                        else it.locale.notifyUnreadUpdateSingle

                        template.parseMiniMessage("num" templated tickets).run(it::sendMessage)
                    }
                        .exceptionallyAsync { e -> pushScheduledMessage(e as Exception) } //TODO WATCH FOR THIS
                }
        }

        // Open and Assigned Notify
        instancePluginState.database.getOpenTicketsAsync(1, 0).thenApplyAsync { (openTickets, _, openCount,_) ->
            platformFunctions.getPlayersOnAllServers(instancePluginState.localeHandler)
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

    @OptIn(ExperimentalPathApi::class)
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

                // Builds LocaleHandler object
                val localeHandler = kotlin.run {
                    val colourCode = c.localeHandlerColourCode ?: "&3".addToErrors("Colour_Code") { it }
                    val preferredLocale = c.localeHandlerPreferredLocale ?: "en_ca".addToErrors("Preferred_Locale") { it }
                    val consoleLocale = c.localeHandlerConsoleLocale ?: "en_ca".addToErrors("Console_Locale") { it }
                    val forceLocale = c.localeHandlerForceLocale ?: false.addToErrors("Force_Locale", Boolean::toString)
                    val enableAdvancedVisualControl = c.enableAdvancedVisualControl ?: false.addToErrors("Enable_Advanced_Visual_Control", Boolean::toString)
                    LocaleHandler.buildLocales(colourCode, preferredLocale.lowercase(), consoleLocale.lowercase(), forceLocale, c.pluginFolderPath, enableAdvancedVisualControl)
                }

                // Builds DatabaseBuilders object
                val databaseBuilders = DatabaseBuilders(
                    mySQLBuilder = kotlin.run {
                        val port = c.mySQLPort ?: "_".addToErrors("MySQL_Port") { it }
                        val host = c.MySQLHost ?: "_".addToErrors("MySQL_Host") { it }
                        val dbName = c.mySQLDBName ?: "_".addToErrors("MySQL_DBName") { it }
                        val username = c.mySQLUsername ?: "_".addToErrors("MySQL_Username") { it }
                        val password = c.mySQLPassword ?: "_".addToErrors("MySQL_Password") { it }
                        MySQLBuilder(host, port, dbName, username, password)
                    },
                    memoryBuilder = kotlin.run {
                        val backupFrequency = c.memoryFrequency ?: 600L.addToErrors("Memory_Backup_Frequency", Long::toString)
                        MemoryBuilder(c.pluginFolderPath, backupFrequency)
                    },
                    /*
                    sqLiteBuilder = SQLiteBuilder(c.pluginFolderPath),
                    cachedSQLiteBuilder = CachedSQLiteBuilder(c.pluginFolderPath, globalPluginState.asyncDispatcher),
                     */
                )

                // Builds Database object
                val database = kotlin.run {
                    val type =
                        try { AsyncDatabase.Type.valueOf(c.dbTypeAsStr!!.uppercase()) }
                        catch (e: Exception) { AsyncDatabase.Type.CACHED_SQLITE.addToErrors("Database_Mode", AsyncDatabase.Type::name) }

                    try {
                        when (type) {
                            AsyncDatabase.Type.MEMORY -> databaseBuilders.memoryBuilder.build().apply(AsyncMemory::initializeDatabase)
                            AsyncDatabase.Type.MYSQL -> databaseBuilders.mySQLBuilder.build().apply(AsyncMySQL::initializeDatabase)
                            AsyncDatabase.Type.SQLITE -> TODO()
                            AsyncDatabase.Type.CACHED_SQLITE -> TODO()
                        }
                    } catch (e: Exception) {
                        errorsToPushOnLocalization.add { pushErrors(platformFunctions, instancePluginState, e, TMLocale::consoleErrorBadDatabase) }
                        databaseBuilders.mySQLBuilder.build().apply { initializeDatabase() }//databaseBuilders.cachedSQLiteBuilder.build().apply { initializeDatabase() }
                    }
                }

                // Builds Discord object
                val discord = kotlin.run {
                    val enableDiscord = c.enableDiscord ?: false.addToErrors("Use_Discord_Bot", Boolean::toString)
                    if (enableDiscord) {
                        try {
                            Discord.create(
                                notifyOnAssign = c.DiscordNotifyOnAssign ?: true.addToErrors("Discord_Notify_On_Assign", Boolean::toString),
                                notifyOnClose = c.DiscordNotifyOnClose ?: true.addToErrors("Discord_Notify_On_Close", Boolean::toString),
                                notifyOnCloseAll = c.DiscordNotifyOnCloseAll ?: true.addToErrors("Discord_Notify_On_Close_All", Boolean::toString),
                                notifyOnComment = c.DiscordNotifyOnComment ?: true.addToErrors("Discord_Notify_On_Comment", Boolean::toString),
                                notifyOnCreate = c.DiscordNotifyOnCreate ?: true.addToErrors("Discord_Notify_On_Create", Boolean::toString),
                                notifyOnReopen = c.DiscordNotifyOnReopen ?: true.addToErrors("Discord_Notify_On_Reopen", Boolean::toString),
                                notifyOnPriorityChange = c.DiscordNotifyOnPriorityChange ?: true.addToErrors("Discord_Notify_On_Priority_Change", Boolean::toString),
                                token = c.DiscordToken ?: "0".addToErrors("Discord_Bot_Token") { it },
                                channelID = c.DiscordChannelID ?: (-1L).addToErrors("Discord_Channel_ID", Long::toString),
                                locale = localeHandler.consoleLocale,
                            )
                        } catch (e: Exception) {
                            errorsToPushOnLocalization.add { pushErrors(platformFunctions, instancePluginState, e, TMLocale::consoleErrorBadDiscord) }
                            null
                        }
                    } else null
                }

                // Builds Cooldowns
                val cooldowns = Cooldown(
                    enabled = c.allowCooldowns ?: false.addToErrors("Use_Cooldowns", Boolean::toString),
                    duration = c.cooldownSeconds ?: (0L).addToErrors("Cooldown_Time", Long::toString),
                )

                // Other config items
                val allowUnreadTicketUpdates = c.allowUnreadTicketUpdates ?: true.addToErrors("Allow_Unread_Ticket_Updates", Boolean::toString)
                val updateChecker = UpdateChecker(canCheck = c.checkForPluginUpdates ?: true.addToErrors("Allow_UpdateChecking", Boolean::toString))
                val printModifiedStacktrace = c.printModifiedStacktrace ?: true.addToErrors("Print_Modified_Stacktrace", Boolean::toString)
                val printFullStacktrace = c.printFullStacktrace ?: false.addToErrors("Print_Full_Stacktrace", Boolean::toString)

                // Builds and assigns final objects
                instancePluginState = InstancePluginState(database, cooldowns, discord, databaseBuilders, localeHandler, allowUnreadTicketUpdates, updateChecker, printModifiedStacktrace, printFullStacktrace)
                tabComplete = buildTabComplete(platformFunctions, instancePluginState)
                commandPipeline = buildPipeline(platformFunctions, instancePluginState, globalPluginState)
                joinEvent = buildJoinEvent(globalPluginState, instancePluginState)

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
            }

            // Places update is available into Console
            instancePluginState.run {
                if (pluginUpdateChecker.run { canCheck && latestVersionOrNull != null })
                    localeHandler.consoleLocale.notifyPluginUpdate
                        .parseMiniMessage(
                            "current" templated pluginVersion,
                            "latest" templated pluginUpdateChecker.latestVersionOrNull!!
                        )
                        .run(platformFunctions.getConsoleAudience()::sendMessage)
            }

            // pushErrors(platformFunctions, instancePluginState, e, TMLocale::consoleErrorBadDiscord) For discord

            // Registers itself
            registerProcesses()

            globalPluginState.pluginLocked.set(false)
        }.exceptionallyAsync { it.printStackTrace(); null; }
    }

    private fun pushScheduledMessage(exception: Exception): Void? {
        pushErrors(platformFunctions, instancePluginState, exception, TMLocale::consoleErrorScheduledNotifications)
        return null
    }
}