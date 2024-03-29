package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.common.bukkitMetricsKey
import com.github.hoshikurama.ticketmanager.paper.impls.TMPluginImpl
import com.github.hoshikurama.ticketmanager.paper.impls.proxy.NotificationSharingChannelImpl
import com.github.hoshikurama.ticketmanager.paper.impls.proxy.PBEVersionChannelImpl
import com.github.hoshikurama.ticketmanager.paper.impls.proxy.ProxyJoinChannelImpl
import com.github.hoshikurama.tmcoroutine.ChanneledCounter
import com.github.hoshikurama.tmcoroutine.TMCoroutine
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
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
        proxyJoinChannel = ProxyJoinChannelImpl(this),
        pbeVersionChannel = PBEVersionChannelImpl(this),
        notificationSharingChannel = NotificationSharingChannelImpl(this),
    )
    private lateinit var metrics: Metrics

    override fun onLoad() {
        CommandAPI.onLoad(CommandAPIBukkitConfig(this))
    }

    override fun onEnable() {
        CommandAPI.onEnable()

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

        TMCoroutine.Global.launch {
            tmPlugin.enableTicketManager()
        }
    }

    override fun onDisable() {
        runBlocking {
            tmPlugin.disableTicketManager(true)
        }
    }
}