package com.github.hoshikurama.ticketmanager.velocity


import com.github.hoshikurama.ticketmanager.common.*
import com.github.hoshikurama.ticketmanager.commonpde.ProxyBridge
import com.google.common.io.ByteStreams
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import com.velocitypowered.api.scheduler.ScheduledTask
import org.bstats.velocity.Metrics
import org.slf4j.Logger
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit

@Plugin(
    id = "ticketmanager",
    name = "TicketManager_Bridge",
    version = bridgePluginVersion,
    description = "Bridge for Advanced Ticket Management Solution",
    authors = ["HoshiKurama"],
)
class TMPluginImpl @Inject constructor(
    private val server: ProxyServer,
    private val metricsFactory: Metrics.Factory,
    private val logger: Logger,
    @DataDirectory val dataDirectory: Path,
): ProxyBridge(dataDirectory) {
    private fun ProxyChannel.applySettings() = MinecraftChannelIdentifier.create(namespace, name)!!

    private val incomingMessage = Server2Proxy.NotificationSharing.applySettings()
    private val outgoingMessage = Proxy2Server.NotificationSharing.applySettings()

    private val serverToProxyTeleport = Server2Proxy.Teleport.applySettings()
    private val proxyToServerTeleport = Proxy2Server.Teleport.applySettings()

    private val serverToProxyUpdate = Server2Proxy.ProxyVersionRequest.applySettings()
    private val proxyToServerUpdate = Proxy2Server.ProxyVersionRequest.applySettings()

    @Volatile
    private var task: ScheduledTask? = null
    private lateinit var metrics: Metrics

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        super.onInitialization()
        logger.info("Bridge initialization complete!")
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        super.onShutdown()
    }

    @Subscribe
    fun onMessage(event: PluginMessageEvent) {
        when (event.identifier) {

            incomingMessage ->
                server.allServers
                    .filter { it.playersConnected.isNotEmpty() }
                    .forEach { it.sendPluginMessage(outgoingMessage, event.data) }

            serverToProxyTeleport -> {
                @Suppress("UnstableApiUsage")
                val input =  ByteStreams.newDataInput(event.data)
                val serverName = input.readUTF()
                val uuid = UUID.fromString(input.readUTF())
                val targetServer = server.allServers.firstOrNull { it.serverInfo.name == serverName }

                if (targetServer != null) {
                    targetServer.sendPluginMessage(proxyToServerTeleport, event.data)                     // Sends data to server
                    server.getPlayer(uuid).get().createConnectionRequest(targetServer).fireAndForget()    // Teleport player
                }
            }

            serverToProxyUpdate -> {
                val serverName = ProxyUpdate.decodeProxyMsg(event.data)
                val targetServer = server.allServers.firstOrNull { it.serverInfo.name == serverName }
                val latestVer = updateChecker.get().latestVersionIfNotLatest

                if (targetServer != null && latestVer != null) {
                    val msg = ProxyUpdate.encodeServerMsg(bridgePluginVersion, latestVer)
                    targetServer.sendPluginMessage(proxyToServerUpdate, msg)
                }
            }
        }
    }

    override fun registerChannels(): Unit = server.channelRegistrar.register(incomingMessage, outgoingMessage, serverToProxyTeleport, proxyToServerTeleport, proxyToServerUpdate, serverToProxyUpdate)
    override fun unregisterChannels(): Unit = server.channelRegistrar.unregister(incomingMessage, outgoingMessage, serverToProxyTeleport, proxyToServerTeleport, proxyToServerUpdate, serverToProxyUpdate)

    override fun doSpecialtyBeforeStart() {
        // Register bStats (unless disabled by player in bStats config)
        metrics = metricsFactory.make(this, velocityBridgeKey)
    }

    override fun scheduleRepeatCheck(frequencyHours: Long) {
        task = server.scheduler.buildTask(this, ::setNewUpdateCheck)
            .repeat(frequencyHours, TimeUnit.HOURS)
            .schedule()
    }

    override fun cancelNextCheck() {
        task?.cancel()
    }

    override fun setNewUpdateCheck() {
        val update = UpdateChecker(true, UpdateChecker.Location.BRIDGE)

        update.latestVersionIfNotLatest?.let {
            logger.info("[TicketManager Proxy] TicketManager has an update!\n  Current Version: $bridgePluginVersion\n  Latest Version: $it")
        }
        updateChecker.set(update)
    }
}