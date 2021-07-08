package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.componentDSL.formattedContent
import com.github.hoshikurama.ticketmanager.common.*
import com.github.shynixn.mccoroutine.*
import com.github.hoshikurama.ticketmanager.common.databases.Database
import com.github.hoshikurama.ticketmanager.common.databases.MySQL
import com.github.hoshikurama.ticketmanager.common.databases.SQLite
import com.github.hoshikurama.ticketmanager.paper.events.Commands
import com.github.hoshikurama.ticketmanager.paper.events.PlayerJoin
import com.github.hoshikurama.ticketmanager.paper.events.TabComplete
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import net.kyori.adventure.extra.kotlin.text
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import java.io.File

class TicketManagerPlugin : SuspendingJavaPlugin() {
    @OptIn(ObsoleteCoroutinesApi::class)
    private val singleOffThread = newSingleThreadContext("SingleOffThread")

    internal val pluginLocked = NonBlockingSync(singleOffThread, true)
    internal lateinit var perms: Permission private set
    internal lateinit var configState: PluginState

    internal val ticketCountMetrics = NonBlockingSync(singleOffThread, 0)
    private lateinit var metrics: Metrics


    companion object { lateinit var plugin: TicketManagerPlugin }
    init { plugin = this }

    override suspend fun onDisableAsync() {
        pluginLocked.set(true)
        asyncDispatcher.cancelChildren()
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
            launchAsync {
                if (pluginLocked.check()) return@launchAsync

                try {
                    val state = pluginState

                    // Mass Unread Notify
                    if (state.allowUnreadTicketUpdates) {
                        state.database.getBasicsWithUpdatesAsFlow()
                            .toList()
                            .groupBy ({ it.creatorUUID }, { it.id })
                            .asSequence()
                            .filter { it.key != null }
                            .mapNotNull { Bukkit.getPlayer(it.key!!)?.run { Pair(this, it.value) } }
                            .filter { it.first.has("ticketmanager.notify.unreadUpdates.scheduled") }
                            .forEach {
                                val template = if (it.second.size > 1) it.first.toTMLocale().notifyUnreadUpdateMulti
                                else it.first.toTMLocale().notifyUnreadUpdateSingle
                                val tickets = it.second.joinToString(", ")

                                val sentMessage = template.replace("%num%", tickets)
                                it.first.sendMessage(text { formattedContent(sentMessage) })
                            }
                    }

                    // Open and Assigned Notify
                    val tickets = state.database.getBasicOpenAsFlow()
                        .map { it.assignedTo }
                        .toList()

                    Bukkit.getOnlinePlayers().asSequence()
                        .filter { it.has("ticketmanager.notify.openTickets.scheduled") }
                        .forEach { p ->
                            val open = tickets.size.toString()
                            val assigned = tickets.asSequence()
                                .filterNotNull()
                                .filter { s ->
                                    if (s.startsWith("::"))
                                        perms.getPlayerGroups(p)
                                            .asSequence()
                                            .map { "::$it" }
                                            .filter { it == s }
                                            .any()
                                    else s == p.name
                                }.count().toString()

                            val sentMessage = p.toTMLocale().notifyOpenAssigned
                                .replace("%open%", open)
                                .replace("%assigned%", assigned)
                            p.sendMessage(text { formattedContent(sentMessage) })

                        }

                   launch { state.cooldowns.filterMapAsync() }

                } catch (e: Exception) {
                    e.printStackTrace()
                    //postModifiedStacktrace(e)
                }
            }
        }, 100, 12000)
    }

    internal suspend fun loadPlugin() = coroutineScope {
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
                            getString("MySQL_Password")!!
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
                        getBoolean("Force_Locale", false)
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
                    path
                )
            }
        }

        launch {
            configState.database.initialiseDatabase()
            val updateNeeded = configState.database.updateNeededAsync().await()

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
                    }
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