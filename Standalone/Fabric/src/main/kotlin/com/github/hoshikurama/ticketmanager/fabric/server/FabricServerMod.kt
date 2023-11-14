package com.github.hoshikurama.ticketmanager.fabric.server

import com.github.hoshikurama.ticketmanager.fabric.server.impls.TMPluginImpl
import com.github.hoshikurama.tmcoroutine.ChanneledCounter
import kotlinx.coroutines.runBlocking
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.minecraft.server.MinecraftServer
import java.nio.file.Path
import kotlin.io.path.absolute

class FabricServerMod : DedicatedServerModInitializer, ServerLifecycleEvents.ServerStarting, ServerLifecycleEvents.ServerStopping {
    private var audience: FabricServerAudiences? = null
    private lateinit var nameUUIDStorage: PlayerNameUUIDStorage
    private lateinit var ticketManagerFolder: Path
    private lateinit var tmPlugin: TMPluginImpl

    override fun onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTING.register(this)
        ServerLifecycleEvents.SERVER_STOPPING.register(this)

        ticketManagerFolder = FabricLoader.getInstance().configDir
            .absolute()
            .parent
            .parent // This fixes a weird issue where it's /./
            .resolve("config")
            .resolve("TicketManager")

        nameUUIDStorage = PlayerNameUUIDStorage(ticketManagerFolder.resolve("playerUUIDStorage.json"))

        //https://docs.advntr.dev/platform/fabric.html
        /*
        PROBLEM: Fabric does not support load ordering.

        Possible Solution:
        TicketManager SE: onInitialize(), allow hook points
        Extensions: In the onInitialize(), register (depend mod on ticketmanager)
        TicketManager SE: Perform logic in Server_Starting
         */
    }

    override fun onServerStarting(server: MinecraftServer) {
        audience = FabricServerAudiences.of(server)

        runBlocking {
            tmPlugin = TMPluginImpl(
                fabricServerMod = this@FabricServerMod,
                playerCache = nameUUIDStorage,
                adventure = audience!!,
                minecraftServer = server,
                ticketCounter = ChanneledCounter(0UL),
                cache = nameUUIDStorage,
                ticketManagerFolder = ticketManagerFolder,
            )
            tmPlugin.enableTicketManager()
        }
    }

    /*
    private val tmPlugin = TMPluginImpl(
        paperPlugin = this,
        ticketCounter = ticketCounter,
        proxyJoinChannel = ProxyJoinChannelImpl(this),
        pbeVersionChannel = PBEVersionChannelImpl(this),
        notificationSharingChannel = NotificationSharingChannelImpl(this),
    )
     */

    override fun onServerStopping(server: MinecraftServer) {
        runBlocking {
            tmPlugin.disableTicketManager(true)
            nameUUIDStorage.writeToFile()
        }
    }
}