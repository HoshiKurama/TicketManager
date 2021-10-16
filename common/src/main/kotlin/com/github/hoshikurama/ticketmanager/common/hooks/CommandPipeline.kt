package com.github.hoshikurama.ticketmanager.common.hooks

import com.github.hoshikurama.componentDSL.buildComponent
import com.github.hoshikurama.componentDSL.formattedContent
import com.github.hoshikurama.componentDSL.onClick
import com.github.hoshikurama.componentDSL.onHover
import com.github.hoshikurama.ticketmanager.common.*
import com.github.hoshikurama.ticketmanager.common.database.Database
import com.github.hoshikurama.ticketmanager.common.database.Memory
import com.github.hoshikurama.ticketmanager.common.database.MySQL
import com.github.hoshikurama.ticketmanager.common.database.SQLite
import com.github.hoshikurama.ticketmanager.common.ticket.*
import kotlinx.coroutines.*
import net.kyori.adventure.extra.kotlin.text
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import java.util.*


abstract class CommandPipeline<T>(private val pluginData: TicketManagerPlugin<T>) {
    private val database: Database
        get() = pluginData.configState.database

    suspend fun execute(
        sender: Sender,
        args: List<String>,
    ) = coroutineScope {
        val senderLocale = sender.locale

        if (args.isEmpty()) {
            sender.sendMessage(senderLocale.warningsInvalidCommand)
            return@coroutineScope false
        }

        if (pluginData.pluginLocked.get()) {
            sender.sendMessage(senderLocale.warningsLocked)
            return@coroutineScope false
        }

        // Grabs BasicTicket. Only null if ID required but doesn't exist. Filters non-valid tickets
        val pseudoTicket = getBasicTicket(args, senderLocale)
        if (pseudoTicket == null) {
            sender.sendMessage(senderLocale.warningsInvalidID)
            return@coroutineScope false
        }

        // Async Calculations
        val hasValidPermission = async { hasValidPermission(sender, pseudoTicket, args) }
        val isValidCommand = async { isValidCommand(sender, pseudoTicket, args) }
        val notUnderCooldown = async { notUnderCooldown(sender, senderLocale, args) }
        // Shortened Commands
        val executeCommand = suspend { executeCommand(sender, args, pseudoTicket) }

        try {
            pluginData.jobCount.run { set(get() + 1) }
            if (notUnderCooldown.await() && isValidCommand.await() && hasValidPermission.await()) {
                executeCommand()?.let { pushNotifications(sender, it, pseudoTicket) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            pluginData.postModifiedStacktrace(e)
            sender.sendMessage(senderLocale.warningsUnexpectedError)
        } finally {
            pluginData.jobCount.run { set(get() - 1) }
        }

        return@coroutineScope true
    }

    private suspend fun getBasicTicket(
        args: List<String>,
        senderLocale: TMLocale,
    ): BasicTicket? {

        suspend fun buildFromID(id: Int) = database.getBasicTicket(id)

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
                        { pluginData.configState.database.type != Database.Type.valueOf(args[1]) }

                else -> false.also { invalidCommand() }
            }
        }
    }

    private suspend fun notUnderCooldown(
        sender: Sender,
        senderLocale: TMLocale,
        args: List<String>
    ): Boolean {
        if (sender is Console) return true
        val player = sender as Player

        val underCooldown = when (args[0]) {
            senderLocale.commandWordCreate,
            senderLocale.commandWordComment,
            senderLocale.commandWordSilentComment ->
                pluginData.configState.cooldowns.checkAndSetAsync(player.uniqueID)
            else -> false
        }

        if (underCooldown)
            player.sendMessage(senderLocale.warningsUnderCooldown)
        return !underCooldown
    }

