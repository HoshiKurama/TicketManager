package com.github.hoshikurama.ticketmanager.commonse

import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.impl.registry.*
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.messagesharing.MessageSharing
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.commonse.commands.CommandTasks
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.NotificationSharingMailbox
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.PBEVersionChannel
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.TeleportJoinMailbox
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
import kotlinx.coroutines.delay
import java.nio.file.Path
import java.util.UUID

import com.github.hoshikurama.ticketmanager.api.impl.TicketManager as TicketManagerInternal

abstract class TMPlugin(
    private val tmDirectory: Path,
    private val ticketCounter: ChanneledCounter,
    private val platformFunctionBuilder: PlatformFunctionBuilder,
) {
    companion object {
        @Volatile lateinit var activeInstance: TMPlugin
    }

    @Volatile protected lateinit var baseTicketCommand: String
    @Volatile private lateinit var databaseClosing: AsyncDatabase
    @Volatile private lateinit var messageSharing: MessageSharing

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

        // Load Message Sharing Data via dependency injection
        messageSharing = TicketManager.MessageSharingRegistry.loadAndInitialize(config.proxyOptions == null)
        val teleportJoinMailbox = TeleportJoinMailbox(messageSharing)
        val notificationSharingMailbox = NotificationSharingMailbox(messageSharing)
        val pbeVersionChannel = PBEVersionChannel(messageSharing)

        // Load remaining core extensions via dependency injection
        val commandTasks = CommandTasks(config, locale, database, platform, permission, ticketCounter, notificationSharingMailbox, teleportJoinMailbox)

        // Load auxiliary extensions and add internal behaviours (fixes extra registrations on reload for internal ones)
        val playerJoinExtensions: PlayerJoinExtensionHolder = run {
            val proxyTeleport = { ProxyTeleport(teleportJoinMailbox) }
            val pbeUpdateCheck = { PBEUpdateChecker(pbeVersionChannel) }

            val extraAsyncPlayerJoinExtensions = listOf(
                ::SEUpdateChecker.takeIf { config.checkForPluginUpdates },
                ::UnreadUpdates.takeIf { config.allowUnreadTicketUpdates },
                proxyTeleport.takeIf { config.proxyOptions != null },
                pbeUpdateCheck.takeIf { config.proxyOptions?.pbeAllowUpdateCheck != null },
                ::StaffCount,
            ).mapNotNull { it?.invoke() }

            PlayerJoinExtensionHolder(
                syncExtensions = TicketManager.PlayerJoinRegistry.getSyncExtensions(),
                asyncExtensions = extraAsyncPlayerJoinExtensions + TicketManager.PlayerJoinRegistry.getAsyncExtensions()
            )
        }

        val extraPreCommandExtensions = run {
            val extraPreCommandDeciders = listOf(
                { Cooldown(config.cooldownOptions!!.duration) }.takeIf { config.cooldownOptions != null }
            ).mapNotNull { it?.invoke() }

            PreCommandExtensionHolder(
                deciders = TicketManager.PreCommandRegistry.getDeciders() + extraPreCommandDeciders,
                syncAfters = TicketManager.PreCommandRegistry.getSyncAfters(),
                asyncAfters = TicketManager.PreCommandRegistry.getAsyncAfters()
            )
        }

        val repeatingTaskExtensions = run {
            val extraRepeatingTaskExtensions = listOf(
                ::UnreadNotify.takeIf { config.allowUnreadTicketUpdates },
                ::RepeatingStaffCount,
            ).mapNotNull { it?.invoke() }

            TicketManager.RepeatingTaskRegistry.getExtensions() + extraRepeatingTaskExtensions
        }

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
            preCommand = extraPreCommandExtensions,
        )

        // Launch repeating tasks
        repeatingTaskExtensions.map {
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

            for (message in notificationSharingMailbox.incomingMessages) {
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

        TMCoroutine.Supervised.cancelTasks("Plugin is reloading or shutting down!")

        databaseClosing.closeDatabase()
        messageSharing.unload(trueShutdown)
    }
}

private suspend fun TMMessageSharingRegistry.loadAndInitialize(isHubOptionsNull: Boolean): MessageSharing {
    return if (isHubOptionsNull)
        object : MessageSharing {   // Dummy object with no behaviour
            override fun relay2Hub(data: ByteArray, channelName: String) {}
            override suspend fun unload(trueShutdown: Boolean) {}
        }
    else loadAndInitialize(
        teleportJoinIntermediary = TeleportJoinMailbox.Intermediary,
        notificationSharingIntermediary = NotificationSharingMailbox.Intermediary,
        pbeVersionIntermediary = PBEVersionChannel.Intermediary
    )
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
    val MessageSharingRegistry: TMMessageSharingRegistry = TicketManagerInternal.MessageSharingRegistry as TMMessageSharingRegistry
}