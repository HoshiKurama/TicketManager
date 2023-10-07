package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.ticketmanager.common.bukkitMetricsKey
import com.github.hoshikurama.ticketmanager.spigot.impls.TMPluginImpl
import com.github.hoshikurama.ticketmanager.spigot.impls.proxy.NotificationSharingChannelImpl
import com.github.hoshikurama.ticketmanager.spigot.impls.proxy.PBEVersionChannelImpl
import com.github.hoshikurama.ticketmanager.spigot.impls.proxy.ProxyJoinChannelImpl
import com.github.hoshikurama.tmcore.ChanneledCounter
import com.github.hoshikurama.tmcore.TMCoroutine
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bstats.bukkit.Metrics
import org.bstats.charts.SimplePie
import org.bstats.charts.SingleLineChart
import org.bukkit.plugin.java.JavaPlugin

class SpigotPlugin : JavaPlugin() {
    private lateinit var metrics: Metrics
    private lateinit var tmPlugin: TMPluginImpl
    private lateinit var adventure: BukkitAudiences
    private val ticketCounter = ChanneledCounter(0UL)

    override fun onLoad() {
        CommandAPI.onLoad(CommandAPIBukkitConfig(this))
    }

    override fun onEnable() {
        CommandAPI.onEnable()

        adventure = BukkitAudiences.create(this)
        tmPlugin = TMPluginImpl(
            spigotPlugin = this,
            adventure = adventure,
            ticketCounter = ticketCounter,
            proxyJoinChannel = ProxyJoinChannelImpl(this),
            pbeVersionChannel = PBEVersionChannelImpl(this),
            notificationSharingChannel = NotificationSharingChannelImpl(this),
        )

        // Launch Metrics
        metrics = Metrics(this, bukkitMetricsKey)
        metrics.addCustomChart(SimplePie("plugin_platform") { "Spigot" })
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
        adventure.close()
    }
}