package com.github.hoshikurama.ticketmanager.spigot.events

import com.github.hoshikurama.ticketmanager.common.*
import com.github.hoshikurama.ticketmanager.common.database.Database
import com.github.hoshikurama.ticketmanager.common.database.Memory
import com.github.hoshikurama.ticketmanager.common.database.MySQL
import com.github.hoshikurama.ticketmanager.common.database.SQLite
import com.github.hoshikurama.ticketmanager.common.ticket.*
import com.github.hoshikurama.ticketmanager.spigot.*
import com.github.shynixn.mccoroutine.SuspendingCommandExecutor
import com.github.shynixn.mccoroutine.asyncDispatcher
import com.github.shynixn.mccoroutine.minecraftDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import net.md_5.bungee.api.ChatColor
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.ComponentBuilder
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

class Commands : SuspendingCommandExecutor {

    override suspend fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean = withContext(mainPlugin.asyncDispatcher) {

        val senderLocale = sender.toTMLocale()
        val argList = args.toList()

        if (argList.isEmpty()) {
            senderLocale.warningsInvalidCommand
                .run(::addColour)
                .run(sender::sendMessage)
            return@withContext false
        }

        if (mainPlugin.pluginState.pluginLocked.get()) {
            senderLocale.warningsLocked
                .run(::addColour)
                .run(sender::sendMessage)
            return@withContext false
        }

        // Grabs BasicTicket. Only null if ID required but doesn't exist. Filters non-valid tickets
        val pseudoTicket = getBasicTicketHandler(argList, senderLocale)
        if (pseudoTicket == null) {
            senderLocale.warningsInvalidID
                .run(::addColour)
                .run(sender::sendMessage)
            return@withContext false
        }

        // Async Calculations
        val hasValidPermission = async { hasValidPermission(sender, pseudoTicket, argList, senderLocale) }
        val isValidCommand = async { isValidCommand(sender, pseudoTicket, argList, senderLocale) }
        val notUnderCooldown = async { notUnderCooldown(sender, senderLocale, argList) }
        // Shortened Commands
        val executeCommand = suspend { executeCommand(sender, argList, senderLocale, pseudoTicket) }

        try {
            mainPlugin.pluginState.jobCount.run { set(get() + 1) }
            if (notUnderCooldown.await() && isValidCommand.await() && hasValidPermission.await()) {
                executeCommand()?.let { pushNotifications(sender, it, senderLocale, pseudoTicket) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            postModifiedStacktrace(e)

            senderLocale.warningsUnexpectedError
                .run(::addColour)
                .run(sender::sendMessage)
        } finally {
            mainPlugin.pluginState.jobCount.run { set(get() - 1) }
        }

        return@withContext true
    }

    private suspend fun getBasicTicketHandler(
        args: List<String>,
        senderLocale: TMLocale,
    ): BasicTicketHandler? {

        suspend fun buildFromID(id: Int) = BasicTicketHandler.buildHandler(configState.database, id)

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
            else -> ConcreteBasicTicket(creatorUUID = null, location = null).run { BasicTicketHandler(this, configState.database) } // Occurs when command does not need valid handler
        }
    }

    private fun hasValidPermission(
        sender: CommandSender,
        basicTicket: BasicTicket,
        args: List<String>,
        senderLocale: TMLocale
    ): Boolean {
        try {
           if (sender !is Player) return true

           fun has(perm: String) = sender.has(perm)
           fun hasSilent() = has("ticketmanager.commandArg.silence")
           fun hasDuality(basePerm: String): Boolean {
               val senderUUID = sender.toUUIDOrNull()
               val ownsTicket = basicTicket.uuidMatches(senderUUID)
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
                       sender.has("ticketmanager.command.history.all") ||
                               sender.has("ticketmanager.command.history.own").let { hasPerm ->
                                   if (args.size >= 2) hasPerm && args[1] == sender.name
                                   else hasPerm
                               }
                   else -> true
               }
           }
               .also { if (!it)
                   senderLocale.warningsNoPermission
                       .run(::addColour)
                       .run(sender::sendMessage)
               }
       } catch (e: Exception) {
            senderLocale.warningsNoPermission
                .run(::addColour)
                .run(sender::sendMessage)
            return false
       }
    }

