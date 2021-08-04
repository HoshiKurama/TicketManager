package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.common.*
import com.github.hoshikurama.ticketmanager.common.databases.Database
import com.github.hoshikurama.ticketmanager.common.databases.Memory
import com.github.hoshikurama.ticketmanager.common.databases.MySQL
import com.github.hoshikurama.ticketmanager.common.databases.SQLite
import com.github.hoshikurama.ticketmanager.common.discord.Discord
import com.github.hoshikurama.ticketmanager.spigot.events.Commands
import com.github.hoshikurama.ticketmanager.spigot.events.PlayerJoin
import com.github.hoshikurama.ticketmanager.spigot.events.TabComplete
import com.github.shynixn.mccoroutine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.md_5.bungee.api.chat.TextComponent
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import java.io.File

class TicketManagerPlugin : SuspendingJavaPlugin() {
    internal val pluginState = PluginState()
    internal lateinit var perms: Permission private set
    internal lateinit var configStateI: ConfigState

    private lateinit var metrics: Metrics


    companion object { lateinit var plugin: TicketManagerPlugin }
    init { plugin = this }

    override suspend fun onDisableAsync() {
        pluginState.pluginLocked.set(true)
        configStateI.database.closeDatabase()
    }

    override fun onEnable() {

        // Find Vault plugin
        Bukkit.getServer().servicesManager.getRegistration(Permission::class.java)?.provider
            ?.let { perms = it }
            ?: this.pluginLoader.disablePlugin(this)

        // Launch Metrics
        launch {
            metrics = Metrics(plugin, metricsKey)
            metrics.addCustomChart(
                Metrics.SingleLineChart("tickets_made") {
                    runBlocking {
                        val ticketCount = pluginState.ticketCountMetrics.get()
                        pluginState.ticketCountMetrics.set(0)
                        ticketCount
                    }
                }
            )
            metrics.addCustomChart(
                Metrics.SimplePie("database_type") {
                    configStateI.database.type.name
                }
            )
        }

        // Launches ConfigState initialisation
        launchAsync { loadPlugin() }

        // Register Event
        Bukkit.getServer().pluginManager.registerSuspendingEvents(PlayerJoin(), plugin)

        // Creates task timers
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            launchAsync { configStateI.cooldowns.filterMapAsync() }

            launchAsync {
                if (pluginState.pluginLocked.get()) return@launchAsync

                try {
                    // Mass Unread Notify
                    if (configStateI.allowUnreadTicketUpdates) {
                        Bukkit.getOnlinePlayers().asFlow()
                            .filter { it.has("ticketmanager.notify.unreadUpdates.scheduled") }
                            .onEach {
                               launch {
                                   val ticketIDs = configStateI.database.getIDsWithUpdatesFor(it.uniqueId).toList()
                                   val tickets = ticketIDs.joinToString(", ")

                                   if (ticketIDs.isEmpty()) return@launch

                                   val template = if (ticketIDs.size > 1) it.toTMLocale().notifyUnreadUpdateMulti
                                   else it.toTMLocale().notifyUnreadUpdateSingle

                                   val sentMessage = template.replace("%num%", tickets)
                                   it.sendMessage(sentMessage.run(::addColour))
                               }
                            }
                    }

                    val openPriority = configStateI.database.getOpenIDPriorityPairs().map { it.first }.toList()
                    val openCount = openPriority.count()

                    // Gets associated tickets
                    val assignments =
                        if (openCount == 0) listOf()
                        else configStateI.database.getBasicTickets(openPriority).mapNotNull { it.assignedTo }.toList()

                    // Open and Assigned Notify
                    Bukkit.getOnlinePlayers().asFlow()
                        .filter { it.has("ticketmanager.notify.openTickets.scheduled") }
                        .onEach { p ->
                            launch {
                                val groups = perms.getPlayerGroups(p).map { "::$it" }
                                val assignedCount = assignments.count { it == p.name || it in groups }

                                val sentMessage = p.toTMLocale().notifyOpenAssigned
                                    .replace("%open%", "$openCount")
                                    .replace("%assigned%", "$assignedCount")
                                    .run(::addColour)
                                    .run(::TextComponent)
                                p.spigot().sendMessage(sentMessage)
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
        pluginState.pluginLocked.set(true)

        configStateI = run {
            // Creates config file if not found
            if (!File(plugin.dataFolder, "config.yml").exists()) {
                plugin.saveDefaultConfig()

                // Notifies users config was generated after plugin state init
                launch {
                    while (!(::configStateI.isInitialized))
                        delay(100L)
                    pushMassNotify("ticketmanager.notify.warning") { it.warningsNoConfig }
                }
            }

            plugin.reloadConfig()
            plugin.config.run {
                val asyncDispatcher = plugin.asyncDispatcher as CoroutineDispatcher
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
                            asyncDispatcher = asyncDispatcher,
                        )
                        Database.Type.SQLite -> SQLite(path)
                        Database.Type.Memory -> Memory(
                            filePath = path,
                            backupFrequency = getLong("Memory_Backup_Frequency", 600)
                        )
                    }
                }

                val cooldown: () -> ConfigState.Cooldown? = {
                    ConfigState.Cooldown(
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
                    getBoolean("Allow_UpdateChecking", true)
                }

                val pluginVersion: () -> String = {
                    mainPlugin.description.version
                }

                val discord: suspend (TMLocale) -> Discord? = result@{
                    val enableDiscord = getBoolean("Use_Discord_Bot", false)

                    if (!enableDiscord) return@result null

                    // Discord is enabled...
                    Discord.create(
                        getBoolean("Discord_Notify_On_Assign"),
                        getBoolean("Discord_Notify_On_Close"),
                        getBoolean("Discord_Notify_On_Close_All"),
                        getBoolean("Discord_Notify_On_Comment"),
                        getBoolean("Discord_Notify_On_Create"),
                        getBoolean("Discord_Notify_On_Reopen"),
                        getBoolean("Discord_Notify_On_Priority_Change"),
                        getString("Discord_Bot_Token", "")!!,
                        getLong("Discord_Channel_ID", -1),
                        locale = it,
                        asyncDispatcher = asyncDispatcher,
                    )
                }

                val configState = ConfigState.createPluginState(
                    database,
                    cooldown,
                    localeHandler,
                    allowUnreadTicketUpdates,
                    checkForPluginUpdate,
                    pluginVersion,
                    discord,
                    path,
                    asyncContext
                )

                // Safely launches Discord coroutine login
                CoroutineScope(asyncDispatcher).launch {
                    try { configState.discord?.login() }
                    catch (e: Exception) { e.printStackTrace() } //Independent from outer coroutine. Must handle exception itself
                }

                configState
            }
        }

        launch {
            val updateNeeded = configStateI.database.updateNeeded()

            if (updateNeeded) {
                configStateI.database.updateDatabase(
                    onBegin = {
                        pushMassNotify("ticketmanager.notify.info") { it.informationDBUpdate }
                    },
                    onComplete = {
                        pushMassNotify("ticketmanager.notify.info") { it.informationDBUpdateComplete }
                        pluginState.pluginLocked.set(true)
                    },
                    offlinePlayerNameToUuidOrNull = {
                        Bukkit.getOfflinePlayers()
                            .filter { it.name == name }
                            .map { it.uniqueId }
                            .firstOrNull()
                    },
                    context = asyncContext
                )
            } else pluginState.pluginLocked.set(false)
        }


        withContext(minecraftDispatcher) {
            // Register events and commands
            configStateI.localeHandler.getCommandBases().forEach {
                getCommand(it)!!.setSuspendingExecutor(Commands())
                getCommand(it)!!.tabCompleter = TabComplete()
                // Remember to register any keyword in plugin.yml
            }
        }
    }
}