package com.github.hoshikurama.ticketmanager.platform

import com.github.hoshikurama.componentDSL.buildComponent
import com.github.hoshikurama.componentDSL.onHover
import com.github.hoshikurama.ticketmanager.TMLocale
import com.github.hoshikurama.ticketmanager.TMPlugin
import com.github.hoshikurama.ticketmanager.data.GlobalPluginState
import com.github.hoshikurama.ticketmanager.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.database.Database
import com.github.hoshikurama.ticketmanager.database.Option
import com.github.hoshikurama.ticketmanager.database.Result
import com.github.hoshikurama.ticketmanager.database.SearchConstraint
import com.github.hoshikurama.ticketmanager.misc.*
import com.github.hoshikurama.ticketmanager.pluginVersion
import com.github.hoshikurama.ticketmanager.ticket.*
import com.github.jasync.sql.db.util.size
import kotlinx.coroutines.*
import net.kyori.adventure.extra.kotlin.plus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.event.HoverEvent.showText
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import java.time.Instant
import java.util.*

abstract class CommandPipeline(
    private val platform: PlatformFunctions,
    val instanceState: InstancePluginState,
    private val globalState: GlobalPluginState,
) {
    private val asyncScope: CoroutineScope
        get() = CoroutineScope(globalState.asyncDispatcher)

    suspend fun execute(sender: Sender, args: List<String>): Boolean = coroutineScope {

        if (args.isEmpty()) {
            sender.sendMessage(sender.locale.warningsInvalidCommand)
            return@coroutineScope false
        }

        if (globalState.pluginLocked.get()) {
            sender.sendMessage(sender.locale.warningsLocked)
            return@coroutineScope false
        }

        // Grabs BasicTicket. Only null if ID required but doesn't exist. Filters non-valid tickets
        val ticket = getBasicTicket(args, sender.locale) ?: kotlin.run {
            sender.sendMessage(sender.locale.warningsInvalidID)
            return@coroutineScope false
        }

        // Async Calculations
        val hasValidPermission = async { hasValidPermission(sender, ticket, args) }
        val isValidCommand = async { isValidCommand(sender, ticket, args) }
        val notUnderCooldown = async { notUnderCooldown(sender, args) }
        // Shortened Commands
        val executeCommand = suspend { executeCommand(sender, args, ticket) }

        try {
            globalState.jobCount.run { set(get() + 1) }
            if (notUnderCooldown.await() && isValidCommand.await() && hasValidPermission.await()) {
                executeCommand()?.let { pushNotifications(sender, it, ticket) }
            }
        } catch (e: Exception) {
            pushErrors(platform, instanceState, e, TMLocale::consoleErrorCommandExecution)
            sender.sendMessage(sender.locale.warningsUnexpectedError)
        } finally {
            globalState.jobCount.run { set(get() - 1) }
        }

        return@coroutineScope true
    }

    private suspend fun getBasicTicket(
        args: List<String>,
        senderLocale: TMLocale,
    ): BasicTicket? {

        suspend fun buildFromID(id: Int) = instanceState.database.getBasicTicketOrNull(id)

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
                    ?.toIntOrNull()
                    ?.let { buildFromID(it) }
            else -> BasicTicketImpl(creatorUUID = null, location = null).run { BasicTicketImpl(creatorUUID = null, location = null) } // Occurs when command does not need valid handler
        }
    }

    private fun hasValidPermission(
        sender: Sender,
        basicTicket: BasicTicket,
        args: List<String>,
    ): Boolean {
        try {
            if (sender is Console) return true
            val player = sender as Player

            fun has(perm: String) = player.has(perm)
            fun hasSilent() = has("ticketmanager.commandArg.silence")
            fun hasDuality(basePerm: String): Boolean {
                val ownsTicket = basicTicket.uuidMatches(player.uniqueID)
                return has("$basePerm.all") || (sender.has("$basePerm.own") && ownsTicket)
            }

            return sender.locale.run {
                when (args[0]) {
                    commandWordAssign, commandWordClaim, commandWordUnassign ->
                        has("ticketmanager.command.assign")
                    commandWordSilentAssign, commandWordSilentClaim,commandWordSilentUnassign ->
                        has("ticketmanager.command.assign") && hasSilent()
                    commandWordClose -> hasDuality("ticketmanager.command.close")
                    commandWordSilentClose -> hasDuality("ticketmanager.command.close") && hasSilent()
                    commandWordCloseAll -> has("ticketmanager.command.closeAll")
                    commandWordSilentCloseAll -> has("ticketmanager.command.closeAll") && hasSilent()
                    commandWordComment -> hasDuality("ticketmanager.command.comment")
                    commandWordSilentComment -> hasDuality("ticketmanager.command.comment") && hasSilent()
                    commandWordCreate -> has("ticketmanager.command.create")
                    commandWordHelp -> has("ticketmanager.command.help")
                    commandWordReload -> has("ticketmanager.command.reload")
                    commandWordList, commandWordListAssigned, commandWordListUnassigned -> has("ticketmanager.command.list")
                    commandWordReopen -> has("ticketmanager.command.reopen")
                    commandWordSilentReopen -> has("ticketmanager.command.reopen") && hasSilent()
                    commandWordSearch -> has("ticketmanager.command.search")
                    commandWordSetPriority -> has("ticketmanager.command.setPriority")
                    commandWordSilentSetPriority -> has("ticketmanager.command.setPriority") && hasSilent()
                    commandWordTeleport -> has("ticketmanager.command.teleport")
                    commandWordView -> hasDuality("ticketmanager.command.view")
                    commandWordDeepView -> hasDuality("ticketmanager.command.viewdeep")
                    commandWordConvertDB -> has("ticketmanager.command.convertDatabase")
                    commandWordHistory ->
                        player.has("ticketmanager.command.history.all") ||
                                player.has("ticketmanager.command.history.own").let { hasPerm ->
                                    if (args.size >= 2) hasPerm && args[1] == sender.name
                                    else hasPerm
                                }
                    else -> true
                }
            }
                .also { if (!it) player.sendMessage(sender.locale.warningsNoPermission) }
        } catch (e: Exception) {
            sender.sendMessage(sender.locale.warningsNoPermission)
            return false
        }
    }

    private fun isValidCommand(
        sender: Sender,
        basicTicket: BasicTicket,
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
                        .thenCheck(::ticketClosed) { basicTicket.status != BasicTicket.Status.CLOSED }

                commandWordClaim, commandWordSilentClaim ->
                    check(::invalidCommand) { args.size == 2 }
                        .thenCheck(::ticketClosed) { basicTicket.status != BasicTicket.Status.CLOSED }

                commandWordClose, commandWordSilentClose ->
                    check(::invalidCommand) { args.size >= 2 }
                        .thenCheck(::ticketClosed) { basicTicket.status != BasicTicket.Status.CLOSED }

                commandWordComment, commandWordSilentComment ->
                    check(::invalidCommand) { args.size >= 3 }
                        .thenCheck(::ticketClosed) { basicTicket.status != BasicTicket.Status.CLOSED }

                commandWordCloseAll, commandWordSilentCloseAll ->
                    check(::invalidCommand) { args.size == 3 }
                        .thenCheck(::notANumber) { args[1].toIntOrNull() != null }
                        .thenCheck(::notANumber) { args[2].toIntOrNull() != null }

                commandWordReopen, commandWordSilentReopen ->
                    check(::invalidCommand) { args.size == 2 }
                        .thenCheck(::ticketOpen) { basicTicket.status != BasicTicket.Status.OPEN }

                commandWordSetPriority, commandWordSilentSetPriority ->
                    check(::invalidCommand) { args.size == 3 }
                        .thenCheck(::outOfBounds) { args[2].toByteOrNull() != null }
                        .thenCheck(::ticketClosed) { basicTicket.status != BasicTicket.Status.CLOSED }

                commandWordUnassign, commandWordSilentUnassign ->
                    check(::invalidCommand) { args.size == 2 }
                        .thenCheck(::ticketClosed) { basicTicket.status != BasicTicket.Status.CLOSED }

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
                                try { Database.Type.valueOf(args[1]); true }
                                catch (e: Exception) { false }
                            }
                        )
                        .thenCheck( { sendMessage(senderLocale.warningsConvertToSameDBType) } )
                            { instanceState.database.type != Database.Type.valueOf(args[1]) }

                else -> false.also { invalidCommand() }
            }
        }
    }

    private suspend fun notUnderCooldown(
        sender: Sender,
        args: List<String>
    ): Boolean {
        if (sender is Console) return true
        val player = sender as Player

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
        ticket: BasicTicket
    ): NotifyParams? {
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

    private fun pushNotifications(
        sender: Sender,
        params: NotifyParams,
        basicTicket: BasicTicket
    ) {
        params.run {
            if (sendSenderMSG)
                senderLambda!!(sender.locale)
                    .run(sender::sendMessage)

            if (sendCreatorMSG)
                basicTicket.creatorUUID
                    ?.run { platform.buildPlayer(this, instanceState.localeHandler) }
                    ?.let { creatorLambda!!(it.locale) }
                    ?.run(creator!!::sendMessage)

            if (sendMassNotifyMSG)
                platform.massNotify(instanceState.localeHandler, massNotifyPerm, massNotifyLambda!!)
        }
    }

    private inner class NotifyParams(
        silent: Boolean,
        ticket: BasicTicket,
        sender: Sender,
        creatorAlertPerm: String,
        val massNotifyPerm: String,
        val senderLambda: ((TMLocale) -> Component)?,
        val creatorLambda: ((TMLocale) -> Component)?,
        val massNotifyLambda: ((TMLocale) -> Component)?,
    ) {
        val creator: Player? = ticket.creatorUUID?.run { platform.buildPlayer(this, instanceState.localeHandler) }
        val sendSenderMSG: Boolean = (!sender.has(massNotifyPerm) || silent)
                && senderLambda != null
        val sendCreatorMSG: Boolean = sender.nonCreatorMadeChange(ticket.creatorUUID)
                && !silent && (creator != null)
                && creator.has(creatorAlertPerm)
                && creator.has(massNotifyPerm).run { !this }
                && creatorLambda != null
        val sendMassNotifyMSG: Boolean = !silent
                && massNotifyLambda != null
    }


    /*-------------------------*/
    /*         Commands        */
    /*-------------------------*/

    private suspend fun allAssignVariations(
        sender: Sender,
        silent: Boolean,
        assignmentID: String,
        dbAssignment: String?,
        ticket: BasicTicket
    ): NotifyParams = coroutineScope {
        val shownAssignment = dbAssignment ?: sender.locale.miscNobody

        launchIndependent { instanceState.database.setAssignment(ticket.id, dbAssignment) }
        launchIndependent {
            instanceState.database.insertAction(
                id = ticket.id,
                action = FullTicket.Action(FullTicket.Action.Type.ASSIGN, sender.toUUIDOrNull(), dbAssignment)
            )
        }

        if (!silent && instanceState.discord?.state?.notifyOnAssign == true) {
            launchIndependent {
                tryNoCatch {
                    instanceState.discord.assignUpdate(sender.name, assignmentID, shownAssignment)
                }
            }
        }

        NotifyParams(
            silent = silent,
            sender = sender,
            ticket = ticket,
            senderLambda = {
                it.notifyTicketAssignSuccess.parseMiniMessage(
                    "ID" templated assignmentID,
                    "Assigned" templated shownAssignment
                )
            },
            massNotifyLambda = {
                it.notifyTicketAssignEvent.parseMiniMessage(
                    "User" templated sender.name,
                    "ID" templated assignmentID,
                    "Assigned" templated shownAssignment,
                )
            },
            creatorLambda = null,
            creatorAlertPerm = "ticketmanager.notify.change.assign",
            massNotifyPerm = "ticketmanager.notify.massNotify.assign"
        )
    }

    // /ticket assign <ID> <Assignment>
    private suspend fun assign(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: BasicTicket,
    ): NotifyParams {
        val sqlAssignment = args.subList(2, args.size).joinToString(" ")
        return allAssignVariations(sender, silent, args[1], sqlAssignment, ticketHandler)
    }

    // /ticket claim <ID>
    private suspend fun claim(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticket: BasicTicket,
    ): NotifyParams {
        return allAssignVariations(sender, silent, args[1], sender.name, ticket)
    }

    // /ticket close <ID> [Comment...]
    private suspend fun close(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: BasicTicket
    ): NotifyParams = coroutineScope {
        val newCreatorStatusUpdate = sender.nonCreatorMadeChange(ticketHandler.creatorUUID) && instanceState.allowUnreadTicketUpdates
        if (newCreatorStatusUpdate != ticketHandler.creatorStatusUpdate) {
            launchIndependent { instanceState.database.setCreatorStatusUpdate(ticketHandler.id, newCreatorStatusUpdate) }
        }

        return@coroutineScope if (args.size >= 3)
            closeWithComment(sender, args, silent, ticketHandler)
        else closeWithoutComment(sender, args, silent, ticketHandler)
    }

    private suspend fun closeWithComment(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticket: BasicTicket,
    ): NotifyParams = coroutineScope {
        val message = args.subList(2, args.size)
            .joinToString(" ")
            .let(platform::stripColour)

        if (!silent && instanceState.discord?.state?.notifyOnClose == true) {
            launchIndependent {
                tryNoCatch { instanceState.discord.closeUpdate(sender.name, ticket.id.toString(), message) }
            }
        }

        launchIndependent {
            instanceState.database.run {
                insertAction(
                    id = ticket.id,
                    action = FullTicket.Action(FullTicket.Action.Type.COMMENT, sender.toUUIDOrNull(), message)
                )
                insertAction(
                    id = ticket.id,
                    action = FullTicket.Action(FullTicket.Action.Type.CLOSE, sender.toUUIDOrNull())
                )
                instanceState.database.setStatus(ticket.id, BasicTicket.Status.CLOSED)
            }
        }

        NotifyParams(
            silent = silent,
            sender = sender,
            ticket = ticket,
            senderLambda = { it.notifyTicketCloseWCommentSuccess.parseMiniMessage("ID" templated args[1]) },
            creatorLambda = { it.notifyTicketModificationEvent.parseMiniMessage("ID" templated args[1]) },
            massNotifyLambda = {
                it.notifyTicketCloseWCommentEvent.parseMiniMessage(
                    "User" templated sender.name,
                    "ID" templated args[1],
                    "Message" templated message,
                )
            },
            massNotifyPerm = "ticketmanager.notify.massNotify.close",
            creatorAlertPerm = "ticketmanager.notify.change.close"
        )
    }

    private suspend fun closeWithoutComment(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: BasicTicket
    ): NotifyParams = coroutineScope {

        launchIndependent {
            instanceState.database.insertAction(
                id = ticketHandler.id,
                action = FullTicket.Action(FullTicket.Action.Type.CLOSE, sender.toUUIDOrNull())
            )
            instanceState.database.setStatus(ticketHandler.id, BasicTicket.Status.CLOSED)
        }

        if (!silent && instanceState.discord?.state?.notifyOnClose == true) {
            launchIndependent {
                tryNoCatch { instanceState.discord.closeUpdate(sender.name, ticketHandler.id.toString()) }
            }
        }

        NotifyParams(
            silent = silent,
            sender = sender,
            ticket = ticketHandler,
            creatorLambda = { it.notifyTicketModificationEvent.parseMiniMessage("ID" templated args[1]) },
            senderLambda = { it.notifyTicketCloseSuccess.parseMiniMessage("ID" templated args[1]) },
            massNotifyLambda = {
                it.notifyTicketCloseEvent.parseMiniMessage(
                    "User" templated sender.name,
                    "ID" templated args[1],
                )
            },
            massNotifyPerm = "ticketmanager.notify.massNotify.close",
            creatorAlertPerm = "ticketmanager.notify.change.close"
        )
    }

    // /ticket closeall <Lower ID> <Upper ID>
    private suspend fun closeAll(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticket: BasicTicket
    ): NotifyParams = coroutineScope {
        val lowerBound = args[1].toInt()
        val upperBound = args[2].toInt()

        launchIndependent { instanceState.database.massCloseTickets(lowerBound, upperBound, sender.toUUIDOrNull()) }

        if (!silent && instanceState.discord?.state?.notifyOnCloseAll == true) {
            launchIndependent {
                tryNoCatch {
                    instanceState.discord.closeAllUpdate(sender.name, "$lowerBound", "$upperBound")
                }
            }
        }

        NotifyParams(
            silent = silent,
            sender = sender,
            ticket = ticket,
            creatorLambda = null,
            senderLambda = {
                it.notifyTicketMassCloseSuccess.parseMiniMessage(
                    "Lower" templated args[1],
                    "Upper" templated args[2],
                )
            },
            massNotifyLambda = {
                it.notifyTicketMassCloseEvent.parseMiniMessage(
                    "User" templated sender.name,
                    "Lower" templated args[1],
                    "Upper" templated args[2],
                )
            },
            massNotifyPerm = "ticketmanager.notify.massNotify.massClose",
            creatorAlertPerm = "ticketmanager.notify.change.massClose"
        )
    }

    // /ticket comment <ID> <Comment…>
    private suspend fun comment(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticket: BasicTicket,
    ): NotifyParams = coroutineScope {
        val message = args.subList(2, args.size)
            .joinToString(" ")
            .let(platform::stripColour)

        val newCreatorStatusUpdate = sender.nonCreatorMadeChange(ticket.creatorUUID) && instanceState.allowUnreadTicketUpdates
        if (newCreatorStatusUpdate != ticket.creatorStatusUpdate) {
            launchIndependent { instanceState.database.setCreatorStatusUpdate(ticket.id, newCreatorStatusUpdate) }
        }

        launchIndependent {
            instanceState.database.insertAction(
                id = ticket.id,
                action = FullTicket.Action(FullTicket.Action.Type.COMMENT, sender.toUUIDOrNull(), message)
            )
        }

        if (!silent && instanceState.discord?.state?.notifyOnComment == true) {
            launchIndependent {
                tryNoCatch {
                    instanceState.discord.commentUpdate(sender.name, ticket.id.toString(), message)
                }
            }
        }

        NotifyParams(
            silent = silent,
            sender = sender,
            ticket = ticket,
            creatorLambda = { it.notifyTicketModificationEvent.parseMiniMessage("ID" templated args[1]) },
            senderLambda = { it.notifyTicketCommentSuccess.parseMiniMessage("ID" templated args[1]) },
            massNotifyLambda = {
                it.notifyTicketCommentEvent.parseMiniMessage(
                    "User" templated sender.name,
                    "ID" templated args[1],
                    "Message" templated message,
                )
            },
            massNotifyPerm = "ticketmanager.notify.massNotify.comment",
            creatorAlertPerm = "ticketmanager.notify.change.comment"
        )
    }

    // /ticket convertdatabase <Target Database>
    private suspend fun convertDatabase(
        args: List<String>,
    ) {
        val type = args[1].run(Database.Type::valueOf)

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
    ): NotifyParams = coroutineScope {
        val message = args.subList(1, args.size)
            .joinToString(" ")
            .let(platform::stripColour)

        val ticket = if (sender is Player) BasicTicketImpl(creatorUUID = sender.uniqueID, location = sender.getTicketLocFromCurLoc())
        else BasicTicketImpl(creatorUUID = null, location = null)

        val fullTicket = ticket + listOf(FullTicket.Action(type = FullTicket.Action.Type.OPEN, user = sender.toUUIDOrNull(), message, Instant.now().epochSecond))

        val deferredID = async { instanceState.database.insertTicket(fullTicket) }
        globalState.ticketCountMetrics.run { set(get() + 1) }
        val id = deferredID.await().toString()

        if (instanceState.discord?.state?.notifyOnCreate == true) {
            launchIndependent {
                tryNoCatch { instanceState.discord.createUpdate(sender.name, id, message) }
            }
        }

        NotifyParams(
            silent = false,
            sender = sender,
            ticket= ticket,
            creatorLambda = null,
            senderLambda = { it.notifyTicketCreationSuccess.parseMiniMessage("ID" templated id) },
            massNotifyLambda = {
                it.notifyTicketCreationEvent.parseMiniMessage(
                    "User" templated sender.name,
                    "ID" templated id,
                    "Message" templated message
                )
            },
            creatorAlertPerm = "ticketmanager.NO NODE",
            massNotifyPerm = "ticketmanager.notify.massNotify.create",
        )
    }

    // /ticket help
    private fun help(
        sender: Sender,
    ) {
        val hasSilentPerm = sender.has("ticketmanager.commandArg.silence")

        open class CommandArg(val content: String)
        class RequiredArg(content: String): CommandArg(content)
        class OptionalArg(content: String): CommandArg(content)
        class Command(val silenceable: Boolean, val command: String, val arguments: List<CommandArg>, val permissions: List<String>)

        buildComponent {

            sender.locale.run {
                // Builds header
                append(helpHeader.parseMiniMessage())
                append(helpLine1.parseMiniMessage())
                if (hasSilentPerm)
                    listOf(helpLine2, helpLine3)
                        .map(String::parseMiniMessage)
                        .forEach(this@buildComponent::append)
                append(helpSep.parseMiniMessage())

                // Builds entries
                listOf(
                    Command(
                        silenceable = true,
                        command = commandWordAssign,
                        arguments = listOf(RequiredArg(parameterID), RequiredArg("$parameterAssignment...")),
                        permissions = listOf("ticketmanager.command.assign"),
                    ),
                    Command(
                        silenceable = true,
                        command = commandWordClaim,
                        arguments = listOf(RequiredArg(parameterID)),
                        permissions = listOf("ticketmanager.command.claim"),
                    ),
                    Command(
                        silenceable = true,
                        command = commandWordClose,
                        arguments = listOf(RequiredArg(parameterID), OptionalArg("$parameterComment...")),
                        permissions = listOf("ticketmanager.command.close.all", "ticketmanager.command.close.own"),
                    ),
                    Command(
                        silenceable = true,
                        command = commandWordCloseAll,
                        arguments = listOf(RequiredArg(parameterLowerID), RequiredArg(parameterUpperID)),
                        permissions = listOf("ticketmanager.command.closeAll"),
                    ),
                    Command(
                        silenceable = true,
                        command = commandWordComment,
                        arguments = listOf(RequiredArg(parameterID), RequiredArg("$parameterComment...")),
                        permissions = listOf("ticketmanager.command.comment.all", "ticketmanager.command.comment.own"),
                    ),
                    Command(
                        silenceable = false,
                        command = commandWordConvertDB,
                        arguments = listOf(RequiredArg(parameterTargetDB)),
                        permissions = listOf("ticketmanager.command.convertDatabase"),
                    ),
                    Command(
                        silenceable = false,
                        command = commandWordCreate,
                        arguments = listOf(RequiredArg("$parameterComment...")),
                        permissions = listOf("ticketmanager.command.create"),
                    ),
                    Command(
                        silenceable = false,
                        command = commandWordHelp,
                        arguments = listOf(),
                        permissions = listOf("ticketmanager.command.help"),
                    ),
                    Command(
                        silenceable = false,
                        command = commandWordHistory,
                        arguments = listOf(OptionalArg(parameterUser), OptionalArg(parameterPage)),
                        permissions = listOf("ticketmanager.command.history.all", "ticketmanager.command.history.own"),
                    ),
                    Command(
                        silenceable = false,
                        command = commandWordList,
                        arguments = listOf(OptionalArg(parameterPage)),
                        permissions = listOf("ticketmanager.command.list"),
                    ),
                    Command(
                        silenceable = false,
                        command = commandWordListAssigned,
                        arguments = listOf(OptionalArg(parameterPage)),
                        permissions = listOf("ticketmanager.command.list"),
                    ),
                    Command(
                        silenceable = false,
                        command = commandWordListUnassigned,
                        arguments = listOf(OptionalArg(parameterPage)),
                        permissions = listOf("ticketmanager.command.list"),
                    ),
                    Command(
                        silenceable = false,
                        command = commandWordReload,
                        arguments = listOf(),
                        permissions = listOf("ticketmanager.command.reload"),
                    ),
                    Command(
                        silenceable = true,
                        command = commandWordReopen,
                        arguments = listOf(RequiredArg(parameterID)),
                        permissions = listOf("ticketmanager.command.reopen"),
                    ),
                    Command(
                        silenceable = false,
                        command = commandWordSearch,
                        arguments = listOf(RequiredArg("$parameterConstraints...")),
                        permissions = listOf("ticketmanager.command.search"),
                    ),
                    Command(
                        silenceable = true,
                        command = commandWordSetPriority,
                        arguments = listOf(RequiredArg(parameterID), RequiredArg(parameterLevel)),
                        permissions = listOf("ticketmanager.command.setPriority"),
                    ),
                    Command(
                        silenceable = false,
                        command = commandWordTeleport,
                        arguments = listOf(RequiredArg(parameterID)),
                        permissions = listOf("ticketmanager.command.teleport"),
                    ),
                    Command(
                        silenceable = true,
                        command = commandWordUnassign,
                        arguments = listOf(RequiredArg(parameterID)),
                        permissions = listOf("ticketmanager.command.assign"),
                    ),
                    Command(
                        silenceable = true,
                        command = commandWordView,
                        arguments = listOf(RequiredArg(parameterID)),
                        permissions = listOf("ticketmanager.command.view.all", "ticketmanager.command.view.own"),
                    ),
                    Command(
                        silenceable = false,
                        command = commandWordDeepView,
                        arguments = listOf(RequiredArg(parameterID)),
                        permissions = listOf("ticketmanager.command.viewdeep.all", "ticketmanager.command.viewdeep.own"),
                    ),
                )
                    .filter { it.permissions.any(sender::has) }
                    .map { Command(it.silenceable, "${sender.locale.commandBase} ${it.command}", it.arguments, it.permissions) }
                    .map { command ->

                        // Builds params into one Component
                        val argsComponent = command.arguments
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
                                if (command.silenceable) helpHasSilence.parseMiniMessage()
                                else helpLackSilence.parseMiniMessage()
                            else Component.text("")

                        helpEntry.parseMiniMessage(
                            "Silenceable" templated silenceableComponent,
                            "Command" templated command.command,
                            "Params" templated argsComponent,
                        )
                    }
                    .reduce(Component::append)
                    .let(this@buildComponent::append)
            }
        }
            .run(sender::sendMessage)
    }

    // /ticket history [User] [Page]
    private suspend fun history(
        sender: Sender,
        args: List<String>,
    ) {
        val locale = sender.locale

        coroutineScope {
            val targetName = if (args.size >= 2) args[1].takeIf { it != locale.consoleName } else sender.name.takeIf { sender is Player }
            val requestedPage = if (args.size >= 3) args[2].toInt() else 1

            val searchedUserUUID: Option<UUID?> = targetName?.run { platform.offlinePlayerNameToUUIDOrNull(this)?.run { Option(this) } ?: Option(UUID.randomUUID()) } ?: Option(null)
            val constraints = SearchConstraint(creator = searchedUserUUID)
            val (results, pageCount, resultCount, returnedPage) = instanceState.database.searchDatabase(
                constraints,
                requestedPage,
                9
            )

            val sentComponent = buildComponent {
                // Header
                locale.historyHeader.parseMiniMessage(
                    "Name" templated (targetName ?: locale.consoleName),
                    "Count" templated "$resultCount"
                ).let(this::append)

                if (results.isNotEmpty()) {
                    results.forEach { t ->
                        val id = "${t.id}"
                        val status = t.status.toLocaledWord(locale)
                        val comment = trimCommentToSize(
                            comment = t.actions[0].message!!,
                            preSize = id.size + status.size + locale.historyFormattingSize,
                            maxSize = locale.historyMaxLineSize
                        )

                        val entry = locale.historyEntry
                            .replace("%SCC%", statusToHexColour(t.status, sender.locale))
                            .parseMiniMessage(
                                "ID" templated id,
                                "STATUS" templated status,
                                "Comment" templated comment,
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
    }

    // /ticket list [Page]
    private suspend fun list(
        sender: Sender,
        args: List<String>,
    ) {
        val page = if (args.size == 2) args[1].toIntOrNull() ?: 1 else 1
        val results = instanceState.database.getOpenTickets(page, 8)
        createGeneralList(sender.locale, sender.locale.listHeader, results) { sender.locale.run { "/$commandBase $commandWordList " } }
            .run(sender::sendMessage)
    }

    // /ticket listassigned [Page]
    private suspend fun listAssigned(
        sender: Sender,
        args: List<String>,
    ) {
        val page = if (args.size == 2) args[1].toIntOrNull() ?: 1 else 1
        val groups: List<String> = if (sender is Player) sender.permissionGroups else listOf()
        val results = instanceState.database.getOpenTicketsAssignedTo(page,8, sender.name, groups)

        createGeneralList(sender.locale, sender.locale.listAssignedHeader, results) { sender.locale.run { "/$commandBase $commandWordListAssigned " } }
            .run(sender::sendMessage)
    }

    private suspend fun listUnassigned(
        sender: Sender,
        args: List<String>,
    ) {
        val page = if (args.size == 2) args[1].toIntOrNull() ?: 1 else 1
        val results = instanceState.database.getOpenTicketsNotAssigned(page, 8)

        createGeneralList(sender.locale, sender.locale.listUnassignedHeader, results) { sender.locale.run { "/$commandBase $commandWordListUnassigned " } }
            .run(sender::sendMessage)
    }

    // /ticket reload
    private suspend fun reload(
        sender: Sender
    ) = coroutineScope {
        try {
            globalState.pluginLocked.set(true)
            platform.massNotify(instanceState.localeHandler, "ticketmanager.notify.info") {
                it.informationReloadInitiated.parseMiniMessage("User" templated sender.name)
            }

            val forceQuitJob = launch {
                delay(30L * 1000L)

                // Long standing task has occurred if it reaches this point
                launch {
                    platform.massNotify(instanceState.localeHandler, "ticketmanager.notify.warning") {
                        it.warningsLongTaskDuringReload.parseMiniMessage()
                    }
                    globalState.jobCount.set(1)
                    globalState.asyncDispatcher.cancelChildren()
                }
            }

            // Waits for other tasks to complete
            while (globalState.jobCount.get() > 1) delay(1000L)

            if (!forceQuitJob.isCancelled)
                forceQuitJob.cancel("Tasks closed on time")

            platform.massNotify(instanceState.localeHandler, "ticketmanager.notify.info") {
                it.informationReloadTasksDone.parseMiniMessage()
            }
            instanceState.database.closeDatabase()
            instanceState.discord?.shutdown()

            // I REALLY HATE THIS STATIC VARIABLE, BUT IT WILL WORK FOR NOW SINCE I ONLY USE IT HERE
            TMPlugin.activeInstance.unregisterProcesses()
            TMPlugin.activeInstance.initializeData() // Also re-registers things

            // Notifications
            platform.massNotify(instanceState.localeHandler, "ticketmanager.notify.info") {
                it.informationReloadSuccess.parseMiniMessage()
            }
            if (!sender.has("ticketmanager.notify.info")) {
                sender.sendMessage(sender.locale.informationReloadSuccess)
            }
            globalState.pluginLocked.set(false)
        } catch (e: Exception) {
            platform.massNotify(instanceState.localeHandler, "ticketmanager.notify.info") {
                it.informationReloadFailure.parseMiniMessage()
            }
            globalState.pluginLocked.set(false)
            throw e
        }
    }

    // /ticket reopen <ID>
    private suspend fun reopen(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticket: BasicTicket,
    ): NotifyParams = coroutineScope {
        val action = FullTicket.Action(FullTicket.Action.Type.REOPEN, sender.toUUIDOrNull())

        // Updates user status if needed
        val newCreatorStatusUpdate = sender.nonCreatorMadeChange(ticket.creatorUUID) && instanceState.allowUnreadTicketUpdates
        if (newCreatorStatusUpdate != ticket.creatorStatusUpdate) {
            launchIndependent { instanceState.database.setCreatorStatusUpdate(ticket.id, newCreatorStatusUpdate) }
        }

        launchIndependent {
            instanceState.database.insertAction(ticket.id, action)
            instanceState.database.setStatus(ticket.id, BasicTicket.Status.OPEN)
        }

        if (!silent && instanceState.discord?.state?.notifyOnReopen == true) {
            launchIndependent {
                tryNoCatch { instanceState.discord.reopenUpdate(sender.name, ticket.id.toString()) }
            }
        }

        NotifyParams(
            silent = silent,
            sender = sender,
            ticket = ticket,
            creatorLambda = { it.notifyTicketModificationEvent.parseMiniMessage("ID" templated args[1]) },
            senderLambda = { it.notifyTicketReopenSuccess.parseMiniMessage("ID" templated args[1]) },
            massNotifyLambda = {
                it.notifyTicketReopenEvent.parseMiniMessage(
                    "User" templated sender.name,
                    "ID" templated args[1],
                )
            },
            creatorAlertPerm = "ticketmanager.notify.change.reopen",
            massNotifyPerm = "ticketmanager.notify.massNotify.reopen",
        )
    }

    private suspend fun search(
        sender: Sender,
        args: List<String>,
    ) {
        val locale = sender.locale

        // Beginning of execution
        sender.sendMessage(locale.searchQuerying.parseMiniMessage())

        fun doComplexStuffWithUUID(value: String): Option<UUID?> = value.takeIf { it != locale.consoleName }?.run { platform.offlinePlayerNameToUUIDOrNull(this)?.run { Option(this) } ?: Option(UUID.randomUUID()) } ?: Option(null)

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
                locale.searchCreator -> constraints.creator = doComplexStuffWithUUID(value)
                locale.searchPriority -> constraints.priority = value.toByteOrNull()?.run(::byteToPriority)?.let { Option(it) }
                locale.searchStatus -> constraints.status = stringToStatusOrNull(value)?.let { Option(it) }
                locale.searchClosedBy -> constraints.closedBy = doComplexStuffWithUUID(value)
                locale.searchLastClosedBy -> constraints.lastClosedBy = doComplexStuffWithUUID(value)
                locale.searchWorld -> constraints.world = Option(value)
                locale.searchTime -> constraints.creationTime = Option(relTimeToEpochSecond(value, locale))
                locale.searchKeywords -> constraints.keywords = Option(value.split(','))
                locale.searchPage -> value.toIntOrNull()?.also { attemptedPage = it }
            }
        }

        // Results calculation and destructuring
        val (results, pageCount, resultCount, returnedPage) = instanceState.database.searchDatabase(constraints, attemptedPage, 9)

        val sentComponent = buildComponent {
            // Initial header
            append(locale.searchHeader.parseMiniMessage("Size" templated "$resultCount"))

            // Adds entries
            if (results.isNotEmpty()) {
                results.forEach {
                    val time = it.actions[0].timestamp.toLargestRelativeTime(locale)
                    val comment = trimCommentToSize(
                        comment = it.actions[0].message!!,
                        preSize = locale.searchFormattingSize + time.length,
                        maxSize = locale.searchMaxLineSize,
                    )

                    locale.searchEntry
                        .replace("%PCC%", priorityToHexColour(it.priority, locale))
                        .replace("%SCC%", statusToHexColour(it.status, locale))
                        .parseMiniMessage(
                            "ID" templated "${it.id}",
                            "Status" templated it.status.toLocaledWord(locale),
                            "Creator" templated (it.creatorUUID?.run(platform::nameFromUUID) ?: sender.locale.consoleName),
                            "Assignment" templated (it.assignedTo ?: ""),
                            "World" templated (it.location?.world ?: ""),
                            "Time" templated time,
                            "Comment" templated comment,
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
    private suspend fun setPriority(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticket: BasicTicket,
    ): NotifyParams = coroutineScope {
        val newPriority = byteToPriority(args[2].toByte())

        launchIndependent {
            instanceState.database.insertAction(
                id = ticket.id,
                action = FullTicket.Action(FullTicket.Action.Type.SET_PRIORITY, sender.toUUIDOrNull(), args[2])
            )
            instanceState.database.setPriority(ticket.id, newPriority)
        }

        if (!silent && instanceState.discord?.state?.notifyOnPriorityChange == true) {
            launchIndependent {
                tryNoCatch {
                    instanceState.discord.priorityChangeUpdate(sender.name, ticket.id.toString(), newPriority)
                }
            }
        }

        NotifyParams(
            silent = silent,
            sender = sender,
            ticket = ticket,
            creatorLambda = null,
            senderLambda = { it.notifyTicketSetPrioritySuccess.parseMiniMessage("ID" templated args[1]) },
            massNotifyLambda = {
                it.notifyTicketSetPriorityEvent
                    .replace("%PCC%", priorityToHexColour(newPriority, sender.locale))
                    .parseMiniMessage(
                        "User" templated sender.name,
                        "ID" templated args[1],
                        "Priority" templated newPriority.toLocaledWord(sender.locale),
                    )
            },
            creatorAlertPerm = "ticketmanager.notify.change.priority",
            massNotifyPerm =  "ticketmanager.notify.massNotify.priority"
        )
    }


    private inline fun launchIndependent(crossinline f: suspend () -> Unit) {
        asyncScope.launch { f() }
    }

    // /ticket teleport <ID>
    private suspend fun teleport(
        sender: Sender,
        basicTicket: BasicTicket,
    ) {
        if (sender is Player && basicTicket.location != null) {
            withContext(globalState.mainDispatcher) {
                platform.teleportToTicketLocation(sender, basicTicket.location!!)
            }
        }
    }

    // /ticket unassign <ID>
    private suspend fun unAssign(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: BasicTicket,
    ): NotifyParams {
        return allAssignVariations(sender, silent, args[1], null, ticketHandler)
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
                content("           V$pluginVersion\n")
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
    private suspend fun view(
        sender: Sender,
        ticket: BasicTicket,
    ) {
        val fullTicket = instanceState.database.getFullTicket(ticket)
        val baseComponent = buildTicketInfoComponent(fullTicket, sender.locale)

        if (!sender.nonCreatorMadeChange(ticket.creatorUUID) && ticket.creatorStatusUpdate)
            launchIndependent { instanceState.database.setCreatorStatusUpdate(ticket.id, false) }

        val entries = fullTicket.actions.asSequence()
            .filter { it.type == FullTicket.Action.Type.COMMENT || it.type == FullTicket.Action.Type.OPEN }
            .map {
                sender.locale.viewComment.parseMiniMessage(
                    "User" templated (it.user?.run(platform::nameFromUUID) ?: sender.locale.consoleName),
                    "Comment" templated it.message!!,
                )
            }
            .reduce(Component::append)

        sender.sendMessage(baseComponent.append(entries))
    }

    // /ticket viewdeep <ID>
    private suspend fun viewDeep(
        sender: Sender,
        ticket: BasicTicket,
    ) {
        val fullTicket = instanceState.database.getFullTicket(ticket)
        val baseComponent = buildTicketInfoComponent(fullTicket, sender.locale)

        if (!sender.nonCreatorMadeChange(ticket.creatorUUID) && ticket.creatorStatusUpdate)
            launchIndependent { instanceState.database.setCreatorStatusUpdate(ticket.id, false) }

        fun formatDeepAction(action: FullTicket.Action): Component {
            val templatedUser = "User" templated (action.user?.run(platform::nameFromUUID) ?: sender.locale.consoleName)
            val templatedTime = "Time" templated action.timestamp.toLargestRelativeTime(sender.locale)

            return when (action.type) {
                FullTicket.Action.Type.OPEN, FullTicket.Action.Type.COMMENT ->
                    sender.locale.viewDeepComment.parseMiniMessage(templatedUser, templatedTime, "Comment" templated action.message!!)

                FullTicket.Action.Type.SET_PRIORITY ->
                    byteToPriority(action.message!!.toByte())
                        .let { it to sender.locale.viewDeepSetPriority.replace("%PCC%", priorityToHexColour(it, sender.locale)) }
                        .let { (priority, string) -> string.parseMiniMessage(templatedUser,templatedTime, "Priority" templated priority.toLocaledWord(sender.locale)) }

                FullTicket.Action.Type.ASSIGN ->
                    sender.locale.viewDeepAssigned.parseMiniMessage(templatedUser, templatedTime, "Assignment" templated (action.message ?: ""))

                FullTicket.Action.Type.REOPEN -> sender.locale.viewDeepReopen.parseMiniMessage(templatedUser, templatedTime)
                FullTicket.Action.Type.CLOSE -> sender.locale.viewDeepClose.parseMiniMessage(templatedUser, templatedTime)
                FullTicket.Action.Type.MASS_CLOSE -> sender.locale.viewDeepMassClose.parseMiniMessage(templatedUser, templatedTime)
            }
        }

        fullTicket.actions.asSequence()
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
            "Back_Button" templated back,
            "Cur_Page" templated "$curPage",
            "Max_Pages" templated "$pageCount",
            "Next_Button" templated next,
        )
    }

    private fun createListEntry(
        ticket: FullTicket,
        locale: TMLocale
    ): Component {
        val id = "${ticket.id}"
        val creator = ticket.creatorUUID?.run(platform::nameFromUUID) ?: locale.consoleName
        val fixedAssign = ticket.assignedTo ?: ""
        val pcc = priorityToHexColour(ticket.priority, locale)
        val fixedComment = trimCommentToSize(
            comment = ticket.actions[0].message!!,
            preSize = locale.listFormattingSize + id.size + creator.size + fixedAssign.size,
            maxSize = 58,
        )

        return locale.listEntry.replace("%PCC%", pcc)
            .parseMiniMessage(
                "ID" templated id,
                "Creator" templated creator,
                "Assignment" templated fixedAssign,
                "Comment" templated fixedComment,
            )
            .hoverEvent(showText(Component.text(locale.clickViewTicket)))
            .clickEvent(ClickEvent.runCommand(locale.run { "/$commandBase $commandWordView ${ticket.id}" }))
    }

    private suspend fun createGeneralList(
        locale: TMLocale,
        headerFormat: String,
        results: Result<BasicTicket>,
        baseCommand: (TMLocale) -> String
    ): Component {
        val (basicTickets, totalPages, _, returnedPage) = results
        val fullTickets = instanceState.database.getFullTickets(basicTickets)

        return buildComponent {
            append(headerFormat.parseMiniMessage())

            if (fullTickets.isNotEmpty()) {
                fullTickets.forEach { append(createListEntry(it, locale)) }

                if (totalPages > 1)
                    append(buildPageComponent(returnedPage, totalPages, locale, baseCommand))

            }
        }
    }

    private fun buildTicketInfoComponent(
        ticket: FullTicket,
        locale: TMLocale,
    ) = buildComponent {

        append(locale.viewHeader.parseMiniMessage("ID" templated "${ticket.id}"))
        append(locale.viewSep1.parseMiniMessage())
        append(locale.viewCreator.parseMiniMessage("Creator" templated (ticket.creatorUUID?.run(platform::nameFromUUID) ?: locale.consoleName)))
        append(locale.viewAssignedTo.parseMiniMessage("Assignment" templated (ticket.assignedTo ?: "")))

        locale.viewPriority.replace("%PCC%", priorityToHexColour(ticket.priority, locale))
            .parseMiniMessage("Priority" templated ticket.priority.toLocaledWord(locale))
            .let(this::append)
        locale.viewStatus.replace("%SCC%", statusToHexColour(ticket.status, locale))
            .parseMiniMessage("Status" templated ticket.status.toLocaledWord(locale))
            .let(this::append)

        locale.viewLocation.parseMiniMessage("Location" templated (ticket.location?.toString() ?: ""))
            .let {
                if (ticket.location != null)
                    it.hoverEvent(showText(Component.text(locale.clickTeleport)))
                        .clickEvent(ClickEvent.runCommand(locale.run { "/$commandBase $commandWordTeleport ${ticket.id}" }))
                else it
            }
            .let(this::append)

        append(locale.viewSep2.parseMiniMessage())
    }
}

/*-------------------------*/
/*     Other Functions     */
/*-------------------------*/

fun trimCommentToSize(comment: String, preSize: Int, maxSize: Int): String {
    return if (comment.length + preSize > maxSize) "${comment.substring(0,maxSize-preSize-3)}..."
    else comment
}

private inline fun check(error: () -> Unit, predicate: () -> Boolean): Boolean {
    return if (predicate()) true else error().run { false }
}

private inline fun Boolean.thenCheck(error: () -> Unit, predicate: () -> Boolean): Boolean {
    return if (!this) false
    else if (predicate()) true
    else error().run { false }
}

private fun Sender.nonCreatorMadeChange(creatorUUID: UUID?): Boolean {
    if (creatorUUID == null) return false
    return if (this is Player) this.uniqueID.notEquals(creatorUUID) else true
}

private inline fun tryNoCatch(f: () -> Unit) =
    try { f() }
    catch(_: Exception) {}