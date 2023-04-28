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
import com.github.hoshikurama.ticketmanager.commonse.misc.toLocalizedName
import com.github.hoshikurama.ticketmanager.commonse.platform.OnlinePlayer
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.*
import dev.jorel.commandapi.arguments.CustomArgument.CustomArgumentException
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.executors.ExecutorType
import net.luckperms.api.LuckPermsProvider
import org.bukkit.command.ConsoleCommandSender

typealias BukkitPlayer = org.bukkit.entity.Player
typealias BukkitCommandSender = org.bukkit.command.CommandSender

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
    private fun ticketFromIdArg(vararg otherChecks: (Ticket, CommandSender.Active) -> Unit): CustomArgument<Ticket, Long> {
        return CustomArgument(LongArgument(locale.parameterID)) { info ->

            val ticket = DatabaseManager.activeDatabase
                .getTicketOrNullAsync(info.currentInput)
                .join() //TODO HMM, SHOULD I WORRY ABOUT THIS?
                ?: throw CustomArgumentException("${locale.brigadierInvalidID}: ${info.currentInput}")

            val tmSender = info.sender.toTMSender()
            otherChecks.forEach { it.invoke(ticket, tmSender) }
            return@CustomArgument ticket
        }
    }

    private fun ticketFromIdArg(otherChecks: (Ticket) -> Unit): CustomArgument<Ticket, Long> {
        val adapter = { ticket: Ticket, _: CommandSender.Active -> otherChecks(ticket) }
        return ticketFromIdArg(adapter)
    }

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
        if (ticket.status == Ticket.Status.CLOSED) throw CustomArgumentException(locale.brigadierTicketAlreadyClosed)
    }

    private fun userDualityError(basePerm: String): (Ticket, CommandSender.Active) -> Unit = { ticket, sender ->
        val hasAll = sender.has("$basePerm.all")
        val hasOwn = sender.has("$basePerm.own")

        val canRunCommand = hasAll || (hasOwn && ticket.creator == sender.asCreator())
        if (!canRunCommand) throw CustomArgumentException(locale.brigadierNoPermission2Modify)
    }


    // Argument Suggestions
    private val ownedTicketIDsAsync: ArgumentSuggestions = ArgumentSuggestions.stringsAsync { info ->
        DatabaseManager.activeDatabase.getOwnedTicketIDsAsync(info.sender.toTicketCreator())
            .thenApplyAsync { it.map(Long::toString).toTypedArray() }
    }
    private val openTicketIDsAsync: ArgumentSuggestions = ArgumentSuggestions.stringsAsync {
        DatabaseManager.activeDatabase.getOpenTicketIDsAsync()
            .thenApplyAsync { it.map(Long::toString).toTypedArray() }
    }
    private val assignmentSuggest: ArgumentSuggestions = ArgumentSuggestions.stringCollection { info ->
        val suggestions = mutableListOf<String>()
        suggestions.addAll(lpGroupNames.map { "::$it" })
        suggestions.addAll(info.sender.toTMSender().run(platform::getOnlineSeenPlayerNames))
        suggestions.add(TicketAssignmentType.Console.toLocalizedName(locale))
        suggestions.add(TicketAssignmentType.Nobody.toLocalizedName(locale))
        suggestions.sorted()
    }

    private fun dualityOpenIDsAsync(basePerm: String): ArgumentSuggestions = ArgumentSuggestions.stringsAsync { info ->
        val tmSender = info.sender.toTMSender()

        val results = if (tmSender.has("$basePerm.all")) DatabaseManager.activeDatabase.getOpenTicketIDsAsync()
        else DatabaseManager.activeDatabase.getOpenTicketIDsForUser(tmSender.asCreator())

        results.thenApplyAsync { it.map(Long::toString).toTypedArray() }
    }


    // Requirements
    private fun notUnderCooldownReq(bukkitSender: BukkitCommandSender): Boolean {
        if (bukkitSender !is BukkitPlayer || cooldown == null) return true
        
        val playerHasOverridePerm = LuckPermsProvider.get()
            .getPlayerAdapter(BukkitPlayer::class.java)
            .getUser(bukkitSender)
            .cachedData
            .permissionData
            .checkPermission("ticketmanager.commandArg.cooldown.override")
            .asBoolean()
        if (playerHasOverridePerm) return true
        
        return cooldown?.underCooldown(bukkitSender.uniqueId) ?: false
    }

    private fun silentPermissionReq(bukkitSender: BukkitCommandSender): Boolean {
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
        /*
        val safeSuggestOpenTicketsAsync: SafeSuggestions<Long> = SafeSuggestions.suggestAsync { info ->
            DatabaseManager.activeDatabase.getOpenTicketsAsync(1, 0)
                .thenApplyAsync { r -> r.filteredResults
                    .map(Ticket::id)
                    .filter { "$it".startsWith(info.currentArg) }
                    .toTypedArray()
                }
        }

        val underCooldown = { sender: BukkitCommandSender ->
            val uuid = (sender as? BukkitPlayer)?.player?.uniqueId
            if (uuid != null && cooldown != null) !cooldown.underCooldown(uuid) else true
        }

         */

        // /ticket assign/s.assign || <ID> <Assignment...>
        fun CommandAPICommand.assignmentGeneration() = this
            .withArguments(ticketFromIdArg(::ticketAlreadyClosedError)
                .replaceSuggestions(openTicketIDsAsync))
            .withArguments(assignmentArg()
                .replaceSuggestions(assignmentSuggest))
        fun CommandAPICommand.executeAssignAsync(isSilent: Boolean) = tmExecuteWithMsgAsync { tmSender, bukkitSender, args ->
            cooldown?.performCommandDuties(bukkitSender)
            commandTasks.assign(
                sender = tmSender,
                assignment = args[1] as TicketAssignmentType,
                ticket = args[0] as Ticket,
                silent = isSilent,
            )
        }

        // /ticket claim/s.claim || <ID>
        fun CommandAPICommand.executeClaimAsync(isSilent: Boolean) = tmExecuteWithMsgAsync { tmSender, bukkitSender, args ->
            cooldown?.performCommandDuties(bukkitSender)
            commandTasks.claim(
                sender = tmSender,
                ticket = args[0] as Ticket,
                silent = isSilent,
            )
        }

        // /ticket unassign/s.unassign <ID>
        fun CommandAPICommand.executeUnassignAsync(isSilent: Boolean) = tmExecuteWithMsgAsync { tmSender, bukkitSender, args ->
            cooldown?.performCommandDuties(bukkitSender)
            commandTasks.unAssign(
                sender = tmSender,
                ticket = args[0] as Ticket,
                silent = isSilent
            )
        }

        // /ticket close/s.close <ID> [Comment...]
        fun CommandAPICommand.closeIDGeneration() = this
            .withArguments(
                ticketFromIdArg(
                    ::ticketAlreadyClosedError,
                    userDualityError("ticketmanager.command.close")
                ).replaceSuggestions(dualityOpenIDsAsync("ticketmanager.command.close"))
            )
        fun CommandAPICommand.executeCloseNoCommentAsync(isSilent: Boolean) = tmExecuteWithMsgAsync { tmSender, bukkitSender, args ->
            cooldown?.performCommandDuties(bukkitSender)
            commandTasks.closeWithoutComment(
                sender = tmSender,
                ticket = args[0] as Ticket,
                silent = isSilent
            )
        }
        fun CommandAPICommand.executeCloseWithCommentAsync(isSilent: Boolean) = tmExecuteWithMsgAsync { tmSender, bukkitSender, args ->
            cooldown?.performCommandDuties(bukkitSender)
            commandTasks.closeWithComment(
                sender = tmSender,
                ticket = args[0] as Ticket,
                comment = args[1] as String,
                silent = isSilent
            )
        }

    // Actual Commands

        // /ticket assign <ID> <Assignment>
        CommandAPICommand(locale.commandBase)
            .withArguments(LiteralArgument(locale.commandWordAssign)
                .withPermission("ticketmanager.command.assign")
                .withRequirement(::notUnderCooldownReq))
            .assignmentGeneration()
            .executeAssignAsync(false)
            .register()

        // /ticket s.assign <ID> <Assignment>
        CommandAPICommand(locale.commandBase)
            .withArguments(LiteralArgument(locale.commandWordSilentAssign)
                .withPermission("ticketmanager.command.assign")
                .withRequirement(::notUnderCooldownReq)
                .withRequirement(::silentPermissionReq))
            .assignmentGeneration()
            .executeAssignAsync(false)
            .register()

        // /ticket claim <ID>
        CommandAPICommand(locale.commandBase)
            .withArguments(LiteralArgument(locale.commandWordClaim)
                .withPermission("ticketmanager.command.assign")
                .withRequirement(::notUnderCooldownReq))
            .withArguments(ticketFromIdArg(::ticketAlreadyClosedError)
                .replaceSuggestions(openTicketIDsAsync))
            .executeClaimAsync(false)
            .register()

        // /ticket s.claim <ID>
        CommandAPICommand(locale.commandBase)
            .withArguments(LiteralArgument(locale.commandWordSilentClaim)
                .withPermission("ticketmanager.command.assign")
                .withRequirement(::notUnderCooldownReq)
                .withRequirement(::silentPermissionReq))
            .withArguments(ticketFromIdArg(::ticketAlreadyClosedError)
                .replaceSuggestions(openTicketIDsAsync))
            .executeClaimAsync(true)
            .register()

        // /ticket unassign <ID>
        CommandAPICommand(locale.commandBase)
            .withArguments(LiteralArgument(locale.commandWordUnassign)
                .withPermission("ticketmanager.command.assign")
                .withRequirement(::notUnderCooldownReq)
            )
            .withArguments(ticketFromIdArg(::ticketAlreadyClosedError)
                .replaceSuggestions(openTicketIDsAsync))
            .executeUnassignAsync(false)
            .register()

        // /ticket s.unassign <ID>
        CommandAPICommand(locale.commandBase)
            .withArguments(LiteralArgument(locale.commandWordSilentUnassign)
                .withPermission("ticketmanager.command.assign")
                .withRequirement(::notUnderCooldownReq)
                .withRequirement(::silentPermissionReq))
            .withArguments(ticketFromIdArg(::ticketAlreadyClosedError)
                .replaceSuggestions(openTicketIDsAsync))
            .executeUnassignAsync(true)
            .register()

        // /ticket close <ID>       (No Comment)
        CommandAPICommand(locale.commandBase)
            .withArguments(LiteralArgument(locale.commandWordClose)
                .withRequirement { hasOneDualityPermission(it, "ticketmanager.command.close") }
                .withRequirement(::notUnderCooldownReq))
            .closeIDGeneration()
            .executeCloseNoCommentAsync(false)
            .register()

        // /ticket s.close <ID>       (No Comment)
        CommandAPICommand(locale.commandBase)
            .withArguments(LiteralArgument(locale.commandWordClose)
                .withRequirement { hasOneDualityPermission(it, "ticketmanager.command.close") }
                .withRequirement(::notUnderCooldownReq)
                .withRequirement(::silentPermissionReq))
            .closeIDGeneration()
            .executeCloseNoCommentAsync(true)
            .register()

        // /ticket close <ID> <Comment..>
        CommandAPICommand(locale.commandBase)
            .withArguments(LiteralArgument(locale.commandWordClose)
                .withRequirement { hasOneDualityPermission(it, "ticketmanager.command.close") }
                .withRequirement(::notUnderCooldownReq))
            .closeIDGeneration()
            .withArguments(GreedyStringArgument(locale.commandWordComment))
            .executeCloseWithCommentAsync(false)
            .register()

        // /ticket s.close <ID> <Comment...>
        CommandAPICommand(locale.commandBase)
            .withArguments(LiteralArgument(locale.commandWordClose)
                .withRequirement { hasOneDualityPermission(it, "ticketmanager.command.close") }
                .withRequirement(::notUnderCooldownReq)
                .withRequirement(::silentPermissionReq))
            .closeIDGeneration()
            .withArguments(GreedyStringArgument(locale.commandWordComment))
            .executeCloseWithCommentAsync(true)
            .register()


        /*

        */
    }

    private fun BukkitCommandSender.toTMSender(): CommandSender.Active {
        return when (this) {
            is ConsoleCommandSender -> PaperConsole(configState.proxyServerName)
            is BukkitPlayer-> PaperPlayer(this, configState.proxyServerName)
            else -> throw Exception("Unsupported Entity Type!")
        }
    }

    private inline fun CommandAPICommand.tmExecuteWithMsgAsync(
        crossinline msgGenerator: suspend (CommandSender.Active, BukkitCommandSender, Array<out Any>) -> MessageNotification<CommandSender.Active>
    ): CommandAPICommand {
        return this.executes(CommandExecutor { bukkitSender, args ->
            TMCoroutine.launchSupervised { 
                executeNotificationsAsync(platform, configState, bukkitSender.toTMSender()) {
                    msgGenerator(bukkitSender.toTMSender(), bukkitSender, args)
                }
            }
        }, ExecutorType.CONSOLE, ExecutorType.PLAYER)
    }
}

fun BukkitCommandSender.toTicketCreator(): TicketCreator = when (this) {
    is ConsoleCommandSender -> TicketCreator.Console
    is BukkitPlayer -> TicketCreator.User(uniqueId)
    else -> throw Exception("Unsupported Entity Type!")
}

// NOTE: TODO Database MUST be loaded first before allowed to run
// Solution:


// /ticket closeall <Lower ID> <Upper ID>
// /ticket comment <ID> <Comment…>
// /ticket create <Message…>
// /ticket list [Page]
// /ticket listassigned [Page]
// /ticket listunassigned [Page]
// /ticket help
// /ticket history [User] [Page]
// /ticket search <Params>
// /ticket teleport <ID>
// /ticket version
// /ticket view <ID>
// /ticket viewdeep <ID>

// Ones to-do:
// /ticket reload