    private suspend fun executeCommand(
        sender: Sender,
        args: List<String>,
        ticket: BasicTicket
    ): NotifyParams? {
        val senderLocale = sender.locale
        return senderLocale.run {
            when (args[0]) {
                commandWordAssign -> assign(sender, args, false, ticket)
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
                    ?.run(::buildPlayer)
                    ?.let { creatorLambda!!(it.locale) }
                    ?.run(creator!!::sendMessage)

            if (sendMassNotifyMSG)
                pushMassNotify(massNotifyPerm, massNotifyLambda!!)
        }
    }

    private inner class NotifyParams(
        silent: Boolean,
        basicTicket: BasicTicket,
        sender: Sender,
        creatorAlertPerm: String,
        val massNotifyPerm: String,
        val senderLambda: ((TMLocale) -> Component)?,
        val creatorLambda: ((TMLocale) -> Component)?,
        val massNotifyLambda: ((TMLocale) -> Component)?,
    ) {
        val creator: Player? = basicTicket.creatorUUID?.run(::buildPlayer)
        val sendSenderMSG: Boolean = (!sender.has(massNotifyPerm) || silent)
                && senderLambda != null
        val sendCreatorMSG: Boolean = sender.nonCreatorMadeChange(basicTicket.creatorUUID)
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
        ticketHandler: BasicTicket
    ): NotifyParams = coroutineScope {
        val shownAssignment = dbAssignment ?: sender.locale.miscNobody

        launchIndependent { database.setAssignment(ticketHandler.id, dbAssignment) }
        launchIndependent {
            pluginData.configState.database.addAction(
                ticketID = ticketHandler.id,
                action = FullTicket.Action(FullTicket.Action.Type.ASSIGN, sender.toUUIDOrNull(), dbAssignment)
            )
        }

        if (!silent && pluginData.configState.discord?.state?.notifyOnAssign == true) {
            pluginData.asyncScope.launch {
                tryNoCatch {
                    pluginData.configState.discord?.assignUpdate(sender.name, assignmentID, shownAssignment)
                }
            }
        }

        NotifyParams(
            silent = silent,
            sender = sender,
            basicTicket = ticketHandler,
            senderLambda = {
                it.notifyTicketAssignSuccess
                    .replace("%id%", assignmentID)
                    .replace("%assign%", shownAssignment)
                    .run(::toColouredAdventure)
            },
            massNotifyLambda = {
                it.notifyTicketAssignEvent
                    .replace("%user%", sender.name)
                    .replace("%id%", assignmentID)
                    .replace("%assign%", shownAssignment)
                    .run(::toColouredAdventure)
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
        ticketHandler: BasicTicket,
    ): NotifyParams {
        return allAssignVariations(sender, silent, args[1], sender.name, ticketHandler)
    }

    // /ticket close <ID> [Comment...]
    private suspend fun close(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: BasicTicket
    ): NotifyParams = coroutineScope {
        val newCreatorStatusUpdate = sender.nonCreatorMadeChange(ticketHandler.creatorUUID) && pluginData.configState.allowUnreadTicketUpdates
        if (newCreatorStatusUpdate != ticketHandler.creatorStatusUpdate) {
            launchIndependent { database.setCreatorStatusUpdate(ticketHandler.id, newCreatorStatusUpdate) }
        }

        return@coroutineScope if (args.size >= 3)
            closeWithComment(sender, args, silent, ticketHandler)
        else closeWithoutComment(sender, args, silent, ticketHandler)
    }

    private suspend fun closeWithComment(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: BasicTicket,
    ): NotifyParams = coroutineScope {
        val message = args.subList(2, args.size)
            .joinToString(" ")
            .let(::stripColour)

        if (!silent && pluginData.configState.discord?.state?.notifyOnClose == true) {
            launchIndependent {
                tryNoCatch {
                    pluginData.configState.discord?.closeUpdate(sender.name, ticketHandler.id.toString(), message)
                }
            }
        }

        launchIndependent {
            pluginData.configState.database.run {
                addAction(
                    ticketID = ticketHandler.id,
                    action = FullTicket.Action(FullTicket.Action.Type.COMMENT, sender.toUUIDOrNull(), message)
                )
                addAction(
                    ticketID = ticketHandler.id,
                    action = FullTicket.Action(FullTicket.Action.Type.CLOSE, sender.toUUIDOrNull())
                )
                database.setStatus(ticketHandler.id, BasicTicket.Status.CLOSED)
            }
        }

        NotifyParams(
            silent = silent,
            sender = sender,
            basicTicket = ticketHandler,
            senderLambda = {
                it.notifyTicketCloseWCommentSuccess
                    .replace("%id%", args[1])
                    .run(::toColouredAdventure)
            },
            massNotifyLambda = {
                it.notifyTicketCloseWCommentEvent
                    .replace("%user%", sender.name)
                    .replace("%id%", args[1])
                    .replace("%message%", message)
                    .run(::toColouredAdventure)
            },
            creatorLambda = {
                it.notifyTicketModificationEvent
                    .replace("%id%", args[1])
                    .run(::toColouredAdventure)
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
            pluginData.configState.database.addAction(
                ticketID = ticketHandler.id,
                action = FullTicket.Action(FullTicket.Action.Type.CLOSE, sender.toUUIDOrNull())
            )
            database.setStatus(ticketHandler.id, BasicTicket.Status.CLOSED)
        }

        if (!silent && pluginData.configState.discord?.state?.notifyOnClose == true) {
            launchIndependent {
                tryNoCatch {
                    pluginData.configState.discord?.closeUpdate(sender.name, ticketHandler.id.toString())
                }
            }
        }

        NotifyParams(
            silent = silent,
            sender = sender,
            basicTicket = ticketHandler,
            creatorLambda = {
                it.notifyTicketModificationEvent
                    .replace("%id%", args[1])
                    .run(::toColouredAdventure)
            },
            massNotifyLambda = {
                it.notifyTicketCloseEvent
                    .replace("%user%", sender.name)
                    .replace("%id%", args[1])
                    .run(::toColouredAdventure)
            },
            senderLambda = {
                it.notifyTicketCloseSuccess
                    .replace("%id%", args[1])
                    .run(::toColouredAdventure)
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
        basicTicket: BasicTicket
    ): NotifyParams = coroutineScope {
        val lowerBound = args[1].toInt()
        val upperBound = args[2].toInt()

        launchIndependent { pluginData.configState.database.massCloseTickets(lowerBound, upperBound, sender.toUUIDOrNull(), this) }

        if (!silent && pluginData.configState.discord?.state?.notifyOnCloseAll == true) {
            launchIndependent {
                tryNoCatch {
                    pluginData.configState.discord?.closeAllUpdate(sender.name, "$lowerBound", "$upperBound")
                }
            }
        }

        NotifyParams(
            silent = silent,
            sender = sender,
            basicTicket = basicTicket,
            creatorLambda = null,
            senderLambda = {
                it.notifyTicketMassCloseSuccess
                    .replace("%low%", args[1])
                    .replace("%high%", args[2])
                    .run(::toColouredAdventure)
            },
            massNotifyLambda = {
                it.notifyTicketMassCloseEvent
                    .replace("%user%", sender.name)
                    .replace("%low%", args[1])
                    .replace("%high%", args[2])
                    .run(::toColouredAdventure)
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
        ticketHandler: BasicTicket,
    ): NotifyParams = coroutineScope {
        val message = args.subList(2, args.size)
            .joinToString(" ")
            .let(::stripColour)

        val newCreatorStatusUpdate = sender.nonCreatorMadeChange(ticketHandler.creatorUUID) && pluginData.configState.allowUnreadTicketUpdates
        if (newCreatorStatusUpdate != ticketHandler.creatorStatusUpdate) {
            launchIndependent { database.setCreatorStatusUpdate(ticketHandler.id, newCreatorStatusUpdate) }
        }

        launchIndependent {
            pluginData.configState.database.addAction(
                ticketID = ticketHandler.id,
                action = FullTicket.Action(FullTicket.Action.Type.COMMENT, sender.toUUIDOrNull(), message)
            )
        }

        if (!silent && pluginData.configState.discord?.state?.notifyOnComment == true) {
            launchIndependent {
                tryNoCatch {
                    pluginData.configState.discord?.commentUpdate(sender.name, ticketHandler.id.toString(), message)
                }
            }
        }

        NotifyParams(
            silent = silent,
            sender = sender,
            basicTicket = ticketHandler,
            creatorLambda = {
                it.notifyTicketModificationEvent
                    .replace("%id%", args[1])
                    .run(::toColouredAdventure)
            },
            senderLambda = {
                it.notifyTicketCommentSuccess
                    .replace("%id%", args[1])
                    .run(::toColouredAdventure)
            },
            massNotifyLambda = {
                it.notifyTicketCommentEvent
                    .replace("%user%", sender.name)
                    .replace("%id%", args[1])
                    .replace("%message%", message)
                    .run(::toColouredAdventure)
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
        val configState = pluginData.configState

        try {
            val sqLite: suspend () -> SQLite = { pluginData.configState.sqliteBuilder.build() as SQLite }
            val mySQL: suspend () -> MySQL? = { pluginData.configState.mySQLBuilder.build() as MySQL? }
            val memory: suspend () -> Memory? = { pluginData.configState.memoryBuilder.build() as Memory? }

            configState.database.migrateDatabase(
                scope = pluginData.asyncScope,
                to = type,
                sqLiteBuilder = sqLite,
                mySQLBuilder = mySQL,
                memoryBuilder = memory,
                onBegin = {
                    pluginData .pluginLocked.set(true)
                    pushMassNotify("ticketmanager.notify.info") {
                        it.informationDBConvertInit
                            .replace("%fromDB%", configState.database.type.name)
                            .replace("%toDB%", type.name)
                            .run(::toColouredAdventure)
                    }
                },
                onComplete = {
                    pluginData.pluginLocked.set(false)
                    pushMassNotify("ticketmanager.notify.info") {
                        it.informationDBConvertSuccess.run(::toColouredAdventure)
                    }
                }
            )
        } catch (e: Exception) {
            pluginData.pluginLocked.set(false)
            throw e
        }
    }

    // /ticket create <Message…>
    private suspend fun create(
        sender: Sender,
        args: List<String>,
    ): NotifyParams = coroutineScope {
        val message = args.subList(1, args.size)
            .joinToString(" ")
            .let(::stripColour)

        val ticket = if (sender is Player) BasicTicketImpl(creatorUUID = sender.uniqueID, location = sender.location)
        else BasicTicketImpl(creatorUUID = null, location = null)

        val deferredID = async { pluginData.configState.database.addNewTicket(ticket, this, message) }
        pluginData.ticketCountMetrics.run { set(get() + 1) }
        val id = deferredID.await().toString()

        if (pluginData.configState.discord?.state?.notifyOnCreate == true) {
            launchIndependent {
                tryNoCatch {
                    pluginData.configState.discord?.createUpdate(sender.name, id, message)
                }
            }
        }

        NotifyParams(
            silent = false,
            sender = sender,
            basicTicket = ticket,
            creatorLambda = null,
            senderLambda = {
                it.notifyTicketCreationSuccess
                    .replace("%id%", id)
                    .run(::toColouredAdventure)
            },
            massNotifyLambda = {
                it.notifyTicketCreationEvent
                    .replace("%user%", sender.name)
                    .replace("%id%", id)
                    .replace("%message%", message)
                    .run(::toColouredAdventure)
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
        val cc = pluginData.configState.localeHandler.mainColourCode
        val locale = sender.locale

        val component = buildComponent {
            text { formattedContent(locale.helpHeader) }
            text { formattedContent(locale.helpLine1) }

            if (hasSilentPerm) {
                text { formattedContent(locale.helpLine2) }
                text { formattedContent(locale.helpLine3) }
            }
            text { formattedContent(locale.helpSep) }

            locale.run {
                listOf( // Triple(silence-able, format, permissions)
                    Triple(true, "$commandWordAssign &f<$parameterID> <$parameterAssignment...>", listOf("ticketmanager.command.assign")),
                    Triple(true, "$commandWordClaim &f<$parameterID>", listOf("ticketmanager.command.claim")),
                    Triple(true, "$commandWordClose &f<$parameterID> &7[$parameterComment...]", listOf("ticketmanager.command.close.all", "ticketmanager.command.close.own")),
                    Triple(true, "$commandWordCloseAll &f<$parameterLowerID> <$parameterUpperID>", listOf("ticketmanager.command.closeAll")),
                    Triple(true, "$commandWordComment &f<$parameterID> <$parameterComment...>", listOf("ticketmanager.command.comment.all", "ticketmanager.command.comment.own")),
                    Triple(false, "$commandWordConvertDB &f<$parameterTargetDB>", listOf("ticketmanager.command.convertDatabase")),
                    Triple(false, "$commandWordCreate &f<$parameterComment...>", listOf("ticketmanager.command.create")),
                    Triple(false, commandWordHelp, listOf("ticketmanager.command.help")),
                    Triple(false, "$commandWordHistory &7[$parameterUser] [$parameterPage]", listOf("ticketmanager.command.history.all", "ticketmanager.command.history.own")),
                    Triple(false, "$commandWordList &7[$parameterPage]", listOf("ticketmanager.command.list")),
                    Triple(false, "$commandWordListAssigned &7[$parameterPage]", listOf("ticketmanager.command.list")),
                    Triple(false, "$commandWordListUnassigned &7[$parameterPage]", listOf("ticketmanager.command.list")),
                    Triple(false, commandWordReload, listOf("ticketmanager.command.reload")),
                    Triple(true, "$commandWordReopen &f<$parameterID>", listOf("ticketmanager.command.reopen")),
                    Triple(false, "$commandWordSearch &f<$parameterConstraints...>", listOf("ticketmanager.command.search")),
                    Triple(true, "$commandWordSetPriority &f<$parameterID> <$parameterLevel>", listOf("ticketmanager.command.setPriority")),
                    Triple(false, "$commandWordTeleport &f<$parameterID>", listOf("ticketmanager.command.teleport")),
                    Triple(true, "$commandWordUnassign &f<$parameterID>", listOf("ticketmanager.command.assign")),
                    Triple(false, "$commandWordView &f<$parameterID>", listOf("ticketmanager.command.view.all", "ticketmanager.command.view.own")),
                    Triple(false, "$commandWordDeepView &f<$parameterID>", listOf("ticketmanager.command.viewdeep.all", "ticketmanager.command.viewdeep.own"))
                )
            }
                .filter { it.third.any(sender::has) }
                .run { this + Triple(false, locale.commandWordVersion, "NA") }
                .map {
                    val commandString = "$cc/${locale.commandBase} ${it.second}"
                    if (hasSilentPerm)
                        if (it.first) "\n&a[✓] $commandString"
                        else "\n&c[✕] $commandString"
                    else "\n$commandString"
                }
                .forEach { text { formattedContent(it) } }
        }

        sender.sendMessage(component)
    }

    // /ticket history [User] [Page]
    private suspend fun history(
        sender: Sender,
        args: List<String>,
    ) {
        val locale = sender.locale

        coroutineScope {
            val targetName =
                if (args.size >= 2) args[1].takeIf { it != locale.consoleName } else sender.name.takeIf { sender is Player }
            val requestedPage = if (args.size >= 3) args[2].toInt() else 1

            // Leaves console as null. Otherwise attempts UUID grab or [PLAYERNOTFOUND]
            fun String.attemptToUUIDString() = getOnlinePlayers()
                .firstOrNull { equals(it.name) }
                ?.run { uniqueID.toString() }
                ?: "[PLAYERNOTFOUND]"

            val searchedUser = targetName?.attemptToUUIDString()

            val resultSize: Int
            val resultsChunked = pluginData.configState.database.searchDatabase(this, locale, listOf(locale.searchCreator to searchedUser)) { true }
                .sortedByDescending(BasicTicket::id)
                .also { resultSize = it.size }
                .chunked(6)

            val sentComponent = buildComponent {
                text {
                    formattedContent(
                        locale.historyHeader
                            .replace("%name%", targetName ?: locale.consoleName)
                            .replace("%count%", "$resultSize")
                    )
                }

                val actualPage = if (requestedPage >= 1 && requestedPage < resultsChunked.size) requestedPage else 1

                if (resultsChunked.isNotEmpty()) {
                    resultsChunked.getOrElse(requestedPage - 1) { resultsChunked[1] }.forEach { t ->
                        text {
                            formattedContent(
                                locale.historyEntry
                                    .let { "\n$it" }
                                    .replace("%id%", "${t.id}")
                                    .replace("%SCC%", t.status.colourCode)
                                    .replace("%status%", t.status.toLocaledWord(locale))
                                    .replace("%comment%", t.actions[0].message!!)
                                    .let { if (it.length > 80) "${it.substring(0, 81)}..." else it }
                            )
                            onHover { showText(Component.text(locale.clickViewTicket)) }
                            onClick {
                                action = ClickEvent.Action.RUN_COMMAND
                                value = locale.run { "/$commandBase $commandWordView ${t.id}" }
                            }
                        }
                    }

                    if (resultsChunked.size > 1) {
                        append(buildPageComponent(actualPage, resultsChunked.size, locale) {
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
        createGeneralList(args, sender.locale, sender.locale.listFormatHeader,
            getIDPriorityPair = Database::getOpenIDPriorityPairs,
            baseCommand = sender.locale.run{ { "/$commandBase $commandWordList " } }
        )
            .run(sender::sendMessage)
    }

    // /ticket listassigned [Page]
    private suspend fun listAssigned(
        sender: Sender,
        args: List<String>,
    ) {
        val groups: List<String> = if (sender is Player) sender.permissionGroups else listOf()

        createGeneralList(args, sender.locale, sender.locale.listFormatAssignedHeader,
            getIDPriorityPair = { it.getAssignedOpenIDPriorityPairs(sender.name, groups) },
            baseCommand = sender.locale.run { { "/$commandBase $commandWordListAssigned " } }
        )
            .run(sender::sendMessage)
    }

    private suspend fun listUnassigned(
        sender: Sender,
        args: List<String>,
    ) {
        createGeneralList(args, sender.locale, sender.locale.listFormatUnassignedHeader,
            getIDPriorityPair = Database::getUnassignedOpenIDPriorityPairs,
            baseCommand = sender.locale.run { { "/$commandBase $commandWordListUnassigned " } }
        )
            .run(sender::sendMessage)
    }

    // /ticket reload
    private suspend fun reload(
        sender: Sender
    ) {
        coroutineScope {
            try {
                pluginData.pluginLocked.set(true)
                pushMassNotify("ticketmanager.notify.info") {
                    it.informationReloadInitiated
                        .replace("%user%", sender.name)
                        .run(::toColouredAdventure)
                }

                val forceQuitJob = launch {
                    delay(30L * 1000L)

                    // Long standing task has occurred if it reaches this point
                    launch {
                        pushMassNotify("ticketmanager.notify.warning") {
                            it.warningsLongTaskDuringReload.run(::toColouredAdventure)
                        }
                        pluginData.jobCount.set(1)
                        pluginData.asyncDispatcher.cancelChildren()
                    }
                }

                // Waits for other tasks to complete
                while (pluginData.jobCount.get() > 1) delay(1000L)

                if (!forceQuitJob.isCancelled)
                    forceQuitJob.cancel("Tasks closed on time")

                pushMassNotify("ticketmanager.notify.info") {
                    it.informationReloadTasksDone.run(::toColouredAdventure)
                }
                pluginData.configState.database.closeDatabase()
                pluginData.configState.discord?.shutdown()
                pluginData.loadPlugin()

                pushMassNotify("ticketmanager.notify.info") {
                    it.informationReloadSuccess.run(::toColouredAdventure)
                }
                if (!sender.has("ticketmanager.notify.info")) {
                    sender.sendMessage(sender.locale.informationReloadSuccess)
                }
            } catch (e: Exception) {
                pushMassNotify("ticketmanager.notify.info") {
                    it.informationReloadFailure.run(::toColouredAdventure)
                }
                throw e
            }
        }
    }

    // /ticket reopen <ID>
    private suspend fun reopen(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: BasicTicket,
    ): NotifyParams = coroutineScope {
        val action = FullTicket.Action(FullTicket.Action.Type.REOPEN, sender.toUUIDOrNull())

        // Updates user status if needed
        val newCreatorStatusUpdate = sender.nonCreatorMadeChange(ticketHandler.creatorUUID) && pluginData.configState.allowUnreadTicketUpdates
        if (newCreatorStatusUpdate != ticketHandler.creatorStatusUpdate) {
            launchIndependent { database.setCreatorStatusUpdate(ticketHandler.id, newCreatorStatusUpdate) }
        }

        launchIndependent {
            pluginData.configState.database.addAction(ticketHandler.id, action)
            database.setStatus(ticketHandler.id, BasicTicket.Status.OPEN)
        }

        if (!silent && pluginData.configState.discord?.state?.notifyOnReopen == true) {
            launchIndependent {
                tryNoCatch {
                    pluginData.configState.discord?.reopenUpdate(sender.name, ticketHandler.id.toString())
                }
            }
        }

        NotifyParams(
            silent = silent,
            sender = sender,
            basicTicket = ticketHandler,
            creatorLambda = {
                it.notifyTicketModificationEvent
                    .replace("%id%", args[1])
                    .run(::toColouredAdventure)
            },
            senderLambda = {
                it.notifyTicketReopenSuccess
                    .replace("%id%", args[1])
                    .run(::toColouredAdventure)
            },
            massNotifyLambda = {
                it.notifyTicketReopenEvent
                    .replace("%user%", sender.name)
                    .replace("%id%", args[1])
                    .run(::toColouredAdventure)
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

        coroutineScope {
            fun String.attemptToUUIDString(): String? =
                if (equals(locale.consoleName)) null
                else getOnlinePlayers()
                    .firstOrNull { equals(it.name) }
                    ?.run { uniqueID.toString() }
                    ?: "[PLAYERNOTFOUND]"

            // Beginning of execution
            sender.sendMessage(text { formattedContent(locale.searchFormatQuerying) })

            // Input args mapped to valid search types
            val arguments = args.subList(1, args.size)
                .asSequence()
                .map { it.split(":", limit = 2) }
                .filter { it.size >= 2 }
                .associate { it[0] to it[1] }

            val mainTableConstrains = arguments
                .mapNotNull { (key, value) ->
                    when (key) {
                        locale.searchAssigned -> key to value
                        locale.searchCreator -> key to value.attemptToUUIDString()
                        locale.searchPriority -> value.toByteOrNull()?.run { key to this.toString() }
                        locale.searchStatus -> {
                            val constraintStatus = when (value) {
                                locale.statusOpen -> BasicTicket.Status.OPEN.name
                                locale.statusClosed -> BasicTicket.Status.CLOSED.name
                                else -> null
                            }
                            constraintStatus?.run { key to this }
                        }
                        else -> null
                    }
                }

            val functionConstraints = arguments
                .mapNotNull { (key, value) ->
                    when (key) {

                        locale.searchClosedBy -> {
                            val searchedUser = value.attemptToUUIDString();
                            { t: FullTicket -> t.actions.any{ it.type == FullTicket.Action.Type.CLOSE && it.user?.toString() == searchedUser } }
                        }

                        locale.searchLastClosedBy -> {
                            val searchedUser = value.attemptToUUIDString();
                            { t: FullTicket ->
                                t.actions.lastOrNull { e -> e.type == FullTicket.Action.Type.CLOSE }
                                    ?.run { user?.toString() == searchedUser }
                                    ?: false
                            }
                        }

                        locale.searchWorld -> { t: FullTicket -> t.location?.world?.equals(value) ?: false }

                        locale.searchTime -> {
                            val creationTime = relTimeToEpochSecond(value, locale);
                            { t: FullTicket -> t.actions[0].timestamp >= creationTime }
                        }

                        locale.searchKeywords -> {
                            val words = value.split(",");

                            { t: FullTicket ->
                                val comments = t.actions
                                    .filter { it.type == FullTicket.Action.Type.OPEN || it.type == FullTicket.Action.Type.COMMENT }
                                    .map { it.message!! }
                                words.map { w -> comments.any { it.lowercase().contains(w.lowercase()) } }
                                    .all { it }
                            }
                        }

                        else -> null
                    }
                }
            val composedSearch =
                if (functionConstraints.isNotEmpty())
                    { t: FullTicket -> functionConstraints.map { it(t) }.all { it } }
                else { _: FullTicket -> true }

            // Results Computation
            val resultSize: Int
            val chunkedTickets = pluginData.configState.database.searchDatabase(this, locale, mainTableConstrains, composedSearch)
                .sortedByDescending(BasicTicket::id)
                .apply { resultSize = size }
                .chunked(8)

            val page = arguments[locale.searchPage]?.toIntOrNull()
                .let { if (it != null && it >= 1 && it < chunkedTickets.size) it else 1 }
            val fixMSGLength = { t: FullTicket -> t.actions[0].message!!.run { if (length > 25) "${substring(0,21)}..." else this } }

            val sentComponent = buildComponent {

                // Initial header
                text {
                    formattedContent(
                        locale.searchFormatHeader.replace("%size%", "$resultSize")
                    )
                }

                // Adds entries
                if (chunkedTickets.isNotEmpty()) {
                    chunkedTickets[page-1]
                        .map {
                            val content = "\n${locale.searchFormatEntry}"
                                .replace("%PCC%", it.priority.colourCode)
                                .replace("%SCC%", it.status.colourCode)
                                .replace("%id%", "${it.id}")
                                .replace("%status%", it.status.toLocaledWord(locale))
                                .replace("%creator%", uuidToName(it.creatorUUID, sender.locale))
                                .replace("%assign%", it.assignedTo ?: "")
                                .replace("%world%", it.location?.world ?: "")
                                .replace("%time%", it.actions[0].timestamp.toLargestRelativeTime(locale))
                                .replace("%comment%", fixMSGLength(it))
                            it.id to content
                        }
                        .forEach {
                            text {
                                formattedContent(it.second)
                                onHover { showText(Component.text(locale.clickViewTicket)) }
                                onClick {
                                    action = ClickEvent.Action.RUN_COMMAND
                                    value = locale.run { "/$commandBase $commandWordView ${it.first}" }
                                }
                            }
                        }
                }

                // Implements pages if needed
                if (chunkedTickets.size > 1) {
                    val pageComponent = buildPageComponent(page, chunkedTickets.size, locale) {
                        // Removes page constraint and converts rest to key:arg
                        val constraints = arguments
                            .filter { it.key != locale.searchPage }
                            .map { (k, v) -> "$k:$v" }
                        "/${locale.commandBase} ${locale.commandWordSearch} $constraints ${locale.searchPage}:"
                    }
                    append(pageComponent)
                }
            }

            sender.sendMessage(sentComponent)
        }
    }

    // /ticket setpriority <ID> <Level>
    private suspend fun setPriority(
        sender: Sender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: BasicTicket,
    ): NotifyParams = coroutineScope {
        val newPriority = byteToPriority(args[2].toByte())

        launchIndependent {
            pluginData.configState.database.addAction(
                ticketID = ticketHandler.id,
                action = FullTicket.Action(FullTicket.Action.Type.SET_PRIORITY, sender.toUUIDOrNull(), args[2])
            )
            database.setPriority(ticketHandler.id, newPriority)
        }

        if (!silent && pluginData.configState.discord?.state?.notifyOnPriorityChange == true) {
            launchIndependent {
                tryNoCatch {
                    pluginData.configState.discord?.priorityChangeUpdate(sender.name, ticketHandler.id.toString(), newPriority)
                }
            }
        }

        NotifyParams(
            silent = silent,
            sender = sender,
            basicTicket = ticketHandler,
            creatorLambda = null,
            senderLambda = {
                it.notifyTicketSetPrioritySuccess
                    .replace("%id%", args[1])
                    .replace("%priority%", ticketHandler.run { newPriority.colourCode + newPriority.toLocaledWord(it) })
                    .run(::toColouredAdventure)
            },
            massNotifyLambda = {
                it.notifyTicketSetPriorityEvent
                    .replace("%user%", sender.name)
                    .replace("%id%", args[1])
                    .replace("%priority%", ticketHandler.run { newPriority.colourCode + newPriority.toLocaledWord(it) })
                    .run(::toColouredAdventure)
            },
            creatorAlertPerm = "ticketmanager.notify.change.priority",
            massNotifyPerm =  "ticketmanager.notify.massNotify.priority"
        )
    }

    // /ticket teleport <ID>
    private suspend fun teleport(
        sender: Sender,
        basicTicket: BasicTicket,
    ) {
        if (sender is Player && basicTicket.location != null) {
            withContext(pluginData.mainDispatcher) {
                teleportToTicketLocation(sender, basicTicket.location!!)
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
        ticketHandler: BasicTicket,
    ) {
        coroutineScope {
            val fullTicket = ticketHandler.toFullTicket(database)
            val baseComponent = buildTicketInfoComponent(fullTicket, sender.locale)

            if (!sender.nonCreatorMadeChange(ticketHandler.creatorUUID) && ticketHandler.creatorStatusUpdate)
                launchIndependent { database.setCreatorStatusUpdate(ticketHandler.id, false) }

            val entries = fullTicket.actions.asSequence()
                .filter { it.type == FullTicket.Action.Type.COMMENT || it.type == FullTicket.Action.Type.OPEN }
                .map {
                    "\n${sender.locale.viewFormatComment}"
                        .replace("%user%", uuidToName(it.user, sender.locale))
                        .replace("%comment%", it.message!!)
                }
                .map { text { formattedContent(it) } }
                .reduce(TextComponent::append)

            sender.sendMessage(baseComponent.append(entries))
        }
    }

    // /ticket viewdeep <ID>
    private suspend fun viewDeep(
        sender: Sender,
        ticketHandler: BasicTicket,
    ) {
        coroutineScope {
            val fullTicket = ticketHandler.toFullTicket(database)
            val baseComponent = buildTicketInfoComponent(fullTicket, sender.locale)

            if (!sender.nonCreatorMadeChange(ticketHandler.creatorUUID) && ticketHandler.creatorStatusUpdate)
                launchIndependent { database.setCreatorStatusUpdate(ticketHandler.id, false) }

            fun formatDeepAction(action: FullTicket.Action): String {
                val result = when (action.type) {
                    FullTicket.Action.Type.OPEN, FullTicket.Action.Type.COMMENT ->
                        "\n${sender.locale.viewFormatDeepComment}"
                            .replace("%comment%", action.message!!)

                    FullTicket.Action.Type.SET_PRIORITY ->
                        "\n${sender.locale.viewFormatDeepSetPriority}"
                            .replace("%priority%",
                                byteToPriority(action.message!!.toByte()).run { colourCode + toLocaledWord(sender.locale) }
                            )

                    FullTicket.Action.Type.ASSIGN ->
                        "\n${sender.locale.viewFormatDeepAssigned}"
                            .replace("%assign%", action.message ?: "")

                    FullTicket.Action.Type.REOPEN -> "\n${sender.locale.viewFormatDeepReopen}"
                    FullTicket.Action.Type.CLOSE -> "\n${sender.locale.viewFormatDeepClose}"
                    FullTicket.Action.Type.MASS_CLOSE -> "\n${sender.locale.viewFormatDeepMassClose}"
                }
                return result
                    .replace("%user%", uuidToName(action.user, sender.locale))
                    .replace("%time%", action.timestamp.toLargestRelativeTime(sender.locale))
            }

            val finalMSG = fullTicket.actions.asSequence()
                .map(::formatDeepAction)
                .map(::toColouredAdventure)
                .reduce(TextComponent::append)
                .run(baseComponent::append)

            sender.sendMessage(finalMSG)
        }
    }

    private fun buildPageComponent(
        curPage: Int,
        pageCount: Int,
        locale: TMLocale,
        baseCommand: (TMLocale) -> String,
    ): Component {

        fun Component.addForward(): Component {
            return color(NamedTextColor.WHITE)
                .clickEvent(ClickEvent.runCommand(baseCommand(locale) + "${curPage + 1}"))
                .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text(locale.clickNextPage)))
        }

        fun Component.addBack(): Component {
            return color(NamedTextColor.WHITE)
                .clickEvent(ClickEvent.runCommand(baseCommand(locale) + "${curPage - 1}"))
                .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text(locale.clickBackPage)))
        }

        var back: Component = Component.text("[${locale.pageBack}]")
        var next: Component = Component.text("[${locale.pageNext}]")
        val separator = text {
            content("...............")
            color(NamedTextColor.DARK_GRAY)
        }
        val cc = pluginData.configState.localeHandler.mainColourCode
        val ofSection = text { formattedContent("$cc($curPage${locale.pageOf}$pageCount)") }

        when (curPage) {
            1 -> {
                back = back.color(NamedTextColor.DARK_GRAY)
                next = next.addForward()
            }
            pageCount -> {
                back = back.addBack()
                next = next.color(NamedTextColor.DARK_GRAY)
            }
            else -> {
                back = back.addBack()
                next = next.addForward()
            }
        }

        return Component.text("\n")
            .append(back)
            .append(separator)
            .append(ofSection)
            .append(separator)
            .append(next)
    }

    private fun createListEntry(
        ticket: FullTicket,
        locale: TMLocale
    ): Component {
        val creator = uuidToName(ticket.creatorUUID, locale)
        val fixedAssign = ticket.assignedTo ?: ""

        // Shortens comment preview to fit on one line
        val fixedComment = ticket.run {
            if (12 + id.toString().length + creator.length + fixedAssign.length + actions[0].message!!.length > 58)
                actions[0].message!!.substring(
                    0,
                    43 - id.toString().length - fixedAssign.length - creator.length
                ) + "..."
            else actions[0].message!!
        }

        return text {
            formattedContent(
                "\n${locale.listFormatEntry}"
                    .replace("%priorityCC%", ticket.priority.colourCode)
                    .replace("%ID%", "${ticket.id}")
                    .replace("%creator%", creator)
                    .replace("%assign%", fixedAssign)
                    .replace("%comment%", fixedComment)
            )
            onHover { showText(Component.text(locale.clickViewTicket)) }
            onClick {
                action = ClickEvent.Action.RUN_COMMAND
                value = locale.run { "/$commandBase $commandWordView ${ticket.id}" }
            }
        }
    }

    private suspend fun createGeneralList(
        args: List<String>,
        locale: TMLocale,
        headerFormat: String,
        getIDPriorityPair: suspend (Database) -> List<Pair<Int, Byte>>,
        baseCommand: (TMLocale) -> String
    ): Component {
        val chunkedIDs = getIDPriorityPair(pluginData.configState.database)
            .sortedWith(compareByDescending<Pair<Int, Byte>> { it.second }.thenByDescending { it.first } )
            .map { it.first }
            .chunked(8)
        val page = if (args.size == 2 && args[1].toInt() in 1..chunkedIDs.size) args[1].toInt() else 1

        val fullTickets = chunkedIDs.getOrNull(page - 1)
            ?.run { pluginData.configState.database.getFullTickets(this, pluginData.asyncScope) }
            ?: emptyList()

        return buildComponent {
            text { formattedContent(headerFormat) }

            if (fullTickets.isNotEmpty()) {
                fullTickets.forEach { append(createListEntry(it, locale)) }

                if (chunkedIDs.size > 1) {
                    append(buildPageComponent(page, chunkedIDs.size, locale, baseCommand))
                }
            }
        }
    }

    private fun buildTicketInfoComponent(
        ticket: FullTicket,
        locale: TMLocale,
    ) = buildComponent {
        text {
            formattedContent(
                "\n${locale.viewFormatHeader}"
                    .replace("%num%", "${ticket.id}")
            )
        }
        text { formattedContent("\n${locale.viewFormatSep1}") }
        text {
            formattedContent(
                "\n${locale.viewFormatInfo1}"
                    .replace("%creator%", uuidToName(ticket.creatorUUID, locale))
                    .replace("%assignment%", ticket.assignedTo ?: "")
            )
        }
        text {
            formattedContent(
                "\n${locale.viewFormatInfo2}"
                    .replace("%priority%", ticket.priority.run { colourCode + toLocaledWord(locale) })
                    .replace("%status%", ticket.status.run { colourCode + toLocaledWord(locale) })
            )
        }
        text {
            formattedContent(
                "\n${locale.viewFormatInfo3}"
                    .replace("%location%", ticket.location?.toString() ?: "")
            )

            if (ticket.location != null) {
                onHover { showText(Component.text(locale.clickTeleport)) }
                onClick {
                    action = ClickEvent.Action.RUN_COMMAND
                    value = locale.run { "/$commandBase $commandWordTeleport ${ticket.id}" }
                }
            }
        }
        text { formattedContent("\n${locale.viewFormatSep2}") }
    }


    private inline fun launchIndependent(crossinline f: suspend () -> Unit) {
        pluginData.asyncScope.launch { f() }
    }

    abstract fun pushMassNotify(permission: String, localeMsg: (TMLocale) -> Component)
    abstract fun buildPlayer(uuid: UUID): Player?
    abstract fun getOnlinePlayers(): List<Player>
    abstract fun stripColour(msg: String): String
    abstract fun offlinePlayerNameToUUIDOrNull(name: String): UUID?
    abstract fun uuidToName(uuid: UUID?, locale: TMLocale): String
    abstract fun teleportToTicketLocation(player: Player, loc: BasicTicket.TicketLocation)
}

/*-------------------------*/
/*     Other Functions     */
/*-------------------------*/

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
    catch(e: Exception) {}