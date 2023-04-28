package com.github.hoshikurama.ticketmanager.OLD.pipeline


/*
class CorePipeline(
    private val platform: PlatformFunctions,
    val instanceState: InstancePluginState,
    private val globalState: GlobalState,
) {
    suspend fun executeLogic(sender: CommandSender, args: List<String>): StandardReturn {

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
    ): TicketInterface? {
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
            else -> TicketSTD(creator = CreatorSTD.Dummy)
        }
    }

    private fun hasValidPermission(
        sender: CommandSender,
        ticket: TicketInterface,
        args: List<String>,
    ): Boolean {
        if (sender is ConsoleSender || args.isEmpty()) return true
        val player = sender as OnlinePlayer

        fun has(perm: String) = player.has(perm)
        fun hasSilent() = has("ticketmanager.commandArg.silence")
        fun hasWithSilent(perm: String): Boolean = has(perm) && hasSilent()
        fun hasDuality(basePerm: String): Boolean {
            val ownsTicket = ticket.creator == CreatorSTD.UserSTD(player.uniqueID) TODO THIS IS BROKEN AF RIGHT HERE
            val hasAll = has("$basePerm.all")
            val hasOwn = has("$basePerm.own")
            return hasAll || (hasOwn && ownsTicket)
        }
        fun hasDualityWithSilent(basePerm: String): Boolean = hasDuality(basePerm) && hasSilent()

        val hasPermission = sender.locale.run {
            when (args[0]) {
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
        sender: CommandSender,
        ticket: TicketInterface,
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

                commandWordComment, commandWordSilentComment ->
                    check(::invalidCommand) { args.size >= 3 }
                        .thenCheck(::ticketClosed) { ticket.status != TicketInterface.Status.CLOSED }

                commandWordCloseAll, commandWordSilentCloseAll ->
                    check(::invalidCommand) { args.size == 3 }
                        .thenCheck(::notANumber) { args[1].toIntOrNull() != null }
                        .thenCheck(::notANumber) { args[2].toIntOrNull() != null }

                commandWordReopen, commandWordSilentReopen ->
                    check(::invalidCommand) { args.size == 2 }
                        .thenCheck(::ticketOpen) { ticket.status != TicketInterface.Status.OPEN }

                commandWordSetPriority, commandWordSilentSetPriority ->
                    check(::invalidCommand) { args.size == 3 }
                        .thenCheck(::outOfBounds) { args[2].toByteOrNull() != null }
                        .thenCheck(::ticketClosed) { ticket.status != TicketInterface.Status.CLOSED }

                commandWordUnassign, commandWordSilentUnassign ->
                    check(::invalidCommand) { args.size == 2 }
                        .thenCheck(::ticketClosed) { ticket.status != TicketInterface.Status.CLOSED }

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
        sender: CommandSender,
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
        sender: CommandSender,
        args: List<String>,
        ticket: TicketSTD,
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
 */