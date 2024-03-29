package com.github.hoshikurama.ticketmanager.commonse

import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.impl.registry.*
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.registry.playerjoin.PlayerJoinRegistry.RunType
import com.github.hoshikurama.ticketmanager.commonse.commands.CommandTasks
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.NotificationSharingChannel
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.PBEVersionChannel
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.ProxyJoinChannel
import com.github.hoshikurama.ticketmanager.commonse.registrydefaults.config.DefaultConfigExtension
import com.github.hoshikurama.ticketmanager.commonse.registrydefaults.database.DefaultDatabaseExtension
import com.github.hoshikurama.ticketmanager.commonse.registrydefaults.locale.DefaultLocaleExtension
import com.github.hoshikurama.ticketmanager.commonse.registrydefaults.permission.LuckPerms
import com.github.hoshikurama.ticketmanager.commonse.registrydefaults.playerjoin.*
import com.github.hoshikurama.ticketmanager.commonse.registrydefaults.precommand.Cooldown
import com.github.hoshikurama.ticketmanager.commonse.registrydefaults.repeatingtasks.RepeatingStaffCount
import com.github.hoshikurama.ticketmanager.commonse.registrydefaults.repeatingtasks.UnreadNotify
import com.github.hoshikurama.tmcoroutine.ChanneledCounter
import com.github.hoshikurama.tmcoroutine.TMCoroutine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.nio.file.Path
import java.util.UUID

import com.github.hoshikurama.ticketmanager.api.impl.TicketManager as TicketManagerInternal