    private fun isValidCommand(
        sender: CommandSender,
        basicTicket: BasicTicket,
        args: List<String>,
        senderLocale: TMLocale
    ): Boolean {
        fun sendMessage(formattedString: String) = formattedString.run(::addColour).run(sender::sendMessage)
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

                commandWordSearch -> check(::invalidCommand) { args.size >= 2}

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
                        { configState.database.type != Database.Type.valueOf(args[1]) }

                else -> false.also { invalidCommand() }
            }
        }
    }

    private suspend fun notUnderCooldown(
        sender: CommandSender,
        senderLocale: TMLocale,
        args: List<String>
    ): Boolean {
        val underCooldown = when (args[0]) {
            senderLocale.commandWordCreate,
            senderLocale.commandWordComment,
            senderLocale.commandWordSilentComment ->
                configState.cooldowns.checkAndSetAsync(sender.toUUIDOrNull())
            else -> false
        }

        if (underCooldown)
            senderLocale.warningsUnderCooldown
                .run(::addColour)
                .run(sender::sendMessage)
        return !underCooldown
    }

    private suspend fun executeCommand(
        sender: CommandSender,
        args: List<String>,
        senderLocale: TMLocale,
        ticketHandler: BasicTicketHandler
    ): NotifyParams? {
        return senderLocale.run {
            when (args[0]) {
                commandWordAssign -> assign(sender, args, false, senderLocale, ticketHandler)
                commandWordAssign -> assign(sender, args, false, senderLocale, ticketHandler)
                commandWordSilentAssign -> assign(sender, args, true, senderLocale, ticketHandler)
                commandWordClaim -> claim(sender, args, false, senderLocale, ticketHandler)
                commandWordSilentClaim -> claim(sender, args, true, senderLocale, ticketHandler)
                commandWordClose -> close(sender, args, false, ticketHandler)
                commandWordSilentClose -> close(sender, args, true, ticketHandler)
                commandWordCloseAll -> closeAll(sender, args, false, ticketHandler)
                commandWordSilentCloseAll -> closeAll(sender, args, true, ticketHandler)
                commandWordComment -> comment(sender, args, false, ticketHandler)
                commandWordSilentComment -> comment(sender, args, true, ticketHandler)
                commandWordCreate -> create(sender, args)
                commandWordHelp -> help(sender, senderLocale).let { null }
                commandWordHistory -> history(sender, args, senderLocale).let { null }
                commandWordList -> list(sender, args, senderLocale).let { null }
                commandWordListAssigned -> listAssigned(sender, args, senderLocale).let { null }
                commandWordListUnassigned -> listUnassigned(sender, args, senderLocale).let { null }
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
        basicTicket: BasicTicket
    ) {
        params.run {
            if (sendSenderMSG)
                senderLambda!!(locale)
                    .run(::addColour)
                    .run(sender::sendMessage)

            if (sendCreatorMSG)
                basicTicket.creatorUUID
                    ?.run(Bukkit::getPlayer)
                    ?.let { creatorLambda!!(it.toTMLocale()) }
                    ?.let(::addColour)
                    ?.run(creator!!::sendMessage)

            if (sendMassNotifyMSG)
                pushMassNotify(massNotifyPerm, massNotifyLambda!!)
        }
    }

    private class NotifyParams(
        silent: Boolean,
        basicTicket: BasicTicket,
        sender: CommandSender,
        creatorAlertPerm: String,
        val massNotifyPerm: String,
        val senderLambda: ((TMLocale) -> String)?,
        val creatorLambda: ((TMLocale) -> String)?,
        val massNotifyLambda: ((TMLocale) -> String)?,
    ) {
        val creator: Player? = basicTicket.creatorUUID?.let(Bukkit::getPlayer)
        val sendSenderMSG: Boolean = (!sender.has(massNotifyPerm) || silent)
                && senderLambda != null
        val sendCreatorMSG: Boolean = sender.nonCreatorMadeChange(basicTicket.creatorUUID)
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

    private suspend fun allAssignVariations(
        sender: CommandSender,
        silent: Boolean,
        senderLocale: TMLocale,
        assignmentID: String,
        dbAssignment: String?,
        ticketHandler: BasicTicketHandler,
    ): NotifyParams = withContext(asyncContext) {
        val shownAssignment = dbAssignment ?: senderLocale.miscNobody

        launchIndependent { ticketHandler.setAssignedTo(dbAssignment) }
        launchIndependent { configState.database.addAction(
            ticketID = ticketHandler.id,
            action = FullTicket.Action(FullTicket.Action.Type.ASSIGN, sender.toUUIDOrNull(), dbAssignment)
        )}

        if (!silent && configState.discord?.state?.notifyOnAssign == true) {
            launchIndependent {
                tryNoCatch {
                    configState.discord?.assignUpdate(sender.name, assignmentID, shownAssignment)
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

    // /ticket assign <ID> <Assignment>
    private suspend fun assign(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        senderLocale: TMLocale,
        ticketHandler: BasicTicketHandler,
    ): NotifyParams {
        val sqlAssignment = args.subList(2, args.size).joinToString(" ")
        return allAssignVariations(sender, silent, senderLocale, args[1], sqlAssignment, ticketHandler)
    }

    // /ticket claim <ID>
    private suspend fun claim(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        senderLocale: TMLocale,
        ticketHandler: BasicTicketHandler,
    ): NotifyParams {
        return allAssignVariations(sender, silent, senderLocale, args[1], sender.name, ticketHandler)
    }

    // /ticket close <ID> [Comment...]
    private suspend fun close(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: BasicTicketHandler
    ): NotifyParams = withContext(asyncContext) {
        val newCreatorStatusUpdate = sender.nonCreatorMadeChange(ticketHandler.creatorUUID) && configState.allowUnreadTicketUpdates
        if (newCreatorStatusUpdate != ticketHandler.creatorStatusUpdate) {
            launchIndependent { ticketHandler.setCreatorStatusUpdate(newCreatorStatusUpdate) }
        }

        return@withContext if (args.size >= 3)
            closeWithComment(sender, args, silent, ticketHandler)
        else closeWithoutComment(sender, args, silent, ticketHandler)
    }

    private suspend fun closeWithComment(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: BasicTicketHandler,
    ): NotifyParams = withContext(asyncContext) {
        val message = args.subList(2, args.size)
            .joinToString(" ")
            .run(ChatColor::stripColor)!!

        if (!silent && configState.discord?.state?.notifyOnClose == true) {
            launchIndependent {
                tryNoCatch {
                    configState.discord?.closeUpdate(sender.name, ticketHandler.id.toString(), message)
                }
            }
        }

        launchIndependent {
            configState.database.run {
                addAction(
                    ticketID = ticketHandler.id,
                    action = FullTicket.Action(FullTicket.Action.Type.COMMENT, sender.toUUIDOrNull(), message)
                )
                addAction(
                    ticketID = ticketHandler.id,
                    action = FullTicket.Action(FullTicket.Action.Type.CLOSE, sender.toUUIDOrNull())
                )
                ticketHandler.setTicketStatus(BasicTicket.Status.CLOSED)
            }
        }

        NotifyParams(
            silent = silent,
            sender = sender,
            basicTicket = ticketHandler,
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

    private suspend fun closeWithoutComment(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: BasicTicketHandler
    ): NotifyParams = withContext(asyncContext) {

        launchIndependent {
            configState.database.addAction(
                ticketID = ticketHandler.id,
                action = FullTicket.Action(FullTicket.Action.Type.CLOSE, sender.toUUIDOrNull())
            )
            ticketHandler.setTicketStatus(BasicTicket.Status.CLOSED)
        }

        if (!silent && configState.discord?.state?.notifyOnClose == true) {
            launchIndependent {
                tryNoCatch {
                    configState.discord?.closeUpdate(sender.name, ticketHandler.id.toString())
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
            },
            massNotifyLambda = {
                it.notifyTicketCloseEvent
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
    private suspend fun closeAll(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        basicTicket: BasicTicket
    ): NotifyParams = withContext(asyncContext) {
        val lowerBound = args[1].toInt()
        val upperBound = args[2].toInt()

        launchIndependent { configState.database.massCloseTickets(lowerBound, upperBound, sender.toUUIDOrNull(), asyncContext) }

        if (!silent && configState.discord?.state?.notifyOnCloseAll == true) {
            launchIndependent {
                tryNoCatch {
                    configState.discord?.closeAllUpdate(sender.name, "$lowerBound", "$upperBound")
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
    private suspend fun comment(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: BasicTicketHandler,
    ): NotifyParams = withContext(asyncContext) {
        val message = args.subList(2, args.size)
            .joinToString(" ")
            .run(ChatColor::stripColor)!!

        val newCreatorStatusUpdate = sender.nonCreatorMadeChange(ticketHandler.creatorUUID) && configState.allowUnreadTicketUpdates
        if (newCreatorStatusUpdate != ticketHandler.creatorStatusUpdate) {
            launchIndependent { ticketHandler.setCreatorStatusUpdate(newCreatorStatusUpdate) }
        }

        launchIndependent {
            configState.database.addAction(
                ticketID = ticketHandler.id,
                action = FullTicket.Action(FullTicket.Action.Type.COMMENT, sender.toUUIDOrNull(), message)
            )
        }

        if (!silent && configState.discord?.state?.notifyOnComment == true) {
            launchIndependent {
                tryNoCatch {
                    configState.discord?.commentUpdate(sender.name, ticketHandler.id.toString(), message)
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
    private suspend fun convertDatabase(args: List<String>) {
        val type = args[1].run(Database.Type::valueOf)
        val config = mainPlugin.config

        try {
            configState.database.migrateDatabase(
                context = asyncContext,
                to = type,
                sqLiteBuilder = { SQLite(mainPlugin.dataFolder.absolutePath) },
                mySQLBuilder = {
                    MySQL(
                        config.getString("MySQL_Host")!!,
                        config.getString("MySQL_Port")!!,
                        config.getString("MySQL_DBName")!!,
                        config.getString("MySQL_Username")!!,
                        config.getString("MySQL_Password")!!,
                        asyncDispatcher = (mainPlugin.asyncDispatcher as CoroutineDispatcher)
                    )
                },
                memoryBuilder = {
                    Memory(
                        filePath = mainPlugin.dataFolder.absolutePath,
                        backupFrequency = config.getLong("Memory_Backup_Frequency", 600),
                    )
                },
                onBegin = {
                    mainPlugin.pluginState.pluginLocked.set(true)
                    pushMassNotify("ticketmanager.notify.info") {
                        it.informationDBConvertInit
                            .replace("%fromDB%", configState.database.type.name)
                            .replace("%toDB%", type.name)
                    }
                },
                onComplete = {
                    mainPlugin.pluginState.pluginLocked.set(false)
                    pushMassNotify("ticketmanager.notify.info") { it.informationDBConvertSuccess }
                }
            )
        } catch (e: Exception) {
            mainPlugin.pluginState.pluginLocked.set(false)
            throw e
        }
    }

    // /ticket create <Message…>
    private suspend fun create(
        sender: CommandSender,
        args: List<String>,
    ): NotifyParams = withContext(asyncContext) {
        val message = args.subList(1, args.size)
            .joinToString(" ")
            .run(ChatColor::stripColor)!!

        val ticket = ConcreteBasicTicket(creatorUUID = sender.toUUIDOrNull(), location = sender.toTicketLocationOrNull())

        val deferredID = async { configState.database.addNewTicket(ticket, asyncContext, message) }
        mainPlugin.pluginState.ticketCountMetrics.run { set(get() + 1) }
        val id = deferredID.await().toString()

        if (configState.discord?.state?.notifyOnCreate == true) {
            launchIndependent {
                tryNoCatch {
                    configState.discord?.createUpdate(sender.name, id, message)
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
    private fun help(
        sender: CommandSender,
        locale: TMLocale,
    ) {
        val hasSilentPerm = sender.has("ticketmanager.commandArg.silence")
        val cc = configState.localeHandler.mainColourCode

        val component = ComponentBuilder("")

        val header = mutableListOf(
            locale.helpHeader,
            locale.helpLine1,
        )
        if (hasSilentPerm) {
            header.add(locale.helpLine2)
            header.add(locale.helpLine3)
        }
        header.add(locale.helpSep)
        header.map(::addColour).forEach(component::append)

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
                .filter { it.third.any(sender::has) }
                .run { this + Triple(false, locale.commandWordVersion, "NA") }
                .map {
                    val commandString = "$cc/${locale.commandBase} ${it.second}"
                    if (hasSilentPerm)
                        if (it.first) "\n&a[✓] $commandString"
                        else "\n&c[✕] $commandString"
                    else "\n$commandString"
                }
                .map(::addColour)
                .forEach(component::append)

            sender.spigot().sendMessage(*component.create())
        }
    }

    // /ticket history [User] [Page]
    private suspend fun history(
        sender: CommandSender,
        args: List<String>,
        locale: TMLocale,
    ) {
        withContext(asyncContext) {
            val targetName =
                if (args.size >= 2) args[1].takeIf { it != locale.consoleName } else sender.name.takeIf { sender is Player }
            val requestedPage = if (args.size >= 3) args[2].toInt() else 1

            // Leaves console as null. Otherwise attempts UUID grab or [PLAYERNOTFOUND]
            fun String.attemptToUUIDString(): String =
                Bukkit.getOfflinePlayers().asSequence()
                    .firstOrNull { equals(it.name) }
                    ?.run { uniqueId.toString() }
                    ?: "[PLAYERNOTFOUND]"

            val searchedUser = targetName?.attemptToUUIDString()

            val resultSize: Int
            val resultsChunked = configState.database.searchDatabase(asyncContext, locale, listOf(locale.searchCreator to searchedUser)) { true }
                .toList()
                .sortedByDescending(BasicTicket::id)
                .also { resultSize = it.size }
                .chunked(6)

            val componentBuilder = ComponentBuilder()

            locale.historyHeader
                .replace("%name%", targetName ?: locale.consoleName)
                .replace("%count%", "$resultSize")
                .run(::addColour)
                .run(componentBuilder::append)

            val actualPage = if (requestedPage >= 1 && requestedPage < resultsChunked.size) requestedPage else 1
            if (resultsChunked.isNotEmpty()) {
                resultsChunked.getOrElse(requestedPage - 1) { resultsChunked[1] }.forEach { t ->
                    locale.historyEntry
                        .let { "\n$it" }
                        .replace("%id%", "${t.id}")
                        .replace("%SCC%", t.status.colourCode)
                        .replace("%status%", t.status.toLocaledWord(locale))
                        .replace("%comment%", t.actions[0].message!!)
                        .let { if (it.length > 80) "${it.substring(0, 81)}..." else it }
                        .run(::addColour)
                        .run(::TextComponent)
                        .run { convertToClickToView(t.id, locale) }
                        .run(componentBuilder::append)
                }

                if (resultsChunked.size > 1) {
                    buildPageComponent(actualPage, resultsChunked.size, locale) {
                        "/${it.commandBase} ${it.commandWordHistory} ${targetName ?: it.consoleName} "
                    }.run(componentBuilder::append)
                }
            }

            sender.spigot().sendMessage(*componentBuilder.create())
        }
    }

    // /ticket list [Page]
    private suspend fun list(
        sender: CommandSender,
        args: List<String>,
        locale: TMLocale,
    ) {
        sender.spigot().sendMessage(
            createGeneralList(args, locale, locale.listFormatHeader,
                getIDPriorityPair = Database::getOpenIDPriorityPairs,
                baseCommand = locale.run{ { "/$commandBase $commandWordList " } }
            )
        )
    }

    // /ticket listassigned [Page]
    private suspend fun listAssigned(
        sender: CommandSender,
        args: List<String>,
        locale: TMLocale,
    ) {
        val groups: List<String> = if (sender is Player) mainPlugin.perms.getPlayerGroups(sender).toList() else listOf()

        sender.spigot().sendMessage(
            createGeneralList(args, locale, locale.listFormatAssignedHeader,
                getIDPriorityPair = { it.getAssignedOpenIDPriorityPairs(sender.name, groups) },
                baseCommand = locale.run { { "/$commandBase $commandWordListAssigned " } }
            )
        )
    }

    private suspend fun listUnassigned(
        sender: CommandSender,
        args: List<String>,
        locale: TMLocale,
    ) {
        sender.spigot().sendMessage(
            createGeneralList(args, locale, locale.listFormatUnassignedHeader,
                getIDPriorityPair = Database::getUnassignedOpenIDPriorityPairs,
                baseCommand = locale.run { { "/$commandBase $commandWordListUnassigned " } }
            )
        )
    }

    // /ticket reload
    private suspend fun reload(
        sender: CommandSender,
        locale: TMLocale,
    ) {
        withContext(asyncContext) {
            try {
                mainPlugin.pluginState.pluginLocked.set(true)
                pushMassNotify("ticketmanager.notify.info") {
                    it.informationReloadInitiated
                        .replace("%user%", sender.name)
                }

                val forceQuitJob = launch {
                    delay(30L * 1000L)

                    // Long standing task has occurred if it reaches this point
                    launch {
                        pushMassNotify("ticketmanager.notify.warning") {
                            it.warningsLongTaskDuringReload
                        }
                        mainPlugin.pluginState.jobCount.set(1)
                        mainPlugin.asyncDispatcher.cancelChildren()
                    }
                }

                // Waits for other tasks to complete
                while (mainPlugin.pluginState.jobCount.get() > 1) delay(1000L)

                if (!forceQuitJob.isCancelled)
                    forceQuitJob.cancel("Tasks closed on time")

                pushMassNotify("ticketmanager.notify.info") { it.informationReloadTasksDone }
                configState.database.closeDatabase()
                configState.discord?.shutdown()
                mainPlugin.loadPlugin()

                pushMassNotify("ticketmanager.notify.info") { it.informationReloadSuccess }
                if (!sender.has("ticketmanager.notify.info")) {
                    locale.informationReloadSuccess
                        .run(::addColour)
                        .run(sender::sendMessage)
                }
            } catch (e: Exception) {
                pushMassNotify("ticketmanager.notify.info") { it.informationReloadFailure }
                throw e
            }
        }
    }

    // /ticket reopen <ID>
    private suspend fun reopen(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: BasicTicketHandler,
    ): NotifyParams = withContext(asyncContext) {
        val action = FullTicket.Action(FullTicket.Action.Type.REOPEN, sender.toUUIDOrNull())

        // Updates user status if needed
        val newCreatorStatusUpdate = sender.nonCreatorMadeChange(ticketHandler.creatorUUID) && configState.allowUnreadTicketUpdates
        if (newCreatorStatusUpdate != ticketHandler.creatorStatusUpdate) {
            launchIndependent { ticketHandler.setCreatorStatusUpdate(newCreatorStatusUpdate) }
        }

        launchIndependent {
            configState.database.addAction(ticketHandler.id, action)
            ticketHandler.setTicketStatus(BasicTicket.Status.OPEN)
        }

        if (!silent && configState.discord?.state?.notifyOnReopen == true) {
            launchIndependent {
                tryNoCatch {
                    configState.discord?.reopenUpdate(sender.name, ticketHandler.id.toString())
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

    private suspend fun search(
        sender: CommandSender,
        args: List<String>,
        locale: TMLocale,
    ) {
        withContext(asyncContext) {
            fun String.attemptToUUIDString(): String? =
                if (equals(locale.consoleName)) null
                else Bukkit.getOfflinePlayers().asSequence()
                    .firstOrNull { equals(it.name) }
                    ?.run { uniqueId.toString() }
                    ?: "[PLAYERNOTFOUND]"

            // Beginning of execution
            locale.searchFormatQuerying
                .run(::addColour)
                .run(sender::sendMessage)

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
                            { t: FullTicket -> t.actions.any { it.type == FullTicket.Action.Type.CLOSE && it.user?.toString() == searchedUser } }
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
            val chunkedTickets =
                configState.database.searchDatabase(asyncContext, locale, mainTableConstrains, composedSearch)
                    .toList()
                    .sortedByDescending(BasicTicket::id)
                    .apply { resultSize = size }
                    .chunked(8)

            val page = arguments[locale.searchPage]?.toIntOrNull()
                .let { if (it != null && it >= 1 && it < chunkedTickets.size) it else 1 }
            val fixMSGLength =
                { t: FullTicket -> t.actions[0].message!!.run { if (length > 25) "${substring(0, 21)}..." else this } }

            val component = TextComponent("")

            // Adds header
            locale.searchFormatHeader
                .replace("%size%", "$resultSize")
                .run(::addColour)
                .run(component::addExtra)

            // Adds entries
            if (chunkedTickets.isNotEmpty()) {
                chunkedTickets[page - 1]
                    .map {
                        "\n${locale.searchFormatEntry}"
                            .replace("%PCC%", it.priority.colourCode)
                            .replace("%SCC%", it.status.colourCode)
                            .replace("%id%", "${it.id}")
                            .replace("%status%", it.status.toLocaledWord(locale))
                            .replace("%creator%", it.creatorUUID.toName(locale))
                            .replace("%assign%", it.assignedTo ?: "")
                            .replace("%world%", it.location?.world ?: "")
                            .replace("%time%", it.actions[0].timestamp.toLargestRelativeTime(locale))
                            .replace("%comment%", fixMSGLength(it))
                            .run(::addColour)
                            .run(::TextComponent)
                            .run { convertToClickToView(it.id, locale) }
                    }
                    .forEach(component::addExtra)

                // Implements pages if needed
                if (chunkedTickets.size > 1) {
                    buildPageComponent(page, chunkedTickets.size, locale) {
                        // Removes page constraint and converts rest to key:arg
                        val constraints = arguments
                            .filter { it.key != locale.searchPage }
                            .map { (k, v) -> "$k:$v" }
                        "/${locale.commandBase} ${locale.commandWordSearch} $constraints ${locale.searchPage}:"
                    }.run(component::addExtra)
                }
            }
            sender.spigot().sendMessage(component)
        }
    }

    // /ticket setpriority <ID> <Level>
    private suspend fun setPriority(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        ticketHandler: BasicTicketHandler,
    ): NotifyParams = withContext(asyncContext) {
        val newPriority = byteToPriority(args[2].toByte())

        launchIndependent {
            configState.database.addAction(
                ticketID = ticketHandler.id,
                action = FullTicket.Action(FullTicket.Action.Type.SET_PRIORITY, sender.toUUIDOrNull(), args[2])
            )
            ticketHandler.setTicketPriority(newPriority)
        }

        if (!silent && configState.discord?.state?.notifyOnPriorityChange == true) {
            launchIndependent {
                tryNoCatch {
                    configState.discord?.priorityChangeUpdate(sender.name, ticketHandler.id.toString(), newPriority)
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
            },
            massNotifyLambda = {
                it.notifyTicketSetPriorityEvent
                    .replace("%user%", sender.name)
                    .replace("%id%", args[1])
                    .replace("%priority%", ticketHandler.run { newPriority.colourCode + newPriority.toLocaledWord(it) })
            },
            creatorAlertPerm = "ticketmanager.notify.change.priority",
            massNotifyPerm =  "ticketmanager.notify.massNotify.priority"
        )
    }

    // /ticket teleport <ID>
    private suspend fun teleport(
        sender: CommandSender,
        basicTicket: BasicTicket,
    ) {
        if (sender is Player && basicTicket.location != null) {
            val loc = basicTicket.location!!.run { Location(Bukkit.getWorld(world), x.toDouble(), y.toDouble(), z.toDouble()) }
            withContext(mainPlugin.minecraftDispatcher) {
                sender.teleport(loc)
            }
        }
    }

    // /ticket unassign <ID>
    private suspend fun unAssign(
        sender: CommandSender,
        args: List<String>,
        silent: Boolean,
        senderLocale: TMLocale,
        ticketHandler: BasicTicketHandler,
    ): NotifyParams {
        return allAssignVariations(sender, silent, senderLocale, args[1], null, ticketHandler)
    }

    // /ticket version
    private fun version(
        sender: CommandSender,
        locale: TMLocale,
    ) {
        val sentComponent = TextComponent("")
        val components = listOf(
            "&3===========================&r\n",
            "&3&lTicketManager:&r&7 by HoshiKurama&r\n",
            "      &3GitHub Wiki: ",
            "&7&nHERE&r\n",
            "           &3V$pluginVersion\n",
            "&3===========================&r"
        )
            .map(::addColour)
            .map(::TextComponent)

        components[3].clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, locale.wikiLink)
        components[3].hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(locale.clickWiki))

        components.forEach(sentComponent::addExtra)

        sender.spigot().sendMessage(sentComponent)
    }

    // /ticket view <ID>
    private suspend fun view(
        sender: CommandSender,
        locale: TMLocale,
        ticketHandler: BasicTicketHandler,
    ) {


        withContext(asyncContext) {
            val fullTicket = ticketHandler.toFullTicket()
            val baseComponent = buildTicketInfoComponent(fullTicket, locale)

            if (!sender.nonCreatorMadeChange(ticketHandler.creatorUUID) && ticketHandler.creatorStatusUpdate)
                launchIndependent { ticketHandler.setCreatorStatusUpdate(false) }

            // Entries
            fullTicket.actions.asSequence()
                .filter { it.type == FullTicket.Action.Type.COMMENT || it.type == FullTicket.Action.Type.OPEN }
                .map {
                    "\n${locale.viewFormatComment}"
                        .replace("%user%", it.user.toName(locale))
                        .replace("%comment%", it.message!!)
                }
                .map(::addColour)
                .map(::TextComponent)
                .forEach(baseComponent::addExtra)

            sender.spigot().sendMessage(baseComponent)
        }
    }

    // /ticket viewdeep <ID>
    private suspend fun viewDeep(
        sender: CommandSender,
        locale: TMLocale,
        ticketHandler: BasicTicketHandler,
    ) {
        withContext(asyncContext) {
            val fullTicket = ticketHandler.toFullTicket()
            val baseComponent = buildTicketInfoComponent(fullTicket, locale)

            if (!sender.nonCreatorMadeChange(ticketHandler.creatorUUID) && ticketHandler.creatorStatusUpdate)
                launchIndependent { ticketHandler.setCreatorStatusUpdate(false) }

            fun formatDeepAction(action: FullTicket.Action): String {
                val result = when (action.type) {
                    FullTicket.Action.Type.OPEN, FullTicket.Action.Type.COMMENT ->
                        "\n${locale.viewFormatDeepComment}"
                            .replace("%comment%", action.message!!)

                    FullTicket.Action.Type.SET_PRIORITY ->
                        "\n${locale.viewFormatDeepSetPriority}"
                            .replace("%priority%",
                                byteToPriority(action.message!!.toByte()).run { colourCode + toLocaledWord(locale) }
                            )

                    FullTicket.Action.Type.ASSIGN ->
                        "\n${locale.viewFormatDeepAssigned}"
                            .replace("%assign%", action.message ?: "")

                    FullTicket.Action.Type.REOPEN -> "\n${locale.viewFormatDeepReopen}"
                    FullTicket.Action.Type.CLOSE -> "\n${locale.viewFormatDeepClose}"
                    FullTicket.Action.Type.MASS_CLOSE -> "\n${locale.viewFormatDeepMassClose}"
                }
                return result
                    .replace("%user%", action.user.toName(locale))
                    .replace("%time%", action.timestamp.toLargestRelativeTime(locale))
            }

            fullTicket.actions.asSequence()
                .map(::formatDeepAction)
                .map(::addColour)
                .map(::TextComponent)
                .forEach(baseComponent::addExtra)

            sender.spigot().sendMessage(baseComponent)
        }
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

        val back = TextComponent("[${locale.pageBack}]".run(::addColour))
        val next = TextComponent("[${locale.pageNext}]".run(::addColour))

        val separator = TextComponent("...............")
        separator.color = ChatColor.DARK_GRAY

        val cc = configState.localeHandler.mainColourCode
        val ofSection = "$cc($curPage${locale.pageOf}$pageCount)"
            .run(::addColour)
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

    private fun createListEntry(
        ticket: FullTicket,
        locale: TMLocale
    ): TextComponent {
        val creator = ticket.creatorUUID.toName(locale)
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

        return "\n${locale.listFormatEntry}"
            .replace("%priorityCC%", ticket.priority.colourCode)
            .replace("%ID%", "${ticket.id}")
            .replace("%creator%", creator)
            .replace("%assign%", fixedAssign)
            .replace("%comment%", fixedComment)
            .run(::addColour)
            .run(::TextComponent)
            .run { convertToClickToView(ticket.id, locale) }
    }

    private suspend fun createGeneralList(
        args: List<String>,
        locale: TMLocale,
        headerFormat: String,
        getIDPriorityPair: suspend (Database) -> Flow<Pair<Int, Byte>>,
        baseCommand: (TMLocale) -> String
    ): TextComponent {
        val chunkedIDs = getIDPriorityPair(configState.database)
            .toList()
            .sortedWith(compareByDescending<Pair<Int, Byte>> { it.second }.thenByDescending { it.first } )
            .map { it.first }
            .chunked(8)

        val page = if (args.size == 2 && args[1].toInt() in 1..chunkedIDs.size) args[1].toInt() else 1

        val fullTickets = chunkedIDs.getOrNull(page - 1)
            ?.run { configState.database.getFullTickets(this, asyncContext) }
            ?.toList()
            ?: emptyList()

        val builder = TextComponent("")
        headerFormat
            .run(::addColour)
            .run(builder::addExtra)

        if (fullTickets.isNotEmpty()) {

            fullTickets.map { createListEntry(it, locale) }
                .forEach(builder::addExtra)

            if (chunkedIDs.size > 1)
                buildPageComponent(page, chunkedIDs.size, locale, baseCommand)
                    .run(builder::addExtra)
        }

        return builder
    }

    private fun buildTicketInfoComponent(
        ticket: FullTicket,
        locale: TMLocale,
    ): TextComponent {
        val textComponent = TextComponent("")

        val info = listOf(
            "\n${locale.viewFormatHeader}"
                .replace("%num%", "${ticket.id}"),
            "\n${locale.viewFormatSep1}",
            "\n${locale.viewFormatInfo1}"
                .replace("%creator%", ticket.creatorUUID.toName(locale))
                .replace("%assignment%", ticket.assignedTo ?: ""),
            "\n${locale.viewFormatInfo2}"
                .replace("%priority%", ticket.priority.run { colourCode + toLocaledWord(locale) })
                .replace("%status%", ticket.status.run { colourCode + toLocaledWord(locale) }),
            "\n${locale.viewFormatInfo3}"
                .replace("%location%", ticket.location?.toString() ?: ""),
        )
            .map(::addColour)
            .map(::TextComponent)

        if (ticket.location != null) {
            info[4].hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(locale.clickTeleport))
            info[4].clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND,
                locale.run { "/$commandBase $commandWordTeleport ${ticket.id}" })
        }

        info.forEach(textComponent::addExtra)
        return textComponent
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

private fun CommandSender.nonCreatorMadeChange(creatorUUID: UUID?): Boolean {
    if (creatorUUID == null) return false
    return this.toUUIDOrNull()?.notEquals(creatorUUID) ?: true
}

private fun CommandSender.toTicketLocationOrNull() = if (this is Player)
        location.run { BasicTicket.TicketLocation(world!!.name, blockX, blockY, blockZ) }
    else null

private fun TextComponent.convertToClickToView(id: Int, locale: TMLocale): TextComponent {
    hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(locale.clickViewTicket))
    clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, locale.run { "/$commandBase $commandWordView $id" } )

    val contentStop = TextComponent("")
    contentStop.hoverEvent = null
    contentStop.clickEvent = null

    this.addExtra(contentStop)
    return this
}

private inline fun tryNoCatch(f: () -> Unit) =
    try { f() }
    catch(e: Exception) {}

private suspend inline fun launchIndependent(crossinline f: suspend CoroutineScope.() -> Unit) = CoroutineScope(asyncDispatcher).launch { f(this) }