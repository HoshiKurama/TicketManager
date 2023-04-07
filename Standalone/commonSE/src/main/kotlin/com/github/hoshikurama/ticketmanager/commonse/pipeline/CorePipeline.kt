package com.github.hoshikurama.ticketmanager.commonse.pipeline

import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.common.discord.DiscordConsole
import com.github.hoshikurama.ticketmanager.common.discord.DiscordNoOne
import com.github.hoshikurama.ticketmanager.common.discord.DiscordPlayerOrStr
import com.github.hoshikurama.ticketmanager.common.discord.DiscordTarget
import com.github.hoshikurama.ticketmanager.common.discord.notifications.*
import com.github.hoshikurama.ticketmanager.common.mainPluginVersion
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.TMPlugin
import com.github.hoshikurama.ticketmanager.commonse.apiTesting.Creator
import com.github.hoshikurama.ticketmanager.commonse.apiTesting.Ticket
import com.github.hoshikurama.ticketmanager.commonse.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.commonse.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.commonse.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.commonse.database.Option
import com.github.hoshikurama.ticketmanager.commonse.database.Result
import com.github.hoshikurama.ticketmanager.commonse.database.SearchConstraint
import com.github.hoshikurama.ticketmanager.commonse.misc.*
import com.github.hoshikurama.ticketmanager.commonse.misc.kyoriComponentDSL.buildComponent
import com.github.hoshikurama.ticketmanager.commonse.misc.kyoriComponentDSL.onHover
import com.github.hoshikurama.ticketmanager.commonse.platform.OnlinePlayer
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.platform.Sender
import com.github.hoshikurama.ticketmanager.commonse.ticket.CreatorImpl
import com.github.hoshikurama.ticketmanager.commonse.ticket.TicketImpl
import com.github.hoshikurama.ticketmanager.commonse.ticket.toLocaledWord
import com.github.jasync.sql.db.util.size
import kotlinx.coroutines.delay
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.event.HoverEvent.showText
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import java.util.*

typealias ConsoleObject = CreatorImpl.ConsoleImpl
typealias ConsoleSender = com.github.hoshikurama.ticketmanager.commonse.platform.Console
typealias ResultDB = Result
typealias StandardReturn = Notification?

