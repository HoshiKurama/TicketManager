package com.github.hoshikurama.ticketmanager.paper.impls

import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.commonse.PlayerJoinExtensionHolder
import com.github.hoshikurama.ticketmanager.commonse.PreCommandExtensionHolder
import com.github.hoshikurama.ticketmanager.commonse.TMPlugin
import com.github.hoshikurama.ticketmanager.commonse.commands.CommandTasks
import com.github.hoshikurama.ticketmanager.paper.PaperPlugin
import com.github.hoshikurama.ticketmanager.paper.commands.CommandReferences
import com.github.hoshikurama.ticketmanager.paper.commands.PaperCommandRunner
import com.github.hoshikurama.ticketmanager.paper.hooks.JoinEventListener
import com.github.hoshikurama.tmcoroutine.ChanneledCounter
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.Bukkit
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import java.util.function.Consumer
import kotlin.io.path.absolute

class TMPluginImpl(
    private val paperPlugin: PaperPlugin,
    ticketCounter: ChanneledCounter,
) : TMPlugin(
    tmDirectory = paperPlugin.dataFolder.toPath().absolute(),
    ticketCounter = ticketCounter,
    platformFunctionBuilder = { permissions, config ->
        PlatformFunctionsImpl(paperPlugin, permissions, config)
    }
) {

    init {
        activeInstance = this
    }

    override fun unregisterCommands(trueShutdown: Boolean) {
        // Paper doesn't need this anymore
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
        // Update references
        CommandReferences.config = config
        CommandReferences.locale = locale
        CommandReferences.database = database
        CommandReferences.permissions = permission
        CommandReferences.commandTasks = commandTasks
        CommandReferences.platform = platformFunctions
        CommandReferences.preCommandExtensionHolder = preCommand

        val scheduleSync = { block: () -> Unit -> paperPlugin.runTask(block)}

        // See comment on CommandReferences. TLDR Registration can only happen once, sync, and during Lifecycle expectations
        if (CommandReferences.shouldRegisterCommands) {
            paperPlugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) {
                val command = PaperCommandRunner(scheduleSync).generateCommands()
                it.registrar().register(command)
            }
        }

        CommandReferences.shouldRegisterCommands = false
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

internal inline fun Plugin.runTask(crossinline f: () -> Unit) =
    Bukkit.getScheduler().runTask(this, Consumer { f() })