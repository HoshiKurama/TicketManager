package com.github.hoshikurama.ticketmanager

import com.github.hoshikurama.componentDSL.formattedContent
import com.github.hoshikurama.ticketmanager.data.Cooldown
import com.github.hoshikurama.ticketmanager.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.database.*
import com.github.hoshikurama.ticketmanager.misc.*
import com.github.hoshikurama.ticketmanager.platform.CommandPipeline
import com.github.hoshikurama.ticketmanager.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.platform.PlayerJoinEvent
import com.github.hoshikurama.ticketmanager.platform.TabComplete
import kotlinx.coroutines.*
import net.kyori.adventure.extra.kotlin.text

abstract class TMPlugin(
    mainDispatcher: CoroutineDispatcher,
    asyncDispatcher: CoroutineDispatcher,
    private val platformFunctions: PlatformFunctions,
    private val buildJoinEvent: (GlobalPluginState, InstancePluginState) -> PlayerJoinEvent,
    private val buildTabComplete: (PlatformFunctions, InstancePluginState) -> TabComplete,
    private val buildPipeline: (PlatformFunctions, InstancePluginState, GlobalPluginState) -> CommandPipeline,
) {
    protected val globalPluginState = GlobalPluginState(asyncDispatcher = asyncDispatcher, mainDispatcher = mainDispatcher)

    protected lateinit var instancePluginState: InstancePluginState private set
    protected lateinit var tabComplete: TabComplete private set
    protected lateinit var joinEvent: PlayerJoinEvent private set
    protected lateinit var commandPipeline: CommandPipeline private set

    private val independentAsyncScope: CoroutineScope
        get() = CoroutineScope(globalPluginState.asyncDispatcher)

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
        independentAsyncScope.launch { initializeData() } // Also registers
        performAsyncTaskTimer { independentAsyncScope.launch {  periodicTasks() } }
    }

    private suspend fun periodicTasks() {
        if (globalPluginState.pluginLocked.get()) return

        independentAsyncScope.launch { instancePluginState.cooldowns.filterMapAsync() } // only does stuff if enabled
        independentAsyncScope.launch {
            try {
                // Mass Unread Notify
                launch {
                    if (instancePluginState.allowUnreadTicketUpdates) {
                        platformFunctions.getOnlinePlayers(instancePluginState.localeHandler)
                            .filter { it.has("ticketmanager.notify.unreadUpdates.scheduled") }
                            .pForEach {
                                val ticketIDs = instancePluginState.database.getTicketIDsWithUpdatesFor(it.uniqueID)
                                val tickets = ticketIDs.joinToString(", ")
                                if (ticketIDs.isEmpty()) return@pForEach

                                val template = if (ticketIDs.size > 1) it.locale.notifyUnreadUpdateMulti
                                else it.locale.notifyUnreadUpdateSingle

                                template.replace("%num%", tickets)
                                    .run(it::sendMessage)
                            }
                    }
                }

                // Open and Assigned Notify
                launch {
                    val (openTickets, _, openCount,_) = instancePluginState.database.getOpenTickets(1, 0)

                    platformFunctions.getOnlinePlayers(instancePluginState.localeHandler)
                        .filter { it.has("ticketmanager.notify.openTickets.scheduled") }
                        .pForEach { p ->
                            val groups = p.permissionGroups.map { "::$it" }
                            val assignedCount = openTickets.count { it.assignedTo == p.name || it.assignedTo in groups }

                            if (assignedCount != 0)
                                p.locale.notifyOpenAssigned
                                    .replace("%open%", "$openCount")
                                    .replace("%assigned%", "$assignedCount")
                                    .run(p::sendMessage)
                        }
                }
            } catch (e: Exception) {
                pushErrors(platformFunctions, instancePluginState, e, TMLocale::consoleErrorScheduledNotifications)
            }
        }
    }

    suspend fun disablePluginAsync() {
        globalPluginState.pluginLocked.set(true)
        instancePluginState.database.closeDatabase()
    }

    suspend fun initializeData() = coroutineScope {
        globalPluginState.pluginLocked.set(true)

        // Generate config file if not found and alert users new config was generated
        if (!configExists()) {
            generateConfig()

            independentAsyncScope.launch {
                while (!::instancePluginState.isInitialized)
                    delay(100L)
                platformFunctions.massNotify(instancePluginState.localeHandler, "ticketmanager.notify.warning") {
                    text { formattedContent(it.warningsNoConfig) }
                }
            }
        }
        reloadConfig()

        // Builds associated objects from config
        readConfig().let { c ->
            // List of errors
            val errors = mutableListOf<Pair<String, String>>()
            fun <T> T.addToErrors(location: String, toString: (T) -> String): T = this.also { errors.add(location to toString(it)) }

            // Builds LocaleHandler object
            val localeHandler = kotlin.run {
                val colourCode = c.localeHandlerColourCode ?: "&3".addToErrors("Colour_Code") { it }
                val preferredLocale = c.localeHandlerPreferredLocale ?: "en_ca".addToErrors("Preferred_Locale") { it }
                val consoleLocale = c.localeHandlerConsoleLocale ?: "en_ca".addToErrors("Console_Locale") { it }
                val forceLocale = c.localeHandlerForceLocale ?: false.addToErrors("Force_Locale", Boolean::toString)
                LocaleHandler.buildLocalesAsync(colourCode, preferredLocale, consoleLocale, forceLocale)
            }

            // Builds DatabaseBuilders object
            val databaseBuilders = DatabaseBuilders(
                mySQLBuilder = kotlin.run {
                    val port = c.mySQLPort ?: "_".addToErrors("MySQL_Port") { it }
                    val host = c.MySQLHost ?: "_".addToErrors("MySQL_Host") { it }
                    val dbName = c.mySQLDBName ?: "_".addToErrors("MySQL_DBName") { it }
                    val username = c.mySQLUsername ?: "_".addToErrors("MySQL_Username") { it }
                    val password = c.mySQLPassword ?: "_".addToErrors("MySQL_Password") { it }
                    MySQLBuilder(host, port, dbName, username, password, globalPluginState.asyncDispatcher)
                },
                memoryBuilder = kotlin.run {
                    val backupFrequency = c.memoryFrequency ?: 600L.addToErrors("Memory_Backup_Frequency", Long::toString)
                    MemoryBuilder(c.memoryPath, backupFrequency)
                },
                sqLiteBuilder = SQLiteBuilder(c.sqLitePath)
            )

            // Builds Database object
            val database = kotlin.run {
                val type =
                    try { Database.Type.valueOf(c.dbTypeAsStr!!.uppercase()) }
                    catch (e: Exception) { Database.Type.SQLITE.addToErrors("Database_Mode", Database.Type::name) }

                try {
                    when (type) {
                        Database.Type.SQLITE -> databaseBuilders.sqLiteBuilder.build().apply { initializeDatabase() }
                        Database.Type.MYSQL -> databaseBuilders.mySQLBuilder.build().apply { initializeDatabase() }
                        Database.Type.MEMORY -> databaseBuilders.memoryBuilder.build().apply { initializeDatabase() }
                    }
                } catch (e: Exception) {
                    independentAsyncScope.launch {
                        while (!::instancePluginState.isInitialized) delay(100L)
                        pushErrors(platformFunctions, instancePluginState, e, TMLocale::consoleErrorBadDatabase)
                    }
                    databaseBuilders.sqLiteBuilder.build().apply { initializeDatabase() }
                }
            }

            // Builds Discord object
            val discord = kotlin.run {
                val enableDiscord = c.enableDiscord ?: false.addToErrors("Use_Discord_Bot", Boolean::toString)
                if (enableDiscord) Discord.create(
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
                    asyncDispatcher = globalPluginState.asyncDispatcher,
                ) else null
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
            errors.forEach { (node, default) ->
                localeHandler.consoleLocale.consoleWarningInvalidConfigNode
                    .replaceFirst("%node%", node)
                    .replaceFirst("%default%", default)
                    .run(platformFunctions::pushWarningToConsole)
            }

            if (errors.size > 0) {
                platformFunctions.massNotify(localeHandler, "ticketmanager.notify.warning") {
                    it.warningsInvalidConfig.replaceFirst("%num%", errors.size.toString())
                        .run(::toColouredAdventure)
                }
            }
        }

        platformFunctions.pushInfoToConsole(instancePluginState.localeHandler.consoleLocale.consoleInitializationComplete)


        // Launch Discord if requested on coroutine with independent scope
        instancePluginState.discord?.run {
            CoroutineScope(globalPluginState.asyncDispatcher).launch {
                try { login() }
                catch (e: Exception) {
                    pushErrors(platformFunctions, instancePluginState, e, TMLocale::consoleErrorBadDiscord)
                }
            }
        }
        // Registers itself
        registerProcesses()

        globalPluginState.pluginLocked.set(false)
    }
}