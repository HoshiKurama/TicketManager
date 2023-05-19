package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.api.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.database.Option
import com.github.hoshikurama.ticketmanager.api.database.SearchConstraints
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import com.github.hoshikurama.ticketmanager.api.ticket.TicketAssignmentType
import com.github.hoshikurama.ticketmanager.api.ticket.TicketCreationLocation
import com.github.hoshikurama.ticketmanager.api.ticket.TicketCreator
import com.github.hoshikurama.ticketmanager.commonse.TMCoroutine
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.TMPlugin
import com.github.hoshikurama.ticketmanager.commonse.commands.CommandTasks
import com.github.hoshikurama.ticketmanager.commonse.commands.MessageNotification
import com.github.hoshikurama.ticketmanager.commonse.commands.executeNotificationsAsync
import com.github.hoshikurama.ticketmanager.commonse.datas.ConfigState
import com.github.hoshikurama.ticketmanager.commonse.datas.Cooldown
import com.github.hoshikurama.ticketmanager.commonse.extensions.DatabaseManager
import com.github.hoshikurama.ticketmanager.commonse.misc.asCreator
import com.github.hoshikurama.ticketmanager.commonse.misc.pushErrors
import com.github.hoshikurama.ticketmanager.commonse.misc.toLocalizedName
import com.github.hoshikurama.ticketmanager.commonse.platform.OnlinePlayer
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.SuggestionInfo
import dev.jorel.commandapi.arguments.*
import dev.jorel.commandapi.arguments.CustomArgument.CustomArgumentException
import dev.jorel.commandapi.executors.CommandArguments
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.executors.ExecutorType
import dev.jorel.commandapi.kotlindsl.*
import java.util.concurrent.CompletableFuture
import com.github.hoshikurama.ticketmanager.api.database.SearchConstraints.Symbol as SCSymbol
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
    private inline fun CommandAPICommand.argumentTicketFromID(
        vararg otherChecks: (Ticket, CommandSender.Active) -> Unit,
        otherFunctions: Argument<*>.() -> Unit
    ) = argument(
        CustomArgument(LongArgument(locale.parameterID)) { info ->
            val ticket = DatabaseManager.activeDatabase
                .getTicketOrNullAsync(info.currentInput)
                .join() //TODO HMM, SHOULD I WORRY ABOUT THIS? NOTE: IF THIS IS A PROBLEM, PASS DOWN COMPLETEABLEFUTURE
                ?: throw CustomArgumentException.fromString("${locale.brigadierInvalidID}: ${info.currentInput}")

            val tmSender = info.sender.toTMSender()
            otherChecks.forEach { it.invoke(ticket, tmSender) }
            return@CustomArgument ticket
        }
    ) { otherFunctions(this) }
    private fun assignmentArg(): CustomArgument<TicketAssignmentType, String> {
        return CustomArgument(GreedyStringArgument(locale.parameterAssignment)) { info ->
            when (info.currentInput) {
                TicketAssignmentType.Nobody.toLocalizedName(locale) -> TicketAssignmentType.Nobody
                TicketAssignmentType.Console.toLocalizedName(locale) -> TicketAssignmentType.Console
                else -> TicketAssignmentType.Other(info.currentInput)
            }
        }
    }

    // Errors
    private fun ticketIsOpen(ticket: Ticket, ignored: CommandSender.Active) {
        if (ticket.status != Ticket.Status.OPEN) throw CustomArgumentException.fromString(locale.brigadierTicketAlreadyClosed)
    }
    private fun ticketIsClosed(ticket: Ticket, ignored: CommandSender.Active) {
        if (ticket.status != Ticket.Status.CLOSED) throw CustomArgumentException.fromString(locale.brigadierTicketAlreadyOpen)
    }
    private fun userDualityError(basePerm: String): (Ticket, CommandSender.Active) -> Unit = { ticket, sender ->
        val hasAll = sender.has("$basePerm.all")
        val hasOwn = sender.has("$basePerm.own")

        val canRunCommand = hasAll || (hasOwn && ticket.creator == sender.asCreator())
        if (!canRunCommand) throw CustomArgumentException.fromString(locale.brigadierNotYourTicket)
    }


    // Argument Suggestions
    private val ownedTicketIDsAsync = ArgumentSuggestions.stringsAsync { info ->
        DatabaseManager.activeDatabase.getOwnedTicketIDsAsync(info.sender.toTicketCreator())
            .thenApplyAsync { it.map(Long::toString).toTypedArray() }
    }
    private val openTicketIDsAsync = ArgumentSuggestions.stringsAsync { _: SuggestionInfo<BukkitCommandSender> ->
        DatabaseManager.activeDatabase.getOpenTicketIDsAsync()
            .thenApplyAsync { it.map(Long::toString).toTypedArray() }
    }
    private val assignmentSuggest = ArgumentSuggestions.stringCollection { info ->
        val suggestions = mutableListOf<String>()
        suggestions.addAll(lpGroupNames.map { "::$it" })
        suggestions.addAll(info.sender.toTMSender().run(platform::getOnlineSeenPlayerNames))
        suggestions.add(TicketAssignmentType.Console.toLocalizedName(locale))
        suggestions.add(TicketAssignmentType.Nobody.toLocalizedName(locale))
        suggestions.sorted()
    }
    private fun dualityOpenIDsAsync(basePerm: String) = ArgumentSuggestions.stringsAsync { info ->
        val tmSender = info.sender.toTMSender()

        val results = if (tmSender.has("$basePerm.all")) DatabaseManager.activeDatabase.getOpenTicketIDsAsync()
        else DatabaseManager.activeDatabase.getOpenTicketIDsForUser(tmSender.asCreator())

        results.thenApplyAsync { it.map(Long::toString).toTypedArray() }
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
            is OnlinePlayer -> tmSender.has("ticketmanager.commandArg.silence")
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
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.create(tmSender,
                    message = args[0] as String
                )
            }
        }

        // /ticket assign <ID> <Assignment...>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordAssign) {
                withPermission("ticketmanager.command.assign")
                withRequirement(::notUnderCooldown)
            }
            argumentTicketFromID(::ticketIsOpen) { replaceSuggestions(openTicketIDsAsync) }
            argument(assignmentArg()) { replaceSuggestions(assignmentSuggest) }
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.assign(tmSender,
                    assignment = args[1] as TicketAssignmentType,
                    ticket = args[0] as Ticket,
                    silent = false,
                )
            }
        }

        // /ticket s.assign <ID> <Assignment...>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordSilentAssign) {
                withPermission("ticketmanager.command.assign")
                withRequirement(::notUnderCooldown)
                withRequirement(::hasSilentPermission)
            }
            argumentTicketFromID(::ticketIsOpen) { replaceSuggestions(openTicketIDsAsync) }
            argument(assignmentArg()) { replaceSuggestions(assignmentSuggest) }
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.assign(tmSender,
                    assignment = args[1] as TicketAssignmentType,
                    ticket = args[0] as Ticket,
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
            argumentTicketFromID(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.unAssign(tmSender,
                    ticket = args[0] as Ticket,
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
            argumentTicketFromID(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.unAssign(tmSender,
                    ticket = args[0] as Ticket,
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
            argumentTicketFromID(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.claim(tmSender,
                    ticket = args[0] as Ticket,
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
            argumentTicketFromID(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.claim(tmSender,
                    ticket = args[0] as Ticket,
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
            argumentTicketFromID(::ticketIsOpen, userDualityError("ticketmanager.command.close")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.close"))
            }
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.closeWithoutComment(tmSender,
                    ticket = args[0] as Ticket,
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
            argumentTicketFromID(::ticketIsOpen, userDualityError("ticketmanager.command.close")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.close"))
            }
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.closeWithoutComment(tmSender,
                    ticket = args[0] as Ticket,
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
            argumentTicketFromID(::ticketIsOpen, userDualityError("ticketmanager.command.close")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.close"))
            }
            greedyStringArgument(locale.parameterComment)
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.closeWithComment(tmSender,
                    ticket = args[0] as Ticket,
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
            argumentTicketFromID(::ticketIsOpen, userDualityError("ticketmanager.command.close")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.close"))
            }
            greedyStringArgument(locale.parameterComment)
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.closeWithComment(tmSender,
                    ticket = args[0] as Ticket,
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
            executeRegisterTMMessage { tmSender, args ->
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
            executeRegisterTMMessage { tmSender, args ->
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
            argumentTicketFromID(::ticketIsOpen, userDualityError("ticketmanager.command.comment")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.comment"))
            }
            greedyStringArgument(locale.parameterComment)
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.comment(tmSender,
                    ticket = args[0] as Ticket,
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
            argumentTicketFromID(::ticketIsOpen, userDualityError("ticketmanager.command.comment")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.comment"))
            }
            greedyStringArgument(locale.parameterComment)
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.comment(tmSender,
                    ticket = args[0] as Ticket,
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
            argumentTicketFromID(::ticketIsClosed) {} // Note: Suggestions intentionally blank (require all closed)
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.reopen(tmSender,
                    ticket = args[0] as Ticket,
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
            argumentTicketFromID(::ticketIsClosed) {} // Note: Suggestions intentionally blank (require all closed)
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.reopen(tmSender,
                    ticket = args[0] as Ticket,
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
            argumentTicketFromID({ ticket, sender ->
                val location = ticket.actions[0].location

                // Only teleport to player tickets
                if (location !is TicketCreationLocation.FromPlayer)
                    throw CustomArgumentException.fromString("You may not teleport to tickets created by Console!")

                when (location.server == configState.proxyServerName) {
                    true -> if (!sender.has("ticketmanager.command.teleport"))
                        throw CustomArgumentException.fromString("You do not have permission to teleport to tickets on the same server!")
                    false -> when (!configState.enableProxyMode) {
                        false -> throw CustomArgumentException.fromString("You may not teleport to tickets on other servers when proxy mode is disabled!")
                        true -> if (!sender.has("ticketmanager.command.proxyteleport"))
                            throw CustomArgumentException.fromString("You do not have permission to teleport to tickets on other servers!")
                    }
                }
            }) {} // Note: Suggests intentionally blank (would require all tickets)
            executeRegisterTMAction { tmSender, args ->
                commandTasks.teleport(tmSender,
                    ticket = args[0] as Ticket
                )
            }
        }

        // /ticket view <ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordView) {
                withRequirement { hasOneDualityPermission(it, "ticketmanager.command.view") }
            }
            argumentTicketFromID(userDualityError("ticketmanager.command.view")) {}
            executeRegisterTMAction { tmSender, args ->
                commandTasks.view(tmSender,
                    ticket = args[0] as Ticket
                )
            }
        }

        // /ticket viewdeep <ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordView) {
                withRequirement { hasOneDualityPermission(it, "ticketmanager.command.viewdeep") }
            }
            argumentTicketFromID(userDualityError("ticketmanager.command.viewdeep")) {}
            executeRegisterTMAction { tmSender, args ->
                commandTasks.viewDeep(tmSender,
                    ticket = args[0] as Ticket
                )
            }
        }
//TODO MAKE EVERYTHING RUN WITH THE CF
        // /ticket setpriority <ID> <Priority>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordSetPriority) {
                withPermission("ticketmanager.command.setPriority")
            }
            argumentTicketFromID(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            multiLiteralArgument(locale.priorityLowest, locale.priorityLow,
                locale.priorityNormal, locale.priorityHigh, locale.priorityHighest, "1", "2", "3", "4", "5")
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.setPriority(tmSender,
                    ticket = args[0] as Ticket,
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
            argumentTicketFromID(::ticketIsOpen) {
                replaceSuggestions(openTicketIDsAsync)
            }
            multiLiteralArgument(locale.priorityLowest, locale.priorityLow,
                locale.priorityNormal, locale.priorityHigh, locale.priorityHighest, "1", "2", "3", "4", "5")
            executeRegisterTMMessage { tmSender, args ->
                commandTasks.setPriority(tmSender,
                    ticket = args[0] as Ticket,
                    priority = numberOrWordToPriority(args[1] as String),
                    silent = true
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
                var creator: Option<TicketCreator>? = null
                var assigned: Option<TicketAssignmentType>? = null
                var priority: Option<Ticket.Priority>? = null
                var status: Option<Ticket.Status>? = null
                var closedBy: Option<TicketCreator>? = null
                var lastClosedBy: Option<TicketCreator>? = null
                var world: Option<String>? = null
                var creationTime: Option<Long>? = null
                var keywords: Option<List<String>>? = null
                var page = 1


                // This enforces "key = value(s)... and ..." syntax and filters out bad stuff
                "(\\b\\w+\\s*(?:=|!=|<|>)\\s*.*?)(?=\\s+&&\\s+|\$)".toRegex()
                    .findAll(info.currentInput)
                    .map(MatchResult::value)
                    .map { it.split(" ", limit = 3) }
                    .map { Triple(it[0], it[1], it[2]) }
                    .forEach { (keyword, symbol, value) ->
                        when (keyword) {
                            locale.searchCreator -> creator = when (symbol) {
                                "=" -> Option(SCSymbol.EQUALS, value.attemptNameToTicketCreator())
                                "!=" -> Option(SCSymbol.NOT_EQUALS, value.attemptNameToTicketCreator())
                                else -> throw CustomArgumentException.fromString("$symbol is not a valid symbol for keyword $keyword! Please use = or !=")
                            }
                            locale.searchAssigned -> assigned = when (symbol) {
                                "=" -> Option(SCSymbol.EQUALS, value.attemptToAssignmentType())
                                "!=" -> Option(SCSymbol.NOT_EQUALS, value.attemptToAssignmentType())
                                else -> throw CustomArgumentException.fromString("$symbol is not a valid symbol for keyword $keyword! Please use = or !=")
                            }
                            locale.searchLastClosedBy -> lastClosedBy = when (symbol) {
                                "=" -> Option(SCSymbol.EQUALS, value.attemptNameToTicketCreator())
                                "!=" -> Option(SCSymbol.NOT_EQUALS, value.attemptNameToTicketCreator())
                                else -> throw CustomArgumentException.fromString("$symbol is not a valid symbol for keyword $keyword! Please use = or !=")
                            }
                            locale.searchClosedBy -> closedBy = when (symbol) {
                                "=" -> Option(SCSymbol.EQUALS, value.attemptNameToTicketCreator())
                                "!=" -> Option(SCSymbol.NOT_EQUALS, value.attemptNameToTicketCreator())
                                else -> throw CustomArgumentException.fromString("$symbol is not a valid symbol for keyword $keyword! Please use = or !=")
                            }
                            locale.searchWorld -> world = when (symbol) {
                                "=" -> Option(SCSymbol.EQUALS, value)
                                "!=" -> Option(SCSymbol.NOT_EQUALS, value)
                                else -> throw CustomArgumentException.fromString("$symbol is not a valid symbol for keyword $keyword! Please use = or !=")
                            }
                            locale.searchStatus -> {
                                val ticketStatus = when (value) {
                                    locale.statusOpen -> Ticket.Status.OPEN
                                    locale.statusClosed -> Ticket.Status.CLOSED
                                    else -> throw CustomArgumentException.fromString("Invalid status type!")
                                }
                                status = when (symbol) {
                                    "=" -> Option(SCSymbol.EQUALS, ticketStatus)
                                    "!=" -> Option(SCSymbol.NOT_EQUALS, ticketStatus)
                                    else -> throw CustomArgumentException.fromString("$symbol is not a valid symbol for keyword $keyword! Please use = or !=")
                                }
                            }
                            locale.searchPriority -> priority = when (symbol) {
                                    "=" -> Option(SCSymbol.EQUALS, numberOrWordToPriority(info.currentInput))
                                    "!=" -> Option(SCSymbol.NOT_EQUALS, numberOrWordToPriority(info.currentInput))
                                    "<" -> Option(SCSymbol.LESS_THAN, numberOrWordToPriority(info.currentInput))
                                    ">" -> Option(SCSymbol.GREATER_THAN, numberOrWordToPriority(info.currentInput))
                                    else -> throw CustomArgumentException.fromString("$symbol is not a valid symbol for keyword $keyword! Valid types are: =, !=, <, or >")
                                }
                            locale.searchKeywords -> {
                                val foundSearches = value.split(" || ")
                                keywords = when (symbol) {
                                    "=" -> Option(SCSymbol.EQUALS, foundSearches)
                                    "!=" -> Option(SCSymbol.NOT_EQUALS, foundSearches)
                                    else -> throw CustomArgumentException.fromString("$symbol is not a valid symbol for keyword $keyword! Please use = or !=")
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
                                    else -> throw CustomArgumentException.fromString("$symbol is not a valid symbol for keyword $keyword! Please use < or >")
                                }
                            }
                            locale.searchPage -> page = value.toIntOrNull()
                                ?: throw CustomArgumentException.fromString("Invalid page")
                            else -> throw CustomArgumentException.fromString("Invalid search constraint: $keyword")
                        }
                    }

                return@CustomArgument SearchConstraints(creator, assigned, priority, status, closedBy, lastClosedBy, world, creationTime, keywords, page)
            }) {
                replaceSuggestions { args, builder -> CompletableFuture.supplyAsync { // Remove everything behind last &&
                    val curArgsSet = args.currentArg.split(" && ").last().split(" ")
                    val newBuilder = builder.createOffset(args.currentArg.lastIndexOf(" "))
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
                                platform.getOnlineSeenPlayerNames(args.sender.toTMSender())

                            locale.searchAssigned ->
                                (TMPlugin.lpGroupNames.map { "::$it" } + platform.getOnlineSeenPlayerNames(args.sender.toTMSender()))

                            locale.searchPriority ->
                                listOf("1", "2", "3", "4", "5", locale.priorityLowest, locale.priorityLow,
                                    locale.priorityNormal, locale.priorityHigh, locale.priorityHighest,
                                ).filter { it.startsWith(curArgsSet[2]) }

                            locale.searchStatus -> listOf(locale.statusOpen, locale.statusClosed)
                                .filter { it.startsWith(curArgsSet[2]) }

                            locale.searchWorld -> platform.getWorldNames().filter { it.startsWith(curArgsSet[2]) }

                            locale.searchTime ->
                                if (curArgsSet.size % 2 == 0) { // timeUnit entry
                                    listOf(locale.timeSeconds, locale.timeMinutes, locale.timeHours,
                                        locale.timeDays, locale.timeWeeks, locale.timeYears,
                                    ).filter { it.startsWith(curArgsSet[curArgsSet.lastIndex]) }
                                } else listOf("&&").filter { it.startsWith(curArgsSet[curArgsSet.lastIndex]) }

                            locale.searchKeywords -> listOf("||", "&&").filter { it.startsWith(curArgsSet[curArgsSet.lastIndex]) }

                            else -> throw Exception("Impossible")
                            //TODO GO BACK AND MAKE SURE TO ADD && CLAUSES
                        }.forEach(newBuilder::suggest)
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
                        }.forEach(newBuilder::suggest)
                    }

                    // "" or "somethingHere"
                    /*
                    if (curArgsSet.first().isBlank() || curArgsSet.size == 1) {

                    }

                     */
                    else keywordList.forEach(newBuilder::suggest)

                    newBuilder.build()
                }}
            }
            executeRegisterTMGetArgs { tmSender, args ->
                commandTasks.search(tmSender,
                    searchParameters = args[0] as SearchConstraints,
                    useNewFormat = true,
                    newRawArgumentString = args.fullInput,
                )
            }
        }

    //TODO REWRITE ALL CUSTOM ARGUMENT EXCEPTIONS USING KYORI MINIMESSAGE
    }

    private fun BukkitCommandSender.toTMSender(): CommandSender.Active = when (this) {
        is BukkitConsole -> PaperConsole(locale, configState.proxyServerName)
        is BukkitPlayer-> PaperPlayer(this, configState.proxyServerName)
        else -> throw Exception("Unsupported Entity Type!")
    }

    private inline fun CommandAPICommand.executeRegisterTMMessage(
        crossinline commandTask: suspend (tmSender: CommandSender.Active, args: Array<out Any>) -> MessageNotification<CommandSender.Active>?
    ) {
        executeTMAbstract { bukkitSender, args ->
            val tmSender = bukkitSender.toTMSender()
            val message = commandTask(tmSender, args.args())

            cooldown?.performCommandDuties(bukkitSender)
            message?.run { executeNotificationsAsync(platform, configState, tmSender, locale, this) }
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
            TMCoroutine.launchSupervised {
                try { commandTask(bukkitSender, args) }
                catch(e: Exception) { pushErrors(platform, configState, locale, e, TMLocale::warningsUnexpectedError) }
            }
        }, ExecutorType.CONSOLE, ExecutorType.PLAYER).register()
    }



    private fun String.attemptNameToTicketCreator(): TicketCreator {
        return if (this == locale.consoleName) TicketCreator.Console
        else platform.offlinePlayerNameToUUIDOrNull(this)
            ?.run(TicketCreator::User)
            ?: TicketCreator.UUIDNoMatch
    }

    private fun String.attemptToAssignmentType(): TicketAssignmentType = when (this) {
        locale.miscNobody -> TicketAssignmentType.Nobody
        locale.consoleName -> TicketAssignmentType.Console
        else -> TicketAssignmentType.Other(this)
    }

    private fun timeUnitToMultiplier(timeUnit: String) = when (timeUnit) {
        locale.searchTimeSecond -> 1L
        locale.searchTimeMinute -> 60L
        locale.searchTimeHour -> 3600L
        locale.searchTimeDay -> 86400L
        locale.searchTimeWeek -> 604800L
        locale.searchTimeYear -> 31556952L
        else -> throw CustomArgumentException.fromString("Invalid time unit: $timeUnit")
    }

    private fun numberOrWordToPriority(input: String): Ticket.Priority = when (input) {
        "1", locale.priorityLowest -> Ticket.Priority.LOWEST
        "2", locale.priorityLow -> Ticket.Priority.LOW
        "3", locale.priorityNormal -> Ticket.Priority.NORMAL
        "4", locale.priorityHigh -> Ticket.Priority.HIGH
        "5", locale.priorityHighest -> Ticket.Priority.HIGHEST
        else -> throw CustomArgumentException.fromString("$input is not a valid priority!")
    }
}

fun BukkitCommandSender.toTicketCreator(): TicketCreator = when (this) {
    is BukkitConsole -> TicketCreator.Console
    is BukkitPlayer -> TicketCreator.User(uniqueId)
    else -> throw Exception("Unsupported Entity Type!")
}


// /ticket history [User] [Page]

// Ones to-do:
// /ticket reload