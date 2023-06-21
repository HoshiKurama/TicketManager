package com.github.hoshikurama.ticketmanager.commonse

import com.github.hoshikurama.ticketmanager.api.common.ticket.Assignment
import com.github.hoshikurama.ticketmanager.api.common.ticket.Creator
import com.github.hoshikurama.ticketmanager.common.*
import com.github.hoshikurama.ticketmanager.commonse.datas.ConfigState
import com.github.hoshikurama.ticketmanager.commonse.datas.Cooldown
import com.github.hoshikurama.ticketmanager.commonse.datas.GlobalState
import com.github.hoshikurama.ticketmanager.commonse.extensions.DatabaseManager
import com.github.hoshikurama.ticketmanager.commonse.extensions.defaultDatabase
import com.github.hoshikurama.ticketmanager.commonse.h2database.CachedH2
import com.github.hoshikurama.ticketmanager.commonse.misc.ConfigParameters
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.misc.pushErrors
import com.github.hoshikurama.ticketmanager.commonse.misc.templated
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.platform.PlayerJoinEvent
import com.github.hoshikurama.ticketmanager.commonse.platform.events.EventBuilder
import com.github.hoshikurama.ticketmanager.commonse.utilities.asDeferredThenAwait
import kotlinx.coroutines.*
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.model.group.Group
import java.nio.file.Files
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.time.DurationUnit
import kotlin.time.toDuration

