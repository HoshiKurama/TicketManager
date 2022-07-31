package com.github.hoshikurama.ticketmanager.waterfall

import com.github.hoshikurama.ticketmanager.common.ProxyUpdate
import com.github.hoshikurama.ticketmanager.common.UpdateChecker
import com.github.hoshikurama.ticketmanager.common.bridgePluginVersion
import com.github.hoshikurama.ticketmanager.common.waterfallBridgeKey
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
import java.util.concurrent.TimeUnit

class WaterfallBridgeAdapter<T>(private val plugin: T) : ProxyBridge()
    where T: Plugin, T: Listener
{
    @Volatile
    private var task: ScheduledTask? = null
    private lateinit var metrics: Metrics

    private val dataDirectory: Path
        get() = plugin.dataFolder.toPath()
    override fun registerChannels() {
        plugin.proxy.registerChannel("ticketmanager:inform_proxy")
        plugin.proxy.registerChannel("ticketmanager:relayed_message")
        plugin.proxy.registerChannel("ticketmanager:server_to_proxy_tp")
        plugin.proxy.registerChannel("ticketmanager:proxy_to_server_tp")
        plugin.proxy.registerChannel("ticketmanager:s2p_proxy_update")
        plugin.proxy.registerChannel("ticketmanager:p2s_proxy_update")
    }

    override fun unregisterChannels() {
        plugin.proxy.unregisterChannel("ticketmanager:inform_proxy")
        plugin.proxy.unregisterChannel("ticketmanager:relayed_message")
        plugin.proxy.unregisterChannel("ticketmanager:server_to_proxy_tp")
        plugin.proxy.unregisterChannel("ticketmanager:proxy_to_server_tp")
        plugin.proxy.unregisterChannel("ticketmanager:s2p_proxy_update")
        plugin.proxy.unregisterChannel("ticketmanager:p2s_proxy_update")
    }

    override fun doSpecialtyBeforeStart() {
        plugin.proxy.pluginManager.registerListener(plugin, plugin)

        // Initialize Metrics
        metrics = Metrics(plugin, waterfallBridgeKey)
    }

    override fun loadExternalConfig(): List<String> = dataDirectory.resolve("config.yml").run(Files::readAllLines)

    override fun configExists(): Boolean = dataDirectory.resolve("config.yml").toFile().exists()

    override fun writeConfig(list: List<String>) {
        val writer = dataDirectory.resolve("config.yml").toFile().bufferedWriter()

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
            plugin.logger.info("A new bridge update is available!\n Latest: $it\n Current: $bridgePluginVersion")
        }
        updateChecker.set(update)
    }
}

@Suppress("Unused")
class WaterfallBridge : Plugin(), Listener {
    private val adapter = WaterfallBridgeAdapter(this)

    override fun onEnable() {
        adapter.onInitialization()
        logger.info("Bridge initialization complete!")
    }

    override fun onDisable() {
        adapter.onShutdown()
    }

    @EventHandler
    fun onMessage(event: PluginMessageEvent) {
        when (event.tag) {

            "ticketmanager:inform_proxy" ->
                proxy.serversCopy
                    .map { it.value }
                    .filter { it.players.isNotEmpty() }
                    .forEach { it.sendData("ticketmanager:relayed_message", event.data) }

            "ticketmanager:server_to_proxy_tp" -> {
                val input =  ByteStreams.newDataInput(event.data)

                val serverName = input.readUTF()
                val uuid = UUID.fromString(input.readUTF())
                val targetServer = proxy.serversCopy[serverName]

                if (targetServer != null) {
                    targetServer.sendData("ticketmanager:proxy_to_server_tp", event.data)    // Sends data to server
                    proxy.getPlayer(uuid).connect(targetServer)                                     // Teleport player
                } // Note: Order is correct; data contains player info to match with
            }

            "ticketmanager:s2p_proxy_update" -> {
                val serverName = ProxyUpdate.decodeProxyMsg(event.data)
                val targetServer = proxy.serversCopy[serverName]
                val latestVer = adapter.updateChecker.get().latestVersionIfNotLatest

                if (targetServer != null && latestVer != null) {
                    val msg = ProxyUpdate.encodeServerMsg(bridgePluginVersion, latestVer)
                    targetServer.sendData("ticketmanager:p2s_proxy_update", msg)
                }
            }
        }
    }
}