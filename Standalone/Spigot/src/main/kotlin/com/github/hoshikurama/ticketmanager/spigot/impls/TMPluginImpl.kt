package com.github.hoshikurama.ticketmanager.spigot.impls

import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.common.Proxy2Server
import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.commonse.PlayerJoinExtensionHolder
import com.github.hoshikurama.ticketmanager.commonse.PreCommandExtensionHolder
import com.github.hoshikurama.ticketmanager.commonse.TMPlugin
import com.github.hoshikurama.ticketmanager.commonse.commands.CommandTasks
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.NotificationSharingChannel
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.PBEVersionChannel
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.ProxyJoinChannel
import com.github.hoshikurama.ticketmanager.spigot.CommandAPIRunner
import com.github.hoshikurama.ticketmanager.spigot.SpigotPlugin
import com.github.hoshikurama.ticketmanager.spigot.hooks.JoinEventListener
import com.github.hoshikurama.ticketmanager.spigot.hooks.Proxy
import com.github.hoshikurama.tmcoroutine.ChanneledCounter
import dev.jorel.commandapi.CommandAPI
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.Bukkit
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import java.util.function.Consumer

class TMPluginImpl(
    private val spigotPlugin: SpigotPlugin,
    private val adventure: BukkitAudiences,
    pbeVersionChannel: PBEVersionChannel,
    proxyJoinChannel: ProxyJoinChannel,
    ticketCounter: ChanneledCounter,
    notificationSharingChannel: NotificationSharingChannel,
) : TMPlugin(
    tmDirectory = spigotPlugin.dataFolder.toPath(),
    pbeVersionChannel = pbeVersionChannel,
    proxyJoinChannel = proxyJoinChannel,
    ticketCounter = ticketCounter,
    notificationSharingChannel = notificationSharingChannel,
    platformFunctionBuilder = { permissions, config ->
        PlatformFunctionsImpl(adventure, spigotPlugin, permissions, config)
    },
) {

    override fun unregisterCommands(trueShutdown: Boolean) {
        val unregister = { CommandAPI.unregister(baseTicketCommand, true) }

        if (trueShutdown) unregister()
        else spigotPlugin.runTask(unregister)
    }

    override fun registerCommands(
        permission: Permission,
        config: Config,
        locale: Locale,
        database: AsyncDatabase,
        platformFunctions: PlatformFunctions,
        preCommand: PreCommandExtensionHolder,
        commandTasks: CommandTasks
    ) {
        spigotPlugin.runTask {
            CommandAPIRunner(
                config = config,
                locale = locale,
                database = database,
                commandTasks = commandTasks,
                permissions = permission,
                platform = platformFunctions,
                preCommandExtensionHolder = preCommand,
                adventure = adventure,
            ).generateCommands()
        }
    }

    override fun unregisterProxyChannels(trueShutdown: Boolean) {
        val unregister = {
            spigotPlugin.server.messenger.unregisterIncomingPluginChannel(spigotPlugin)
            spigotPlugin.server.messenger.unregisterOutgoingPluginChannel(spigotPlugin)
        }

        if (trueShutdown) unregister()
        else spigotPlugin.runTask(unregister)
    }

    override fun registerProxyChannels(
        proxyJoinChannel: ProxyJoinChannel,
        pbeVersionChannel: PBEVersionChannel,
        notificationSharingChannel: NotificationSharingChannel
    ) {
        spigotPlugin.runTask {
            val proxy = Proxy(notificationSharingChannel, pbeVersionChannel, proxyJoinChannel)

            spigotPlugin.server.messenger.run {
                registerOutgoingPluginChannel(spigotPlugin, Server2Proxy.NotificationSharing.waterfallString())
                registerIncomingPluginChannel(spigotPlugin, Proxy2Server.NotificationSharing.waterfallString(), proxy)
                registerOutgoingPluginChannel(spigotPlugin, Server2Proxy.Teleport.waterfallString())
                registerIncomingPluginChannel(spigotPlugin, Proxy2Server.Teleport.waterfallString(), proxy)
                registerOutgoingPluginChannel(spigotPlugin, Server2Proxy.ProxyVersionRequest.waterfallString())
                registerIncomingPluginChannel(spigotPlugin, Proxy2Server.ProxyVersionRequest.waterfallString(), proxy)
            }
        }
    }

    override fun unregisterPlayerJoinEvent(trueShutdown: Boolean) {
        val unregister = { PlayerJoinEvent.getHandlerList().unregister(spigotPlugin) }

        if (trueShutdown) unregister()
        else spigotPlugin.runTask(unregister)
    }

    override fun registerPlayerJoinEvent(
        config: Config,
        locale: Locale,
        permission: Permission,
        database: AsyncDatabase,
        platformFunctions: PlatformFunctions,
        extensions: PlayerJoinExtensionHolder
    ) {
        val joinEventHook = JoinEventListener(config, locale, permission, database, platformFunctions, extensions, adventure)

        spigotPlugin.runTask {
            spigotPlugin.server.pluginManager.registerEvents(joinEventHook, spigotPlugin)
        }
    }
}

private inline fun Plugin.runTask(crossinline f: () -> Unit) =
    Bukkit.getScheduler().runTask(this, Consumer { f() })