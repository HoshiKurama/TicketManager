package com.github.hoshikurama.ticketmanager.paper.impls

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
import com.github.hoshikurama.ticketmanager.paper.CommandAPIRunner
import com.github.hoshikurama.ticketmanager.paper.PaperPlugin
import com.github.hoshikurama.ticketmanager.paper.hooks.JoinEventListener
import com.github.hoshikurama.ticketmanager.paper.hooks.Proxy
import com.github.hoshikurama.tmcoroutine.ChanneledCounter
import dev.jorel.commandapi.CommandAPI
import org.bukkit.Bukkit
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import java.util.function.Consumer

class TMPluginImpl(
    private val paperPlugin: PaperPlugin,
    pbeVersionChannel: PBEVersionChannel,
    proxyJoinChannel: ProxyJoinChannel,
    ticketCounter: ChanneledCounter,
    notificationSharingChannel: NotificationSharingChannel,
) : TMPlugin(
    tmDirectory = paperPlugin.dataFolder.toPath(),
    pbeVersionChannel = pbeVersionChannel,
    proxyJoinChannel = proxyJoinChannel,
    ticketCounter = ticketCounter,
    notificationSharingChannel = notificationSharingChannel,
    platformFunctionBuilder = { permissions, config ->
        PlatformFunctionsImpl(paperPlugin, permissions, config)
    },
) {

    init {
        activeInstance = this
    }

    override fun unregisterCommands(trueShutdown: Boolean) {
        val unregister = { CommandAPI.unregister(baseTicketCommand, true) }

        if (trueShutdown) unregister()
        else paperPlugin.runTask(unregister)
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
        paperPlugin.runTask {
            CommandAPIRunner(
                config = config,
                locale = locale,
                database = database,
                commandTasks = commandTasks,
                permissions = permission,
                platform = platformFunctions,
                preCommandExtensionHolder = preCommand
            ).generateCommands()
        }
    }

    override fun registerProxyChannels(
        proxyJoinChannel: ProxyJoinChannel,
        pbeVersionChannel: PBEVersionChannel,
        notificationSharingChannel: NotificationSharingChannel
    ) {
        paperPlugin.runTask {
            val proxy = Proxy(notificationSharingChannel, pbeVersionChannel, proxyJoinChannel)

            paperPlugin.server.messenger.run {
                registerOutgoingPluginChannel(paperPlugin, Server2Proxy.NotificationSharing.waterfallString())
                registerIncomingPluginChannel(paperPlugin, Proxy2Server.NotificationSharing.waterfallString(), proxy)
                registerOutgoingPluginChannel(paperPlugin, Server2Proxy.Teleport.waterfallString())
                registerIncomingPluginChannel(paperPlugin, Proxy2Server.Teleport.waterfallString(), proxy)
                registerOutgoingPluginChannel(paperPlugin, Server2Proxy.ProxyVersionRequest.waterfallString())
                registerIncomingPluginChannel(paperPlugin, Proxy2Server.ProxyVersionRequest.waterfallString(), proxy)
            }
        }
    }

    override fun unregisterProxyChannels(trueShutdown: Boolean) {
        val unregister = {
            paperPlugin.server.messenger.unregisterIncomingPluginChannel(paperPlugin)
            paperPlugin.server.messenger.unregisterOutgoingPluginChannel(paperPlugin)
        }

        if (trueShutdown) unregister()
        else paperPlugin.runTask(unregister)
    }

    override fun registerPlayerJoinEvent(
        config: Config,
        locale: Locale,
        permission: Permission,
        database: AsyncDatabase,
        platformFunctions: PlatformFunctions,
        extensions: PlayerJoinExtensionHolder
    ) {
        val joinEventHook = JoinEventListener(config, locale, permission, database, platformFunctions, extensions)

        paperPlugin.runTask {
            paperPlugin.server.pluginManager.registerEvents(joinEventHook, paperPlugin)
        }
    }

    override fun unregisterPlayerJoinEvent(trueShutdown: Boolean) {
        val unregister = { PlayerJoinEvent.getHandlerList().unregister(paperPlugin) }

        if (trueShutdown) unregister()
        else paperPlugin.runTask(unregister)
    }
}

private inline fun Plugin.runTask(crossinline f: () -> Unit) =
    Bukkit.getScheduler().runTask(this, Consumer { f() })