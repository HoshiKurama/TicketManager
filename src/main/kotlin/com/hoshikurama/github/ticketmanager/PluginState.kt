package com.hoshikurama.github.ticketmanager

import com.hoshikurama.github.ticketmanager.databases.Database
import com.hoshikurama.github.ticketmanager.databases.MySQL
import com.hoshikurama.github.ticketmanager.databases.SQLite
import com.hoshikurama.github.ticketmanager.events.Commands
import com.hoshikurama.github.ticketmanager.events.TabCompletePaper
import com.hoshikurama.github.ticketmanager.events.TabCompleteSpigot
import org.bukkit.Bukkit
import java.io.File
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

class PluginState {
    internal val enabledLocales: AllLocales
    internal val cooldowns: Cooldown
    internal val database: Database
    internal val serverType: ServerType
    internal val allowUnreadTicketUpdates: Boolean

    init {
        mainPlugin.pluginLocked = true

        var announceGeneration = false
        if (!File(mainPlugin.dataFolder, "config.yml").exists()) {
            mainPlugin.saveDefaultConfig()
            announceGeneration = true
        }

        mainPlugin.reloadConfig()
        val config = mainPlugin.config

        enabledLocales = attemptConfigRead(
            { AllLocales(
                config.getString("Colour_Code")!!,
                config.getString("Preferred_Locale")!!,
                config.getString("Console_Locale")!!,
                config.getString("Force_Locale").toBoolean())
            }, AllLocales())

        cooldowns = attemptConfigRead(
            { Cooldown(
                config.getString("Use_Cooldowns").toBoolean(),
                config.getString("Cooldown_Time")!!.toLong())
            }, Cooldown(false, 0))

        database = attemptConfigRead(
            { val type = config.getString("Database_Mode")!!
                .let {
                    try { Database.Types.valueOf(it) }
                    catch (e: Exception) { Database.Types.SQLite }
                }
                when (type) {
                    Database.Types.MySQL -> MySQL(
                        config.getString("MySQL_Host")!!,
                        config.getString("MySQL_Port")!!,
                        config.getString("MySQL_DBName")!!,
                        config.getString("MySQL_Username")!!,
                        config.getString("MySQL_Password")!!)
                    Database.Types.SQLite -> SQLite()
                }
            }, SQLite())

        allowUnreadTicketUpdates = attemptConfigRead(
            { config.getString("Allow_Unread_Ticket_Updates").toBoolean() },
            true
        )

        serverType = attemptConfigRead(
            { Class.forName("com.destroystokyo.paper.VersionHistoryManager\$VersionData")
                ?.let { ServerType.Paper } ?: ServerType.Spigot },
            ServerType.Spigot)

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

    private inline fun <T> attemptConfigRead(expected: () -> T, failed: T): T =
        try { expected() }
        catch (e: Exception) {
            e.printStackTrace()
            postModifiedStacktrace(e)
            failed
        }
}