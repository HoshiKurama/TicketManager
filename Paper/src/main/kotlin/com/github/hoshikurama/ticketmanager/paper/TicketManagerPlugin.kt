package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.componentDSL.formattedContent
import com.github.hoshikurama.ticketmanager.common.*
import com.github.hoshikurama.ticketmanager.common.databases.Database
import com.github.hoshikurama.ticketmanager.common.databases.MySQL
import com.github.hoshikurama.ticketmanager.common.databases.Redis
import com.github.hoshikurama.ticketmanager.common.databases.SQLite
import com.github.hoshikurama.ticketmanager.paper.events.Commands
import com.github.hoshikurama.ticketmanager.paper.events.PlayerJoin
import com.github.hoshikurama.ticketmanager.paper.events.TabComplete
import com.github.shynixn.mccoroutine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.kyori.adventure.extra.kotlin.text
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import java.io.File

class TicketManagerPlugin : SuspendingJavaPlugin() {
    @OptIn(ObsoleteCoroutinesApi::class)
    private val singleOffThread = newSingleThreadContext("SingleOffThread")

    internal val jobCount = NonBlockingSync(singleOffThread, 0)
    internal val pluginLocked = NonBlockingSync(singleOffThread, true)
    internal lateinit var perms: Permission private set
    internal lateinit var configState: PluginState

    internal val ticketCountMetrics = NonBlockingSync(singleOffThread, 0)
    private lateinit var metrics: Metrics


    companion object { lateinit var plugin: TicketManagerPlugin }
    init { plugin = this }

    override suspend fun onDisableAsync() {
        pluginLocked.set(true)
        pluginState.database.closeDatabase()
    }

