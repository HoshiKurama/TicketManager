package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.api.common.TMCoroutine
import com.github.hoshikurama.ticketmanager.api.common.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.common.database.Option
import com.github.hoshikurama.ticketmanager.api.common.database.SearchConstraints
import com.github.hoshikurama.ticketmanager.api.common.ticket.ActionLocation
import com.github.hoshikurama.ticketmanager.api.common.ticket.Assignment
import com.github.hoshikurama.ticketmanager.api.common.ticket.Creator
import com.github.hoshikurama.ticketmanager.api.common.ticket.Ticket
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.commands.CommandTasks
import com.github.hoshikurama.ticketmanager.commonse.commands.MessageNotification
import com.github.hoshikurama.ticketmanager.commonse.commands.executeNotificationsAsync
import com.github.hoshikurama.ticketmanager.commonse.datas.ConfigState
import com.github.hoshikurama.ticketmanager.commonse.datas.Cooldown
import com.github.hoshikurama.ticketmanager.commonse.datas.GlobalState
import com.github.hoshikurama.ticketmanager.commonse.extensions.DatabaseManager
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.misc.pushErrors
import com.github.hoshikurama.ticketmanager.commonse.misc.templated
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.utilities.asDeferredThenAwait
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.SuggestionInfo
import dev.jorel.commandapi.arguments.*
import dev.jorel.commandapi.arguments.CustomArgument.CustomArgumentException
import dev.jorel.commandapi.executors.CommandArguments
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.executors.ExecutorType
import dev.jorel.commandapi.kotlindsl.*
import kotlinx.coroutines.future.asCompletableFuture
import net.kyori.adventure.text.Component
import org.bukkit.OfflinePlayer
import java.util.concurrent.CompletableFuture
import com.github.hoshikurama.ticketmanager.api.common.database.SearchConstraints.Symbol as SCSymbol
import org.bukkit.command.ConsoleCommandSender as BukkitConsole

typealias BukkitPlayer = org.bukkit.entity.Player
typealias BukkitCommandSender = org.bukkit.command.CommandSender

// Note: This needs to be set
object ReloadObjectCommand {
    @Volatile internal lateinit var configState: ConfigState
    @Volatile internal lateinit var platform: PlatformFunctions
    @Volatile internal var cooldown: Cooldown? = null
    @Volatile internal lateinit var lpGroupNames: List<String>
    @Volatile internal lateinit var commandTasks: CommandTasks
    @Volatile internal lateinit var locale: TMLocale
}

class CommandAPIRunner {
    // Note: This will enforce that CommandAPI Command execution utilizes the latest reload for visuals
    private val configState: ConfigState
        get() = ReloadObjectCommand.configState
    private val platform: PlatformFunctions
        get() = ReloadObjectCommand.platform
    private val cooldown: Cooldown?
        get() = ReloadObjectCommand.cooldown
    private val lpGroupNames: List<String>
        get() = ReloadObjectCommand.lpGroupNames
    private val commandTasks: CommandTasks
        get() = ReloadObjectCommand.commandTasks
    private val locale: TMLocale
        get() = ReloadObjectCommand.locale

    // Arguments
    private inline fun CommandAPICommand.argumentTicketFromIDAsync(
        vararg otherChecks: (Ticket, CommandSender.Active) -> Error?,
        otherFunctions: Argument<*>.() -> Unit
    ) = argument(
        CustomArgument(LongArgument(locale.parameterID)) { info ->
            TMCoroutine.asyncSupervised {
                // Verifies plugin state
                if (GlobalState.isPluginLocked)
                    return@asyncSupervised locale.warningsLocked
                        .parseMiniMessage()
                        .run(::Error)

                val ticketOrNull = DatabaseManager.activeDatabase
                    .getTicketOrNullAsync(info.currentInput)

                // Filter Invalid Ticket
                val ticket = ticketOrNull ?: return@asyncSupervised locale.brigadierInvalidID
                    .parseMiniMessage("id" templated info.currentInput.toString())
                    .run(::Error)

                // Other Checks
                val tmSender = info.sender.toTMSender()
                val failPoint = otherChecks.mapNotNull { it(ticket, tmSender) }

                if (failPoint.isNotEmpty()) failPoint.first() else Success(ticket)
            }.asCompletableFuture()
        }
    ) { otherFunctions(this) }

    // Errors
    @Suppress("UNUSED_PARAMETER") // Note: allows method reference
    private fun ticketIsOpen(ticket: Ticket, ignored: CommandSender.Active): Error? {
        return if (ticket.status != Ticket.Status.OPEN)
            locale.brigadierTicketAlreadyClosed
                .parseMiniMessage()
                .run(::Error)
        else null

    }
    @Suppress("UNUSED_PARAMETER") // Note: allows method reference
    private fun ticketIsClosed(ticket: Ticket, ignored: CommandSender.Active): Error? {
        return if (ticket.status != Ticket.Status.CLOSED)
            locale.brigadierTicketAlreadyOpen
                .parseMiniMessage()
                .run(::Error)
        else null
    }
    private fun userDualityError(basePerm: String): (Ticket, CommandSender.Active) -> Error? = { ticket, sender ->
        val hasAll = sender.has("$basePerm.all")
        val hasOwn = sender.has("$basePerm.own")

        val canRunCommand = hasAll || (hasOwn && ticket.creator == sender.asCreator())
        if (!canRunCommand)
            locale.brigadierNotYourTicket
                .parseMiniMessage()
                .run(::Error)
        else null
    }

    // Argument Suggestions
    private val openTicketIDsAsync = ArgumentSuggestions.stringsAsync { _: SuggestionInfo<BukkitCommandSender> ->
        TMCoroutine.asyncGlobal {
            DatabaseManager.activeDatabase.getOpenTicketIDsAsync()
                .map(Long::toString).toTypedArray()
        }.asCompletableFuture()
    }

