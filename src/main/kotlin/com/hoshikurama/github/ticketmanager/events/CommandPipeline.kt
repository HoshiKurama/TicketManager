package com.hoshikurama.github.ticketmanager.events

import com.hoshikurama.github.ticketmanager.*
import com.hoshikurama.github.ticketmanager.databases.Database
import com.hoshikurama.github.ticketmanager.ticket.*
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

class Commands : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args1: Array<out String>): Boolean {
        val senderLocale = getLocale(sender)
        val args = args1.toList()

        if (args.isEmpty()) {
            sender.sendMessage(senderLocale.warningsInvalidCommand)
            return false
        }

        if (anyLocksPresent()) {
            sender.sendMessage(senderLocale.warningsLocked)
            return false
        }

        // Attempts to grab ticket and filters out invalid number for ticket
        val attemptTicket = getTicketHandler(args, senderLocale)
        val pseudoTicket: TicketHandler
        if (attemptTicket == null) {
            senderLocale.warningsInvalidNumber.sendColouredMessageTo(sender)
            return false
        } else pseudoTicket = attemptTicket

        if (args.isEmpty()) {
            help(sender, senderLocale)
            return false
        }

        // Shortened Functions
        val hasValidPermission = { hasValidPermission(sender, pseudoTicket, args, senderLocale) }
        val isValidCommand = { isValidCommand(sender, pseudoTicket, args, senderLocale) }
        val notUnderCooldown = { notUnderCooldown(sender, senderLocale, args) }
        val executeCommand = { executeCommand(sender, args, senderLocale, pseudoTicket) }

        // Main Bukkit Task
        Bukkit.getScheduler().runTaskAsynchronously(mainPlugin, Runnable {
            try {
                if (notUnderCooldown() && isValidCommand() && hasValidPermission())
                    executeCommand()?.let { pushNotifications(sender, it, senderLocale, pseudoTicket) }
            } catch (e: Exception) {
                e.printStackTrace()
                postModifiedStacktrace(e)
                sender.sendMessage(senderLocale.warningsUnexpectedError)
            }
        })

