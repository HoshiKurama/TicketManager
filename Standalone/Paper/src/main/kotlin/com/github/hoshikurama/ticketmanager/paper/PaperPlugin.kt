package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.api.impl.TicketManager
import com.github.hoshikurama.ticketmanager.common.bukkitMetricsKey
import com.github.hoshikurama.ticketmanager.paper.impls.ProxyMessageSharingExtension
import com.github.hoshikurama.ticketmanager.paper.impls.TMPluginImpl
import com.github.hoshikurama.tmcoroutine.ChanneledCounter
import kotlinx.coroutines.runBlocking
import org.bstats.bukkit.Metrics
import org.bstats.charts.SimplePie
import org.bstats.charts.SingleLineChart
import org.bukkit.plugin.java.JavaPlugin

class PaperPlugin : JavaPlugin() {
    private val ticketCounter = ChanneledCounter(0UL)
    private val tmPlugin = TMPluginImpl(
        paperPlugin = this,
        ticketCounter = ticketCounter,
    )
    private lateinit var metrics: Metrics

    override fun onLoad() {
        //CommandAPI.onLoad(CommandAPIBukkitConfig(this))
    }

    override fun onEnable() {
        //CommandAPI.onEnable()

        // Launch Metrics
        metrics = Metrics(this, bukkitMetricsKey)
        metrics.addCustomChart(SimplePie("plugin_platform") { "Paper" })
        metrics.addCustomChart(
            SingleLineChart("tickets_made") {
                runBlocking {
                    ticketCounter.get()
                        .apply { ticketCounter.set(0UL) }
                        .toInt()
                }
            }
        )

        // Internally TM:SE will handle using this or the Dummy object based on config
        TicketManager.MessageSharingRegistry.register(ProxyMessageSharingExtension(this))

        runBlocking {
            tmPlugin.enableTicketManager()
        }
    }

    override fun onDisable() {
        runBlocking {
            tmPlugin.disableTicketManager(true)
        }
    }
}