    override fun onEnable() {

        // Find Vault plugin
        server.servicesManager.getRegistration(Permission::class.java)?.provider
            ?.let { perms = it }
            ?: this.pluginLoader.disablePlugin(this)

        // Launch Metrics
        launch {
            metrics = Metrics(plugin, metricsKey)
            metrics.addCustomChart(
                Metrics.SingleLineChart("tickets_made") {
                    runBlocking {
                        val ticketCount = ticketCountMetrics.check()
                        ticketCountMetrics.set(0)
                        ticketCount
                    }
                }
            )
        }

        // Launches PluginState initialisation
        launchAsync { loadPlugin() }

        // Register Event
        server.pluginManager.registerSuspendingEvents(PlayerJoin(), plugin)

        // Creates task timers
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            launchAsync { configState.cooldowns.filterMapAsync() }

            launchAsync {
                if (pluginLocked.check()) return@launchAsync

                try {
                    // Mass Unread Notify
                    if (configState.allowUnreadTicketUpdates) {
                        Bukkit.getOnlinePlayers().asFlow()
                            .filter { it.has("ticketmanager.notify.unreadUpdates.scheduled") }
                            .onEach {
                               launch {
                                   val ticketIDs = configState.database.getIDsWithUpdatesFor(it.uniqueId).toList()
                                   val tickets = ticketIDs.joinToString(", ")

                                   if (ticketIDs.isEmpty()) return@launch

                                   val template = if (ticketIDs.size > 1) it.toTMLocale().notifyUnreadUpdateMulti
                                   else it.toTMLocale().notifyUnreadUpdateSingle

                                   val sentMessage = template.replace("%num%", tickets)
                                   it.sendMessage(text { formattedContent(sentMessage) })
                               }
                            }
                    }

                    val openPriority = configState.database.getOpenIDPriorityPairs().map { it.first }.toList()
                    val openCount = openPriority.count()
                    val assignments = configState.database.getBasicTickets(openPriority).mapNotNull { it.assignedTo }.toList()

                    // Open and Assigned Notify
                    Bukkit.getOnlinePlayers().asFlow()
                        .filter { it.has("ticketmanager.notify.openTickets.scheduled") }
                        .onEach { p ->
                            launch {
                                val groups = perms.getPlayerGroups(p).map { "::$it" }
                                val assignedCount = assignments
                                    .filter { it == p.name || it in groups }
                                    .count()

                                val sentMessage = p.toTMLocale().notifyOpenAssigned
                                    .replace("%open%", "$openCount")
                                    .replace("%assigned%", "$assignedCount")
                                p.sendMessage(text { formattedContent(sentMessage) })
                            }
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                    postModifiedStacktrace(e)
                }
            }
        }, 100, 12000)
    }

    internal suspend fun loadPlugin() = withContext(plugin.asyncDispatcher) {
        pluginLocked.set(true)

        configState = run {
            // Creates config file if not found
            if (!File(plugin.dataFolder, "config.yml").exists()) {
                plugin.saveDefaultConfig()

                // Notifies users config was generated after plugin state init
                launch {
                    while (!(::configState.isInitialized))
                        delay(100L)
                    pushMassNotify("ticketmanager.notify.warning") { text { formattedContent(it.warningsNoConfig) } }
                }
            }

            plugin.reloadConfig()
            plugin.config.run {
                val path = plugin.dataFolder.absolutePath
                val database: () -> Database? = {
                    val type = getString("Database_Mode", "SQLite")!!
                        .let { tryOrNull { Database.Type.valueOf(it) } ?: Database.Type.SQLite }

                    when (type) {
                        Database.Type.MySQL -> MySQL(
                            getString("MySQL_Host")!!,
                            getString("MySQL_Port")!!,
                            getString("MySQL_DBName")!!,
                            getString("MySQL_Username")!!,
                            getString("MySQL_Password")!!,
                            asyncDispatcher = (plugin.asyncDispatcher as CoroutineDispatcher),
                        )
                        Database.Type.Redis -> Redis(
                            absoluteDataFolderPath = path,
                            password = getString("Redis_Password", "default")!!,
                            getInt("Redis_Backup_Frequency", 300)
                        )
                        Database.Type.SQLite -> SQLite(path)
                    }
                }

                val cooldown: () -> PluginState.Cooldown? = {
                    PluginState.Cooldown(
                        getBoolean("Use_Cooldowns", false),
                        getLong("Cooldown_Time", 0L)
                    )
                }

                val localeHandler: suspend () -> LocaleHandler? = {
                    LocaleHandler.buildLocalesAsync(
                        getString("Colour_Code", "&3")!!,
                        getString("Preferred_Locale", "en_ca")!!,
                        getString("Console_Locale", "en_ca")!!,
                        getBoolean("Force_Locale", false),
                        asyncContext
                    )
                }

                val allowUnreadTicketUpdates: () -> Boolean? = {
                    getBoolean("Allow_Unread_Ticket_Updates", true)
                }

                val checkForPluginUpdate: () -> Boolean? = {
                    getBoolean("Allow_UpdateChecking", false)
                }

                val pluginVersion: () -> String = {
                    mainPlugin.description.version
                }

                PluginState.createPluginState(
                    database,
                    cooldown,
                    localeHandler,
                    allowUnreadTicketUpdates,
                    checkForPluginUpdate,
                    pluginVersion,
                    path,
                    asyncContext
                )
            }
        }

        launch {
            val updateNeeded = configState.database.updateNeeded()

            if (updateNeeded) {
                configState.database.updateDatabase(
                    onBegin = {
                        pushMassNotify("ticketmanager.notify.info") {
                            text { formattedContent(it.informationDBUpdate) }
                        }
                    },
                    onComplete = {
                        pushMassNotify("ticketmanager.notify.info") {
                            text { formattedContent(it.informationDBUpdateComplete) }
                        }
                        pluginLocked.set(true)
                    },
                    offlinePlayerNameToUuidOrNull = {
                        Bukkit.getOfflinePlayers()
                            .filter { it.name == name }
                            .map { it.uniqueId }
                            .firstOrNull()
                    },
                    context = asyncContext
                )
            } else pluginLocked.set(false)
        }


        withContext(minecraftDispatcher) {
            // Register events and commands
            configState.localeHandler.getCommandBases().forEach {
                getCommand(it)!!.setSuspendingExecutor(Commands())
                server.pluginManager.registerEvents(TabComplete(), this@TicketManagerPlugin)
                // Remember to register any keyword in plugin.yml
            }
        }
    }
}