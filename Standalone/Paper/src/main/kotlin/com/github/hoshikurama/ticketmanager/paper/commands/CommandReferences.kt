package com.github.hoshikurama.ticketmanager.paper.commands

import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.commonse.PreCommandExtensionHolder
import com.github.hoshikurama.ticketmanager.commonse.commands.CommandTasks

/**
 * This object is a workaround to the Paper Brigadier system not allowing command registration outside of the lifecycle
 * period. This allows TicketManager to maintain most reload functionality.
 *
 * The one drawback for users is that changing the locale to another language requires a server restart.
 */
internal object CommandReferences {
    @Volatile lateinit var config: Config
    @Volatile lateinit var  locale: Locale
    @Volatile lateinit var  database: AsyncDatabase
    @Volatile lateinit var  permissions: Permission
    @Volatile lateinit var  commandTasks: CommandTasks
    @Volatile lateinit var platform: PlatformFunctions
    @Volatile lateinit var preCommandExtensionHolder: PreCommandExtensionHolder
    @Volatile var shouldRegisterCommands = true
}