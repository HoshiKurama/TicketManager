package com.hoshikurama.github.ticketmanager

import com.hoshikurama.github.ticketmanager.databases.Database
import com.hoshikurama.github.ticketmanager.databases.MySQL
import com.hoshikurama.github.ticketmanager.databases.SQLite
import com.hoshikurama.github.ticketmanager.events.Commands
import com.hoshikurama.github.ticketmanager.events.TabCompletePaper
import com.hoshikurama.github.ticketmanager.events.TabCompleteSpigot
import org.bukkit.Bukkit
import java.io.File
import java.io.InputStream
import java.net.URL
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Level

class PluginState {
    internal val enabledLocales: AllLocales
    internal val cooldowns: Cooldown
    internal val database: Database
    internal val serverType: ServerType
    internal val allowUnreadTicketUpdates: Boolean
    internal val updateAvailable: AtomicReference<Pair<String, String>?> = AtomicReference(null)

    init {
        mainPlugin.pluginLocked = true

        var announceGeneration = false
        if (!File(mainPlugin.dataFolder, "config.yml").exists()) {
            mainPlugin.saveDefaultConfig()
            announceGeneration = true
        }

        mainPlugin.reloadConfig()
        val config = mainPlugin.config

        config.run {
            enabledLocales = AllLocales(
                getString("Colour_Code", "&3")!!,
                getString("Preferred_Locale", "en_ca")!!,
                getString("Console_Locale", "en_ca")!!,
                getBoolean("Force_Locale", false)
            )

            cooldowns = Cooldown(
                getBoolean("Use_Cooldowns", false),
                getLong("Cooldown_Time", 0L)
            )

            database = tryOrNull {
                val type = getString("Database_Mode", "SQLite")!!
                    .let { tryOrNull { Database.Types.valueOf(it) } ?: Database.Types.SQLite }

                when (type) {
                    Database.Types.MySQL -> MySQL(
                        getString("MySQL_Host")!!,
                        getString("MySQL_Port")!!,
                        getString("MySQL_DBName")!!,
                        getString("MySQL_Username")!!,
                        getString("MySQL_Password")!!)
                    Database.Types.SQLite -> SQLite()
                }
            } ?: SQLite()

            allowUnreadTicketUpdates = getBoolean("Allow_Unread_Ticket_Updates", true)

            // Assigns update available
            val allowUpdateCheck = getBoolean("Allow_UpdateChecking", false)
            if (allowUpdateCheck) {
                Bukkit.getScheduler().runTaskAsynchronously(mainPlugin, Runnable {

                    val curVersion = mainPlugin.description.version
                    val latestVersion = UpdateChecker(91178).getLatestVersion()
                        .run { this ?: curVersion }
                    updateAvailable.set(curVersion to latestVersion)
                })
            } else updateAvailable.set(null)
        }

        serverType = tryOrNull {
            Class.forName("com.destroystokyo.paper.VersionHistoryManager\$VersionData")
            ServerType.Paper
        } ?: ServerType.Spigot


        if (announceGeneration) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(mainPlugin, {
                pushMassNotify("ticketmanager.notify.warning", { it.warningsNoConfig }, Level.WARNING)
            }, 100)
        }

        // Register events and commands
        enabledLocales.getCommandBases().forEach {
            mainPlugin.getCommand(it)?.setExecutor(Commands())
            if (serverType == ServerType.Paper)
                mainPlugin.server.pluginManager.registerEvents(TabCompletePaper(), mainPlugin)
            else mainPlugin.getCommand(it)?.tabCompleter = TabCompleteSpigot()
            // Remember to register any keyword in plugin.yml
        }

        // Allows object to initialise while scheduling db update
        if (database.updateNeeded()) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(mainPlugin, Runnable {
                database.updateDatabase()
                mainPlugin.pluginLocked = false
            }, 20)
        } else mainPlugin.pluginLocked = false
    }

    class Cooldown(private val enabled: Boolean, private val duration: Long) {
        private val map = ConcurrentHashMap<UUID, Long>()

        fun checkAndSet(uuid: UUID?): Boolean {
            if (!enabled || uuid == null) return false

            val curTime = Instant.now().epochSecond
            val applies = map[uuid]?.let { it <= curTime } ?: false
            return if (applies) true
            else map.put(uuid, duration + curTime).run { true }
        }

        fun filterMap(): Unit = map.forEach { if (it.value > Instant.now().epochSecond) map.remove(it.key) }
    }

    enum class ServerType {
        Paper, Spigot
    }

    private inline fun <T> tryOrNull(function: () -> T): T? =
        try { function() }
        catch (ignored: Exception) { null }
}