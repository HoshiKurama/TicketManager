package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.common.metricsKey
import com.github.shynixn.mccoroutine.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.asyncDispatcher
import com.github.shynixn.mccoroutine.launchAsync
import com.github.shynixn.mccoroutine.minecraftDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit

class SpigotPlugin : SuspendingJavaPlugin() {
    private lateinit var ticketManagerPlugin: SpigotTicketManagerPlugin
    internal lateinit var perms: Permission
    private lateinit var metrics: Metrics
    private lateinit var adventure: BukkitAudiences

    override fun onEnable() {
        // Find Vault plugin
        server.servicesManager.getRegistration(Permission::class.java)?.provider
            ?.let { perms = it }
            ?: this.pluginLoader.disablePlugin(this)

        // Gets Adventure API object
        this.adventure = BukkitAudiences.create(this)

        // Creates Platform object
        ticketManagerPlugin = SpigotTicketManagerPlugin(
            this,
            minecraftDispatcher as CoroutineDispatcher,
            asyncDispatcher as CoroutineDispatcher,
            adventure,
        )
        ticketManagerPlugin.commandPipeline = SpigotCommandPipeline(ticketManagerPlugin, perms, adventure)

        // Launch Metrics async once ticketManager object is booted
        launchAsync {
            while (!(::ticketManagerPlugin.isInitialized)) {
                delay(100)

                ticketManagerPlugin.mainScope.launch {
                    metrics = Metrics(this@SpigotPlugin, metricsKey)
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
                }
            }
        }

        launchAsync { ticketManagerPlugin.enable() }

        // Schedules async repeating tasks
        Bukkit.getScheduler().runTaskTimerAsynchronously(ticketManagerPlugin.mainPlugin, Runnable {
            ticketManagerPlugin.asyncScope.launch { ticketManagerPlugin.performPeriodicTasks() }
        }, 100, 12000)
    }

    override suspend fun onDisableAsync() {
        ticketManagerPlugin.disableAsync()
        this.adventure.close()
    }
}