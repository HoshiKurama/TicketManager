package com.hoshikurama.github.ticketmanager

import com.hoshikurama.github.ticketmanager.events.PlayerJoin
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level

class TicketManagerPlugin : JavaPlugin() {
    private val pluginLockedInternal = AtomicBoolean(true)
    private lateinit var metrics: Metrics
    internal val ticketCountMetrics = AtomicInteger(0)

    lateinit var perms: Permission
        private set
    lateinit var configState: PluginState
        internal set

    var pluginLocked: Boolean
        get() = pluginLockedInternal.get()
        set(v) = pluginLockedInternal.set(v)

    companion object { lateinit var plugin: TicketManagerPlugin }
    init { plugin = this }


    override fun onEnable() {
        pluginLocked = true

        // Creates Plugin State from config file
        configState = PluginState()

        // Find Vault plugin
        server.servicesManager.getRegistration(Permission::class.java)?.provider
            ?.let { perms = it }
            ?: consoleLog(Level.SEVERE, configState.enabledLocales.consoleLocale.warningsVaultNotFound)
                .also { this.pluginLoader.disablePlugin(this) }

        // Register Event
        server.pluginManager.registerEvents(PlayerJoin(), this)

        // Launch Metrics
        metrics = Metrics(this, metricsKey)
        metrics.addCustomChart(
            Metrics.SingleLineChart("tickets_made")
            { ticketCountMetrics.getAndSet(0) }
        )


        Bukkit.getScheduler().runTaskTimerAsynchronously(mainPlugin, Runnable {
            if (anyLocksPresent()) return@Runnable

            // Mass Unread Notify
            try {
                if (pluginState.allowUnreadTicketUpdates) {
                    configState.database.getTicketIDsWithUpdates()
                        .groupBy({ it.first }, { it.second })
                        .asSequence()
                        .mapNotNull { Bukkit.getPlayer(it.key)?.run { Pair(this, it.value) } }
                        .filter { it.first.has("ticketmanager.notify.unreadUpdates.scheduled") }
                        .forEach {
                            val template = if (it.second.size > 1) getLocale(it.first).notifyUnreadUpdateMulti
                            else getLocale(it.first).notifyUnreadUpdateSingle
                            val tickets = it.second.joinToString(", ")

                            template.replace("%num%", tickets)
                                .sendColouredMessageTo(it.first)
                        }
                }

                // Open and Assigned Notify
                val tickets = pluginState.database.getOpen()
                    .map { it.assignedTo }

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

                        getLocale(p).notifyOpenAssigned
                            .replace("%open%", open)
                            .replace("%assigned%", assigned)
                            .sendColouredMessageTo(p)
                    }

                configState.cooldowns.filterMap()

            } catch (e: Exception) {
                e.printStackTrace()
                postModifiedStacktrace(e)
            }
        }, 0, 12000)
    }

    override fun onDisable() {
        configState.database.closeDatabase()
    }
}