        return false
    }

    private fun getTicketHandler(args: List<String>, senderLocale: TMLocale): TicketHandler? {
        val id: Int? = when (args[0]) {
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
            senderLocale.commandWordDeepView -> args.getOrNull(1)?.toIntOrNull()
            else -> 0
        }

        return id?.let(::TicketHandler)
    }

    private fun hasValidPermission(
        sender: CommandSender,
        ticketHandler: TicketHandler,
        args: List<String>,
        senderLocale: TMLocale
    ): Boolean {
        if (sender !is Player) return true

        fun has(perm: String) = sender.has(perm)
        fun hasSilent() = has("ticketmanager.commandArg.silence")
        fun hasDuality(basePerm: String): Boolean {
            val senderUUID = sender.toUUIDOrNull()
            val ownsTicket = ticketHandler.UUIDMatches(senderUUID)
            return has("$basePerm.all") || (sender.has("$basePerm.own") && ownsTicket)
        }

        return senderLocale.run {
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
                commandWordList -> has("ticketmanager.command.list")
                commandWordListAssigned -> has("ticketmanager.command.list")
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
                    sender.has("ticketmanager.command.history.all") ||
                    sender.has("ticketmanager.command.history.own").let { hasPerm ->
                            if (args.size >= 2) hasPerm && args[1] == sender.name
                            else hasPerm
                    }
                else -> true
            }
        }
            .also { if (!it) senderLocale.warningsNoPermission.sendColouredMessageTo(sender) }
    }

    private fun isValidCommand(
        sender: CommandSender,
        ticketHandler: TicketHandler,
        args: List<String>,
        senderLocale: TMLocale
    ): Boolean {
        val dbRef = pluginState.database
        fun invalidCommand() = senderLocale.warningsInvalidCommand.sendColouredMessageTo(sender)
        fun notANumber() = senderLocale.warningsInvalidNumber.sendColouredMessageTo(sender)
        fun notATicket() = senderLocale.warningsInvalidID.sendColouredMessageTo(sender)
        fun outOfBounds() = senderLocale.warningsPriorityOutOfBounds.sendColouredMessageTo(sender)
        fun ticketClosed() = senderLocale.warningsTicketAlreadyClosed.sendColouredMessageTo(sender)
        fun ticketOpen() = senderLocale.warningsTicketAlreadyOpen.sendColouredMessageTo(sender)

        return senderLocale.run {
            when (args[0]) {
                commandWordAssign, commandWordSilentAssign ->
                    check(::invalidCommand) { args.size >= 3 }
                        .thenCheck(::notATicket) { dbRef.isValidID(args[1].toInt()) }
                        .thenCheck(::ticketClosed) { ticketHandler.status != Ticket.Status.CLOSED }

                commandWordClaim, commandWordSilentClaim ->
                    check(::invalidCommand) { args.size == 2 }
                        .thenCheck(::notATicket) { pluginState.database.isValidID(args[1].toInt()) }
                        .thenCheck(::ticketClosed) { ticketHandler.status != Ticket.Status.CLOSED }

                commandWordClose, commandWordSilentClose ->
                    check(::invalidCommand) { args.size >= 2 }
                        .thenCheck(::notATicket) { pluginState.database.isValidID(args[1].toInt()) }
                        .thenCheck(::ticketClosed) { ticketHandler.status != Ticket.Status.CLOSED }

                commandWordComment, commandWordSilentComment ->
                    check(::invalidCommand) { args.size >= 3 }
                        .thenCheck(::notATicket) { pluginState.database.isValidID(args[1].toInt()) }
                        .thenCheck(::ticketClosed) { ticketHandler.status != Ticket.Status.CLOSED }

                commandWordCloseAll, commandWordSilentCloseAll ->
                    check(::invalidCommand) { args.size == 3 }
                        .thenCheck(::notANumber) { args[1].toIntOrNull() != null }
                        .thenCheck(::notANumber) { args[2].toIntOrNull() != null }

                commandWordReopen, commandWordSilentReopen ->
                    check(::invalidCommand) { args.size == 2 }
                        .thenCheck(::notATicket) { pluginState.database.isValidID(args[1].toInt()) }
                        .thenCheck(::ticketOpen) { ticketHandler.status != Ticket.Status.OPEN }

                commandWordSetPriority, commandWordSilentSetPriority ->
                    check(::invalidCommand) { args.size == 3 }
                        .thenCheck(::outOfBounds) { args[2].toByteOrNull() != null }
                        .thenCheck(::notATicket) { pluginState.database.isValidID(args[1].toInt()) }
                        .thenCheck(::ticketClosed) { ticketHandler.status != Ticket.Status.CLOSED }

                commandWordUnassign, commandWordSilentUnassign ->
                    check(::invalidCommand) { args.size == 2 }
                        .thenCheck(::notATicket) { pluginState.database.isValidID(args[1].toInt()) }
                        .thenCheck(::ticketClosed) { ticketHandler.status != Ticket.Status.CLOSED }

                commandWordView ->
                    check(::invalidCommand) { args.size == 2 }
                        .thenCheck(::notATicket) { pluginState.database.isValidID(args[1].toInt()) }

                commandWordDeepView ->
                    check(::invalidCommand) { args.size == 2 }
                        .thenCheck(::notATicket) { pluginState.database.isValidID(args[1].toInt()) }

                commandWordTeleport ->
                    check(::invalidCommand) { args.size == 2 }
                        .thenCheck(::notATicket) { pluginState.database.isValidID(args[1].toInt()) }

                commandWordCreate -> check(::invalidCommand) { args.size >= 2 }

                commandWordHistory ->
                    check(::invalidCommand) { args.isNotEmpty() }
                        .thenCheck(::notANumber) { if (args.size >= 3) args[2].toIntOrNull() != null else true}

                commandWordList ->
                    check(::notANumber) { if (args.size == 2) args[1].toIntOrNull() != null else true }

                commandWordListAssigned ->
                    check(::notANumber) { if (args.size == 2) args[1].toIntOrNull() != null else true }

                commandWordSearch -> check(::invalidCommand) { args.size >= 2}

                commandWordReload -> true
                commandWordVersion -> true
                commandWordHelp -> true

                commandWordConvertDB ->
                    check(::invalidCommand) { args.size == 2 }
                        .thenCheck( { senderLocale.warningsInvalidDBType.sendColouredMessageTo(sender) },
                            {
                                try { Database.Types.valueOf(args[1]); true }
                                catch (e: Exception) { false }
                            }
                        )
                        .thenCheck( { senderLocale.warningsConvertToSameDBType.sendColouredMessageTo(sender) } )
                            { pluginState.database.type != Database.Types.valueOf(args[1]) }

                else -> false.also { invalidCommand() }
            }
        }
    }

    private fun notUnderCooldown(
        sender: CommandSender,
        senderLocale: TMLocale,
        args: List<String>
    ): Boolean {
        val underCooldown = when (args[0]) {
            senderLocale.commandWordCreate,
            senderLocale.commandWordComment,
            senderLocale.commandWordSilentComment ->
                pluginState.cooldowns.checkAndSet(sender.toUUIDOrNull())
            else -> false
        }

        return !underCooldown.also { if (it) senderLocale.warningsUnderCooldown.sendColouredMessageTo(sender) }
    }

    private fun executeCommand(
        sender: CommandSender,
        args: List<String>,
        senderLocale: TMLocale,
        ticketHandler: TicketHandler
    ): NotifyParams? {
        return senderLocale.run {
            when (args[0]) {
                commandWordAssign -> assign(sender, args, false, senderLocale, ticketHandler)
                commandWordSilentAssign -> assign(sender, args, true, senderLocale, ticketHandler)
                commandWordClaim -> claim(sender, args, false, senderLocale, ticketHandler)
                commandWordSilentClaim -> claim(sender, args, true, senderLocale, ticketHandler)
                commandWordClose -> close(sender, args, false, ticketHandler)
                commandWordSilentClose -> close(sender, args, true, ticketHandler)
                commandWordCloseAll -> closeAll(sender, args, false)
                commandWordSilentCloseAll -> closeAll(sender, args, true)
                commandWordComment -> comment(sender, args, false, ticketHandler)
                commandWordSilentComment -> comment(sender, args, true, ticketHandler)
                commandWordCreate -> create(sender, args)
                commandWordHelp -> help(sender, senderLocale).let { null }
                commandWordHistory -> history(sender, args, senderLocale).let { null }
                commandWordList -> list(sender, args, senderLocale).let { null }
                commandWordListAssigned -> listAssigned(sender, args, senderLocale).let { null }
                commandWordReload -> reload(sender, senderLocale).let { null }
                commandWordReopen -> reopen(sender,args, false, ticketHandler)
                commandWordSilentReopen -> reopen(sender,args, true, ticketHandler)
                commandWordSearch -> search(sender, args, senderLocale).let { null }
                commandWordSetPriority -> setPriority(sender, args, false, ticketHandler)
                commandWordSilentSetPriority -> setPriority(sender, args, true, ticketHandler)
                commandWordTeleport -> teleport(sender, ticketHandler).let { null }
                commandWordUnassign -> unAssign(sender, args, false, senderLocale, ticketHandler)
                commandWordSilentUnassign -> unAssign(sender, args, true, senderLocale, ticketHandler)
                commandWordVersion -> version(sender, senderLocale).let { null }
                commandWordView -> view(sender, senderLocale, ticketHandler).let { null }
                commandWordDeepView -> viewDeep(sender, senderLocale, ticketHandler).let { null }
                commandWordConvertDB -> convertDatabase(args).let { null }
                else -> null
            }
        }
    }

    private fun pushNotifications(
        sender: CommandSender,
        params: NotifyParams,
        locale: TMLocale,
        ticketHandler: TicketHandler
    ) {
        params.run {
            if (sendSenderMSG)
                senderLambda!!.invoke(locale).sendColouredMessageTo(sender)

            if (sendCreatorMSG)
                ticketHandler.creatorUUID
                    ?.run(Bukkit::getPlayer)
                    ?.let { creatorLambda!!(getLocale(it)) }
                    ?.run { sendColouredMessageTo(creator!!) }

            if (sendMassNotifyMSG)
                pushMassNotify(massNotifyPerm, massNotifyLambda!!)
        }
    }

    private class NotifyParams(
        silent: Boolean,
        ticketHandler: TicketHandler,
        sender: CommandSender,
        creatorAlertPerm: String,
        val massNotifyPerm: String,
        val senderLambda: ((TMLocale) -> String)?,
        val creatorLambda: ((TMLocale) -> String)?,
        val massNotifyLambda: ((TMLocale) -> String)?,
    ) {
        val creator: Player? = ticketHandler.creatorUUID?.let(Bukkit::getPlayer)
        val sendSenderMSG: Boolean = (!sender.has(massNotifyPerm) || silent)
                && senderLambda != null
        val sendCreatorMSG: Boolean = sender.nonCreatorMadeChange(ticketHandler.creatorUUID)
                && !silent && (creator?.isOnline ?: false)
                && (creator?.has(creatorAlertPerm) ?: false)
                && (creator?.has(massNotifyPerm)?.run { !this } ?: false)
                && creatorLambda != null
        val sendMassNotifyMSG: Boolean = !silent
                && massNotifyLambda != null
    }

    /*-------------------------*/
    /*         Commands        */
    /*-------------------------*/

    // /ticket assign <ID> <Assignment>

    private fun allAssignVariations(
        sender: CommandSender,
        silent: Boolean,
        senderLocale: TMLocale,
        assignmentID: String,
        assignment: String?,
        ticketHandler: TicketHandler
    ): NotifyParams {
        val shownAssignment = assignment ?: senderLocale.miscNobody

        pluginState.database.run {
            setAssignment(ticketHandler.id, assignment)
            addAction(ticketHandler.id, Ticket.Action(Ticket.Action.Type.ASSIGN, sender.toUUIDOrNull(), assignment))
        }

        return NotifyParams(
            silent = silent,
            sender = sender,
            ticketHandler = ticketHandler,
            senderLambda = {
                it.notifyTicketAssignSuccess
                    .replace("%id%", assignmentID)
                    .replace("%assign%", shownAssignment)
            },
            massNotifyLambda = {
                it.notifyTicketAssignEvent
                    .replace("%user%", sender.name)
                    .replace("%id%", assignmentID)
                    .replace("%assign%", shownAssignment)
            },
            creatorLambda = null,
            creatorAlertPerm = "ticketmanager.notify.change.assign",
            massNotifyPerm = "ticketmanager.notify.massNotify.assign"
        )
    }

    private fun assign(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        senderLocale: TMLocale,
        ticketHandler: TicketHandler
    ): NotifyParams {
        val sqlAssignment = args.subList(2, args.size)
            .joinToString(" ")
        return allAssignVariations(sender, silent, senderLocale, args[1], sqlAssignment, ticketHandler)
    }

    // /ticket claim <ID>
    private fun claim(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        senderLocale: TMLocale,
        ticketHandler: TicketHandler
    ): NotifyParams {
        return allAssignVariations(sender, silent, senderLocale, args[1], sender.name, ticketHandler)
    }

    // /ticket close <ID> [Comment...]
    private fun close(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: TicketHandler
    ): NotifyParams {
        ticketHandler.statusUpdateForCreator = sender.nonCreatorMadeChange(ticketHandler.creatorUUID) && pluginState.allowUnreadTicketUpdates
        return if (args.size >= 3)
            closeWithComment(sender, args, silent, ticketHandler)
        else closeWithoutComment(sender, args, silent, ticketHandler)
    }

    private fun closeWithComment(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: TicketHandler
    ): NotifyParams {
        val message = args.subList(2, args.size)
            .joinToString(" ")
            .run(::stripColour)

        pluginState.database.addAction(
            ticketID = ticketHandler.id,
            action = Ticket.Action(Ticket.Action.Type.COMMENT, sender.toUUIDOrNull(), message)
        )

        pluginState.database.addAction(
            ticketID = ticketHandler.id,
            action = Ticket.Action(Ticket.Action.Type.CLOSE, sender.toUUIDOrNull())
        )
        ticketHandler.status = Ticket.Status.CLOSED

        return NotifyParams(
            silent = silent,
            sender = sender,
            ticketHandler = ticketHandler,
            senderLambda = {
                it.notifyTicketCloseWCommentSuccess
                    .replace("%id%", args[1])
            },
            massNotifyLambda = {
                it.notifyTicketCloseWCommentEvent
                    .replace("%user%", sender.name)
                    .replace("%id%", args[1])
                    .replace("%message%", message)
            },
            creatorLambda = {
                it.notifyTicketModificationEvent
                    .replace("%id%", args[1])
            },
            massNotifyPerm = "ticketmanager.notify.massNotify.close",
            creatorAlertPerm = "ticketmanager.notify.change.close"
        )
    }

    private fun closeWithoutComment(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: TicketHandler
    ): NotifyParams {
        pluginState.database.addAction(
            ticketID = ticketHandler.id,
            action = Ticket.Action(Ticket.Action.Type.CLOSE, sender.toUUIDOrNull())
        )
        ticketHandler.status = Ticket.Status.CLOSED

        return NotifyParams(
            silent = silent,
            sender = sender,
            ticketHandler = ticketHandler,
            creatorLambda = {
                it.notifyTicketModificationEvent
                    .replace("%id%", args[1])
            },
            massNotifyLambda = {
                it.notifyTicketCloseEvent // %user% %id%
                    .replace("%user%", sender.name)
                    .replace("%id%", args[1])
            },
            senderLambda = {
                it.notifyTicketCloseSuccess
                    .replace("%id%", args[1])
            },
            massNotifyPerm = "ticketmanager.notify.massNotify.close",
            creatorAlertPerm = "ticketmanager.notify.change.close"
        )
    }

    // /ticket closeall <Lower ID> <Upper ID>
    private fun closeAll(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean
    ): NotifyParams {
        val lowerBound = args[1].toInt()
        val upperBound = args[2].toInt()

        pluginState.database.massCloseTickets(lowerBound, upperBound, sender.toUUIDOrNull())

        return NotifyParams(
            silent = silent,
            sender = sender,
            ticketHandler = TicketHandler(-1),
            creatorLambda = null,
            senderLambda = {
                it.notifyTicketMassCloseSuccess
                    .replace("%low%", args[1])
                    .replace("%high%", args[2])
            },
            massNotifyLambda = {
                it.notifyTicketMassCloseEvent
                    .replace("%user%", sender.name)
                    .replace("%low%", args[1])
                    .replace("%high%", args[2])
            },
            massNotifyPerm = "ticketmanager.notify.massNotify.massClose",
            creatorAlertPerm = "ticketmanager.notify.change.massClose"
        )
    }

    // /ticket comment <ID> <Comment…>
    private fun comment(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: TicketHandler
    ): NotifyParams {
        val message = args.subList(2, args.size)
            .joinToString(" ")
            .run(::stripColour)
        val nonCreatorMadeChange = sender.nonCreatorMadeChange(ticketHandler.creatorUUID)

        pluginState.database.addAction(
            ticketID = ticketHandler.id,
            action = Ticket.Action(Ticket.Action.Type.COMMENT, sender.toUUIDOrNull(), message)
        )

        ticketHandler.statusUpdateForCreator = nonCreatorMadeChange && pluginState.allowUnreadTicketUpdates

        return NotifyParams(
            silent = silent,
            sender = sender,
            ticketHandler = ticketHandler,
            creatorLambda = {
                it.notifyTicketModificationEvent
                    .replace("%id%", args[1])
            },
            senderLambda = {
                it.notifyTicketCommentSuccess
                    .replace("%id%", args[1])
            },
            massNotifyLambda = {
                it.notifyTicketCommentEvent
                    .replace("%user%", sender.name)
                    .replace("%id%", args[1])
                    .replace("%message%", message)
            },
            massNotifyPerm = "ticketmanager.notify.massNotify.comment",
            creatorAlertPerm = "ticketmanager.notify.change.comment"
        )
    }

    // /ticket convertdatabase <Target Database>
    private fun convertDatabase(
        args: List<String>,
    ) {
        val type = args[1].run(Database.Types::valueOf)
        pluginState.database.migrateDatabase(type)
    }

    // /ticket create <Message…>
    private fun create(
        sender: CommandSender,
        args: List<String>
    ): NotifyParams {
        val message = args.subList(1, args.size)
            .joinToString(" ")
            .run(::stripColour)
        val action = Ticket.Action(Ticket.Action.Type.OPEN, sender.toUUIDOrNull(), message)
        val ticket = Ticket(sender.toUUIDOrNull(), sender.toLocationOrNull())
        val id = pluginState.database.addTicket(ticket, action).toString()

        mainPlugin.ticketCountMetrics.incrementAndGet()
        return NotifyParams(
            silent = false,
            sender = sender,
            ticketHandler = TicketHandler(-1),
            creatorLambda = null,
            senderLambda = {
                it.notifyTicketCreationSuccess
                .replace("%id%", id)
            },
            massNotifyLambda = {
                it.notifyTicketCreationEvent
                    .replace("%user%", sender.name)
                    .replace("%id%", id)
                    .replace("%message%", message)
            },
            creatorAlertPerm = "ticketmanager.NO NODE",
            massNotifyPerm = "ticketmanager.notify.massNotify.create",
        )
    }

    // /ticket help
    private fun help(sender: CommandSender, locale: TMLocale) {
        val hasSilentPerm = sender.has("ticketmanager.commandArg.silence")
        val cc = pluginState.enabledLocales.colourCode

        // Builds base header
        val sentComponent = TextComponent(locale.helpHeader)
        sentComponent.addExtra(locale.helpLine1)
        if (hasSilentPerm) {
            sentComponent.addExtra(locale.helpLine2)
            sentComponent.addExtra(locale.helpLine3)
        }
        sentComponent.addExtra(locale.helpSep)

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
            .map(::toColour)
            .map(::TextComponent)
            .forEach { sentComponent.addExtra(it) }

        sender.sendPlatformMessage(sentComponent)
    }

    // /ticket history [User] [Page]
    private fun history(
        sender: CommandSender,
        args: List<String>,
        locale: TMLocale
    ) {
        val targetName = if (args.size >= 2) args[1].takeIf { it != locale.consoleName } else sender.name.takeIf { sender is Player }
        val requestedPage = if (args.size >= 3) args[2].toInt() else 1

        // Leaves console as null. Otherwise attempts UUID grab or UUID_NOT_FOUND
        val uuidAsString = targetName?.run { getUUUIDStringOrNull(this) ?: "UUID_NOT_FOUND" } ?: "NULL"

        val resultSize: Int
        val resultsChunked = pluginState.database.searchDB(mapOf("creator" to uuidAsString))
            .sortedByDescending(Ticket::id)
            .also { resultSize = it.size }
            .chunked(6)

        val sentComponent = locale.historyHeader
            .replace("%name%", targetName ?: locale.consoleName)
            .replace("%count%", "$resultSize")
            .run(::toColour)
            .run(::TextComponent)

        val actualPage = if (requestedPage >= 1 && requestedPage < resultsChunked.size) requestedPage else 1

        if (resultsChunked.isNotEmpty()) {
            resultsChunked.getOrElse(requestedPage - 1) { resultsChunked[1] }
                .forEach {
                    locale.historyEntry
                        .run { "\n$this" }
                        .replace("%id%", "${it.id}")
                        .replace("%SCC%", it.status.getColourCode())
                        .replace("%status%", it.status.toColouredString(locale))
                        .replace("%comment%", it.actions[0].message!!
                            .run { if (length > 80) "${substring(0, 81)}..." else this } )
                        .run(::toColour)
                        .run(::TextComponent)
                        .apply { addViewTicketOnClick(it.id, locale) }
                        .apply { sentComponent.addExtra(this) }
                }

            if (resultsChunked.size > 1) {
                buildPageComponent(actualPage, resultsChunked.size, locale) { "/${it.commandBase} ${it.commandWordHistory} ${targetName ?: it.consoleName} " }
                    .apply(sentComponent::addExtra)
            }
        }

        sender.sendPlatformMessage(sentComponent)
    }

    // /ticket list [Page]
    private fun list(
        sender: CommandSender,
        args: List<String>,
        locale: TMLocale
    ) {
        val sentMSG = createGeneralList(args, locale, locale.listFormatHeader,
            getTickets = { db -> db.getOpen() },
            baseCommand = locale.run{ { "/$commandBase $commandWordList " } }
        )

        sender.sendPlatformMessage(sentMSG)
    }

    private fun createGeneralList(
        args: List<String>,
        locale: TMLocale,
        headerFormat: String,
        getTickets: (Database) -> List<Ticket>,
        baseCommand: (TMLocale) -> String
    ): TextComponent {
        val chunkedTickets = getTickets(pluginState.database).chunked(8)
        val page = if (args.size == 2 && args[1].toInt() in 1..chunkedTickets.size) args[1].toInt() else 1

        val sentMSG = headerFormat
            .run(::toColour)
            .run(::TextComponent)

        if (chunkedTickets.isNotEmpty()) {
            chunkedTickets[page - 1]
                .map { createListEntry(it, locale) }
                .forEach { sentMSG.addExtra(it) }
        }

        if (chunkedTickets.size > 1) {
            sentMSG.addExtra(buildPageComponent(page, chunkedTickets.size, locale, baseCommand))
        }

        return sentMSG
    }

    private fun buildPageComponent(
        curPage: Int,
        pageCount: Int,
        locale: TMLocale,
        baseCommand: (TMLocale) -> String,
    ): TextComponent {
        fun TextComponent.addForward() {
            this.color = ChatColor.WHITE
            this.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, baseCommand(locale) + "${curPage + 1}")
            this.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(locale.clickNextPage))
        }
        fun TextComponent.addBack() {
            this.color = ChatColor.WHITE
            this.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, baseCommand(locale) + "${curPage - 1}")
            this.hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(locale.clickBackPage))
        }

        val back = TextComponent("[${locale.pageBack}]")
        val next = TextComponent("[${locale.pageNext}]")

        val separator = TextComponent("...............")
        separator.color = ChatColor.DARK_GRAY

        val cc = pluginState.enabledLocales.colourCode
        val ofSection = "$cc($curPage${locale.pageOf}$pageCount)"
            .run(::toColour)
            .run(::TextComponent)

        when (curPage) {
            1 -> {
                back.color = ChatColor.DARK_GRAY
                next.addForward()
            }
            pageCount -> {
                back.addBack()
                next.color = ChatColor.DARK_GRAY
            }
            else -> {
                back.addBack()
                next.addForward()
            }
        }

        val textComponent = TextComponent("\n")
        textComponent.apply {
            addExtra(back)
            addExtra(separator)
            addExtra(ofSection)
            addExtra(separator)
            addExtra(next)
        }

        return textComponent
    }


    private fun createListEntry(ticket: Ticket, locale: TMLocale): TextComponent {
        val creator = ticket.creatorUUID.toName(locale)
        val fixedAssign = ticket.assignedTo ?: ""

        // Shortens comment preview to fit on one line
        val fixedComment = ticket.run {
            if (12 + id.toString().length + creator.length + fixedAssign.length + actions[0].message!!.length > 58)
                actions[0].message!!.substring(0, 43 - id.toString().length - fixedAssign.length - creator.length) + "..."
            else actions[0].message!!
        }

        return locale.listFormatEntry
            .run { "\n$this" }
            .replace("%priorityCC%", ticket.priority.getColourCode())
            .replace("%ID%", "${ticket.id}")
            .replace("%creator%", creator)
            .replace("%assign%", fixedAssign)
            .replace("%comment%", fixedComment)
            .run(::toColour)
            .run(::TextComponent)
            .apply { addViewTicketOnClick(ticket.id, locale) }
    }

    // /ticket listassigned [Page]
    private fun listAssigned(
        sender: CommandSender,
        args: List<String>,
        locale: TMLocale
    ) {
        val groups = if (sender is Player)
            mainPlugin.perms.getPlayerGroups(sender).map { "::$it" }
        else listOf()

        val sentMSG = createGeneralList(args, locale, locale.listFormatAssignedHeader,
            getTickets = { db -> db.getOpenAssigned(sender.name, groups ) },
            baseCommand = locale.run { { "/$commandBase $commandWordListAssigned " } }
        )

        sender.sendPlatformMessage(sentMSG)
    }

    // /ticket reload
    private fun reload(sender: CommandSender, locale: TMLocale) {
        mainPlugin.pluginLocked = true
        pushMassNotify("ticketmanager.notify.info", { it.informationReloadInitiated.replace("%user%", sender.name) } )

        // Forces async thread to wait for other TicketManager tasks to complete
        while (Bukkit.getScheduler().pendingTasks.filter { it.owner == mainPlugin }.size > 2)
            Thread.sleep(1000)

        pushMassNotify("ticketmanager.notify.info", { it.informationReloadTasksDone } )
        pluginState.database.closeDatabase()
        mainPlugin.configState = PluginState()

        pushMassNotify("ticketmanager.notify.info", { it.informationReloadSuccess })
        if (!sender.has("ticketmanager.notify.info"))
            locale.informationReloadSuccess.sendColouredMessageTo(sender)

        mainPlugin.pluginLocked = false
    }

    // /ticket reopen <ID>
    private fun reopen(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: TicketHandler
    ): NotifyParams {
        val action = Ticket.Action(Ticket.Action.Type.REOPEN, sender.toUUIDOrNull())

        ticketHandler.statusUpdateForCreator = sender.nonCreatorMadeChange(ticketHandler.creatorUUID) && pluginState.allowUnreadTicketUpdates
        pluginState.database.addAction(ticketHandler.id, action)
        ticketHandler.status = Ticket.Status.OPEN

        return NotifyParams(
            silent = silent,
            sender = sender,
            ticketHandler = ticketHandler,
            creatorLambda = {
                it.notifyTicketModificationEvent
                    .replace("%id%", args[1])
            },
            senderLambda = {
                it.notifyTicketReopenSuccess
                    .replace("%id%", args[1])
            },
            massNotifyLambda = {
                it.notifyTicketReopenEvent
                    .replace("%user%", sender.name)
                    .replace("%id%", args[1])
                },
            creatorAlertPerm = "ticketmanager.notify.change.reopen",
            massNotifyPerm = "ticketmanager.notify.massNotify.reopen",
        )
    }

    // /ticket search <Constraints…>
    private fun search(
        sender: CommandSender,
        args: List<String>,
        locale: TMLocale
    ) {
        sender.sendMessage(locale.searchFormatQuerying)
        val constraintTypes = locale.run {
            listOf(
                searchAssigned,
                searchCreator,
                searchKeywords,
                searchPriority,
                searchStatus,
                searchTime,
                searchWorld,
                searchPage
            )
        }

        fun convertStatus(str: String) = when (str) {
            locale.statusOpen -> Ticket.Status.OPEN.name
            locale.statusClosed -> Ticket.Status.CLOSED.name
            else -> str
        }
        fun convertPriority(str: String) = str.toByteOrNull()?.toString() ?: "0"
        fun convertCreator(str: String) = if (str == locale.consoleName) "NULL" else
            Bukkit.getOfflinePlayers().asSequence()
                .filter { it.name?.equals(str) ?: false }
                .map { it.uniqueId.toString() }
                .firstOrNull() ?: "[PLAYERNOTFOUND]"

        val localedConstraintMap = args.subList(1, args.size)
            .asSequence()
            .map { it.split(":", limit = 2) }
            .filter { it[0] in constraintTypes }
            .filter { it.size >= 2 }
            .associate { it[0] to it[1] }

        val sqlConstraintMap = localedConstraintMap
            .mapNotNull {
                when (it.key) {
                    locale.searchPage -> "page" to it.value
                    locale.searchWorld -> "world" to it.value
                    locale.searchCreator -> "creator" to convertCreator(it.value)
                    locale.searchAssigned -> "assignedto" to it.value
                    locale.searchKeywords -> "keywords" to it.value
                    locale.searchStatus -> "status" to convertStatus(it.value)
                    locale.searchPriority -> "priority" to convertPriority(it.value)
                    locale.searchTime -> "time" to "${relTimeToEpochSecond(it.value, locale)}"
                    else -> null
                }
            }
            .toMap()

        val resultSize: Int
        val chunkedTickets = pluginState.database.searchDB(sqlConstraintMap)
            .sortedByDescending(Ticket::id)
            .apply { resultSize = size }
            .chunked(8)


        val page = sqlConstraintMap["page"]?.toIntOrNull()
            .let { if (it != null && it >= 1 && it < chunkedTickets.size) it else 1 }
        // Initial header
        val sentComponent = TextComponent(locale.searchFormatHeader
            .replace("%size%", "$resultSize")
            .run(::toColour)
        )

        // Function for fixing messageLength
        val fixMSGLength = { t: Ticket -> t.actions[0].message!!.run { if (length > 25) "${substring(0,21)}..." else this } }

        // Adds entries
        if (chunkedTickets.isNotEmpty()) {
            chunkedTickets[page-1]
                .map {
                    locale.searchFormatEntry
                        .run { "\n$this" }
                        .replace("%PCC%", it.priority.getColourCode())
                        .replace("%SCC%", it.status.getColourCode())
                        .replace("%id%", "${it.id}")
                        .replace("%status%", it.status.toColouredString(locale))
                        .replace("%creator%", it.creatorUUID.toName(locale))
                        .replace("%assign%", it.assignedTo ?: "")
                        .replace("%world%", it.location?.world ?: "")
                        .replace("%time%", it.actions[0].timestamp.toLargestRelativeTime(locale))
                        .run(::toColour)
                        .replace("%comment%", fixMSGLength(it))
                        .run(::TextComponent)
                        .apply { addViewTicketOnClick(it.id, locale) }
                }
                .forEach { sentComponent.addExtra(it) }
        }

        // Implements pages if needed
        if (chunkedTickets.size > 1) {
            val pageComponent = buildPageComponent(page, chunkedTickets.size, locale) {
                // Removes page constraint and converts rest to key:arg
                val constraints = localedConstraintMap
                    .filter { it.key != locale.searchPage }
                    .map { (k,v) -> "$k:$v" }
                "/${locale.commandBase} ${locale.commandWordSearch} $constraints ${locale.searchPage}:"
            }
            sentComponent.addExtra(pageComponent)
        }
        sender.sendPlatformMessage(sentComponent)
    }

    // /ticket setpriority <ID> <Level>
    private fun setPriority(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: TicketHandler
    ): NotifyParams {
        pluginState.database.addAction(
            ticketID = ticketHandler.id,
            action = Ticket.Action(Ticket.Action.Type.SET_PRIORITY, sender.toUUIDOrNull(), args[2])
        )
        ticketHandler.priority = byteToPriority(args[2].toByte())

        return NotifyParams(
            silent = silent,
            sender = sender,
            ticketHandler = ticketHandler,
            creatorLambda = null,
            senderLambda = {
                it.notifyTicketSetPrioritySuccess
                    .replace("%id%", args[1])
                    .replace("%priority%", ticketHandler.priority.toColouredString(it))
            },
            massNotifyLambda = {
                it.notifyTicketSetPriorityEvent
                    .replace("%user%", sender.name)
                    .replace("%id%", args[1])
                    .replace("%priority%", ticketHandler.priority.toColouredString(it))
            },
            creatorAlertPerm = "ticketmanager.notify.change.priority",
            massNotifyPerm =  "ticketmanager.notify.massNotify.priority"
        )
    }

    // /ticket teleport <ID>
    private fun teleport(sender: CommandSender, ticketHandler: TicketHandler) {
        if (sender is Player)
            ticketHandler.location?.let {
                Bukkit.getScheduler().runTask(mainPlugin,
                    Runnable { sender.teleport(it) }
                )
            }
    }

    // /ticket unassign <ID>
    private fun unAssign(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        senderLocale: TMLocale,
        ticketHandler: TicketHandler
    ): NotifyParams {
        return allAssignVariations(sender, silent, senderLocale, args[1], null, ticketHandler)
    }

    // /ticket version
    private fun version(
        sender: CommandSender,
        locale: TMLocale
    ) {
        val sentComponent = TextComponent("")
        val components = listOf(
            "&3===========================&r\n",
            "&3&lTicketManager:&r&7 by HoshiKurama&r\n",
            "      &3GitHub Wiki: ",
            "&7&nHERE&r\n",
            "           &3V4.0.0&r\n",
            "&3===========================&r"
        )
            .map(::toColour)
            .map(::TextComponent)

        components[3].clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, locale.wikiLink)
        components[3].hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(locale.clickWiki))

        components.forEach { sentComponent.addExtra(it) }

        sender.sendPlatformMessage(sentComponent)
    }

    // /ticket view <ID>
    private fun view(
        sender: CommandSender,
        locale: TMLocale,
        ticketHandler: TicketHandler
    ) {
        val ticket = pluginState.database.getTicket(ticketHandler.id)!!
        val message = buildTicketInfo(ticket, locale)

        if (!sender.nonCreatorMadeChange(ticket.creatorUUID))
            ticketHandler.statusUpdateForCreator = false

        ticket.actions.asSequence()
            .filter { it.type == Ticket.Action.Type.COMMENT || it.type == Ticket.Action.Type.OPEN }
            .map { locale.viewFormatComment
                .run { "\n$this" }
                .run(::toColour)
                .replace("%user%", it.user.toName(locale))
                .replace("%comment%", it.message!!)
            }
            .map(::TextComponent)
            .forEach { message.addExtra(it) }

        sender.sendPlatformMessage(message)
    }

    private fun buildTicketInfo(ticket: Ticket, locale: TMLocale): TextComponent {
        val message = TextComponent("")

        val textComponents = listOf(
            locale.viewFormatHeader
                .replace("%num%", "${ticket.id}"),
            locale.viewFormatSep1,
            locale.viewFormatInfo1
                .replace("%creator%", ticket.creatorUUID.toName(locale))
                .replace("%assignment%", ticket.assignedTo ?: ""),
            locale.viewFormatInfo2
                .replace("%priority%", ticket.priority.toColouredString(locale))
                .replace("%status%", ticket.status.toColouredString(locale)),
            locale.viewFormatInfo3
                .replace("%location%", ticket.location?.toString() ?: ""),
            locale.viewFormatSep2
        )
            .map(::toColour)
            .map { "\n$it" }
            .map(::TextComponent)

        if (ticket.location != null) {
            textComponents[4].hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(locale.clickTeleport))
            textComponents[4].clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND,
                locale.run { "/$commandBase $commandWordTeleport ${ticket.id}" })
            textComponents[4]
        }

        textComponents.forEach { message.addExtra(it) }
        return message
    }

    // /ticket viewdeep <ID>
    private fun viewDeep(
        sender: CommandSender,
        locale: TMLocale,
        ticketHandler: TicketHandler
    )
    {
        val ticket = pluginState.database.getTicket(ticketHandler.id)!!
        val sentMessage = buildTicketInfo(ticket, locale)

        if (!sender.nonCreatorMadeChange(ticket.creatorUUID))
            ticketHandler.statusUpdateForCreator = false

        fun Ticket.Action.formatDeepAction(): String {
            val result = when (type) {
                Ticket.Action.Type.OPEN, Ticket.Action.Type.COMMENT ->
                    locale.viewFormatDeepComment
                        .replace("%comment%", message!!)

                Ticket.Action.Type.SET_PRIORITY ->
                    locale.viewFormatDeepSetPriority
                        .replace("%priority%", byteToPriority(message!!.toByte())
                            .toColouredString(locale))

                Ticket.Action.Type.ASSIGN ->
                    locale.viewFormatDeepAssigned
                        .replace("%assign%", message ?: "")

                Ticket.Action.Type.REOPEN -> locale.viewFormatDeepReopen
                Ticket.Action.Type.CLOSE -> locale.viewFormatDeepClose
                Ticket.Action.Type.MASS_CLOSE -> locale.viewFormatDeepMassClose
            }
            return result
                .replace("%user%", user.toName(locale))
                .replace("%time%", timestamp.toLargestRelativeTime(locale))
        }

        ticket.actions
            .map { it.formatDeepAction() }
            .map { "\n$it" }
            .map(::toColour)
            .map(::TextComponent)
            .forEach { sentMessage.addExtra(it) }

        sender.sendPlatformMessage(sentMessage)
    }
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

private fun CommandSender.toUUIDOrNull() = if (this is Player) this.uniqueId else null
private fun CommandSender.toLocationOrNull() = if (this is Player) Ticket.Location(this.location) else null

private fun CommandSender.nonCreatorMadeChange(creatorUUID: UUID?): Boolean {
    if (creatorUUID == null) return false
    return this.toUUIDOrNull()?.notEquals(creatorUUID) ?: true
}

fun <T> T.notEquals(t: T) = this != t