package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.api.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import com.github.hoshikurama.ticketmanager.api.ticket.TicketAssignmentType
import com.github.hoshikurama.ticketmanager.api.ticket.TicketCreator
import com.github.hoshikurama.ticketmanager.commonse.TMCoroutine
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
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
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.executors.ExecutorType
import dev.jorel.commandapi.kotlindsl.*
import org.bukkit.command.ConsoleCommandSender

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
    private fun CommandAPICommand.argumentTicketFromID(
        vararg otherChecks: (Ticket, CommandSender.Active) -> Unit,
        otherFunctions: Argument<*>.() -> Unit
    ) = argument(
        CustomArgument(LongArgument(locale.parameterID)) { info ->
            val ticket = DatabaseManager.activeDatabase
                .getTicketOrNullAsync(info.currentInput)
                .join() //TODO HMM, SHOULD I WORRY ABOUT THIS?
                ?: throw CustomArgumentException.fromString("${locale.brigadierInvalidID}: ${info.currentInput}")

            val tmSender = info.sender.toTMSender()
            otherChecks.forEach { it.invoke(ticket, tmSender) }
            ticket
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
    private fun ticketAlreadyClosedError(ticket: Ticket, ignored: CommandSender.Active) {
        if (ticket.status == Ticket.Status.CLOSED) throw CustomArgumentException.fromString(locale.brigadierTicketAlreadyClosed)
    }

    private fun ticketAlreadyOpenError(ticket: Ticket, ignored: CommandSender.Active) {
        if (ticket.status == Ticket.Status.OPEN) throw CustomArgumentException.fromString(locale.brigadierTicketAlreadyOpen)
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
            executeAndRegisterTMMessage { tmSender, args ->
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
            argumentTicketFromID(::ticketAlreadyClosedError) { replaceSuggestions(openTicketIDsAsync) }
            argument(assignmentArg()) { replaceSuggestions(assignmentSuggest) }
            executeAndRegisterTMMessage { tmSender, args ->
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
            argumentTicketFromID(::ticketAlreadyClosedError) { replaceSuggestions(openTicketIDsAsync) }
            argument(assignmentArg()) { replaceSuggestions(assignmentSuggest) }
            executeAndRegisterTMMessage { tmSender, args ->
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
            argumentTicketFromID(::ticketAlreadyClosedError) {
                replaceSuggestions(openTicketIDsAsync)
            }
            executeAndRegisterTMMessage { tmSender, args ->
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
            argumentTicketFromID(::ticketAlreadyClosedError) {
                replaceSuggestions(openTicketIDsAsync)
            }
            executeAndRegisterTMMessage { tmSender, args ->
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
            argumentTicketFromID(::ticketAlreadyClosedError) {
                replaceSuggestions(openTicketIDsAsync)
            }
            executeAndRegisterTMMessage { tmSender, args ->
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
            argumentTicketFromID(::ticketAlreadyClosedError) {
                replaceSuggestions(openTicketIDsAsync)
            }
            executeAndRegisterTMMessage { tmSender, args ->
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
            argumentTicketFromID(::ticketAlreadyClosedError, userDualityError("ticketmanager.command.close")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.close"))
            }
            executeAndRegisterTMMessage { tmSender, args ->
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
            argumentTicketFromID(::ticketAlreadyClosedError, userDualityError("ticketmanager.command.close")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.close"))
            }
            executeAndRegisterTMMessage { tmSender, args ->
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
            argumentTicketFromID(::ticketAlreadyClosedError, userDualityError("ticketmanager.command.close")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.close"))
            }
            greedyStringArgument(locale.parameterComment)
            executeAndRegisterTMMessage { tmSender, args ->
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
            argumentTicketFromID(::ticketAlreadyClosedError, userDualityError("ticketmanager.command.close")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.close"))
            }
            greedyStringArgument(locale.parameterComment)
            executeAndRegisterTMMessage { tmSender, args ->
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
            executeAndRegisterTMMessage { tmSender, args ->
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
            executeAndRegisterTMMessage { tmSender, args ->
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
            argumentTicketFromID(::ticketAlreadyClosedError, userDualityError("ticketmanager.command.comment")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.comment"))
            }
            greedyStringArgument(locale.parameterComment)
            executeAndRegisterTMMessage { tmSender, args ->
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
            argumentTicketFromID(::ticketAlreadyClosedError, userDualityError("ticketmanager.command.comment")) {
                replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.comment"))
            }
            greedyStringArgument(locale.parameterComment)
            executeAndRegisterTMMessage { tmSender, args ->
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
            executeAndRegisterTMMessage { tmSender, args ->
                commandTasks.list(tmSender,
                    requestedPage = args[0] as Int,
                )
                null
            }
        }

        // /ticket list
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordList) {
                withPermission("ticketmanager.command.list")
            }
            executeAndRegisterTMMessage { tmSender, _ ->
                commandTasks.list(tmSender, 1)
                null
            }
        }

        // /ticket listassigned [Page]
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordListAssigned) {
                withPermission("ticketmanager.command.list")
            }
            integerArgument(locale.parameterPage)
            executeAndRegisterTMMessage { tmSender, args ->
                commandTasks.listAssigned(tmSender,
                    requestedPage = args[0] as Int,
                )
                null
            }
        }

        // /ticket listassigned [Page]
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordListAssigned) {
                withPermission("ticketmanager.command.list")
            }
            executeAndRegisterTMMessage { tmSender, _ ->
                commandTasks.listAssigned(tmSender, 1)
                null
            }
        }

        // /ticket listunassigned [Page]
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordListUnassigned) {
                withPermission("ticketmanager.command.list")
            }
            integerArgument(locale.parameterPage)
            executeAndRegisterTMMessage { tmSender, args ->
                commandTasks.listUnassigned(tmSender,
                    requestedPage = args[0] as Int,
                )
                null
            }
        }

        // /ticket listunassigned
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordListUnassigned) {
                withPermission("ticketmanager.command.list")
            }
            executeAndRegisterTMMessage { tmSender, _ ->
                commandTasks.listUnassigned(tmSender, 1)
                null
            }
        }

        // /ticket help
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordHelp) {
                withPermission("ticketmanager.command.help")
            }
            executeAndRegisterTMMessage { tmSender, _ ->
                commandTasks.help(tmSender)
                null
            }
        }

        // /ticket reopen <ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordReopen) {
                withPermission("ticketmanager.command.reopen")
                withRequirement(::notUnderCooldown)
            }
            argumentTicketFromID(::ticketAlreadyOpenError) {}
            executeAndRegisterTMMessage { tmSender, args ->
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
            argumentTicketFromID(::ticketAlreadyOpenError) {}
            executeAndRegisterTMMessage { tmSender, args ->
                commandTasks.reopen(tmSender,
                    ticket = args[0] as Ticket,
                    silent = true
                )
            }
        }

        // /ticket version
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordVersion)
            executeAndRegisterTMMessage { tmSender, _ ->
                commandTasks.version(tmSender)
                null
            }
        }

        /*
        // /ticket teleport <ID>
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordTeleport) {
                withRequirement {
                    it.toTMSender().has("ticketmanager.command.teleport") ||
                    it.toTMSender().has("ticketmanager.command.proxyteleport")
                }
            }

            //TODO
            argumentTicketFromID({ ticket, active ->
                if (ticket.actions[0].location.server == configState.proxyServerName) {
                    throw CustomArgumentException.fromString("")
                }


                if (ticket.actions[0].location !is TicketCreationLocation.FromPlayer)
                    throw CustomArgumentException.fromString("You ca")
            }) {

            }
        }
         */


//TODO REWRITE ALL CUSTOM ARGUEMENT EXCEPTIONS USING KYORI MINIMESSAGE
// TODO: I SHOULD MAKE THE COMMAND STUFF SEAMLESS! MAKE SURE THAT GREEDY STRINGS USE CUSTOM ERROR MESSAGE (How???)
    }

    private fun BukkitCommandSender.toTMSender(): CommandSender.Active = when (this) {
        is ConsoleCommandSender -> PaperConsole(locale, configState.proxyServerName)
        is BukkitPlayer-> PaperPlayer(this, configState.proxyServerName)
        else -> throw Exception("Unsupported Entity Type!")
    }

    private inline fun CommandAPICommand.executeAndRegisterTMMessage(
        crossinline commandTask: suspend (tmSender: CommandSender.Active, args: Array<out Any>) -> MessageNotification<CommandSender.Active>?
    ) {
        return this.executes(CommandExecutor { bukkitSender, args ->
            TMCoroutine.launchSupervised {
                try {
                    val tmSender = bukkitSender.toTMSender()
                    val message = commandTask(tmSender, args.args())

                    cooldown?.performCommandDuties(bukkitSender)
                    message?.run { executeNotificationsAsync(platform, configState, tmSender, locale, this) }
                } catch(e: Exception) {
                    pushErrors(platform, configState, locale, e, TMLocale::warningsUnexpectedError)
                }
            }
        }, ExecutorType.CONSOLE, ExecutorType.PLAYER).register()
    }
}

fun BukkitCommandSender.toTicketCreator(): TicketCreator = when (this) {
    is ConsoleCommandSender -> TicketCreator.Console
    is BukkitPlayer -> TicketCreator.User(uniqueId)
    else -> throw Exception("Unsupported Entity Type!")
}


// /ticket setpriority <ID> <Priority>
// /ticket history [User] [Page]
// /ticket search <Params>
// /ticket teleport <ID>
// /ticket view <ID>
// /ticket viewdeep <ID>

// Ones to-do:
// /ticket reload