class CorePipeline(
    private val platform: PlatformFunctions,
    val instanceState: InstancePluginState,
    private val globalState: GlobalPluginState,
) {
    suspend fun executeLogic(sender: Sender, args: List<String>): StandardReturn {

        if (args.isEmpty())
            return executeLogic(sender, listOf(sender.locale.commandWordHelp))

        if (globalState.pluginLocked.get()) {
            sender.sendMessage(sender.locale.warningsLocked)
            return null
        }

        // I'm so sorry future me for writing it this way. Type inference was being terrible
        // (Future Me: I have no clue what you're talking about, but thank you anyways)
        // Future Future Me: I figured out what you meant, past past me. I might have resolved it
        // Future x3 Me: I might be going back to coroutines, so your work is going away
        val ticket = getTicketOrDummyTicketOrNullAsync(args, sender.locale)
        if (ticket == null) {
            sender.sendMessage(sender.locale.warningsInvalidID)
            return null
        }

        if (!hasValidPermission(sender, ticket, args) || !isValidCommand(sender, ticket, args) || !notUnderCooldown(sender, args))
            return null

        return executeCommand(sender, args, ticket)
    }

    private suspend fun getTicketOrDummyTicketOrNullAsync(
        args: List<String>,
        senderLocale: TMLocale,
    ): Ticket? {
        // Grabs Ticket. Only null if ID required but doesn't exist. Filters non-valid tickets
        return when (args[0]) {
            senderLocale.commandWordAssign,
            senderLocale.commandWordSilentAssign,
            senderLocale.commandWordClaim,
            senderLocale.commandWordSilentClaim,
            senderLocale.commandWordClose,
            senderLocale.commandWordSilentClose,
            senderLocale.commandWordComment,
            senderLocale.commandWordSilentComment,
            senderLocale.commandWordReopen,
            senderLocale.commandWordSilentReopen,
            senderLocale.commandWordSetPriority,
            senderLocale.commandWordSilentSetPriority,
            senderLocale.commandWordTeleport,
            senderLocale.commandWordUnassign,
            senderLocale.commandWordSilentUnassign,
            senderLocale.commandWordView,
            senderLocale.commandWordDeepView ->
                args.getOrNull(1)
                    ?.toLongOrNull()
                    ?.let { instanceState.database.getTicketOrNullAsync(it) }
            else -> TicketImpl(creator = CreatorImpl.Dummy)
        }
    }

    private fun hasValidPermission(
        sender: Sender,
        ticket: Ticket,
        args: List<String>,
    ): Boolean {
        if (sender is ConsoleSender || args.isEmpty()) return true
        val player = sender as OnlinePlayer

        fun has(perm: String) = player.has(perm)
        fun hasSilent() = has("ticketmanager.commandArg.silence")
        fun hasWithSilent(perm: String): Boolean = has(perm) && hasSilent()
        fun hasDuality(basePerm: String): Boolean {
            val ownsTicket = ticket.creator == CreatorImpl.UserImpl(player.uniqueID)
            val hasAll = has("$basePerm.all")
            val hasOwn = has("$basePerm.own")
            return hasAll || (hasOwn && ownsTicket)
        }
        fun hasDualityWithSilent(basePerm: String): Boolean = hasDuality(basePerm) && hasSilent()

        val hasPermission = sender.locale.run {
            when (args[0]) {
                commandWordAssign, commandWordClaim, commandWordUnassign -> has("ticketmanager.command.assign")
                commandWordSilentAssign, commandWordSilentClaim,commandWordSilentUnassign -> hasWithSilent("ticketmanager.command.assign")
                commandWordClose -> hasDuality("ticketmanager.command.close")
                commandWordSilentClose -> hasDualityWithSilent("ticketmanager.command.close")
                commandWordCloseAll -> has("ticketmanager.command.closeAll")
                commandWordSilentCloseAll -> hasWithSilent("ticketmanager.command.closeAll")
                commandWordComment -> hasDuality("ticketmanager.command.comment")
                commandWordSilentComment -> hasDualityWithSilent("ticketmanager.command.comment")
                commandWordCreate -> has("ticketmanager.command.create")
                commandWordHelp -> has("ticketmanager.command.help")
                commandWordReload -> has("ticketmanager.command.reload")
                commandWordList, commandWordListAssigned, commandWordListUnassigned -> has("ticketmanager.command.list")
                commandWordReopen -> has("ticketmanager.command.reopen")
                commandWordSilentReopen -> hasWithSilent("ticketmanager.command.reopen")
                commandWordSearch -> has("ticketmanager.command.search")
                commandWordSetPriority -> has("ticketmanager.command.setPriority")
                commandWordSilentSetPriority -> hasWithSilent("ticketmanager.command.setPriority")
                commandWordView -> hasDuality("ticketmanager.command.view")
                commandWordDeepView -> hasDuality("ticketmanager.command.viewdeep")
                commandWordConvertDB -> has("ticketmanager.command.convertDatabase")
                commandWordHistory -> kotlin.run {
                    val hasAll = has("ticketmanager.command.history.all")
                    val hasOwn = has("ticketmanager.command.history.own")

                    hasAll || hasOwn.let { perm ->
                            if (args.size >= 2)
                                perm && args[1] == player.name
                            else perm
                        }
                }
                commandWordTeleport ->
                    if (ticket.actions[0].location.server == instanceState.proxyServerName)
                        has("ticketmanager.command.teleport")
                    else has("ticketmanager.command.proxyteleport")
                else -> true
            }
        }

        return hasPermission.also { if (!it) player.sendMessage(sender.locale.warningsNoPermission) }
    }

    private fun isValidCommand(
        sender: Sender,
        ticket: Ticket,
        args: List<String>,
    ): Boolean {
        val senderLocale = sender.locale
        fun sendMessage(msg: String) = msg.run(sender::sendMessage)
        fun invalidCommand() = sendMessage(senderLocale.warningsInvalidCommand)
        fun notANumber() = sendMessage(senderLocale.warningsInvalidNumber)
        fun outOfBounds() = sendMessage(senderLocale.warningsPriorityOutOfBounds)
        fun ticketClosed() = sendMessage(senderLocale.warningsTicketAlreadyClosed)
        fun ticketOpen() = sendMessage(senderLocale.warningsTicketAlreadyOpen)

        return senderLocale.run {
            when (args[0]) {
                commandWordAssign, commandWordSilentAssign ->
                    check(::invalidCommand) { args.size >= 3 }
                        .thenCheck(::ticketClosed) { ticket.status != Ticket.Status.CLOSED }

                commandWordClaim, commandWordSilentClaim ->
                    check(::invalidCommand) { args.size == 2 }
                        .thenCheck(::ticketClosed) { ticket.status != Ticket.Status.CLOSED }

                commandWordClose, commandWordSilentClose ->
                    check(::invalidCommand) { args.size >= 2 }
                        .thenCheck(::ticketClosed) { ticket.status != Ticket.Status.CLOSED }

                commandWordComment, commandWordSilentComment ->
                    check(::invalidCommand) { args.size >= 3 }
                        .thenCheck(::ticketClosed) { ticket.status != Ticket.Status.CLOSED }

                commandWordCloseAll, commandWordSilentCloseAll ->
                    check(::invalidCommand) { args.size == 3 }
                        .thenCheck(::notANumber) { args[1].toIntOrNull() != null }
                        .thenCheck(::notANumber) { args[2].toIntOrNull() != null }

                commandWordReopen, commandWordSilentReopen ->
                    check(::invalidCommand) { args.size == 2 }
                        .thenCheck(::ticketOpen) { ticket.status != Ticket.Status.OPEN }

                commandWordSetPriority, commandWordSilentSetPriority ->
                    check(::invalidCommand) { args.size == 3 }
                        .thenCheck(::outOfBounds) { args[2].toByteOrNull() != null }
                        .thenCheck(::ticketClosed) { ticket.status != Ticket.Status.CLOSED }

                commandWordUnassign, commandWordSilentUnassign ->
                    check(::invalidCommand) { args.size == 2 }
                        .thenCheck(::ticketClosed) { ticket.status != Ticket.Status.CLOSED }

                commandWordView -> check(::invalidCommand) { args.size == 2 }

                commandWordDeepView -> check(::invalidCommand) { args.size == 2 }

                commandWordTeleport -> check(::invalidCommand) { args.size == 2 }

                commandWordCreate -> check(::invalidCommand) { args.size >= 2 }

                commandWordHistory ->
                    check(::invalidCommand) { args.isNotEmpty() }
                        .thenCheck(::notANumber) { if (args.size >= 3) args[2].toIntOrNull() != null else true}

                commandWordList, commandWordListAssigned, commandWordListUnassigned ->
                    check(::notANumber) { if (args.size == 2) args[1].toIntOrNull() != null else true }

                commandWordSearch -> check(::invalidCommand) { args.size >= 2 }

                commandWordReload -> true
                commandWordVersion -> true
                commandWordHelp -> true

                commandWordConvertDB ->
                    check(::invalidCommand) { args.size == 2 }
                        .thenCheck( { sendMessage(senderLocale.warningsInvalidDBType) },
                            {
                                try { AsyncDatabase.Type.valueOf(args[1]); true }
                                catch (e: Exception) { false }
                            }
                        )
                        .thenCheck( { sendMessage(senderLocale.warningsConvertToSameDBType) } )
                        { instanceState.database.type != AsyncDatabase.Type.valueOf(args[1]) }

                else -> false.also { invalidCommand() }
            }
        }
    }

    private fun notUnderCooldown(
        sender: Sender,
        args: List<String>,
    ): Boolean {
        if (args.isEmpty()) return false
        if (sender is ConsoleSender) return true

        val player = sender as OnlinePlayer
        val underCooldown = when (args[0]) {
            sender.locale.commandWordCreate,
            sender.locale.commandWordComment,
            sender.locale.commandWordSilentComment ->
                instanceState.cooldowns.checkAndSetAsync(player.uniqueID)
            else -> false
        }

        if (underCooldown)
            player.sendMessage(sender.locale.warningsUnderCooldown)
        return !underCooldown
    }

    private suspend fun executeCommand(
        sender: Sender,
        args: List<String>,
        ticket: TicketImpl,
    ): StandardReturn {
        return sender.locale.run {
            when (args[0]) {
                commandWordAssign -> assign(sender, args, false, ticket)
                commandWordSilentAssign -> assign(sender, args, true, ticket)
                commandWordClaim -> claim(sender, args, false, ticket)
                commandWordSilentClaim -> claim(sender, args, true, ticket)
                commandWordClose -> close(sender, args, false, ticket)
                commandWordSilentClose -> close(sender, args, true, ticket)
                commandWordCloseAll -> closeAll(sender, args, false, ticket)
                commandWordSilentCloseAll -> closeAll(sender, args, true, ticket)
                commandWordComment -> comment(sender, args, false, ticket)
                commandWordSilentComment -> comment(sender, args, true, ticket)
                commandWordCreate -> create(sender, args)
                commandWordHelp -> help(sender).let { null }
                commandWordHistory -> history(sender, args).let { null }
                commandWordList -> list(sender, args).let { null }
                commandWordListAssigned -> listAssigned(sender, args).let { null }
                commandWordListUnassigned -> listUnassigned(sender, args).let { null }
                commandWordReload -> reload(sender).let { null }
                commandWordReopen -> reopen(sender,args, false, ticket)
                commandWordSilentReopen -> reopen(sender,args, true, ticket)
                commandWordSearch -> search(sender, args).let { null }
                commandWordSetPriority -> setPriority(sender, args, false, ticket)
                commandWordSilentSetPriority -> setPriority(sender, args, true, ticket)
                commandWordTeleport -> teleport(sender, ticket).let { null }
                commandWordUnassign -> unAssign(sender, args, false, ticket)
                commandWordSilentUnassign -> unAssign(sender, args, true, ticket)
                commandWordVersion -> version(sender).let { null }
                commandWordView -> view(sender, ticket).let { null }
                commandWordDeepView -> viewDeep(sender, ticket).let { null }
                commandWordConvertDB -> convertDatabase(args).let { null }
                else -> null
            }
        }
    }

    /*
    Command Functions
     */
    private fun allAssignVariations(
        sender: Sender,
        silent: Boolean,
        assignmentID: String,
        dbAssignment: String?,
        ticket: Ticket,
    ): StandardReturn {
        // Determines the assignment type
        val assignmentIsConsole = sender.locale.consoleName == dbAssignment
        val assignmentIsNobody = dbAssignment == null || sender.locale.miscNobody == dbAssignment
        val shownAssignment = when {
            assignmentIsConsole -> DiscordConsole(instanceState.localeHandler.consoleLocale)
            assignmentIsNobody -> DiscordNoOne(instanceState.localeHandler.consoleLocale)
            else -> DiscordPlayerOrStr(assignmentID)
        }

        TMCoroutine.runAsync { instanceState.database.setAssignment(ticket.id, dbAssignment) }
        TMCoroutine.runAsync {
            instanceState.database.insertAction(
                id = ticket.id,
                action = TicketImpl.ActionImpl(TicketImpl.ActionImpl.ASSIGNImpl(dbAssignment), sender.toCreator(), sender.getLocAsTicketLoc())
            )
        }

        //Discord (Async on coroutine)
        attemptDiscordMessageIfEnabledAsync(
            silent = silent,
            sender = sender,
            hasPermission = instanceState.discordSettings.notifyOnAssign,
            buildNotification = { user ->
                val discordShownAssignment = when {
                    assignmentIsConsole -> DiscordConsole(instanceState.localeHandler.consoleLocale)
                    assignmentIsNobody -> DiscordNoOne(instanceState.localeHandler.consoleLocale)
                    else -> DiscordPlayerOrStr(assignmentID)
                }
                Assign(user, ticket.id.toString(), discordShownAssignment)
            }
        )

        return Notification.Assign.build(
            silent = silent,
            ticket = ticket,
            sender = sender,
            argID = assignmentID,
            argAssigned = shownAssignment.name,
            argUser = sender.name,
            argAssignedIsConsole = assignmentIsConsole,
            argAssignedIsNobody = assignmentIsNobody,
            argUserIsConsole = sender is ConsoleSender,
        )
    }

    // /ticket assign <ID> <Assignment>
    private fun assign(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticket: Ticket,
    ): StandardReturn {
        val sqlAssignment = args.subList(2, args.size).joinToString(" ")
        return allAssignVariations(sender, silent, args[1], sqlAssignment, ticket)
    }

    // /ticket claim <ID>
    private fun claim(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticket: Ticket,
    ): StandardReturn {
        return allAssignVariations(sender, silent, args[1], sender.name, ticket)
    }

    // /ticket close <ID> [Comment...]
    private fun close(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticket: Ticket,
    ): StandardReturn {

        val newCreatorStatusUpdate = (ticket.creator != sender.toCreator()) && instanceState.allowUnreadTicketUpdates
        if (newCreatorStatusUpdate != ticket.creatorStatusUpdate)
            TMCoroutine.runAsync {
                instanceState.database.setCreatorStatusUpdate(ticket.id, newCreatorStatusUpdate)
            }

        return if (args.size >= 3) closeWithComment(sender, args, silent, ticket)
                else closeWithoutComment(sender, args, silent, ticket)
    }

    private fun closeWithComment(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticket: Ticket,
    ): StandardReturn {

        val message = args.subList(2, args.size)
            .joinToString(" ")

        // Discord (Async on new coroutine)
        attemptDiscordMessageIfEnabledAsync(
            silent = silent,
            sender = sender,
            hasPermission = instanceState.discordSettings.notifyOnClose,
            buildNotification = { user -> Close(user, ticket.id.toString(), message) }
        )

        instanceState.database.run {
            TMCoroutine.runAsync {
                insertAction(
                    id = ticket.id,
                    action = TicketImpl.ActionImpl(TicketImpl.ActionImpl.COMMENTImpl(message), sender.toCreator(), sender.getLocAsTicketLoc())
                )
                insertAction(
                    id = ticket.id,
                    action = TicketImpl.ActionImpl(TicketImpl.ActionImpl.CLOSEImpl, sender.toCreator(), sender.getLocAsTicketLoc())
                )
                instanceState.database.setStatus(ticket.id, Ticket.Status.CLOSED)
            }
        }

        return Notification.CloseWithComment.build(
            silent = silent,
            ticket = ticket,
            sender = sender,
            argID = args[1],
            argUser = sender.name,
            argMessage = message,
            argUserIsConsole = sender is ConsoleSender
        )
    }

    private fun closeWithoutComment(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticket: Ticket,
    ): StandardReturn {

        TMCoroutine.runAsync {
            instanceState.database.insertAction(
                id = ticket.id,
                action = TicketImpl.ActionImpl(TicketImpl.ActionImpl.CLOSEImpl, sender.toCreator(), sender.getLocAsTicketLoc())
            )
            instanceState.database.setStatus(ticket.id, Ticket.Status.CLOSED)
        }

        // Discord (async in new coroutine)
        attemptDiscordMessageIfEnabledAsync(
            silent = silent,
            sender = sender,
            hasPermission = instanceState.discordSettings.notifyOnClose,
            buildNotification = { user -> Close(user, ticket.id.toString(), null) }
        )

        return Notification.CloseWithoutComment.build(
            silent = silent,
            ticket = ticket,
            sender = sender,
            argID = args[1],
            argUser = sender.name,
            argUserIsConsole = sender is ConsoleSender
        )
    }

    // /ticket closeall <Lower ID> <Upper ID>
    private fun closeAll(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticket: Ticket
    ): StandardReturn {
        val lowerBound = args[1].toLong()
        val upperBound = args[2].toLong()

        TMCoroutine.runAsync { instanceState.database.massCloseTickets(lowerBound, upperBound, sender.toCreator(), sender.getLocAsTicketLoc()) }

        // Discord
        attemptDiscordMessageIfEnabledAsync(
            silent = silent,
            sender = sender,
            hasPermission = instanceState.discordSettings.notifyOnCloseAll,
            buildNotification = { user -> CloseAll(user, "$lowerBound", "$upperBound") }
        )

        return Notification.MassClose.build(
            silent = silent,
            ticket = ticket,
            sender = sender,
            argLower = args[1],
            argUpper = args[2],
            argUser = sender.name,
            argUserIsConsole = sender is ConsoleSender,
        )
    }

    // /ticket comment <ID> <Comment…>
    private fun comment(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticket: Ticket,
    ): StandardReturn {
        val message = args.subList(2, args.size)
            .joinToString(" ")

        val newCreatorStatusUpdate = (ticket.creator != sender.toCreator()) && instanceState.allowUnreadTicketUpdates
        if (newCreatorStatusUpdate != ticket.creatorStatusUpdate)
            TMCoroutine.runAsync { instanceState.database.setCreatorStatusUpdate(ticket.id, newCreatorStatusUpdate) }

        TMCoroutine.runAsync {
            instanceState.database.insertAction(
                id = ticket.id,
                action = TicketImpl.ActionImpl(TicketImpl.ActionImpl.COMMENTImpl(message), sender.toCreator(), sender.getLocAsTicketLoc())
            )
        }

        // Discord (async in new coroutine)
        attemptDiscordMessageIfEnabledAsync(
            silent = silent,
            sender = sender,
            hasPermission = instanceState.discordSettings.notifyOnComment,
            buildNotification = { user -> Comment(user, ticket.id.toString(), message) }
        )

        return Notification.Comment.build(
            silent = silent,
            ticket = ticket,
            sender = sender,
            argID = args[1],
            argUser = sender.name,
            argMessage = message,
            argUserIsConsole = sender is ConsoleSender
        )
    }

    // /ticket convertdatabase <Target Database>
    private suspend fun convertDatabase(args: List<String>) {
        val type = args[1].run(AsyncDatabase.Type::valueOf)

        instanceState.database.migrateDatabase(
            to = type,
            databaseBuilders = instanceState.databaseBuilders,
            onBegin = {
                globalState.pluginLocked.set(true)
                platform.massNotify(instanceState.localeHandler, "ticketmanager.notify.info") {
                    it.informationDBConvertInit.parseMiniMessage(
                        "FromDB" templated instanceState.database.type.name,
                        "ToDB" templated type.name
                    )
                }
            },
            onComplete = {
                globalState.pluginLocked.set(false)
                platform.massNotify(instanceState.localeHandler,"ticketmanager.notify.info") {
                    it.informationDBConvertSuccess.parseMiniMessage()
                }
            },
            onError = {
                globalState.pluginLocked.set(false)
                pushErrors(platform, instanceState, it, TMLocale::consoleErrorDBConversion)
            }
        )
    }

    // /ticket create <Message…>
    private suspend fun create(
        sender: Sender,
        args: List<String>,
    ): StandardReturn {
        val message = args.subList(1, args.size)
            .joinToString(" ")

        val ticket = when (sender) {
            is OnlinePlayer -> TicketImpl(creator = CreatorImpl.UserImpl(sender.uniqueID))
            is ConsoleSender -> TicketImpl(creator = ConsoleObject)
        }
            .let { it + listOf(TicketImpl.ActionImpl(type = TicketImpl.ActionImpl.OPENImpl(message), user = sender.toCreator(), sender.getLocAsTicketLoc())) }


        // Inserts ticket and receives ID
        val id = instanceState.database.insertTicketAsync(ticket)
        globalState.ticketCountMetrics.getAndIncrement()

        //Discord (async in new coroutine)
        attemptDiscordMessageIfEnabledAsync(
            silent = false,
            sender = sender,
            hasPermission = instanceState.discordSettings.notifyOnCreate,
            buildNotification = { user -> Create(user, id.toString(), message) }
        )

        return Notification.Create.build(
            silent = false,
            ticket = ticket,
            sender = sender,
            argID = id.toString(),
            argUser = sender.name,
            argMessage = message,
            argUserIsConsole = sender is ConsoleSender
        )
    }

    private fun help(sender: Sender) {

        open class CommandArg(val content: String)
        class RequiredArg(content: String) : CommandArg(content)
        class OptionalArg(content: String) : CommandArg(content)
        class Command(
            val silenceable: Boolean,
            val command: String,
            val arguments: List<CommandArg>,
            val permissions: List<String>,
            val explanation: String,
        )

        // Builds command node entries
        val commandNodes = sender.locale.run {
            listOf(
                Command(
                    silenceable = true,
                    command = commandWordAssign,
                    arguments = listOf(RequiredArg(parameterID), RequiredArg("$parameterAssignment...")),
                    permissions = listOf("ticketmanager.command.assign"),
                    explanation = helpExplanationAssign,
                ),
                Command(
                    silenceable = true,
                    command = commandWordClaim,
                    arguments = listOf(RequiredArg(parameterID)),
                    permissions = listOf("ticketmanager.command.claim"),
                    explanation = helpExplanationClaim,
                ),
                Command(
                    silenceable = true,
                    command = commandWordClose,
                    arguments = listOf(RequiredArg(parameterID), OptionalArg("$parameterComment...")),
                    permissions = listOf("ticketmanager.command.close.all", "ticketmanager.command.close.own"),
                    explanation = helpExplanationClose,
                ),
                Command(
                    silenceable = true,
                    command = commandWordCloseAll,
                    arguments = listOf(RequiredArg(parameterLowerID), RequiredArg(parameterUpperID)),
                    permissions = listOf("ticketmanager.command.closeAll"),
                    explanation = helpExplanationCloseAll,
                ),
                Command(
                    silenceable = true,
                    command = commandWordComment,
                    arguments = listOf(RequiredArg(parameterID), RequiredArg("$parameterComment...")),
                    permissions = listOf("ticketmanager.command.comment.all", "ticketmanager.command.comment.own"),
                    explanation = helpExplanationComment,
                ),
                Command(
                    silenceable = false,
                    command = commandWordConvertDB,
                    arguments = listOf(RequiredArg(parameterTargetDB)),
                    permissions = listOf("ticketmanager.command.convertDatabase"),
                    explanation = helpExplanationConvertDatabase,
                ),
                Command(
                    silenceable = false,
                    command = commandWordCreate,
                    arguments = listOf(RequiredArg("$parameterComment...")),
                    permissions = listOf("ticketmanager.command.create"),
                    explanation = helpExplanationCreate,
                ),
                Command(
                    silenceable = false,
                    command = commandWordHelp,
                    arguments = listOf(),
                    permissions = listOf("ticketmanager.command.help"),
                    explanation = helpExplanationHelp,
                ),
                Command(
                    silenceable = false,
                    command = commandWordHistory,
                    arguments = listOf(OptionalArg(parameterUser), OptionalArg(parameterPage)),
                    permissions = listOf("ticketmanager.command.history.all", "ticketmanager.command.history.own"),
                    explanation = helpExplanationHistory,
                ),
                Command(
                    silenceable = false,
                    command = commandWordList,
                    arguments = listOf(OptionalArg(parameterPage)),
                    permissions = listOf("ticketmanager.command.list"),
                    explanation = helpExplanationList,
                ),
                Command(
                    silenceable = false,
                    command = commandWordListAssigned,
                    arguments = listOf(OptionalArg(parameterPage)),
                    permissions = listOf("ticketmanager.command.list"),
                    explanation = helpExplanationListAssigned,
                ),
                Command(
                    silenceable = false,
                    command = commandWordListUnassigned,
                    arguments = listOf(OptionalArg(parameterPage)),
                    permissions = listOf("ticketmanager.command.list"),
                    explanation = helpExplanationListUnassigned,
                ),
                Command(
                    silenceable = false,
                    command = commandWordReload,
                    arguments = listOf(),
                    permissions = listOf("ticketmanager.command.reload"),
                    explanation = helpExplanationReload,
                ),
                Command(
                    silenceable = true,
                    command = commandWordReopen,
                    arguments = listOf(RequiredArg(parameterID)),
                    permissions = listOf("ticketmanager.command.reopen"),
                    explanation = helpExplanationReopen,
                ),
                Command(
                    silenceable = false,
                    command = commandWordSearch,
                    arguments = listOf(RequiredArg("$parameterConstraints...")),
                    permissions = listOf("ticketmanager.command.search"),
                    explanation = helpExplanationSearch,
                ),
                Command(
                    silenceable = true,
                    command = commandWordSetPriority,
                    arguments = listOf(RequiredArg(parameterID), RequiredArg(parameterLevel)),
                    permissions = listOf("ticketmanager.command.setPriority"),
                    explanation = helpExplanationSetPriority,
                ),
                Command(
                    silenceable = false,
                    command = commandWordTeleport,
                    arguments = listOf(RequiredArg(parameterID)),
                    permissions = listOf("ticketmanager.command.teleport"),
                    explanation = helpExplanationTeleport,
                ),
                Command(
                    silenceable = true,
                    command = commandWordUnassign,
                    arguments = listOf(RequiredArg(parameterID)),
                    permissions = listOf("ticketmanager.command.assign"),
                    explanation = helpExplanationUnassign,
                ),
                Command(
                    silenceable = true,
                    command = commandWordView,
                    arguments = listOf(RequiredArg(parameterID)),
                    permissions = listOf("ticketmanager.command.view.all", "ticketmanager.command.view.own"),
                    explanation = helpExplanationView,
                ),
                Command(
                    silenceable = false,
                    command = commandWordDeepView,
                    arguments = listOf(RequiredArg(parameterID)),
                    permissions = listOf("ticketmanager.command.viewdeep.all", "ticketmanager.command.viewdeep.own"),
                    explanation = helpExplanationDeepView,
                ),
            )
        }
            .asSequence()
            .filter { it.permissions.any(sender::has) }

        val hasSilentPerm = sender.has("ticketmanager.commandArg.silence")
        val component = buildComponent {
            sender.locale.run {
                // Builds header
                append(helpHeader.parseMiniMessage())
                append(helpLine1.parseMiniMessage())

                if (hasSilentPerm)
                    listOf(helpLine2, helpLine3)
                        .map(String::parseMiniMessage)
                        .forEach(this@buildComponent::append)
                append(helpSep.parseMiniMessage())

                commandNodes.map {
                    // Builds params into one Component
                    val argsComponent = it.arguments
                        .mapNotNull { arg ->
                            when (arg) {
                                is RequiredArg -> helpRequiredParam.replace("%Param%", arg.content)
                                is OptionalArg -> helpOptionalParam.replace("%Param%", arg.content)
                                else -> null
                            }
                        }
                        .joinToString(" ")
                        .parseMiniMessage()

                    val silenceableComponent =
                        if (hasSilentPerm)
                            if (it.silenceable) helpHasSilence.parseMiniMessage()
                            else helpLackSilence.parseMiniMessage()
                        else Component.text("")

                    helpEntry.parseMiniMessage(
                        "silenceable" templated silenceableComponent,
                        "command" templated "${sender.locale.commandBase} ${it.command}",
                        "params" templated argsComponent,
                    ).append(it.explanation.parseMiniMessage())
                }
                    .reduce(Component::append)
                    .let(this@buildComponent::append)
            }
        }

        sender.sendMessage(component)
    }

    // /ticket history [User] [Page]
    private suspend fun history(
        sender: Sender,
        args: List<String>,
    ) {
        val locale = sender.locale

        val targetName = if (args.size >= 2) args[1].takeIf { it != locale.consoleName } else sender.name.takeIf { sender is OnlinePlayer }
        val requestedPage = if (args.size >= 3) args[2].toInt() else 1

        val searchedUser: Creator = targetName?.run { platform.offlinePlayerNameToUUIDOrNull(this)?.let(CreatorImpl::UserImpl)
            ?: CreatorImpl.UserImpl(UUID.randomUUID()) } ?: ConsoleObject //NOTE: Does this need to account for multi-servers?
        val constraints = SearchConstraint(creator = Option(searchedUser))

        // Search
        val (results, pageCount, resultCount, returnedPage) = instanceState.database
            .searchDatabaseAsync(constraints, requestedPage, 9)

        // Component Builder...
        val sentComponent = buildComponent {
            // Header
            locale.historyHeader.parseMiniMessage(
                "name" templated (targetName ?: locale.consoleName),
                "count" templated "$resultCount"
            ).let(this::append)

            if (results.isNotEmpty()) {
                results.forEach { t ->
                    val id = "${t.id}"
                    val status = t.status.toLocaledWord(locale)
                    val comment = trimCommentToSize(
                        comment = (t.actions[0] as Ticket.Action.Type.OPEN).message,
                        preSize = id.size + status.size + locale.historyFormattingSize,
                        maxSize = locale.historyMaxLineSize
                    )

                    val entry = locale.historyEntry
                        .replace("%SCC%", statusToHexColour(t.status, sender.locale))
                        .parseMiniMessage(
                            "id" templated id,
                            "status" templated status,
                            "comment" templated comment,
                        )

                    // Adds click/hover events and appends
                    entry.hoverEvent(showText(Component.text(locale.clickViewTicket)))
                        .clickEvent(ClickEvent.runCommand(locale.run { "/$commandBase $commandWordView ${t.id}" } ))
                        .let(this::append)
                }

                if (pageCount > 1) {
                    append(buildPageComponent(returnedPage, pageCount, locale) {
                        "/${it.commandBase} ${it.commandWordHistory} ${targetName ?: it.consoleName} "
                    })
                }
            }
        }

        sender.sendMessage(sentComponent)
    }

    // /ticket list [Page]
    private suspend fun list(
        sender: Sender,
        args: List<String>,
    ) {
        val page = if (args.size == 2) args[1].toIntOrNull() ?: 1 else 1

        val tickets = instanceState.database.getOpenTicketsAsync(page, 8)
        createGeneralList(sender.locale, sender.locale.listHeader, tickets) { sender.locale.run { "/$commandBase $commandWordList " } }
            .run(sender::sendMessage)
    }

    // /ticket listassigned [Page]
    private suspend fun listAssigned(
        sender: Sender,
        args: List<String>,
    ) {
        val page = if (args.size == 2) args[1].toIntOrNull() ?: 1 else 1
        val groups: List<String> = if (sender is OnlinePlayer) sender.permissionGroups else listOf()

        val tickets = instanceState.database.getOpenTicketsAssignedToAsync(page,8, sender.name, groups)
        createGeneralList(sender.locale, sender.locale.listAssignedHeader, tickets) { sender.locale.run { "/$commandBase $commandWordListAssigned " } }
            .run(sender::sendMessage)
    }

    // /ticket listunassigned [Page]
    private suspend fun listUnassigned(
        sender: Sender,
        args: List<String>,
    ) {
        val page = if (args.size == 2) args[1].toIntOrNull() ?: 1 else 1
        val tickets = instanceState.database.getOpenTicketsNotAssignedAsync(page, 8)

        createGeneralList(sender.locale, sender.locale.listUnassignedHeader, tickets) { sender.locale.run { "/$commandBase $commandWordListUnassigned " } }
            .run(sender::sendMessage)
    }

    // /ticket reload
    private suspend fun reload(
        sender: Sender
    ) {
        try {
            // Lock Plugin
            globalState.pluginLocked.set(true)

            // Announce Intentions
            platform.massNotify(instanceState.localeHandler, "ticketmanager.notify.info") {
                it.informationReloadInitiated.parseMiniMessage("user" templated sender.name)
            }

            // Give time for things to complete
            TMCoroutine.run {
                var counter = 0

                while (activeJobCount != 0) {

                    if (counter > 29) {
                        cancelTasks("User ${sender.name} requested a plugin restart and 1+ tasks is taking too long")
                        platform.massNotify(instanceState.localeHandler, "ticketmanager.notify.warning") {
                            it.warningsLongTaskDuringReload.parseMiniMessage()
                        }
                    }

                    delay(1000)
                    counter++
                }
            }

            // Closed...
            platform.massNotify(instanceState.localeHandler, "ticketmanager.notify.info") {
                it.informationReloadTasksDone.parseMiniMessage()
            }

            instanceState.database.closeDatabase()
            TMCoroutine.beginNewScope()

            // I REALLY HATE THIS STATIC VARIABLE, BUT IT WILL WORK FOR NOW SINCE I ONLY USE IT HERE
            TMPlugin.activeInstance.unregisterProcesses()
            TMPlugin.activeInstance.initializeData() // Also re-registers things

            // Notifications
            platform.massNotify(instanceState.localeHandler, "ticketmanager.notify.info") {
                it.informationReloadSuccess.parseMiniMessage()
            }

            if (!sender.has("ticketmanager.notify.info"))
                sender.sendMessage(sender.locale.informationReloadSuccess)

        } catch (e: Exception) {
            platform.massNotify(instanceState.localeHandler, "ticketmanager.notify.info") {
                it.informationReloadFailure.parseMiniMessage()
            }
            throw e
        } finally { globalState.pluginLocked.set(false) }
    }

    // /ticket reopen <ID>
    private fun reopen(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticket: Ticket,
    ): StandardReturn {
        val action = TicketImpl.ActionImpl(TicketImpl.ActionImpl.REOPENImpl, sender.toCreator(), sender.getLocAsTicketLoc())

        // Updates user status if needed
        val newCreatorStatusUpdate = (ticket.creator != sender.toCreator()) && instanceState.allowUnreadTicketUpdates
        if (newCreatorStatusUpdate != ticket.creatorStatusUpdate) {
            TMCoroutine.runAsync { instanceState.database.setCreatorStatusUpdate(ticket.id, newCreatorStatusUpdate) }
        }

        TMCoroutine.runAsync {
            instanceState.database.insertAction(ticket.id, action)
            instanceState.database.setStatus(ticket.id, Ticket.Status.OPEN)
        }

        // Discord (async in new coroutine)
        attemptDiscordMessageIfEnabledAsync(
            silent = silent,
            sender = sender,
            hasPermission = instanceState.discordSettings.notifyOnReopen,
            buildNotification = { user -> Reopen(user, ticket.id.toString()) }
        )

        return Notification.Reopen.build(
            silent = silent,
            ticket = ticket,
            sender = sender,
            argID = args[1],
            argUser = sender.name,
            argUserIsConsole = sender is ConsoleSender
        )
    }

    // /ticket search <Params>
    private suspend fun search(
        sender: Sender,
        args: List<String>,
    ) {
        val locale = sender.locale

        // Beginning of execution
        sender.sendMessage(locale.searchQuerying.parseMiniMessage())

        fun attemptNameToCreator(name: String): Option<Creator> {
            return if (name == locale.consoleName) Option(ConsoleObject)
            else platform.offlinePlayerNameToUUIDOrNull(name)?.let { Option(CreatorImpl.UserImpl(it)) } ?: Option(CreatorImpl.InvalidUUID)
        }

        // Input args mapped to valid search types
        val arguments = args.subList(1, args.size)
            .asSequence()
            .map { it.split(":", limit = 2) }
            .filter { it.size >= 2 }
            .associate { it[0] to it[1] }

        var attemptedPage = 1
        val constraints = SearchConstraint()
        arguments.forEach { (key, value) ->
            when (key) {
                locale.searchAssigned -> constraints.assigned = Option(value.takeIf { it != locale.consoleName })
                locale.searchCreator -> constraints.creator = attemptNameToCreator(value)
                locale.searchPriority -> constraints.priority = value.toByteOrNull()?.run(::byteToPriority)?.let { Option(it) }
                locale.searchStatus -> constraints.status = stringToStatusOrNull(value)?.let { Option(it) }
                locale.searchClosedBy -> constraints.closedBy = attemptNameToCreator(value)
                locale.searchLastClosedBy -> constraints.lastClosedBy = attemptNameToCreator(value)
                locale.searchWorld -> constraints.world = Option(value)
                locale.searchTime -> constraints.creationTime = Option(relTimeToEpochSecond(value, locale))
                locale.searchKeywords -> constraints.keywords = Option(value.split(','))
                locale.searchPage -> value.toIntOrNull()?.also { attemptedPage = it }
            }
        }

        // Results calculation and destructuring
        val (results, pageCount, resultCount, returnedPage) =
            instanceState.database.searchDatabaseAsync(constraints, attemptedPage, 9)

        // Component Builder...
        val sentComponent = buildComponent {
            // Initial header
            append(locale.searchHeader.parseMiniMessage("size" templated "$resultCount"))

            // Adds entries
            if (results.isNotEmpty()) {
                results.forEach {
                    val time = it.actions[0].timestamp.toLargestRelativeTime(locale)
                    val comment = trimCommentToSize(
                        comment = (it.actions[0] as Ticket.Action.Type.OPEN).message,
                        preSize = locale.searchFormattingSize + time.length,
                        maxSize = locale.searchMaxLineSize,
                    )

                    locale.searchEntry
                        .replace("%PCC%", priorityToHexColour(it.priority, locale))
                        .replace("%SCC%", statusToHexColour(it.status, locale))
                        .parseMiniMessage(
                            "id" templated "${it.id}",
                            "status" templated it.status.toLocaledWord(locale),
                            "creator" templated (it.creator.run { if (this is Creator.User) uuid else null }?.run(platform::nameFromUUID) ?: locale.consoleName),
                            "assignment" templated (it.assignedTo ?: ""),
                            "world" templated (it.actions[0].location.world ?: ""),
                            "time" templated time,
                            "comment" templated comment,
                        )
                        .hoverEvent(showText(Component.text(locale.clickViewTicket)))
                        .clickEvent(ClickEvent.runCommand(locale.run { "/$commandBase $commandWordView ${it.id}" }))
                        .let(this::append)
                }

                // Implement pages if needed
                if (pageCount > 1) {
                    buildPageComponent(returnedPage, pageCount, locale) {
                        // Removes page constraint and converts rest to key:arg
                        val constraintArgs = arguments
                            .filter { it.key != locale.searchPage }
                            .map { (k, v) -> "$k:$v" }
                            .joinToString(" ")
                        "/${locale.commandBase} ${locale.commandWordSearch} $constraintArgs ${locale.searchPage}:"
                    }.let(this::append)
                }
            }
        }
        sender.sendMessage(sentComponent)
    }

    // /ticket setpriority <ID> <Level>
    private fun setPriority(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticket: Ticket,
    ): StandardReturn {

        val newPriority = byteToPriority(args[2].toByte())

        TMCoroutine.runAsync {
            instanceState.database.insertAction(
                id = ticket.id,
                action = TicketImpl.ActionImpl(TicketImpl.ActionImpl.SETPRIORITYImpl(byteToPriority(args[2].toByteOrNull() ?: 3)), sender.toCreator(), sender.getLocAsTicketLoc())
            )
            instanceState.database.setPriority(ticket.id, newPriority)
        }

        // Discord (async on new coroutine)
        attemptDiscordMessageIfEnabledAsync(
            silent = silent,
            sender = sender,
            hasPermission = instanceState.discordSettings.notifyOnPriorityChange,
            buildNotification = { user -> ChangePriority(user, ticket.id.toString(), newPriority.level.toInt()) }
        )
        return Notification.SetPriority.build(
            silent = silent,
            ticket = ticket,
            sender = sender,
            argUser = sender.name,
            argID = args[1],
            argPriority = newPriority,
            argUserIsConsole = sender is ConsoleSender
        )
    }

    private fun teleport(
        sender: Sender,
        ticket: Ticket,
    ) {
        val location = ticket.actions[0].location
        if (sender !is OnlinePlayer || location.world == null) return
        // Sender is player and location exists...

        if (location.server == instanceState.proxyServerName)
            platform.teleportToTicketLocSameServer(sender, location)
        else if (location.server != null)
            platform.teleportToTicketLocDiffServer(sender, location)
        // Else don't teleport
    }

    // /ticket unassign <ID>
    private fun unAssign(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticket: Ticket,
    ): StandardReturn {
        return allAssignVariations(sender, silent, args[1], null, ticket)
    }

    // /ticket version
    private fun version(
        sender: Sender,
    ) {
        val sentComponent = buildComponent {
            text {
                content("===========================\n")
                color(NamedTextColor.DARK_AQUA)
            }
            text {
                content("TicketManager:")
                decorate(TextDecoration.BOLD)
                color(NamedTextColor.DARK_AQUA)
                append(Component.text(" by HoshiKurama\n", NamedTextColor.GRAY))
            }
            text {
                content("      GitHub Wiki: ")
                color(NamedTextColor.DARK_AQUA)
            }
            text {
                content("HERE\n")
                color(NamedTextColor.GRAY)
                decorate(TextDecoration.UNDERLINED)
                clickEvent(ClickEvent.openUrl(sender.locale.wikiLink))
                onHover { showText(Component.text(sender.locale.clickWiki)) }
            }
            text {
                content("           V$mainPluginVersion\n")
                color(NamedTextColor.DARK_AQUA)
            }
            text {
                content("===========================")
                color(NamedTextColor.DARK_AQUA)
            }
        }

        sender.sendMessage(sentComponent)
    }

    // /ticket view <ID>
    private fun view(
        sender: Sender,
        ticket: Ticket,
    ) {
        val baseComponent = buildTicketInfoComponent(ticket, sender.locale)

        val newCreatorStatusUpdate = (ticket.creator != sender.toCreator()) && instanceState.allowUnreadTicketUpdates
        if (newCreatorStatusUpdate != ticket.creatorStatusUpdate)
            TMCoroutine.runAsync { instanceState.database.setCreatorStatusUpdate(ticket.id, false) }

        val entries = ticket.actions.asSequence()
            .filter { it.type is Ticket.Action.Type.COMMENT || it.type is Ticket.Action.Type.OPEN }
            .map {
                sender.locale.viewComment.parseMiniMessage(
                    "user" templated (it.user.run { if (this is Creator.User) uuid else null }?.run(platform::nameFromUUID) ?: sender.locale.consoleName),
                    "comment" templated when (it.type) {
                        is TicketImpl.ActionImpl.COMMENTImpl -> (it.type as TicketImpl.ActionImpl.COMMENTImpl).comment
                        is Ticket.Action.Type.OPEN -> (it.type as Ticket.Action.Type.OPEN).message
                        else -> ""
                    }
                )
            }
            .reduce(Component::append)

        sender.sendMessage(baseComponent.append(entries))
    }

    // /ticket viewdeep <ID>
    private fun viewDeep(
        sender: Sender,
        ticket: TicketImpl,
    ) {
        val baseComponent = buildTicketInfoComponent(ticket, sender.locale)

        val newCreatorStatusUpdate = (ticket.creator != sender.toCreator()) && instanceState.allowUnreadTicketUpdates
        if (newCreatorStatusUpdate != ticket.creatorStatusUpdate)
            TMCoroutine.runAsync { instanceState.database.setCreatorStatusUpdate(ticket.id, false) }

        fun formatDeepAction(action: TicketImpl.ActionImpl): Component {
            val templatedUser = "user" templated (action.user.run { if (this is Creator.User) uuid else null }?.run(platform::nameFromUUID) ?: sender.locale.consoleName)
            val templatedTime = "time" templated action.timestamp.toLargestRelativeTime(sender.locale)

            return when (action.type) {
                is Ticket.Action.Type.OPEN, is Ticket.Action.Type.COMMENT -> kotlin.run {
                    val message = if (action.type is Ticket.Action.Type.OPEN) (action.type as Ticket.Action.Type.OPEN).message else (action.type as Ticket.Action.Type.COMMENT).comment
                    sender.locale.viewDeepComment.parseMiniMessage(templatedUser, templatedTime, "comment" templated message)
                }

                is Ticket.Action.Type.SET_PRIORITY ->
                    byteToPriority(action.type.priority.level)
                        .let { it to sender.locale.viewDeepSetPriority.replace("%PCC%", priorityToHexColour(it, sender.locale)) }
                        .let { (priority, string) -> string.parseMiniMessage(templatedUser,templatedTime, "priority" templated priority.toLocaledWord(sender.locale)) }

                is Ticket.Action.Type.ASSIGN ->
                    sender.locale.viewDeepAssigned.parseMiniMessage(templatedUser, templatedTime, "assignment" templated (action.type.assignment ?: ""))

                is Ticket.Action.Type.REOPEN -> sender.locale.viewDeepReopen.parseMiniMessage(templatedUser, templatedTime)
                is Ticket.Action.Type.CLOSE -> sender.locale.viewDeepClose.parseMiniMessage(templatedUser, templatedTime)
                is Ticket.Action.Type.MASS_CLOSE -> sender.locale.viewDeepMassClose.parseMiniMessage(templatedUser, templatedTime)
            }
        }

        ticket.actions.asSequence()
            .map(::formatDeepAction)
            .reduce(Component::append)
            .run(baseComponent::append)
            .run(sender::sendMessage)
    }

    private fun buildPageComponent(
        curPage: Int,
        pageCount: Int,
        locale: TMLocale,
        baseCommand: (TMLocale) -> String,
    ): Component {

        fun Component.addForward(): Component {
            return clickEvent(ClickEvent.runCommand(baseCommand(locale) + "${curPage + 1}"))
                .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text(locale.clickNextPage)))
        }

        fun Component.addBack(): Component {
            return clickEvent(ClickEvent.runCommand(baseCommand(locale) + "${curPage - 1}"))
                .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text(locale.clickBackPage)))
        }

        val back: Component
        val next: Component

        when (curPage) {
            1 -> {
                back = locale.pageInactiveBack.parseMiniMessage()
                next = locale.pageActiveNext.parseMiniMessage().addForward()
            }
            pageCount -> {
                back = locale.pageActiveBack.parseMiniMessage().addBack()
                next = locale.pageInactiveNext.parseMiniMessage()
            }
            else -> {
                back = locale.pageActiveBack.parseMiniMessage().addBack()
                next = locale.pageActiveNext.parseMiniMessage().addForward()
            }
        }

        return Component.text("\n") + locale.pageFormat.parseMiniMessage(
            "back_button" templated back,
            "cur_page" templated "$curPage",
            "max_pages" templated "$pageCount",
            "next_button" templated next,
        )
    }

    private fun createListEntry(
        ticket: Ticket,
        locale: TMLocale
    ): Component {
        val id = "${ticket.id}"
        val creator = ticket.creator.run { if (this is Creator.User) uuid else null }?.run(platform::nameFromUUID) ?: locale.consoleName
        val fixedAssign = ticket.assignedTo ?: ""
        val pcc = priorityToHexColour(ticket.priority, locale)

        val fixedComment = trimCommentToSize(
            comment = (ticket.actions[0] as Ticket.Action.Type.OPEN).message,
            preSize = locale.listFormattingSize + id.size + creator.size + fixedAssign.size,
            maxSize = 58,
        )

        return locale.listEntry.replace("%PCC%", pcc)
            .parseMiniMessage(
                "id" templated id,
                "creator" templated creator,
                "assignment" templated fixedAssign,
                "comment" templated fixedComment,
            )
            .hoverEvent(showText(Component.text(locale.clickViewTicket)))
            .clickEvent(ClickEvent.runCommand(locale.run { "/$commandBase $commandWordView ${ticket.id}" }))
    }

    private fun createGeneralList(
        locale: TMLocale,
        headerFormat: String,
        results: ResultDB,
        baseCommand: (TMLocale) -> String
    ): Component {
        val (tickets, totalPages, _, returnedPage) = results

        return buildComponent {
            append(headerFormat.parseMiniMessage())

            if (tickets.isNotEmpty()) {
                tickets.forEach { append(createListEntry(it, locale)) }

                if (totalPages > 1)
                    append(buildPageComponent(returnedPage, totalPages, locale, baseCommand))

            }
        }
    }

    private fun buildTicketInfoComponent(
        ticket: Ticket,
        locale: TMLocale,
    ) = buildComponent {

        append(locale.viewHeader.parseMiniMessage("id" templated "${ticket.id}"))
        append(locale.viewSep1.parseMiniMessage())
        append(
            locale.viewCreator.parseMiniMessage(
                "creator" templated (ticket.creator.run { if (this is Creator.User) uuid else null }?.run(platform::nameFromUUID) ?: locale.consoleName)
            )
        )
        append(locale.viewAssignedTo.parseMiniMessage("assignment" templated (ticket.assignedTo ?: "")))

        locale.viewPriority.replace("%PCC%", priorityToHexColour(ticket.priority, locale))
            .parseMiniMessage("priority" templated ticket.priority.toLocaledWord(locale))
            .let(this::append)
        locale.viewStatus.replace("%SCC%", statusToHexColour(ticket.status, locale))
            .parseMiniMessage("status" templated ticket.status.toLocaledWord(locale))
            .let(this::append)

        val locationString = ticket.actions[0].location.let {
            if (!instanceState.enableProxyMode && it.server != null)
                it.toString()
                    .split(" ")
                    .drop(1)
                    .joinToString(" ")
            else it.toString()
        }
        locale.viewLocation.parseMiniMessage("location" templated locationString)
            .let {
                if (ticket.actions[0].location.world != null)
                    it.hoverEvent(showText(Component.text(locale.clickTeleport)))
                        .clickEvent(ClickEvent.runCommand(locale.run { "/$commandBase $commandWordTeleport ${ticket.id}" }))
                else it
            }
            .let(this::append)

        append(locale.viewSep2.parseMiniMessage())
    }

    private fun attemptDiscordMessageIfEnabledAsync(
        silent: Boolean,
        hasPermission: Boolean,
        sender: Sender,
        buildNotification: (DiscordTarget) -> DiscordNotification
    ) {
        if (silent || !hasPermission) return

        if (instanceState.discord != null || instanceState.discordSettings.forwardToProxy) {
            val user = when (sender) {
                is ConsoleSender -> DiscordConsole(instanceState.localeHandler.consoleLocale)
                is OnlinePlayer -> DiscordPlayerOrStr(sender.name)
            }

            val notification = buildNotification(user)
            TMCoroutine.runAsync {
                if (instanceState.discord != null) instanceState.discord.sendMessage(notification, instanceState.localeHandler.consoleLocale)
                else platform.relayMessageToProxy(Server2Proxy.DiscordMessage.waterfallString(), notification.encode())
            }
        }
    }
}

/*-------------------------*/
/*     Other Functions     */
/*-------------------------*/
private inline fun check(error: () -> Unit, predicate: () -> Boolean): Boolean {
    return if (predicate()) true else error().run { false }
}

fun trimCommentToSize(comment: String, preSize: Int, maxSize: Int): String {
    return if (comment.length + preSize > maxSize) "${comment.substring(0,maxSize-preSize-3)}..."
    else comment
}

private inline fun Boolean.thenCheck(error: () -> Unit, predicate: () -> Boolean): Boolean {
    return if (!this) false
    else if (predicate()) true
    else error().run { false }
}

fun Sender.toCreator(): Creator {
    return when(this) {
        is OnlinePlayer -> CreatorImpl.UserImpl(uniqueID)
        is ConsoleSender -> ConsoleObject
    }
}