    private fun dualityOpenIDsAsync(basePerm: String) = ArgumentSuggestions.stringsAsync { info ->
        TMCoroutine.asyncGlobal {
            val tmSender = info.sender.toTMSender()

            val results = if (tmSender.has("$basePerm.all")) DatabaseManager.activeDatabase.getOpenTicketIDsAsync()
            else DatabaseManager.activeDatabase.getOpenTicketIDsForUser(tmSender.asCreator())

            results.map(Long::toString).toTypedArray()
        }.asCompletableFuture()
    }

    // Requirements
    private fun notUnderCooldown(bukkitSender: BukkitCommandSender): Boolean {
        if (cooldown == null) return true

        val sender = bukkitSender.toTMSender()
        if (sender.has("ticketmanager.commandArg.cooldown.override")) return true

        return when (sender) {
            is CommandSender.Active.OnlineConsole -> true
            is CommandSender.Active.OnlinePlayer -> cooldown!!.notUnderCooldown(sender.uuid)
        }
    }
    private fun hasSilentPermission(bukkitSender: BukkitCommandSender): Boolean {
        return when (val tmSender = bukkitSender.toTMSender()) {
            is CommandSender.Active.OnlinePlayer -> tmSender.has("ticketmanager.commandArg.silence")
            is CommandSender.Active.OnlineConsole -> true
        }
    }


    // Other
    private fun updateCommandReqs(bukkitSender: BukkitCommandSender) {
        if (bukkitSender is BukkitPlayer)
            CommandAPI.updateRequirements(bukkitSender)
    }
    private fun hasOneDualityPermission(user: BukkitCommandSender, basePerm: String): Boolean {
        val tmSender = user.toTMSender()
        return tmSender.has("$basePerm.all") || tmSender.has("$basePerm.own")
    }
    private fun Cooldown.performCommandDuties(bukkitSender: BukkitCommandSender) {
        if (bukkitSender !is BukkitPlayer) return

        add(bukkitSender.uniqueId)
        updateCommandReqs(bukkitSender)
    }

