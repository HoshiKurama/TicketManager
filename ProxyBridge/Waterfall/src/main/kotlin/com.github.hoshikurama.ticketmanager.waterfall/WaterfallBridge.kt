package com.github.hoshikurama.ticketmanager.waterfall

import com.github.hoshikurama.ticketmanager.common.*
import com.github.hoshikurama.ticketmanager.common.discord.notifications.DiscordNotification
import com.github.hoshikurama.ticketmanager.commonpde.ProxyBridge
import com.google.common.io.ByteStreams
import net.md_5.bungee.api.event.PluginMessageEvent
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.api.scheduler.ScheduledTask
import net.md_5.bungee.event.EventHandler
import org.bstats.bungeecord.Metrics
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit



class WaterfallBridgeAdapter<T>(private val plugin: T) : ProxyBridge()
    where T: Plugin, T: Listener
{
    @Volatile
    private var task: ScheduledTask? = null
    private lateinit var metrics: Metrics

    override val dataDirectory: Path
        get() = plugin.dataFolder.toPath()

    override fun registerChannels() {
        listOf(
            Proxy2Server.Teleport,
            Server2Proxy.Teleport,
            Proxy2Server.ProxyVersionRequest,
            Server2Proxy.ProxyVersionRequest,
            Proxy2Server.NotificationSharing,
            Server2Proxy.NotificationSharing,
            Server2Proxy.DiscordMessage,
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
            Server2Proxy.DiscordMessage,
        )
            .map(ProxyChannel::waterfallString)
            .forEach(plugin.proxy::unregisterChannel)
    }

    override fun doSpecialtyBeforeStart() {
        plugin.proxy.pluginManager.registerListener(plugin, plugin)

        // Initialize Metrics
        metrics = Metrics(plugin, waterfallBridgeKey)
    }

    override fun loadExternalConfig(): List<String> = this.dataDirectory.resolve("config.yml").run(Files::readAllLines)

    override fun configExists(): Boolean = this.dataDirectory.resolve("config.yml").toFile().exists()

    override fun writeConfig(list: List<String>) {
        val writer = this.dataDirectory.resolve("config.yml").toFile().bufferedWriter()

        list.forEachIndexed { index, str ->
            writer.write(str)

            if (index != list.lastIndex)
                writer.newLine()
        }
        writer.close()
    }

    override fun dataDirectoryExists(): Boolean = plugin.dataFolder.exists()

    override fun writeDataDirectory() {
        plugin.dataFolder.mkdir()
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
            plugin.logger.info(locale.notifyPluginUpdate
                .replace("<current>", bridgePluginVersion)
                .replace("<latest>", it)
            )
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
                proxy.serversCopy
                    .map { it.value }
                    .filter { it.players.isNotEmpty() }
                    .forEach { it.sendData(Proxy2Server.NotificationSharing.waterfallString() , event.data) }

            Server2Proxy.Teleport.waterfallString() -> {
                @Suppress("UnstableApiUsage")
                val input =  ByteStreams.newDataInput(event.data)

                val serverName = input.readUTF()
                val uuid = UUID.fromString(input.readUTF())
                val targetServer = proxy.serversCopy[serverName]

                if (targetServer != null) {
                    targetServer.sendData(Proxy2Server.Teleport.waterfallString(), event.data)      // Sends data to server
                    proxy.getPlayer(uuid).connect(targetServer)                                     // Teleport player
                } // Note: Order is correct; data contains player info to match with
            }

            Server2Proxy.ProxyVersionRequest.waterfallString() -> {
                val serverName = ProxyUpdate.decodeProxyMsg(event.data)
                val targetServer = proxy.serversCopy[serverName]
                val latestVer = adapter.updateChecker.get().latestVersionIfNotLatest

                if (targetServer != null && latestVer != null) {
                    val msg = ProxyUpdate.encodeServerMsg(bridgePluginVersion, latestVer)
                    targetServer.sendData(Proxy2Server.ProxyVersionRequest.waterfallString(), msg)
                }
            }

            Server2Proxy.DiscordMessage.waterfallString() -> {
                if (adapter.discord == null) return

                val notification = DiscordNotification.decode(event.data, adapter.locale)
                CompletableFuture.runAsync { adapter.discord?.sendMessage(notification, adapter.locale) }
            }
        }
    }
}