abstract class TMPlugin(
    private val tmDirectory: Path,
    private val ticketCounter: ChanneledCounter,
    private val platformFunctionBuilder: PlatformFunctionBuilder,
    private val notificationSharingChannel: NotificationSharingChannel,
    private val pbeVersionChannel: PBEVersionChannel,
    private val proxyJoinChannel: ProxyJoinChannel,
) {
    companion object {
        @Volatile lateinit var activeInstance: TMPlugin
    }

    private val repeatingTasks = mutableListOf<Job>()
    @Volatile protected lateinit var baseTicketCommand: String
    @Volatile private lateinit var databaseClosing: AsyncDatabase

    protected abstract fun unregisterCommands(trueShutdown: Boolean)
    protected abstract fun registerCommands(
        permission: Permission,
        config: Config,
        locale: Locale,
        database: AsyncDatabase,
        platformFunctions: PlatformFunctions,
        preCommand: PreCommandExtensionHolder,
        commandTasks: CommandTasks,
    )
    protected abstract fun unregisterProxyChannels(trueShutdown: Boolean)
    protected abstract fun registerProxyChannels(
        proxyJoinChannel: ProxyJoinChannel,
        pbeVersionChannel: PBEVersionChannel,
        notificationSharingChannel: NotificationSharingChannel,
    )
    protected abstract fun unregisterPlayerJoinEvent(trueShutdown: Boolean)
    protected abstract fun registerPlayerJoinEvent(
        config: Config,
        locale: Locale,
        permission: Permission,
        database: AsyncDatabase,
        platformFunctions: PlatformFunctions,
        extensions: PlayerJoinExtensionHolder
    )

    suspend fun enableTicketManager() {
        // Register any missing core extensions
        TicketManager.PermissionRegistry.register(LuckPerms::class)
        TicketManager.ConfigRegistry.register(DefaultConfigExtension::class)
        TicketManager.LocaleRegistry.register(DefaultLocaleExtension::class)
        TicketManager.DatabaseRegistry.register(DefaultDatabaseExtension::class)

        // Load core extensions via dependency injection
        val permission = TicketManager.PermissionRegistry.load()
        val config = TicketManager.ConfigRegistry.load(tmDirectory)
        val locale = TicketManager.LocaleRegistry.load(tmDirectory, config)
        val database = TicketManager.DatabaseRegistry.loadAndInitialize(tmDirectory, config, locale)
        val platform = platformFunctionBuilder.build(permission, config)
        val commandTasks = CommandTasks(config, locale, database, platform, permission, ticketCounter, notificationSharingChannel, proxyJoinChannel)

        // Register auxiliary extensions
        if (config.checkForPluginUpdates) TicketManager.PlayerJoinRegistry.register(SEUpdateChecker::class, RunType.ASYNC)
        if (config.allowUnreadTicketUpdates) TicketManager.PlayerJoinRegistry.register(UnreadUpdates::class, RunType.ASYNC)
        if (config.proxyOptions != null) TicketManager.PlayerJoinRegistry.register(ProxyTeleport(proxyJoinChannel), RunType.ASYNC)
        if (config.proxyOptions != null && config.proxyOptions!!.pbeAllowUpdateCheck) TicketManager.PlayerJoinRegistry.register(PBEUpdateChecker(pbeVersionChannel), RunType.ASYNC)
        if (config.cooldownOptions != null) TicketManager.PreCommandRegistry.register(Cooldown(config.cooldownOptions!!.duration))
        TicketManager.PlayerJoinRegistry.register(StaffCount::class, RunType.ASYNC)

        if (config.allowUnreadTicketUpdates) TicketManager.RepeatingTaskRegistry.register(UnreadNotify::class)
        TicketManager.RepeatingTaskRegistry.register(RepeatingStaffCount::class)


        // Load auxiliary extensions
        val playerJoinExtensions = PlayerJoinExtensionHolder(
            syncExtensions = TicketManager.PlayerJoinRegistry.getSyncExtensions(),
            asyncExtensions = TicketManager.PlayerJoinRegistry.getAsyncExtensions()
        )
        val preCommandExtensions = PreCommandExtensionHolder(
            deciders = TicketManager.PreCommandRegistry.getDeciders(),
            syncAfters = TicketManager.PreCommandRegistry.getSyncAfters(),
            asyncAfters = TicketManager.PreCommandRegistry.getAsyncAfters()
        )

        // Finish external plugin registration
        if (config.proxyOptions != null) registerProxyChannels(
            notificationSharingChannel = notificationSharingChannel,
            pbeVersionChannel = pbeVersionChannel,
            proxyJoinChannel = proxyJoinChannel,
        )
        registerPlayerJoinEvent(
            config = config,
            locale = locale,
            permission = permission,
            database = database,
            platformFunctions = platform,
            extensions = playerJoinExtensions
        )
        registerCommands(
            config = config,
            locale = locale,
            database = database,
            permission = permission,
            commandTasks = commandTasks,
            platformFunctions = platform,
            preCommand = preCommandExtensions,
        )

        // Launch repeating tasks
        TicketManager.RepeatingTaskRegistry.getExtensions().map {
            TMCoroutine.Supervised.launch {
                while (true) {
                    delay(it.frequency)
                    it.onRepeat(config, locale, database, permission, platform)
                }
            }
        }

        // Launch Notification Sharing
        TMCoroutine.Supervised.launch {
            val encounteredUUIDs = hashSetOf<UUID>()
            // Note: Each UUID takes 16 bytes. 500k ~10MB
            // Prevents issue where some users see spam in proxy mode

            for (message in notificationSharingChannel.channelListener) {
                if (message.messageUUID in encounteredUUIDs) continue
                platform.massNotify(message.massNotifyPerm, message.generateMassNotify(locale))
                encounteredUUIDs.add(message.messageUUID)
            }
        }

        baseTicketCommand = locale.commandBase
        databaseClosing = database
    }

    suspend fun disableTicketManager(trueShutdown: Boolean) {
        unregisterCommands(trueShutdown)
        unregisterPlayerJoinEvent(trueShutdown)
        TicketManager.EventBus.internal.run {
            listOf(ticketCreate, ticketAssign, ticketReopen, ticketComment, ticketMassClose,
                ticketSetPriority, ticketCloseWithComment, ticketCloseWithoutComment
            ).forEach { it.clear() }
        }

        repeatingTasks.forEach(Job::cancel)
        repeatingTasks.clear()

        TMCoroutine.Supervised.cancelTasks("Plugin is reloading or shutting down!")

        databaseClosing.closeDatabase()
        unregisterProxyChannels(trueShutdown)
    }
}

// Note: This lets me use "TicketManager" the internal way
private object TicketManager {
    val ConfigRegistry = TicketManagerInternal.ConfigRegistry as TMConfigRegistry
    val DatabaseRegistry = TicketManagerInternal.DatabaseRegistry as TMDatabaseRegistry
    val LocaleRegistry = TicketManagerInternal.LocaleRegistry as TMLocaleRegistry
    val PermissionRegistry = TicketManagerInternal.PermissionRegistry as TMPermissionRegistry
    val PlayerJoinRegistry = TicketManagerInternal.PlayerJoinRegistry as TMPlayerJoinRegistry
    val PreCommandRegistry = TicketManagerInternal.PreCommandRegistry as TMPreCommandRegistry
    val RepeatingTaskRegistry = TicketManagerInternal.RepeatingTaskRegistry as TMRepeatingTaskRegistry
    val EventBus = TicketManagerInternal.EventBus
}