    // Command generator
    fun generateCommands() {

        // /ticket create <Comment...>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordCreate) {
                withPermission("ticketmanager.command.create")
                withRequirement(::notUnderCooldown)
            }
            greedyStringArgument(locale.parameterComment)
            executeRegisterTMMessageY { tmSender, args ->
                commandTasks.create(tmSender,
                    message = args[0] as String
                )
            }
        }

        // /ticket assign <ID> user <Player>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordAssign) {
                withPermission("ticketmanager.command.assign")
                withRequirement(::notUnderCooldown)
            }
            argumentTicketFromIDAsync(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            literalArgument(locale.parameterLiteralPlayer)
            offlinePlayerArgument(locale.parameterLiteralPlayer)
            executeRegisterTMMessage(0) { tmSender, args, ticket ->
                commandTasks.assign(tmSender,
                    assignment = (args[1] as OfflinePlayer)
                        .run(OfflinePlayer::getName)
                        ?.run(Assignment::Player)
                        ?: Assignment.Nobody,
                    ticket = ticket,
                    silent = false,
                )
            }
        }

        // /ticket assign <ID> group <Group>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordAssign) {
                withPermission("ticketmanager.command.assign")
                withRequirement(::notUnderCooldown)
            }
            argumentTicketFromIDAsync(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            literalArgument(locale.parameterLiteralGroup)
            multiLiteralArgument(
                nodeName = locale.parameterLiteralGroup,
                *lpGroupNames.toTypedArray()
            )
            executeRegisterTMMessage(0) { tmSender, args, ticket ->
                commandTasks.assign(tmSender,
                    assignment = Assignment.PermissionGroup((args[1] as String)),
                    ticket = ticket,
                    silent = false,
                )
            }
        }

        // /ticket assign <ID> phrase <phrase...>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordAssign) {
                withPermission("ticketmanager.command.assign")
                withRequirement(::notUnderCooldown)
            }
            argumentTicketFromIDAsync(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            literalArgument(locale.parameterLiteralPhrase)
            greedyStringArgument(locale.parameterLiteralPhrase)
            executeRegisterTMMessage(0) { tmSender, args, ticket ->
                commandTasks.assign(tmSender,
                    assignment = Assignment.Phrase((args[1] as String)),
                    ticket = ticket,
                    silent = false,
                )
            }
        }

        // /ticket s.assign <ID> player <Player>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordSilentAssign) {
                withPermission("ticketmanager.command.assign")
                withRequirement(::notUnderCooldown)
                withRequirement(::hasSilentPermission)
            }
            argumentTicketFromIDAsync(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            literalArgument(locale.parameterLiteralPlayer)
            offlinePlayerArgument(locale.parameterLiteralPlayer)
            executeRegisterTMMessage(0) { tmSender, args, ticket ->
                commandTasks.assign(tmSender,
                    assignment = (args[1] as OfflinePlayer)
                        .run(OfflinePlayer::getName)
                        ?.run(Assignment::Player)
                        ?: Assignment.Nobody,
                    ticket = ticket,
                    silent = true,
                )
            }
        }

        // /ticket s.assign <ID> group <Group>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordSilentAssign) {
                withPermission("ticketmanager.command.assign")
                withRequirement(::notUnderCooldown)
                withRequirement(::hasSilentPermission)
            }
            argumentTicketFromIDAsync(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            literalArgument(locale.parameterLiteralGroup)
            multiLiteralArgument(
                nodeName = locale.parameterLiteralGroup,
                *lpGroupNames.toTypedArray()
            )
            executeRegisterTMMessage(0) { tmSender, args, ticket ->
                commandTasks.assign(tmSender,
                    assignment = Assignment.PermissionGroup((args[1] as String)),
                    ticket = ticket,
                    silent = true,
                )
            }
        }

        // /ticket s.assign <ID> phrase <phrase...>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordSilentAssign) {
                withPermission("ticketmanager.command.assign")
                withRequirement(::notUnderCooldown)
                withRequirement(::hasSilentPermission)
            }
            argumentTicketFromIDAsync(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            literalArgument(locale.parameterLiteralPhrase)
            greedyStringArgument(locale.parameterLiteralPhrase)
            executeRegisterTMMessage(0) { tmSender, args, ticket ->
                commandTasks.assign(tmSender,
                    assignment = Assignment.Phrase((args[1] as String)),
                    ticket = ticket,
                    silent = true,
                )
            }
        }

        // /ticket unassign <ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordUnassign) {
                withPermission("ticketmanager.command.assign")
                withRequirement(::notUnderCooldown)
            }
            argumentTicketFromIDAsync(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            executeRegisterTMMessage(0) { tmSender, _, ticket ->
                commandTasks.unAssign(tmSender,
                    ticket = ticket,
                    silent = false,
                )
            }
        }

        // /ticket s.unassign <ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordSilentUnassign) {
                withPermission("ticketmanager.command.assign")
                withRequirement(::notUnderCooldown)
                withRequirement(::hasSilentPermission)
            }
            argumentTicketFromIDAsync(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            executeRegisterTMMessage(0) { tmSender, _, ticket ->
                commandTasks.unAssign(tmSender,
                    ticket = ticket,
                    silent = true
                )
            }
        }

        // /ticket claim <ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordClaim) {
                withPermission("ticketmanager.command.assign")
                withRequirement(::notUnderCooldown)
            }
            argumentTicketFromIDAsync(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            executeRegisterTMMessage(0) { tmSender, _, ticket ->
                commandTasks.claim(tmSender,
                    ticket = ticket,
                    silent = false
                )
            }
        }

        // /ticket s.claim <ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordSilentClaim) {
                withPermission("ticketmanager.command.assign")
                withRequirement(::notUnderCooldown)
                withRequirement(::hasSilentPermission)
            }
            argumentTicketFromIDAsync(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            executeRegisterTMMessage(0) { tmSender, _, ticket ->
                commandTasks.claim(tmSender,
                    ticket = ticket,
                    silent = true
                )
            }
        }

        // /ticket close <ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordClose) {
                withRequirement { hasOneDualityPermission(it, "ticketmanager.command.close") }
                withRequirement(::notUnderCooldown)
            }
            argumentTicketFromIDAsync(::ticketIsOpen, userDualityError("ticketmanager.command.close")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.close"))
            }
            executeRegisterTMMessage(0) { tmSender, _, ticket ->
                commandTasks.closeWithoutComment(tmSender,
                    ticket = ticket,
                    silent = false
                )
            }
        }

        // /ticket s.close <ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordSilentClose) {
                withRequirement { hasOneDualityPermission(it, "ticketmanager.command.close") }
                withRequirement(::hasSilentPermission)
                withRequirement(::notUnderCooldown)
            }
            argumentTicketFromIDAsync(::ticketIsOpen, userDualityError("ticketmanager.command.close")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.close"))
            }
            executeRegisterTMMessage(0) { tmSender, _, ticket ->
                commandTasks.closeWithoutComment(tmSender,
                    ticket = ticket,
                    silent = true
                )
            }
        }

        // /ticket close <ID> [Comment...]
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordClose) {
                withRequirement { hasOneDualityPermission(it, "ticketmanager.command.close") }
                withRequirement(::notUnderCooldown)
            }
            argumentTicketFromIDAsync(::ticketIsOpen, userDualityError("ticketmanager.command.close")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.close"))
            }
            greedyStringArgument(locale.parameterComment)
            executeRegisterTMMessage(0) { tmSender, args, ticket ->
                commandTasks.closeWithComment(tmSender,
                    ticket = ticket,
                    comment = args[1] as String,
                    silent = false
                )
            }
        }

        // /ticket s.close <ID> [Comment...]
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordSilentClose) {
                withRequirement { hasOneDualityPermission(it, "ticketmanager.command.close") }
                withRequirement(::notUnderCooldown)
                withRequirement(::hasSilentPermission)
            }
            argumentTicketFromIDAsync(::ticketIsOpen, userDualityError("ticketmanager.command.close")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.close"))
            }
            greedyStringArgument(locale.parameterComment)
            executeRegisterTMMessage(0) { tmSender, args, ticket ->
                commandTasks.closeWithComment(tmSender,
                    ticket = ticket,
                    comment = args[1] as String,
                    silent = true
                )
            }
        }

        // /ticket closeall <Lower ID> <Upper ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordCloseAll) {
                withPermission("ticketmanager.command.closeAll")
                withRequirement(::notUnderCooldown)
            }
            longArgument(locale.parameterLowerID)
            longArgument(locale.parameterUpperID)
            executeRegisterTMMessageY { tmSender, args ->
                commandTasks.closeAll(tmSender,
                    lowerBound = args[0] as Long,
                    upperBound = args[1] as Long,
                    silent = false
                )
            }
        }

        // /ticket s.closeall <Lower ID> <Upper ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordSilentCloseAll) {
                withPermission("ticketmanager.command.closeAll")
                withRequirement(::notUnderCooldown)
                withRequirement(::hasSilentPermission)
            }
            longArgument(locale.parameterLowerID)
            longArgument(locale.parameterUpperID)
            executeRegisterTMMessageY { tmSender, args ->
                commandTasks.closeAll(tmSender,
                    lowerBound = args[0] as Long,
                    upperBound = args[1] as Long,
                    silent = true
                )
            }
        }

        // /ticket comment <ID> <Comment…>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordComment) {
                withRequirement { hasOneDualityPermission(it, "ticketmanager.command.comment") }
                withRequirement(::notUnderCooldown)
            }
            argumentTicketFromIDAsync(::ticketIsOpen, userDualityError("ticketmanager.command.comment")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.comment"))
            }
            greedyStringArgument(locale.parameterComment)
            executeRegisterTMMessage(0) { tmSender, args, ticket ->
                commandTasks.comment(tmSender,
                    ticket = ticket,
                    comment = args[1] as String,
                    silent = false
                )
            }
        }

        // /ticket s.comment <ID> <Comment…>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordSilentComment) {
                withRequirement { hasOneDualityPermission(it, "ticketmanager.command.comment") }
                withRequirement(::notUnderCooldown)
                withRequirement(::hasSilentPermission)
            }
            argumentTicketFromIDAsync(::ticketIsOpen, userDualityError("ticketmanager.command.comment")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.comment"))
            }
            greedyStringArgument(locale.parameterComment)
            executeRegisterTMMessage(0) { tmSender, args, ticket ->
                commandTasks.comment(tmSender,
                    ticket = ticket,
                    comment = args[1] as String,
                    silent = true
                )
            }
        }

        // /ticket list [Page]
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordList) {
                withPermission("ticketmanager.command.list")
            }
            integerArgument(locale.parameterPage)
            executeRegisterTMAction { tmSender, args ->
                commandTasks.list(tmSender,
                    requestedPage = args[0] as Int,
                )
            }
        }

        // /ticket list
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordList) {
                withPermission("ticketmanager.command.list")
            }
            executeRegisterTMAction { tmSender, _ ->
                commandTasks.list(tmSender, 1)
            }
        }

        // /ticket listassigned [Page]
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordListAssigned) {
                withPermission("ticketmanager.command.list")
            }
            integerArgument(locale.parameterPage)
            executeRegisterTMAction { tmSender, args ->
                commandTasks.listAssigned(tmSender,
                    requestedPage = args[0] as Int,
                )
            }
        }

        // /ticket listassigned [Page]
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordListAssigned) {
                withPermission("ticketmanager.command.list")
            }
            executeRegisterTMAction { tmSender, _ ->
                commandTasks.listAssigned(tmSender, 1)
            }
        }

        // /ticket listunassigned [Page]
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordListUnassigned) {
                withPermission("ticketmanager.command.list")
            }
            integerArgument(locale.parameterPage)
            executeRegisterTMAction { tmSender, args ->
                commandTasks.listUnassigned(tmSender,
                    requestedPage = args[0] as Int,
                )
            }
        }

        // /ticket listunassigned
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordListUnassigned) {
                withPermission("ticketmanager.command.list")
            }
            executeRegisterTMAction{ tmSender, _ ->
                commandTasks.listUnassigned(tmSender, 1)
            }
        }

        // /ticket help
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordHelp) {
                withPermission("ticketmanager.command.help")
            }
            executeRegisterTMAction { tmSender, _ ->
                commandTasks.help(tmSender)
            }
        }

        // /ticket reopen <ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordReopen) {
                withPermission("ticketmanager.command.reopen")
                withRequirement(::notUnderCooldown)
            }
            argumentTicketFromIDAsync(::ticketIsClosed) {
                replaceSuggestions(ArgumentSuggestions.strings("<${locale.parameterID}>"))
            }
            executeRegisterTMMessage(0) { tmSender, _, ticket ->
                commandTasks.reopen(tmSender,
                    ticket = ticket,
                    silent = false
                )
            }
        }

        // /ticket s.reopen <ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordSilentReopen) {
                withPermission("ticketmanager.command.reopen")
                withRequirement(::notUnderCooldown)
                withRequirement(::hasSilentPermission)
            }
            argumentTicketFromIDAsync(::ticketIsClosed) {
                replaceSuggestions(ArgumentSuggestions.strings("<${locale.parameterID}>"))
            }
            executeRegisterTMMessage(0) { tmSender, _, ticket ->
                commandTasks.reopen(tmSender,
                    ticket = ticket,
                    silent = true
                )
            }
        }

        // /ticket version
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordVersion)
            executeRegisterTMAction { tmSender, _ ->
                commandTasks.version(tmSender)
            }
        }

        // /ticket teleport <ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordTeleport) {
                withRequirement {
                    it.toTMSender().has("ticketmanager.command.teleport") ||
                            it.toTMSender().has("ticketmanager.command.proxyteleport")
                }
            }
            argumentTicketFromIDAsync({ ticket, sender ->
                val location = ticket.actions[0].location

                // Only teleport to player tickets
                if (location !is ActionLocation.FromPlayer)
                    return@argumentTicketFromIDAsync locale.brigadierConsoleLocTP.parseMiniMessage().run(::Error)

                when (location.server == configState.proxyServerName) {
                    true -> if (!sender.has("ticketmanager.command.teleport"))
                        return@argumentTicketFromIDAsync Error(locale.brigadierNoTPSameServer.parseMiniMessage())
                    false -> when (!configState.enableProxyMode) {
                        false -> return@argumentTicketFromIDAsync Error(locale.brigadierNoTPProxyDisabled.parseMiniMessage())
                        true -> if (!sender.has("ticketmanager.command.proxyteleport"))
                            return@argumentTicketFromIDAsync Error(locale.brigadierNoTPDiffServer.parseMiniMessage())
                    }
                }
                null
            }) {
                replaceSuggestions(ArgumentSuggestions.strings("<${locale.parameterID}>"))
            }
            executeRegisterTMAction(0) { tmSender, _, ticket ->
                commandTasks.teleport(tmSender, ticket)
            }
        }

        // /ticket view <ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordView) {
                withRequirement { hasOneDualityPermission(it, "ticketmanager.command.view") }
            }
            argumentTicketFromIDAsync(userDualityError("ticketmanager.command.view")) {
                replaceSuggestions(ArgumentSuggestions.strings("<${locale.parameterID}>"))
            }
            executeRegisterTMAction(0) { tmSender, _, ticket ->
                commandTasks.view(tmSender, ticket)
            }
        }

        // /ticket viewdeep <ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordDeepView) {
                withRequirement { hasOneDualityPermission(it, "ticketmanager.command.viewdeep") }
            }
            argumentTicketFromIDAsync(userDualityError("ticketmanager.command.viewdeep")) {
                replaceSuggestions(ArgumentSuggestions.strings("<${locale.parameterID}>"))
            }
            executeRegisterTMAction(0) { tmSender, _, ticket ->
                commandTasks.viewDeep(tmSender, ticket)
            }
        }

        // /ticket setpriority <ID> <Priority>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordSetPriority) {
                withPermission("ticketmanager.command.setPriority")
            }
            argumentTicketFromIDAsync(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            multiLiteralArgument(nodeName = locale.searchPriority,
                locale.priorityLowest, locale.priorityLow, locale.priorityNormal,
                locale.priorityHigh, locale.priorityHighest, "1", "2", "3", "4", "5"
            )
            executeRegisterTMMessage(0) { tmSender, args, ticket ->
                commandTasks.setPriority(tmSender,
                    ticket = ticket,
                    priority = numberOrWordToPriority(args[1] as String),
                    silent = false
                )
            }
        }

        // /ticket s.setpriority <ID> <Priority>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordSilentSetPriority) {
                withPermission("ticketmanager.command.setPriority")
                withRequirement(::hasSilentPermission)
            }
            argumentTicketFromIDAsync(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            multiLiteralArgument(nodeName = locale.searchPriority,
                locale.priorityLowest, locale.priorityLow, locale.priorityNormal,
                locale.priorityHigh, locale.priorityHighest, "1", "2", "3", "4", "5"
            )
            executeRegisterTMMessage(0) { tmSender, args, ticket ->
                commandTasks.setPriority(tmSender,
                    ticket = ticket,
                    priority = numberOrWordToPriority(args[1] as String),
                    silent = true
                )
            }
        }

        // /ticket reload
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordReload) {
                withPermission("ticketmanager.command.reload")
            }
            executeRegisterTMAction { tmSender, _ -> commandTasks.reload(tmSender) }
        }

        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordHistory) {
                withRequirement { it.hasPermission("ticketmanager.command.history.all") || it.hasPermission("ticketmanager.command.history.own") }
            }
            executeRegisterTMAction { tmSender, _ ->
                commandTasks.history(tmSender,
                    checkedCreator = tmSender.asCreator(),
                    requestedPage = 1
                )
            }
        }

        // /ticket history Console
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordHistory) {
                withRequirement { it.hasPermission("ticketmanager.command.history.all") || it.hasPermission("ticketmanager.command.history.own") }
            }
            literalArgument(locale.consoleName)
            executeRegisterTMAction { tmSender, _ ->
                commandTasks.history(tmSender,
                    checkedCreator = Creator.Console,
                    requestedPage = 1
                )
            }
        }

        // /ticket history Console [Page]
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordHistory) {
                withPermission("ticketmanager.command.history.all")
            }
            literalArgument(locale.consoleName)
            integerArgument(locale.parameterPage)
            executeRegisterTMAction { tmSender, args ->
                commandTasks.history(tmSender,
                    checkedCreator = Creator.Console,
                    requestedPage = args[0] as Int
                )
            }
        }

        // /ticket history [Page]
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordHistory) {
                withPermission("ticketmanager.command.history.all")
            }
            integerArgument(locale.parameterPage)
            executeRegisterTMAction { tmSender, args ->
                commandTasks.history(tmSender,
                    checkedCreator = tmSender.asCreator(),
                    requestedPage = args[0] as Int
                )
            }
        }

        // /ticket history [User]
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordHistory) {
                withRequirement { it.hasPermission("ticketmanager.command.history.all") || it.hasPermission("ticketmanager.command.history.own") }
            }
            argument(CustomArgument(OfflinePlayerArgument(locale.parameterUser)) {
                if (!it.sender.hasPermission("ticketmanager.command.history.all")
                    && it.sender.hasPermission("ticketmanager.command.history.own")
                    && it.input != it.sender.name
                    ) throw CustomArgumentException.fromAdventureComponent(locale.brigadierOtherHistory.parseMiniMessage())
                it.currentInput
            })
            executeRegisterTMAction { tmSender, args ->
                commandTasks.history(tmSender,
                    checkedCreator = (args[0] as OfflinePlayer)
                        .run(OfflinePlayer::getUniqueId)
                        .run(Creator::User),
                    requestedPage = 1
                )
            }
        }

        // /ticket history [User] [Page]
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordHistory) {
                withRequirement { it.hasPermission("ticketmanager.command.history.all") || it.hasPermission("ticketmanager.command.history.own") }
            }
            argument(CustomArgument(OfflinePlayerArgument(locale.parameterUser)) {
                if (!it.sender.hasPermission("ticketmanager.command.history.all")
                    && it.sender.hasPermission("ticketmanager.command.history.own")
                    && it.input != it.sender.name
                ) throw CustomArgumentException.fromAdventureComponent(locale.brigadierOtherHistory.parseMiniMessage())
                it.currentInput
            })
            integerArgument(locale.parameterPage)
            executeRegisterTMAction { tmSender, args ->
                commandTasks.history(tmSender,
                    checkedCreator = (args[0] as OfflinePlayer)
                        .run(OfflinePlayer::getUniqueId)
                        .run(Creator::User),
                    requestedPage = args[1] as Int
                )
            }
        }

        // /ticket search where <Params...>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordSearch) {
                withPermission("ticketmanager.command.search")
            }
            literalArgument(locale.parameterNewSearchIndicator)
            argument(CustomArgument(GreedyStringArgument(locale.parameterConstraints)) { info ->
                if (info.currentInput.isBlank() || info.currentInput.trimEnd().endsWith("&&"))
                    return@CustomArgument SearchConstraints(requestedPage = 1) // This prevents random check at beginning

                // Temp variables to assign
                var creator: Option<Creator>? = null
                var assigned: Option<Assignment>? = null
                var priority: Option<Ticket.Priority>? = null
                var status: Option<Ticket.Status>? = null
                var closedBy: Option<Creator>? = null
                var lastClosedBy: Option<Creator>? = null
                var world: Option<String>? = null
                var creationTime: Option<Long>? = null
                var keywords: Option<List<String>>? = null
                var page = 1


                // This enforces "key = value(s)... and ..." syntax and filters out bad stuff
                "(\\b\\w+\\s*(?:=|!=|<|>)\\s*.*?)(?=\\s+&&\\s+|\$)".toRegex()
                    .findAll(info.currentInput)
                    .map(MatchResult::value)
                    .map { it.split(" ", limit = 3) }
                    .forEach { (keyword, symbol, value) ->
                        when (keyword) {
                            locale.searchCreator -> creator = when (symbol) {
                                "=" -> Option(SCSymbol.EQUALS, value.attemptNameToTicketCreator())
                                "!=" -> Option(SCSymbol.NOT_EQUALS, value.attemptNameToTicketCreator())
                                else -> throw CustomArgumentException
                                    .fromAdventureComponent(locale.brigadierSearchBadSymbol1.parseMiniMessage(
                                        "symbol" templated symbol,
                                        "keyword" templated keyword,
                                    ))
                            }
                            locale.searchAssigned -> assigned = when (symbol) {
                                "=" -> Option(SCSymbol.EQUALS, value.searchToAssignment())
                                "!=" -> Option(SCSymbol.NOT_EQUALS, value.searchToAssignment())
                                else -> throw CustomArgumentException
                                    .fromAdventureComponent(locale.brigadierSearchBadSymbol1.parseMiniMessage(
                                        "symbol" templated symbol,
                                        "keyword" templated keyword,
                                    ))
                            }
                            locale.searchLastClosedBy -> lastClosedBy = when (symbol) {
                                "=" -> Option(SCSymbol.EQUALS, value.attemptNameToTicketCreator())
                                "!=" -> Option(SCSymbol.NOT_EQUALS, value.attemptNameToTicketCreator())
                                else -> throw CustomArgumentException
                                    .fromAdventureComponent(locale.brigadierSearchBadSymbol1.parseMiniMessage(
                                        "symbol" templated symbol,
                                        "keyword" templated keyword,
                                    ))
                            }
                            locale.searchClosedBy -> closedBy = when (symbol) {
                                "=" -> Option(SCSymbol.EQUALS, value.attemptNameToTicketCreator())
                                "!=" -> Option(SCSymbol.NOT_EQUALS, value.attemptNameToTicketCreator())
                                else -> throw CustomArgumentException
                                    .fromAdventureComponent(locale.brigadierSearchBadSymbol1.parseMiniMessage(
                                        "symbol" templated symbol,
                                        "keyword" templated keyword,
                                    ))
                            }
                            locale.searchWorld -> world = when (symbol) {
                                "=" -> Option(SCSymbol.EQUALS, value)
                                "!=" -> Option(SCSymbol.NOT_EQUALS, value)
                                else -> throw CustomArgumentException
                                    .fromAdventureComponent(locale.brigadierSearchBadSymbol1.parseMiniMessage(
                                        "symbol" templated symbol,
                                        "keyword" templated keyword,
                                    ))
                            }
                            locale.searchStatus -> {
                                val ticketStatus = when (value) {
                                    locale.statusOpen -> Ticket.Status.OPEN
                                    locale.statusClosed -> Ticket.Status.CLOSED
                                    else -> throw CustomArgumentException.fromAdventureComponent(locale.brigadierSearchBadStatus.parseMiniMessage(
                                        "status" templated value,
                                        "open" templated Ticket.Status.OPEN.name,
                                        "closed" templated Ticket.Status.CLOSED.name,
                                    ))
                                }
                                status = when (symbol) {
                                    "=" -> Option(SCSymbol.EQUALS, ticketStatus)
                                    "!=" -> Option(SCSymbol.NOT_EQUALS, ticketStatus)
                                    else -> throw CustomArgumentException
                                        .fromAdventureComponent(locale.brigadierSearchBadSymbol1.parseMiniMessage(
                                            "symbol" templated symbol,
                                            "keyword" templated keyword,
                                        ))
                                }
                            }
                            locale.searchPriority -> priority = when (symbol) {
                                    "=" -> Option(SCSymbol.EQUALS, numberOrWordToPriority(value))
                                    "!=" -> Option(SCSymbol.NOT_EQUALS, numberOrWordToPriority(value))
                                    "<" -> Option(SCSymbol.LESS_THAN, numberOrWordToPriority(value))
                                    ">" -> Option(SCSymbol.GREATER_THAN, numberOrWordToPriority(value))
                                    else -> throw CustomArgumentException
                                        .fromAdventureComponent(locale.brigadierSearchBadSymbol2.parseMiniMessage(
                                            "symbol" templated symbol,
                                            "keyword" templated keyword,
                                        ))
                                }
                            locale.searchKeywords -> {
                                val foundSearches = value.split(" || ")
                                keywords = when (symbol) {
                                    "=" -> Option(SCSymbol.EQUALS, foundSearches)
                                    "!=" -> Option(SCSymbol.NOT_EQUALS, foundSearches)
                                    else -> throw CustomArgumentException
                                        .fromAdventureComponent(locale.brigadierSearchBadSymbol1.parseMiniMessage(
                                            "symbol" templated symbol,
                                            "keyword" templated keyword,
                                        ))
                                }
                            }
                            locale.searchTime -> {
                                val epochTime = "\\b\\d+\\s+\\w+\\b".toRegex()
                                    .findAll(value)
                                    .map(MatchResult::value)
                                    .map { it.split(" ", limit = 2).run { get(0).toLong() to get(1) } }
                                    .fold(0L) {acc, (timeVal, timeUnit) -> acc + timeVal * timeUnitToMultiplier(timeUnit) }
                                creationTime = when (symbol) {
                                    "<" -> Option(SCSymbol.LESS_THAN, epochTime)
                                    ">" -> Option(SCSymbol.GREATER_THAN, epochTime)
                                    else -> throw CustomArgumentException
                                        .fromAdventureComponent(locale.brigadierSearchBadSymbol3.parseMiniMessage(
                                            "symbol" templated symbol,
                                            "keyword" templated keyword,
                                        ))
                                }
                            }
                            locale.searchPage -> page = value.toIntOrNull()
                                ?: throw CustomArgumentException.fromAdventureComponent(locale.brigadierBadPageNumber.parseMiniMessage())
                            else -> throw CustomArgumentException
                                .fromAdventureComponent(locale.brigadierBadSearchConstraint.parseMiniMessage(
                                    "keyword" templated keyword
                                ))
                        }
                    }
                return@CustomArgument SearchConstraints(creator, assigned, priority, status, closedBy, lastClosedBy, world, creationTime, keywords, page)
            }) {
                replaceSuggestions { args, builder -> CompletableFuture.supplyAsync { // Remove everything behind last &&
                    val curArgsSet = args.currentArg.split(" && ").last().split(" ")


                    val newBuilder = builder.createOffset(builder.start + args.currentArg.lastIndexOf(" ") + 1)
                    val keywordList = listOf(
                        locale.searchAssigned,
                        locale.searchCreator,
                        locale.searchKeywords,
                        locale.searchPriority,
                        locale.searchStatus,
                        locale.searchWorld,
                        locale.searchClosedBy,
                        locale.searchLastClosedBy,
                        locale.searchTime,
                    )

                    if (curArgsSet.size > 2 && curArgsSet[0] in keywordList) {
                        when (curArgsSet[0]) {

                            locale.searchCreator, locale.searchClosedBy, locale.searchLastClosedBy ->
                                if (curArgsSet.size == 3)
                                    platform.getOnlineSeenPlayerNames(args.sender.toTMSender()) + listOf(locale.consoleName)
                                else listOf("&&")

                            locale.searchAssigned ->
                                if (curArgsSet.size == 3)
                                    listOf(locale.consoleName, locale.miscNobody, locale.parameterLiteralPlayer, locale.parameterLiteralGroup, locale.parameterLiteralPhrase)
                                else if (curArgsSet.size > 3 && curArgsSet[2] == locale.parameterLiteralPhrase)
                                    listOf("${locale.parameterLiteralPhrase}...", "&&")
                                else listOf("&&")

                            locale.searchPriority ->
                                if (curArgsSet.size == 3) {
                                    listOf("1", "2", "3", "4", "5", locale.priorityLowest, locale.priorityLow,
                                        locale.priorityNormal, locale.priorityHigh, locale.priorityHighest,
                                    )
                                } else listOf("&&")

                            locale.searchStatus ->
                                if (curArgsSet.size == 3)
                                    listOf(locale.statusOpen, locale.statusClosed)
                                else listOf("&&")

                            locale.searchWorld ->
                                if (curArgsSet.size == 3)
                                    platform.getWorldNames()
                                else listOf("&&")

                            locale.searchTime ->
                                if (curArgsSet.size % 2 == 1)
                                    if (curArgsSet.size > 4)
                                        listOf("&&", "<${locale.searchTime}>")
                                    else listOf( "<${locale.searchTime}>")
                                else listOf(locale.timeSeconds.trimStart(), locale.timeMinutes.trimStart(), locale.timeHours.trimStart(),
                                    locale.timeDays.trimStart(), locale.timeWeeks.trimStart(), locale.timeYears.trimStart(),
                                )
                            locale.searchKeywords -> {
                                if (curArgsSet.size == 3 || curArgsSet[curArgsSet.lastIndex-1] == "||")
                                    listOf("<${locale.searchKeywords}>")
                                else listOf("||", "&&", "<${locale.searchKeywords}>")
                            }
                            else -> throw Exception("Impossible")
                        }
                            .filter { it.startsWith(curArgsSet.last()) }
                            .forEach(newBuilder::suggest)
                    }

                    // "somethingHere "
                    else if (curArgsSet.size == 2 && curArgsSet[0] in keywordList) {
                        when (curArgsSet[0]) {
                            locale.searchCreator,
                            locale.searchAssigned,
                            locale.searchLastClosedBy,
                            locale.searchClosedBy,
                            locale.searchWorld,
                            locale.searchStatus,
                            locale.searchKeywords -> listOf("=", "!=")
                            locale.searchPriority -> listOf("=", "!=", "<", ">")
                            locale.searchTime -> listOf("<", ">")
                            else -> throw Exception("Reach Impossible")
                        }
                            .filter { it.startsWith(curArgsSet.last()) }
                            .forEach(newBuilder::suggest)
                    }
                    else keywordList
                        .filter { it.startsWith(curArgsSet.last()) }
                        .forEach(newBuilder::suggest)

                    newBuilder.build()
                }}
            }
            executeRegisterTMGetArgs { tmSender, args ->
                commandTasks.search(tmSender,
                    searchParameters = args[0] as SearchConstraints,
                    useNewFormat = true,
                    newRawArgumentString = args.fullInput(),
                )
            }
        }
    }

    private fun BukkitCommandSender.toTMSender(): CommandSender.Active = when (this) {
        is BukkitConsole -> PaperConsole(configState.proxyServerName)
        is BukkitPlayer-> PaperPlayer(this, configState.proxyServerName)
        else -> throw Exception("Unsupported Entity Type!")
    }

    private inline fun CommandAPICommand.executeRegisterTMMessageY(
        crossinline commandTask: suspend (tmSender: CommandSender.Active, args: Array<out Any>) -> MessageNotification<CommandSender.Active>?
    ) {
        executeTMAbstract { bukkitSender, args ->
            // Standard Operations
            val tmSender = bukkitSender.toTMSender()
            val message = commandTask(tmSender, args.args())

            cooldown?.performCommandDuties(bukkitSender)
            message?.run { executeNotificationsAsync(platform, configState, tmSender, locale, this) }
        }
    }

    private inline fun CommandAPICommand.executeRegisterTMMessage(
        resultArgNum: Int,
        crossinline commandTask: suspend (tmSender: CommandSender.Active, args: Array<out Any>, ticket: Ticket) -> MessageNotification<CommandSender.Active>?
    ) {
        executeTMAbstract { bukkitSender, args ->
            val ticket = when(val result = awaitCommandResult(args.args()[resultArgNum])) {
                is Error -> bukkitSender
                    .sendMessage(result.errorComponent)
                    .let { return@executeTMAbstract null }
                is Success -> result.ticket
            }

            // Standard Operations
            val tmSender = bukkitSender.toTMSender()
            val message = commandTask(tmSender, args.args(), ticket)

            cooldown?.performCommandDuties(bukkitSender)
            message?.run { executeNotificationsAsync(platform, configState, tmSender, locale, this) }
        }
    }

    private inline fun CommandAPICommand.executeRegisterTMAction(
        resultArgNum: Int,
        crossinline commandTask: suspend (tmSender: CommandSender.Active, args: Array<out Any>, ticket: Ticket) -> Unit
    ) {
        executeTMAbstract { bukkitSender, args ->
            val ticket = when(val result = awaitCommandResult(args.args()[resultArgNum])) {
                is Error -> bukkitSender
                    .sendMessage(result.errorComponent)
                    .let { return@executeTMAbstract null }
                is Success -> result.ticket
            }

            val tmSender = bukkitSender.toTMSender()
            commandTask(tmSender, args.args(), ticket)

            cooldown?.performCommandDuties(bukkitSender)
        }
    }

    private inline fun CommandAPICommand.executeRegisterTMAction(

        crossinline commandTask: suspend (tmSender: CommandSender.Active, args: Array<out Any>) -> Unit
    ) {
        executeTMAbstract { bukkitSender, args ->
            val tmSender = bukkitSender.toTMSender()
            commandTask(tmSender, args.args())

            cooldown?.performCommandDuties(bukkitSender)
        }
    }

    private inline fun CommandAPICommand.executeRegisterTMGetArgs(
        crossinline commandTask: suspend (tmSender: CommandSender.Active, args: CommandArguments) -> Unit
    ) {
        executeTMAbstract { bukkitSender, args ->
            val tmSender = bukkitSender.toTMSender()
            commandTask(tmSender, args)
        }
    }

    private inline fun <T> CommandAPICommand.executeTMAbstract(
        crossinline commandTask: suspend (bukkitSender: BukkitCommandSender, args: CommandArguments) -> T
    ) {
        executes(CommandExecutor { bukkitSender, args ->
            if (GlobalState.isPluginLocked) {
                locale.warningsLocked
                    .parseMiniMessage()
                    .run(bukkitSender::sendMessage)

                return@CommandExecutor
            }

            TMCoroutine.launchSupervised {
                try { commandTask(bukkitSender, args) }
                catch(e: Exception) { pushErrors(platform, configState, locale, e, TMLocale::warningsUnexpectedError) }
            }
        }, ExecutorType.CONSOLE, ExecutorType.PLAYER).register()
    }

    private fun String.attemptNameToTicketCreator(): Creator {
        return if (this == locale.consoleName) Creator.Console
        else platform.offlinePlayerNameToUUIDOrNull(this)
            ?.run(Creator::User)
            ?: Creator.UUIDNoMatch
    }

    private fun String.searchToAssignment(): Assignment {
        val (type, value) = this.split(" ", limit = 2)
            .let { it[0] to it.getOrNull(1) }

        return when (type) {
            locale.miscNobody -> Assignment.Nobody
            locale.consoleName -> Assignment.Console
            locale.parameterLiteralPlayer -> Assignment.Player(value!!)
            locale.parameterLiteralGroup -> Assignment.PermissionGroup(value!!)
            locale.parameterLiteralPhrase -> Assignment.Phrase(value!!)
            else -> throw CustomArgumentException
                .fromAdventureComponent(locale.brigadierInvalidAssignment.parseMiniMessage(
                    "assignment" templated (value ?: "???")
                ))
        }
    }

    private fun timeUnitToMultiplier(timeUnit: String) = when (timeUnit) {
        locale.timeSeconds.trimStart() -> 1L
        locale.timeMinutes.trimStart() -> 60L
        locale.timeHours.trimStart() -> 3600L
        locale.timeDays.trimStart() -> 86400L
        locale.timeWeeks.trimStart() -> 604800L
        locale.timeYears.trimStart() -> 31556952L
        else -> throw CustomArgumentException
            .fromAdventureComponent(locale.brigadierInvalidTimeUnit.parseMiniMessage(
                "timeunit" templated timeUnit
            ))
    }

    private fun numberOrWordToPriority(input: String): Ticket.Priority = when (input) {
        "1", locale.priorityLowest -> Ticket.Priority.LOWEST
        "2", locale.priorityLow -> Ticket.Priority.LOW
        "3", locale.priorityNormal -> Ticket.Priority.NORMAL
        "4", locale.priorityHigh -> Ticket.Priority.HIGH
        "5", locale.priorityHighest -> Ticket.Priority.HIGHEST
        else -> throw CustomArgumentException
            .fromAdventureComponent(locale.brigadierInvalidPriority.parseMiniMessage(
                "priority" templated input
            ))
    }

    private suspend fun awaitCommandResult(thing: Any) = (thing as CompletableFuture<*>).asDeferredThenAwait() as CommandResult
}

sealed interface CommandResult
private class Error(val errorComponent: Component): CommandResult
private class Success(val ticket: Ticket): CommandResult