package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.common.metricsKey
import com.github.shynixn.mccoroutine.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.asyncDispatcher
import com.github.shynixn.mccoroutine.launchAsync
import com.github.shynixn.mccoroutine.minecraftDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit

class PaperPlugin : SuspendingJavaPlugin() {
    private lateinit var ticketManagerPlugin: PaperTicketManagerPlugin
    internal lateinit var perms: Permission
    private lateinit var metrics: Metrics

    override fun onEnable() {
        // Find Vault plugin
        server.servicesManager.getRegistration(Permission::class.java)?.provider
            ?.let { perms = it }
            ?: this.pluginLoader.disablePlugin(this)

        // Creates Platform object
        ticketManagerPlugin = PaperTicketManagerPlugin(
            this,
            minecraftDispatcher as CoroutineDispatcher,
            asyncDispatcher as CoroutineDispatcher,
        )
        ticketManagerPlugin.commandPipeline = PaperCommandPipeline(ticketManagerPlugin, perms)

        // Launch Metrics
        metrics = Metrics(this@PaperPlugin, metricsKey)
        metrics.addCustomChart(
            Metrics.SingleLineChart("tickets_made") {
                runBlocking {
                    val ticketCount = ticketManagerPlugin.ticketCountMetrics.get()
                    ticketManagerPlugin.ticketCountMetrics.set(0)
                    ticketCount
                }
            }
        )
        metrics.addCustomChart(
            Metrics.SimplePie("database_type") {
                ticketManagerPlugin.configState.database.type.name
            }
        )

        // Enable normal plugin startup
        launchAsync { ticketManagerPlugin.enable() }

        // Schedules async repeating tasks
        Bukkit.getScheduler().runTaskTimerAsynchronously(ticketManagerPlugin.mainPlugin, Runnable {
            ticketManagerPlugin.asyncScope.launch { ticketManagerPlugin.performPeriodicTasks() }
        }, 100, 12000)
    }

    override suspend fun onDisableAsync() {
        ticketManagerPlugin.disableAsync()
    }
}