package com.github.hoshikurama.ticketmanager.fabric.server.impls

import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.commonse.PlayerJoinExtensionHolder
import com.github.hoshikurama.ticketmanager.commonse.PreCommandExtensionHolder
import com.github.hoshikurama.ticketmanager.commonse.TMPlugin
import com.github.hoshikurama.ticketmanager.commonse.commands.CommandTasks
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.NotificationSharingChannel
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.PBEVersionChannel
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.ProxyJoinChannel
import com.github.hoshikurama.ticketmanager.fabric.server.FabricServerMod
import com.github.hoshikurama.ticketmanager.fabric.server.PlayerNameUUIDStorage
import com.github.hoshikurama.ticketmanager.fabric.server.commands.Brigadier
import com.github.hoshikurama.ticketmanager.fabric.server.hooks.registerJoinEventListener
import com.github.hoshikurama.tmcoroutine.ChanneledCounter
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.minecraft.server.MinecraftServer
import java.nio.file.Path

class TMPluginImpl(
    private val fabricServerMod: FabricServerMod,
    playerCache: PlayerNameUUIDStorage,
    private val adventure: FabricServerAudiences,
    private val minecraftServer: MinecraftServer,
    private val cache: PlayerNameUUIDStorage,
    ticketCounter: ChanneledCounter,
    ticketManagerFolder: Path,
    ) : TMPlugin(
    ticketCounter = ticketCounter,
    tmDirectory = ticketManagerFolder,
    pbeVersionChannel = object : PBEVersionChannel() {
        override fun relayToProxy(inputArray: ByteArray) {
            throw Exception("Unsupported operation attempted! TicketManager Fabric does not currently support proxies")
        }
    },
    proxyJoinChannel = object : ProxyJoinChannel() {
        override fun relayToProxy(inputArray: ByteArray) {
            throw Exception("Unsupported operation attempted! TicketManager Fabric does not currently support proxies")
        }
    },
    notificationSharingChannel = object : NotificationSharingChannel() {
        override fun relayToProxy(inputArray: ByteArray) {
            throw Exception("Unsupported operation attempted! TicketManager Fabric does not currently support proxies")
        }
    },
    platformFunctionBuilder = { permissions, config ->
        PlatformFunctionsImpl(adventure, permissions, playerCache, minecraftServer, config)
    },
) {
    override fun unregisterCommands(trueShutdown: Boolean) {}

    override fun registerCommands(
        permission: Permission,
        config: Config,
        locale: Locale,
        database: AsyncDatabase,
        platformFunctions: PlatformFunctions,
        preCommand: PreCommandExtensionHolder,
        commandTasks: CommandTasks
    ) {
        Brigadier(config, locale, database, permission, commandTasks, platformFunctions, preCommand, adventure, cache)
            .generateCommands(minecraftServer.commandManager.dispatcher)
        minecraftServer.playerManager?.playerList?.forEach(minecraftServer.commandManager::sendCommandTree)
    }

    override fun unregisterProxyChannels(trueShutdown: Boolean) {}

    override fun registerProxyChannels(
        proxyJoinChannel: ProxyJoinChannel,
        pbeVersionChannel: PBEVersionChannel,
        notificationSharingChannel: NotificationSharingChannel
    ) {}

    override fun unregisterPlayerJoinEvent(trueShutdown: Boolean) {}

    override fun registerPlayerJoinEvent(
        config: Config,
        locale: Locale,
        permission: Permission,
        database: AsyncDatabase,
        platformFunctions: PlatformFunctions,
        extensions: PlayerJoinExtensionHolder
    ) {
        registerJoinEventListener(config, locale, permission, database, platformFunctions, extensions)
    }
}
