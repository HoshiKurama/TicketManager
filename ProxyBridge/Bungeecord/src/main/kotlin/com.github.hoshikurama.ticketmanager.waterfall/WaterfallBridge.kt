package com.github.hoshikurama.ticketmanager.waterfall

import com.github.hoshikurama.ticketmanager.common.*
import com.github.hoshikurama.ticketmanager.commonpde.ProxyBridge
import com.google.common.io.ByteStreams
import net.md_5.bungee.api.event.PluginMessageEvent
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.api.scheduler.ScheduledTask
import net.md_5.bungee.event.EventHandler
import org.bstats.bungeecord.Metrics
import java.net.URI
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class WaterfallBridgeAdapter<T>(private val plugin: T) : ProxyBridge(plugin.dataFolder.toPath())
    where T: Plugin, T: Listener
{
    @Volatile
    private var task: ScheduledTask? = null
    private lateinit var metrics: Metrics

    override fun registerChannels() {
        listOf(
            Proxy2Server.Teleport,
            Server2Proxy.Teleport,
            Proxy2Server.ProxyVersionRequest,
            Server2Proxy.ProxyVersionRequest,
            Proxy2Server.NotificationSharing,
            Server2Proxy.NotificationSharing,
        )
            .map(ProxyChannel::waterfallString)
            .forEach(plugin.proxy::registerChannel)
    }

    override fun unregisterChannels() {
        listOf(
            Proxy2Server.Teleport,
            Server2Proxy.Teleport,
            Proxy2Server.ProxyVersionRequest,
            Server2Proxy.ProxyVersionRequest,
            Proxy2Server.NotificationSharing,
            Server2Proxy.NotificationSharing,
        )
            .map(ProxyChannel::waterfallString)
            .forEach(plugin.proxy::unregisterChannel)
    }

    override fun doSpecialtyBeforeStart() {
        plugin.proxy.pluginManager.registerListener(plugin, plugin)

        // Initialize Metrics
        metrics = Metrics(plugin, waterfallBridgeKey)
    }

    override fun scheduleRepeatCheck(frequencyHours: Long) {
        task = plugin.proxy.scheduler.schedule(plugin, ::setNewUpdateCheck, 0, frequencyHours, TimeUnit.HOURS)
    }

    override fun cancelNextCheck() {
        task?.cancel()
    }
    override fun setNewUpdateCheck() {
        val update = UpdateChecker(true, UpdateChecker.Location.BRIDGE)

        update.latestVersionIfNotLatest?.let {
            plugin.logger.info("[TicketManager Proxy] TicketManager has an update!\n  Current Version: $bridgePluginVersion\n  Latest Version: $it")
        }
        updateChecker.set(update)
    }
}

@Suppress("Unused")
class WaterfallBridge : Plugin(), Listener {
    private val adapter = WaterfallBridgeAdapter(this)
    private val onlyOnceMap = ConcurrentLinkedQueue<ByteArray>()

    override fun onEnable() {
        adapter.onInitialization()
        logger.info("Bridge initialization complete!")
    }

    override fun onDisable() {
        adapter.onShutdown()
    }

    @EventHandler
    fun onMessage(event: PluginMessageEvent) {
        if (onlyOnceMap.any { Arrays.equals(it, event.data) }) return

        onlyOnceMap.add(event.data)
        proxy.scheduler.schedule(this, { onlyOnceMap.remove(event.data) }, 1, TimeUnit.SECONDS)

        when (event.tag) {

            Server2Proxy.NotificationSharing.waterfallString() ->
                proxy.servers
                    .map { it.value }
                    .filter { it.players.isNotEmpty() }
                    .forEach { it.sendData(Proxy2Server.NotificationSharing.waterfallString() , event.data) }

            Server2Proxy.Teleport.waterfallString() -> {
                val input =  ByteStreams.newDataInput(event.data)

                val serverName = input.readUTF()
                val uuid = UUID.fromString(input.readUTF())
                val targetServer = proxy.servers[serverName]

                if (targetServer != null) {
                    targetServer.sendData(Proxy2Server.Teleport.waterfallString(), event.data)      // Sends data to server
                    proxy.getPlayer(uuid).connect(targetServer)                                     // Teleport player
                } // Note: Order is correct; data contains player info to match with
            }

            Server2Proxy.ProxyVersionRequest.waterfallString() -> {
                val serverName = ByteStreams.newDataInput(event.data).readUTF()
                val targetServer = proxy.servers[serverName]
                val latestVersion = getLatestVersionOrNullOnFail() ?: bridgePluginVersion

                if (targetServer != null) {
                    val msg = ByteStreams.newDataOutput()
                        .apply { writeUTF(bridgePluginVersion) }
                        .apply { writeUTF(latestVersion) }
                        .toByteArray()
                    targetServer.sendData(Proxy2Server.ProxyVersionRequest.waterfallString(), msg)
                }
            }
        }
    }

    private fun getLatestVersionOrNullOnFail(): String? {
        return try {
            val regex = "\"name\":\"[^,]*".toRegex()
            URI("https://api.github.com/repos/HoshiKurama/TicketManager-Bridge-Releases/tags")
                .toURL()
                .openStream()
                .bufferedReader()
                .readText()
                .let(regex::find)!!
                .value
                .substring(8) // "x.y.z...."
                .replace("\"","")
        } catch (ignored: Exception) { null }
    }
}