abstract class TMPlugin(
    private val buildPlatformFunctions: (String?) -> PlatformFunctions,
    private val buildJoinEvent: (ConfigState, PlatformFunctions, TMLocale) -> PlayerJoinEvent,
    private val cooldownAfterRemoval: suspend (UUID) -> Unit,
    private val eventBuilder: EventBuilder
) {
    companion object {
        @Volatile
        lateinit var lpGroupNames: List<String> private set
        @Volatile internal var cooldown: Cooldown? = null
            private set
        @Volatile lateinit var activeInstance: TMPlugin
    }

    private val periodicTasks = mutableListOf<Job>()

// Abstract Functions
    abstract fun platformRunFirst()
    abstract fun platformRunSyncAfterCoreLaunch(
        cooldown: Cooldown?,
        activeLocale: TMLocale,
        configState: ConfigState,
        joinEvent: PlayerJoinEvent,
        lpGroupNames: List<String>,
        platformFunctions: PlatformFunctions,
        eventBuilder: EventBuilder,
    )
    abstract fun platformUpdateOnReload(
        cooldown: Cooldown?,
        activeLocale: TMLocale,
        configState: ConfigState,
        joinEvent: PlayerJoinEvent,
        lpGroupNames: List<String>,
        platformFunctions: PlatformFunctions,
        eventBuilder: EventBuilder,
    )

    // Config Functions
    abstract fun configExists(): Boolean
    abstract fun generateConfig()
    abstract fun reloadConfig()
    abstract fun readConfig(): ConfigParameters

    // Config update functions
    abstract fun loadInternalConfig(): List<String>
    abstract fun loadPlayerConfig(): List<String>
    abstract fun writeNewConfig(entries: List<String>)

// Non-Abstract Functions
    fun enableTicketManager() {
        GlobalState.dataInitializationComplete = false
        GlobalState.databaseSelected = false

        // This part runs sync for initial startup
        platformRunFirst()
        val lpGroupNamesDeferred = loadLPGroupNames()
        val errors = mutableListOf<Pair<String, String>>()
        val (coreItems, config) = loadCoreItems(errors)
        val (cooldown, activeLocale, configState, joinEvent, platformFunctions) = coreItems

        // Database Handling
        println(activeLocale.consoleDatabaseWaitStart)
        DatabaseManager.register(defaultDatabase) { CachedH2(config.pluginFolderPath.absolutePathString()) }
        TMCoroutine.launchGlobal { loadDatabase(configState, config, platformFunctions, errors, activeLocale) {
            platformFunctions.pushInfoToConsole(activeLocale.consoleDatabaseLoaded)
            GlobalState.databaseSelected = true
        } }

        lpGroupNames = runBlocking { lpGroupNamesDeferred.await() }

        platformRunSyncAfterCoreLaunch(
            cooldown = cooldown,
            activeLocale = activeLocale,
            configState = configState,
            joinEvent = joinEvent,
            lpGroupNames = lpGroupNames,
            platformFunctions = platformFunctions,
            eventBuilder = eventBuilder,
        )

        // Startup is now async...
        performRemainingTasksAsync(activeLocale, configState, platformFunctions, errors)
        loadUpdateCheckersAsync(config, configState, platformFunctions, errors)
        GlobalState.dataInitializationComplete = true
    }

    fun disableTicketManager() {
        GlobalState.dataInitializationComplete = false
        GlobalState.databaseSelected = false

        // Cancels periodic coroutine tasks
        cooldown?.shutdown()
        periodicTasks.forEach(Job::cancel)

        DatabaseManager.activeDatabase.closeDatabase()
        runBlocking { TMCoroutine.cancelTasks("Plugin shutting down!") }
    }

    suspend fun reloadTicketManager(): Unit = coroutineScope {
        // Disable
        cooldown?.shutdown()
        periodicTasks.forEach(Job::cancel)
        DatabaseManager.activeDatabase.closeDatabase()

        try { TMCoroutine.cancelTasks("Plugin shutting down!") }
        catch (ignored: Exception) {}

        // Startup
        val lpGroupNamesAsync = loadLPGroupNames()
        val errors = mutableListOf<Pair<String, String>>()
        val (coreItems, config) = loadCoreItems(errors)
        val (cooldown, activeLocale, configState, joinEvent, platformFunctions) = coreItems

        DatabaseManager.register(defaultDatabase) { CachedH2(config.pluginFolderPath.absolutePathString()) }
        loadDatabase(configState, config, platformFunctions, errors, activeLocale)

        platformUpdateOnReload(
            cooldown = cooldown,
            joinEvent = joinEvent,
            configState = configState,
            activeLocale = activeLocale,
            platformFunctions = platformFunctions,
            eventBuilder = eventBuilder,
            lpGroupNames = lpGroupNamesAsync.await(),
        )
        lpGroupNames = lpGroupNamesAsync.await()
        performRemainingTasksAsync(activeLocale, configState, platformFunctions, errors)
    }

// Locale stuff...

    private fun loadCoreItems(
        errors: MutableList<Pair<String, String>>
    ): Pair<CoreItems, ConfigParameters> {

        fun <T> T.addToErrors(location: String, toString: (T) -> String): T =
            this.also { errors.add(location to toString(it)) }

        // Config file...
        val configGenerationRequired = !configExists()
        if (configGenerationRequired) generateConfig()
        reloadConfig()
        val config = readConfig()

        kotlin.run {
            // Updates config file if requested
            val autoUpdateConfig = config.autoUpdateConfig ?: true.addToErrors("Auto_Update_Config", Boolean::toString)
            if (autoUpdateConfig) TMCoroutine.launchGlobal { updateConfig(::loadPlayerConfig, ::loadInternalConfig, ::writeNewConfig) }
        }

        // AVC...
        val enableAdvancedVisualControl = kotlin.run {
            val enableAVC = config.enableAdvancedVisualControl
                ?: false.addToErrors("Enable_Advanced_Visual_Control", Boolean::toString)

            return@run enableAVC.also {
                if (!enableAVC) return@also
                val localeFolder = config.pluginFolderPath.resolve("locales")
                localeFolder.toFile().run { if (!exists()) mkdir() }
                supportedLocales
                    .filterNot { localeFolder.resolve("$it.yml").exists() }
                    .forEach {
                        this@TMPlugin::class.java.classLoader
                            .getResourceAsStream("locales/visual/$it.yml")!!
                            .use { input -> Files.copy(input, localeFolder.resolve("$it.yml")) }
                    }
            }
        }

        // Locale...
        val activeLocale = buildLocale(
            mainColourCode = config.localedColourCode ?: "&3".addToErrors("Colour_Code") { it },
            preferredLocale = config.selectedLocale ?: "en_ca".addToErrors("Preferred_Locale") { it },
            enableAVC = enableAdvancedVisualControl,
            localesFolderPath = config.pluginFolderPath,
        )

        // Config State...
        val configState = kotlin.run {

            val proxyServerName = config.proxyServerName.let {
                if (it == null) null.addToErrors("Proxy_Server_Name") { "null" }
                else it.takeIf(String::isNotBlank)
            }
            val enableProxyMode = proxyServerName != null && config.enableProxyMode
                    ?: false.addToErrors("Enable_Proxy", Boolean::toString)

            ConfigState(
                proxyUpdate = null,
                enableProxyMode = enableProxyMode,
                proxyServerName = proxyServerName,
                allowProxyUpdatePings = enableProxyMode && config.allowProxyUpdateChecks
                    ?: true.addToErrors("Allow_Proxy_UpdateChecking", Boolean::toString),
                allowUnreadTicketUpdates = config.allowUnreadTicketUpdates
                    ?: true.addToErrors("Allow_Unread_Ticket_Updates", Boolean::toString),
                printModifiedStacktrace = config.printModifiedStacktrace
                    ?: true.addToErrors("Print_Modified_Stacktrace", Boolean::toString),
                printFullStacktrace = config.printFullStacktrace
                    ?: false.addToErrors("Print_Full_Stacktrace", Boolean::toString),
                pluginUpdate = UpdateChecker(
                    canCheck = config.checkForPluginUpdates
                        ?: true.addToErrors("Allow_UpdateChecking", Boolean::toString),
                    location= UpdateChecker.Location.MAIN
                ),
            )
        }

        val cooldown = Cooldown(
            duration = config.cooldownSeconds ?: 1L.addToErrors("Cooldown_Time", Long::toString),
            runAfterRemoval = cooldownAfterRemoval
        ).takeIf { config.allowCooldowns ?: false.addToErrors("Use_Cooldowns", Boolean::toString) }

        // Platform Functions...
        val platformFunctions = buildPlatformFunctions(configState.proxyServerName)

        // Error Pushing...
        TMCoroutine.launchGlobal {
            if (!configGenerationRequired) return@launchGlobal
            platformFunctions.massNotify(
                "ticketmanager.notify.warning",
                activeLocale.warningsNoConfig.parseMiniMessage()
            )
        }

        return CoreItems(
            cooldown,
            activeLocale,
            configState,
            buildJoinEvent(configState, platformFunctions, activeLocale),
            platformFunctions) to config
    }

    private suspend fun loadDatabase(
        configState: ConfigState,
        configParameters: ConfigParameters,
        platformFunctions: PlatformFunctions,
        errors: MutableList<Pair<String, String>>,
        locale: TMLocale,
        afterLoad: (() -> Unit)? = null,
    ) {
        fun <T> T.addToErrors(location: String, toString: (T) -> String): T =
            this.also { errors.add(location to toString(it)) }

        GlobalState.databaseType = configParameters.dbTypeAsStr ?: defaultDatabase.addToErrors("Database_Mode") { it }
        DatabaseManager.activateNewDatabase(GlobalState.databaseType!!, platformFunctions, configState, locale)
        afterLoad?.invoke()
    }

    private fun loadLPGroupNames(): Deferred<List<String>> = TMCoroutine.asyncGlobal {
        LuckPermsProvider.get().groupManager.run {
            loadAllGroups().asDeferredThenAwait()
            loadedGroups.map(Group::getName)
        }
    }

    private fun loadUpdateCheckersAsync(
        config: ConfigParameters,
        configState: ConfigState,
        platformFunctions: PlatformFunctions,
        errors: MutableList<Pair<String, String>>,
    ) {
        // Config Read with Error Checks
        fun <T> T.addToErrors(location: String, toString: (T) -> String): T =
            this.also { errors.add(location to toString(it)) }

        // Launch Continuous Update Checker
        if (configState.pluginUpdate.canCheck) {
            TMCoroutine.launchSupervised {
                val pluginUpdateFreq = config.pluginUpdateFrequency
                    ?: 1L.addToErrors("Plugin_Update_Check_Frequency", Long::toString)

                while (true) {
                    configState.pluginUpdate = UpdateChecker(true, UpdateChecker.Location.MAIN)
                    delay(pluginUpdateFreq.toDuration(DurationUnit.HOURS))
                }
            }.run(periodicTasks::add)
        }

        // Proxy Update Checker...
        if (configState.enableProxyMode && configState.allowProxyUpdatePings) {
            val proxyUpdateFreq = config.proxyUpdateFrequency
                ?: 1L.addToErrors("Proxy_Update_Check_Frequency", Long::toString)

            TMCoroutine.launchSupervised {
                while (true) {
                    delay(proxyUpdateFreq.toDuration(DurationUnit.HOURS))
                    val message = ProxyUpdate.encodeProxyMsg(configState.proxyServerName!!)
                    platformFunctions.relayMessageToProxy(
                        Server2Proxy.ProxyVersionRequest.waterfallString(),
                        message
                    )
                }
            }.run(periodicTasks::add)
        }
    }

    private fun performRemainingTasksAsync(
        activeLocale: TMLocale,
        configState: ConfigState,
        platformFunctions: PlatformFunctions,
        errors: MutableList<Pair<String, String>>,
    ) {
        // Standard Periodic Tasks...
        TMCoroutine.launchSupervised noReturn@{
            while (true) {
                delay(10L.toDuration(DurationUnit.MINUTES))
                if (!GlobalState.isPluginLocked) continue

                // Mass Unread Notify Period Task
                TMCoroutine.launchSupervised {
                    if (configState.allowUnreadTicketUpdates) return@launchSupervised
                    platformFunctions.getAllOnlinePlayers()
                        .filter { it.has("ticketmanager.notify.unreadUpdates.scheduled") }
                        .forEach {
                            val ids = DatabaseManager.activeDatabase
                                .getTicketIDsWithUpdatesForAsync(Creator.User(it.uuid))
                                .asDeferredThenAwait()
                            val tickets = ids.joinToString(", ")

                            if (ids.isEmpty()) return@forEach

                            val template = if (ids.size > 1) activeLocale.notifyUnreadUpdateMulti
                            else activeLocale.notifyUnreadUpdateSingle

                            template.parseMiniMessage("num" templated tickets)
                                .run(it::sendMessage)
                        }
                }

                // Open and Assigned Notify
                TMCoroutine.launchSupervised {
                    try {
                        val (openTickets, _, openCount, _) = DatabaseManager.activeDatabase
                            .getOpenTicketsAsync(1, 0)
                            .asDeferredThenAwait()
                        platformFunctions.getAllOnlinePlayers()
                            .filter { it.has("ticketmanager.notify.openTickets.scheduled") }
                            .forEach { p ->
                                val groups = p.permissionGroups.map(Assignment::PermissionGroup)
                                val assignedCount = openTickets.count {
                                    it.assignedTo == Assignment.Player(p.username)
                                            || it.assignedTo in groups
                                }

                                if (assignedCount != 0) {
                                    activeLocale.notifyOpenAssigned.parseMiniMessage(
                                        "open" templated "$openCount",
                                        "assigned" templated "$assignedCount"
                                    ).run(p::sendMessage)
                                }
                            }
                    } catch (e: Exception) {
                        pushErrors(platformFunctions, configState, activeLocale, e, TMLocale::consoleErrorScheduledNotifications)
                    }


                }
            }
        }.run(periodicTasks::add)

        // Notification Tasks which aren't important to wait for (Not repeating)
        TMCoroutine.launchGlobal {

            // Print warnings
            errors.forEach { (node, default) ->
                activeLocale.consoleWarningInvalidConfigNode
                    .replaceFirst("%node%", node)
                    .replaceFirst("%default%", default)
                    .run(platformFunctions::pushWarningToConsole)
            }

            // Notification stating something went wrong during startup
            if (errors.isNotEmpty()) {
                platformFunctions.massNotify(
                    "ticketmanager.notify.warning",
                    activeLocale.warningsInvalidConfig
                        .parseMiniMessage("count" templated "${errors.size}")
                )
            }
            platformFunctions.pushInfoToConsole(activeLocale.consoleInitializationComplete)


            // Place update is available into Console
            if (configState.pluginUpdate.canCheck && configState.pluginUpdate.latestVersionIfNotLatest != null) {
                activeLocale.notifyPluginUpdate.parseMiniMessage(
                    "current" templated mainPluginVersion,
                    "latest" templated configState.pluginUpdate.latestVersionIfNotLatest!!
                ).run(platformFunctions.getConsoleAudience()::sendMessage)
            }
        }
    }
}

private data class CoreItems(
    val cooldown: Cooldown?,
    val activeLocale: TMLocale,
    val configState: ConfigState,
    val joinEvent: PlayerJoinEvent,
    val platformFunctions: PlatformFunctions,
)