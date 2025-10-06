package com.github.hoshikurama.ticketmanager.paper.commands

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.registry.precommand.PreCommandExtension.SyncDecider.Decision
import com.github.hoshikurama.ticketmanager.api.ticket.ActionLocation
import com.github.hoshikurama.ticketmanager.api.ticket.Assignment
import com.github.hoshikurama.ticketmanager.api.ticket.Creator
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import com.github.hoshikurama.ticketmanager.commonse.PreCommandExtensionHolder
import com.github.hoshikurama.ticketmanager.commonse.commands.CommandTasks
import com.github.hoshikurama.ticketmanager.commonse.commands.MessageNotification
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.paper.impls.PaperConsole
import com.github.hoshikurama.ticketmanager.paper.impls.PaperPlayer
import com.github.hoshikurama.tmcoroutine.TMCoroutine
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds
import org.bukkit.command.ConsoleCommandSender as BukkitConsole

class PaperCommandRunner(private val scheduleSync: (() -> Unit) -> Unit) {
    // Regarding these, see note attached to CommandReferences
    private val config: Config
        get() = CommandReferences.config
    private val locale: Locale
        get() = CommandReferences.locale
    private val database: AsyncDatabase
        get() = CommandReferences.database
    private val permissions: Permission
        get() = CommandReferences.permissions
    private val commandTasks: CommandTasks
        get() = CommandReferences.commandTasks
    private val preCommandExtensionHolder: PreCommandExtensionHolder
        get() = CommandReferences.preCommandExtensionHolder

    private val playerNamesCacher = OfflinePlayerNamesCacher()


    fun generateCommands(): LiteralCommandNode<CommandSourceStack> {
        val root = literal(locale.commandBase)

        // /ticket create <Comment...>
        run {
            val create = literal(locale.commandWordCreate)
                .permission("ticketmanager.command.create")
            val commentArg = greedyStringArgument(locale.parameterComment)
                .executesTMMessage { tmSender, ctx ->
                    commandTasks.create(tmSender,
                        message = StringArgumentType.getString(ctx, locale.parameterComment)
                    )
                }

            create.then(commentArg)
            root.then(create)
        }

        // /ticket (s.)assign {...}
        run {
            val assign = literal(locale.commandWordAssign)
                .permission("ticketmanager.command.assign")
            val silentAssign = literal(locale.commandWordSilentAssign)
                .allPermissions("ticketmanager.command.assign", "ticketmanager.commandArg.silence")

            val ticketIDArg = ticketIDArgument(::ticketIsOpen)
                .suggests(::openTicketIDsAsync)
            val silentTicketIDArg = ticketIDArgument(::ticketIsOpen)
                .suggests(::openTicketIDsAsync)

            // Generators
            val generateUserBranch = { isSilent: Boolean ->
                literal(locale.parameterLiteralPlayer)
                    .then(Commands.argument(locale.parameterLiteralPlayer, UserAssignmentGrabberArgument(playerNamesCacher))
                        .executesTMMessageWithTicket { tmSender, ctx, ticket ->
                            commandTasks.assign(tmSender,
                                assignment = UserAssignmentGrabberArgument.get(ctx, locale.parameterLiteralPlayer)
                                    .retrieveOrNull()
                                    ?: Assignment.Nobody, //TODO: DON'T EXECUTE IF DOESN'T EXIST. ADD INVALID PLAYER MESSAGE
                                ticket = ticket,
                                silent = isSilent,
                            )
                        }
                    )
            }
            val generateGroupBranch = { isSilent: Boolean ->
                literal(locale.parameterLiteralGroup)
                    .then(Commands.argument(locale.parameterLiteralGroup, StringArgumentType.string())
                        .suggests { _, builder ->
                            permissions.allGroupNames()
                                .filter { it.startsWith(builder.remaining) }
                                .forEach(builder::suggest)
                            builder.buildFuture()
                        }
                        .executesTMMessageWithTicket { tmSender, ctx, ticket ->
                            val group = StringArgumentType.getString(ctx, locale.parameterLiteralGroup)
                            commandTasks.assign(
                                tmSender,
                                assignment = Assignment.PermissionGroup(group),
                                ticket = ticket,
                                silent = isSilent,
                            )
                        }
                    )
            }
            val generatePhraseBranch = { isSilent: Boolean ->
                literal(locale.parameterLiteralPhrase)
                    .then(Commands.argument(locale.parameterLiteralPhrase, StringArgumentType.greedyString())
                        .executesTMMessageWithTicket { tmSender, ctx, ticket ->
                            val phrase = StringArgumentType.getString(ctx, locale.parameterLiteralPhrase)
                            commandTasks.assign(
                                tmSender,
                                assignment = Assignment.Phrase(phrase),
                                ticket = ticket,
                                silent = isSilent,
                            )
                        }
                    )
            }

            ticketIDArg.then(generateUserBranch(false))      // /ticket assign <ID> user <Player>
            ticketIDArg.then(generateGroupBranch(false))     // /ticket assign <ID> group <Group>
            ticketIDArg.then(generatePhraseBranch(false))    // /ticket assign <ID> phrase <phrase...>
            root.then(assign.then(ticketIDArg))

            silentTicketIDArg.then(generateUserBranch(true))      // /ticket s.assign <ID> user <Player>
            silentTicketIDArg.then(generateGroupBranch(true))     // /ticket s.assign <ID> group <Group>
            silentTicketIDArg.then(generatePhraseBranch(true))    // /ticket s.assign <ID> phrase <phrase...>
            root.then(silentAssign.then(silentTicketIDArg))
        }

        // /ticket (s.)unassign <ID>
        run {
            val unassign = literal(locale.commandWordUnassign)
                .permission("ticketmanager.command.assign")
            val unassignSilent = literal(locale.commandWordSilentUnassign)
                .allPermissions("ticketmanager.command.assign", "ticketmanager.commandArg.silence")

            val generateBranch = { isSilent: Boolean ->
                ticketIDArgument(::ticketIsOpen)
                    .suggests(::openTicketIDsAsync)
                    .executesTMMessageWithTicket { tmSender, _, ticket ->
                        commandTasks.unAssign(
                            sender = tmSender,
                            ticket = ticket,
                            silent = isSilent,
                        )
                    }
            }

            unassign.then(generateBranch(false))           // /ticket unassign <ID>
            unassignSilent.then(generateBranch(true))      // /ticket s.unassign <ID>

            root.then(unassign)
            root.then(unassignSilent)
        }

        // /ticket (s.)claim <ID>
        run {
            val claim = literal(locale.commandWordClaim)
                .permission("ticketmanager.command.assign")
            val claimSilent = literal(locale.commandWordSilentClaim)
                .allPermissions("ticketmanager.command.assign", "ticketmanager.commandArg.silence")

            val generateBranch = { isSilent: Boolean ->
                ticketIDArgument(::ticketIsOpen)
                    .suggests(::openTicketIDsAsync)
                    .executesTMMessageWithTicket { tmSender, _, ticket ->
                        commandTasks.claim(
                            sender = tmSender,
                            ticket = ticket,
                            silent = isSilent,
                        )
                    }
            }

            claim.then(generateBranch(false))
            claimSilent.then(generateBranch(true))

            root.then(claim)
            root.then(claimSilent)

        }

        // /ticket (s.)close {...}
        run {
            val close = literal(locale.commandWordClose)
                .requires { hasOneDualityPermission(it.sender, "ticketmanager.command.close") }
            val silentClose = literal(locale.commandWordSilentClose)
                .requires { hasSilentPermission(it.sender) && hasOneDualityPermission(it.sender, "ticketmanager.command.close") }

            val generateBranches = { isSilent: Boolean ->
                val ticketIDArg = ticketIDArgument(::ticketIsOpen, userDualityError("ticketmanager.command.close"))
                    .suggests { ctx, builder -> dualityOpenIDsAsync(ctx, builder,"ticketmanager.command.close") }
                    .executesTMMessageWithTicket { tmSender, _, ticket ->
                        commandTasks.closeWithoutComment(
                            sender = tmSender,
                            ticket = ticket,
                            silent = isSilent
                        )
                    }
                val commentArg = greedyStringArgument(locale.parameterComment)
                    .executesTMMessageWithTicket { tmSender, ctx, ticket ->
                        commandTasks.closeWithComment(
                            sender = tmSender,
                            ticket = ticket,
                            comment = StringArgumentType.getString(ctx, locale.parameterComment),
                            silent = isSilent
                        )
                    }

                ticketIDArg.then(commentArg)
            }
            // /ticket close <ID>
            // /ticket close <ID> [Comment...]
            close.then(generateBranches(false))

            // /ticket s.close <ID>
            // /ticket s.close <ID> [Comment...]
            silentClose.then(generateBranches(true))

            root.then(close)
            root.then(silentClose)
        }

        // /ticket (s.)closeall <Lower ID> <Upper ID>
        run {
            val closeAll = literal(locale.commandWordCloseAll)
                .permission("ticketmanager.command.closeAll")
            val closeAllSilent = literal(locale.commandWordSilentCloseAll)
                .allPermissions("ticketmanager.command.closeAll", "ticketmanager.commandArg.silence")

            val generateBranch = { isSilent: Boolean ->
                val lowerID = longArgument(locale.parameterLowerID)
                val upperID = longArgument(locale.parameterUpperID)
                    .executesTMMessage { tmSender, ctx ->
                        commandTasks.closeAll(
                            sender = tmSender,
                            lowerBound = LongArgumentType.getLong(ctx, locale.parameterLowerID),
                            upperBound = LongArgumentType.getLong(ctx, locale.parameterUpperID),
                            silent = isSilent,
                        )
                    }


                lowerID.then(upperID)
            }

            closeAll.then(generateBranch(false))        // /ticket closeall <Lower ID> <Upper ID>
            closeAllSilent.then(generateBranch(true))   // /ticket s.closeall <Lower ID> <Upper ID>

            root.then(closeAll)
            root.then(closeAllSilent)
        }

        // /ticket (s.)comment <ID> <Comment…>
        run {
            val comment = literal(locale.commandWordComment)
                .requires { hasOneDualityPermission(it.sender, "ticketmanager.command.comment") }
            val commentSilent = literal(locale.commandWordSilentComment)
                .requires { hasOneDualityPermission(it.sender, "ticketmanager.command.comment") && hasSilentPermission(it.sender) }

            val generateBranch = { isSilent: Boolean ->
                val ticketIDArg = ticketIDArgument(::ticketIsOpen, userDualityError("ticketmanager.command.comment"))
                    .suggests { ctx, builder -> dualityOpenIDsAsync(ctx, builder,"ticketmanager.command.comment") }
                val commentArg = greedyStringArgument(locale.parameterComment)
                    .executesTMMessageWithTicket { tmSender, ctx, ticket ->
                        commandTasks.comment(tmSender,
                            ticket = ticket,
                            comment = StringArgumentType.getString(ctx, locale.parameterComment),
                            silent = isSilent
                        )
                    }

                ticketIDArg.then(commentArg)
            }

            comment.then(generateBranch(false))         // /ticket comment <ID> <Comment…>
            commentSilent.then(generateBranch(true))    // /ticket s.comment <ID> <Comment…>

            root.then(comment)
            root.then(commentSilent)
        }

        // /ticket list (<Page>)
        run {
            val list = literal(locale.commandWordList)
                .permission("ticketmanager.command.list")
                .executesTMAction { tmSender, _ -> commandTasks.list(tmSender, requestedPage = 1) } // /ticket list
            val pageArg = intArgument(locale.parameterPage)
                .executesTMAction { tmSender, ctx -> // /ticket list [Page]
                    commandTasks.list(tmSender,
                        requestedPage = IntegerArgumentType.getInteger(ctx, locale.parameterPage)
                    )
                }

            list.then(pageArg)
            root.then(list)
        }

        // /ticket listassigned (<Page>)
        run {
            val listAssigned = literal(locale.commandWordListAssigned)
                .permission("ticketmanager.command.list")
                .executesTMAction { tmSender, _ -> commandTasks.listAssigned(tmSender, 1) } // /ticket listassigned
            val pageArg = intArgument(locale.parameterPage)
                .executesTMAction { tmSender, ctx -> // /ticket listassigned [Page]
                    commandTasks.listAssigned(tmSender,
                        requestedPage = IntegerArgumentType.getInteger(ctx, locale.parameterPage)
                    )
                }

            listAssigned.then(pageArg)
            root.then(listAssigned)
        }

        // /ticket listunassigned (<Page>)
        run {
            val listUnassigned = literal(locale.commandWordListUnassigned)
                .permission("ticketmanager.command.list")
                .executesTMAction { tmSender, _ -> commandTasks.listUnassigned(tmSender, 1) }   // /ticket listunassigned
            val pageArg = intArgument(locale.parameterPage)
                .executesTMAction { tmSender, ctx -> // /ticket listunassigned [Page]
                    commandTasks.listUnassigned(tmSender,
                        requestedPage = IntegerArgumentType.getInteger(ctx, locale.parameterPage)
                    )
                }

            listUnassigned.then(pageArg)
            root.then(listUnassigned)
        }

        // /ticket help
        run {
            val help = literal(locale.commandWordHelp)
                .permission("ticketmanager.command.help")
                .executesTMAction { tmSender, _ -> commandTasks.help(tmSender) }
            root.then(help)
        }

        // /ticket (s.)reopen <ID>
        run {
            val reopen = literal(locale.commandWordReopen)
                .permission("ticketmanager.command.reopen")
            val reopenSilent = literal(locale.commandWordSilentReopen)
                .allPermissions("ticketmanager.command.reopen", "ticketmanager.commandArg.silence")

            val generateBranch = { isSilent: Boolean ->
                ticketIDArgument(::ticketIsClosed)
                    .suggests { _, builder -> builder.suggest("<${locale.parameterID}>").buildFuture() }
                    .executesTMMessageWithTicket { tmSender, _, ticket ->
                        commandTasks.reopen(tmSender,
                            ticket = ticket,
                            silent = isSilent
                        )
                    }
            }

            reopen.then(generateBranch(false))          // /ticket reopen <ID>
            reopenSilent.then(generateBranch(true))     // /ticket s.reopen <ID>

            root.then(reopen)
            root.then(reopenSilent)
        }

        // /ticket version
        run {
            val version = literal(locale.commandWordVersion)
                .executesTMAction { tmSender, _ ->  commandTasks.version(tmSender) }
            root.then(version)
        }

        // /ticket teleport <ID>
        run {
            val teleport = literal(locale.commandWordTeleport)
                .anyPermissions("ticketmanager.command.teleport", "ticketmanager.command.proxyteleport")
            val ticketIDArg = ticketIDArgument({ ticket, sender ->
                val location = ticket.actions[0].location

                // Only teleport to player tickets
                if (location !is ActionLocation.FromPlayer)
                    return@ticketIDArgument locale.brigadierConsoleLocTP.parseMiniMessage().run(TicketGrabber::Error)

                when (location.server == config.proxyOptions?.serverName) {
                    true -> if (!sender.has("ticketmanager.command.teleport"))
                        return@ticketIDArgument locale.brigadierNoTPSameServer.parseMiniMessage().run(TicketGrabber::Error)
                    false -> when (config.proxyOptions != null) {
                        false -> return@ticketIDArgument locale.brigadierNoTPProxyDisabled.parseMiniMessage().run(TicketGrabber::Error)
                        true -> if (!sender.has("ticketmanager.command.proxyteleport"))
                            return@ticketIDArgument locale.brigadierNoTPDiffServer.parseMiniMessage().run(TicketGrabber::Error)
                    }
                }
                null
            })
                .suggests { _, builder -> builder.suggest("<${locale.parameterID}>").buildFuture() }
                .executesTMActionWithTicket { tmSender, _, ticket -> commandTasks.teleport(tmSender, ticket) }

            teleport.then(ticketIDArg)
            root.then(teleport)
        }

        // /ticket view <ID>
        run {
            val view = literal(locale.commandWordView)
                .requires { hasOneDualityPermission(it.sender, "ticketmanager.command.view") }
            val ticketIDArg = ticketIDArgument(userDualityError("ticketmanager.command.view"))
                .suggests { _, builder -> builder.suggest("<${locale.parameterID}>").buildFuture() }
                .executesTMActionWithTicket { tmSender, _, ticket -> commandTasks.view(tmSender, ticket) }

            view.then(ticketIDArg)
            root.then(view)
        }

        // /ticket viewdeep <ID>
        run {
            val viewDeep = literal(locale.commandWordDeepView)
                .requires { hasOneDualityPermission(it.sender, "ticketmanager.command.viewdeep") }
            val ticketIDArg = ticketIDArgument(userDualityError("ticketmanager.command.viewdeep"))
                .suggests { _, builder -> builder.suggest("<${locale.parameterID}>").buildFuture() }
                .executesTMActionWithTicket { tmSender, _, ticket -> commandTasks.viewDeep(tmSender, ticket) }

            viewDeep.then(ticketIDArg)
            root.then(viewDeep)
        }

        // /ticket (s.)setpriority <ID> <Priority>
        run {
            val setPriority = literal(locale.commandWordSetPriority)
                .permission("ticketmanager.command.setPriority")
            val setPrioritySilent = literal(locale.commandWordSilentSetPriority)
                .allPermissions("ticketmanager.command.setPriority", "ticketmanager.commandArg.silence")

            val generateBranches = { isSilent: Boolean ->
                val ticketIDArg = ticketIDArgument(::ticketIsOpen)
                    .suggests(::openTicketIDsAsync)
                val literals = listOf(
                    literal(locale.priorityLowest) to Ticket.Priority.LOWEST,
                    literal(locale.priorityLow) to Ticket.Priority.LOW,
                    literal(locale.priorityNormal) to Ticket.Priority.NORMAL,
                    literal(locale.priorityHigh) to Ticket.Priority.HIGH,
                    literal(locale.priorityHighest) to Ticket.Priority.HIGHEST,
                    literal("1") to Ticket.Priority.LOWEST,
                    literal("2") to Ticket.Priority.LOW,
                    literal("3") to Ticket.Priority.NORMAL,
                    literal("4") to Ticket.Priority.HIGH,
                    literal("5") to Ticket.Priority.HIGHEST,
                ).map { (node, priority) -> node.executesTMMessageWithTicket { tmSender, _, ticket -> commandTasks.setPriority(tmSender, priority, ticket, isSilent) } }

                literals.forEach(ticketIDArg::then)
                ticketIDArg
            }

            setPriority.then(generateBranches(false))           // /ticket setpriority <ID> <Priority>
            setPrioritySilent.then(generateBranches(true))      // /ticket s.setpriority <ID> <Priority>

            root.then(setPriority)
            root.then(setPrioritySilent)
        }

        // /ticket reload
        run {
            val reload = literal(locale.commandWordReload)
                .permission("ticketmanager.command.reload")
                .executesTMAction { tmSender, _ ->
                    val job = commandTasks.reload(tmSender)

                    TMCoroutine.Global.launch {
                        while (!job.isCompleted)
                            delay(1.seconds)

                        scheduleSync {
                            Bukkit.dispatchCommand(Bukkit.getServer().consoleSender,"minecraft:reload")
                        }
                    }
                }

            root.then(reload)
        }

        // /ticket history ({...})
        run {
            suspend fun lookupHistory(tmSender: CommandSender.Active, creator: Creator, requestedPage: Int) {
                commandTasks.history(tmSender, creator, requestedPage)
            }
            fun senderCannotViewTicket(tmSender: CommandSender.Active, userStr: String): Boolean {
                return !tmSender.has("ticketmanager.command.history.all")
                        && tmSender.has("ticketmanager.command.history.own")
                        && userStr != tmSender.serverName
            }

            // /ticket history
            val history = literal(locale.commandWordHistory)
                .requires { hasOneDualityPermission(it.sender, "ticketmanager.command.history") }
                .executesTMAction { tmSender, _ -> lookupHistory(tmSender, tmSender.asCreator(), 1) }

            // /ticket history <User>
            val userArg = Commands.argument(locale.parameterUser, OfflinePlayerGrabberArgument(playerNamesCacher))
                .executesTMAction { tmSender, ctx ->

                    val offlinePlayer = when (val result = ctx.getArgument(locale.parameterUser, OfflinePlayerGrabber::class.java).retrieve()) {
                        is OfflinePlayerGrabber.ValidPlayer -> result.player
                        is OfflinePlayerGrabber.ErrorInvalidName -> {
                            tmSender.sendMessage("<red><i>Invalid player name!</i>".parseMiniMessage()) //TODO EVENTUALLY LOCALIZE THIS
                            return@executesTMAction
                        }
                    }

                    if (senderCannotViewTicket(tmSender, offlinePlayer.name!!)) {
                        tmSender.sendMessage(locale.brigadierOtherHistory.parseMiniMessage())
                        return@executesTMAction
                    }

                    lookupHistory(tmSender, Creator.User(offlinePlayer.uniqueId), 1)
                }

            // /ticket history <User> <Page>
            val pageArgUser = intArgument(locale.parameterPage)
                .executesTMAction { tmSender, ctx ->

                    val offlinePlayer = when (val result = ctx.getArgument(locale.parameterUser, OfflinePlayerGrabber::class.java).retrieve()) {
                        is OfflinePlayerGrabber.ValidPlayer -> result.player
                        is OfflinePlayerGrabber.ErrorInvalidName -> {
                            tmSender.sendMessage("<red><i>Invalid player name!</i>".parseMiniMessage()) //TODO EVENTUALLY LOCALIZE THIS
                            return@executesTMAction
                        }
                    }

                    if (senderCannotViewTicket(tmSender, offlinePlayer.name!!)) {
                        tmSender.sendMessage(locale.brigadierOtherHistory.parseMiniMessage())
                        return@executesTMAction
                    }

                    val page = IntegerArgumentType.getInteger(ctx, locale.parameterPage)
                    lookupHistory(tmSender, Creator.User(offlinePlayer.uniqueId), page)
                }

            // /ticket history Console
            val console = literal(locale.consoleName)
                .permission("ticketmanager.command.history.all")
                .executesTMAction { tmSender, _ ->
                    commandTasks.history(tmSender,
                        checkedCreator = Creator.Console,
                        requestedPage = 1
                    )
                }

            // /ticket history Console <Page>
            val pageArgPage = intArgument(locale.parameterPage)
                .executesTMAction { tmSender, ctx ->
                    commandTasks.history(tmSender,
                        checkedCreator = Creator.Console,
                        requestedPage = IntegerArgumentType.getInteger(ctx, locale.parameterPage)
                    )
                }

            userArg.then(pageArgUser)
            history.then(userArg)

            console.then(pageArgPage)
            history.then(console)

            root.then(history)
        }

            // /ticket search <Params...>
            run {
                val search = literal(locale.commandWordSearch)
                    .permission("ticketmanager.command.search")
                val searchArgs = Commands.argument(locale.parameterConstraints, SearchConstraintsGrabberArgument(playerNamesCacher))
                    .executesTMAction { tmSender, ctx ->
                        when (val result = SearchConstraintsGrabberArgument.get(ctx, locale.parameterConstraints)) {
                            is SearchConstraintsResult.Success -> commandTasks.search(tmSender,
                                searchParameters = result.searchConstraints,
                                useNewFormat = true,
                                newRawArgumentString = "/${ctx.input}",
                            )
                            is SearchConstraintsResult.Fail -> {
                                tmSender.sendMessage(result.message)
                            }
                        }
                    }

            search.then(searchArgs)
            root.then(search)
        }

        return root.build()
    }

    // Errors
    @Suppress("UNUSED_PARAMETER") // Note: allows method reference
    private fun ticketIsOpen(ticket: Ticket, ignored: CommandSender.Active): TicketGrabber.Error? {
        return if (ticket.status != Ticket.Status.OPEN)
            locale.brigadierTicketAlreadyClosed
                .parseMiniMessage()
                .run(TicketGrabber::Error)
        else null

    }
    @Suppress("UNUSED_PARAMETER") // Note: allows method reference
    private fun ticketIsClosed(ticket: Ticket, ignored: CommandSender.Active): TicketGrabber.Error? {
        return if (ticket.status != Ticket.Status.CLOSED)
            locale.brigadierTicketAlreadyOpen
                .parseMiniMessage()
                .run(TicketGrabber::Error)
        else null
    }
    private fun userDualityError(basePerm: String): (Ticket, CommandSender.Active) -> TicketGrabber.Error? = { ticket, sender ->
        val hasAll = sender.has("$basePerm.all")
        val hasOwn = sender.has("$basePerm.own")

        val canRunCommand = hasAll || (hasOwn && ticket.creator == sender.asCreator())
        if (!canRunCommand)
            locale.brigadierNotYourTicket
                .parseMiniMessage()
                .run(TicketGrabber::Error)
        else null
    }

    // Other
    private fun CommandSender.Active.has(permission: String) = when (this) {
        is CommandSender.OnlineConsole -> true
        is CommandSender.OnlinePlayer -> permissions.has(this@has, permission)
    }

    // Argument Suggestions
    @Suppress("UNUSED")
    private fun openTicketIDsAsync(ctx: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder): CompletableFuture<Suggestions> {
        return TMCoroutine.Supervised.async {
            database.getOpenTicketIDsAsync()
                .asSequence()
                .map(Long::toString)
                .filter { it.startsWith(builder.remaining) }
                .forEach(builder::suggest)

            builder.build()
        }.asCompletableFuture()
    }

    private fun dualityOpenIDsAsync(ctx: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder, basePerm: String): CompletableFuture<Suggestions> {
        return TMCoroutine.Supervised.async {
            val tmSender = ctx.source.sender.toTMSender()

            val results = if (tmSender.has("$basePerm.all")) database.getOpenTicketIDsAsync()
            else database.getOpenTicketIDsForUser(tmSender.asCreator())

            results.asSequence()
                .map(Long::toString)
                .filter { it.startsWith(builder.remaining) }
                .forEach(builder::suggest)

            builder.build()
        }.asCompletableFuture()
    }

    private fun hasSilentPermission(bukkitSender: BukkitCommandSender): Boolean {
        return when (val tmSender = bukkitSender.toTMSender()) {
            is CommandSender.OnlinePlayer -> tmSender.has("ticketmanager.commandArg.silence")
            is CommandSender.OnlineConsole -> true
        }
    }

    private fun hasOneDualityPermission(user: BukkitCommandSender, basePerm: String): Boolean {
        val tmSender = user.toTMSender()
        return tmSender.has("$basePerm.all") || tmSender.has("$basePerm.own")
    }

    fun ticketIDArgument(vararg otherChecks: (Ticket, CommandSender.Active) -> TicketGrabber.Error?
    ) = Commands.argument(locale.parameterID, TicketGrabberArgument( *otherChecks))

    private fun BukkitCommandSender.toTMSender(): CommandSender.Active = when (this) {
        is BukkitConsole -> PaperConsole(config.proxyOptions?.serverName)
        is BukkitPlayer -> PaperPlayer(this, config.proxyOptions?.serverName)
        else -> when ((this::class).toString()) {
            "class io.papermc.paper.brigadier.NullCommandSender" -> PaperConsole(config.proxyOptions?.serverName) // I guess this is something Paper does...
            else -> throw Exception("Unsupported Entity Type!:\n Class Type: \"${this::class}\"")
        }
    }

    private inline fun <U : ArgumentBuilder<CommandSourceStack, U>> ArgumentBuilder<CommandSourceStack, U>.executesTMMessage(
        crossinline commandTask: suspend (tmSender: CommandSender.Active, ctx: CommandContext<CommandSourceStack>) -> MessageNotification<CommandSender.Active>?
    ): ArgumentBuilder<CommandSourceStack, U> {
        return this.executes { ctx ->
            val tmSender = ctx.source.sender.toTMSender()

            TMCoroutine.Supervised.launch {
                if (!shouldRunCommand(tmSender)) return@launch

                launch { runOnCommandExtensions(tmSender) }
                launch {
                    commandTask(tmSender, ctx)?.let {
                        commandTasks.executeNotifications(tmSender, it)
                    }
                }
            }

            Command.SINGLE_SUCCESS
        }
    }

    private inline fun <U : ArgumentBuilder<CommandSourceStack, U>> ArgumentBuilder<CommandSourceStack, U>.executesTMMessageWithTicket(
        crossinline commandTask: suspend (tmSender: CommandSender.Active, ctx: CommandContext<CommandSourceStack>, ticket: Ticket) -> MessageNotification<CommandSender.Active>?
    ): ArgumentBuilder<CommandSourceStack, U> {
        return this.executes { ctx ->
            val tmSender = ctx.source.sender.toTMSender()
            val resultArg = ctx.getArgument(locale.parameterID, TicketGrabber::class.java)

            TMCoroutine.Supervised.launch {
                if (!shouldRunCommand(tmSender)) return@launch

                val ticket = when (val result = resultArg.get(tmSender)) {
                    is TicketGrabber.Success -> result.ticket
                    is TicketGrabber.Error -> result.errorComponent
                        .run(tmSender::sendMessage)
                        .run { return@launch }
                }

                launch { runOnCommandExtensions(tmSender) }
                launch {
                    commandTask(tmSender, ctx, ticket)?.let {
                        commandTasks.executeNotifications(tmSender, it)
                    }
                }
            }

            Command.SINGLE_SUCCESS
        }
    }

    private inline fun <U : ArgumentBuilder<CommandSourceStack, U>> ArgumentBuilder<CommandSourceStack, U>.executesTMAction(
        crossinline commandTask: suspend (tmSender: CommandSender.Active, ctx: CommandContext<CommandSourceStack>) -> Unit
    ): ArgumentBuilder<CommandSourceStack, U> {
        return this.executes { ctx ->
            val tmSender = ctx.source.sender.toTMSender()

            TMCoroutine.Supervised.launch {
                if (!shouldRunCommand(tmSender)) return@launch

                launch { runOnCommandExtensions(tmSender) }
                launch { commandTask(tmSender, ctx) }
            }

            Command.SINGLE_SUCCESS
        }
    }

    private inline fun <U : ArgumentBuilder<CommandSourceStack, U>> ArgumentBuilder<CommandSourceStack, U>.executesTMActionWithTicket(
        crossinline commandTask: suspend (tmSender: CommandSender.Active, ctx: CommandContext<CommandSourceStack>, ticket: Ticket) -> Unit
    ): ArgumentBuilder<CommandSourceStack, U> {
        return this.executes { ctx ->
            val tmSender = ctx.source.sender.toTMSender()
            val resultArg = ctx.getArgument(locale.parameterID, TicketGrabber::class.java)

            TMCoroutine.Supervised.launch {
                if (!shouldRunCommand(tmSender)) return@launch

                val ticket = when (val result = resultArg.get(tmSender)) {
                    is TicketGrabber.Success -> result.ticket
                    is TicketGrabber.Error -> result.errorComponent
                        .run(tmSender::sendMessage)
                        .run { return@launch }
                }

                launch { runOnCommandExtensions(tmSender) }
                launch { commandTask(tmSender, ctx, ticket) }
            }

            Command.SINGLE_SUCCESS
        }
    }

    private suspend fun runOnCommandExtensions(tmSender: CommandSender.Active) = coroutineScope {
        preCommandExtensionHolder.asyncAfters.forEach {     // Launch async post-commands
            launch { it.afterCommand(tmSender, permissions, locale) }
        }

        preCommandExtensionHolder.syncAfters.forEach {      // Call sync post-commands
            it.afterCommand(tmSender, permissions, locale)
        }
    }

    private suspend fun shouldRunCommand(tmSender: CommandSender.Active): Boolean {
        return preCommandExtensionHolder.deciders.asFlow()
            .map { it.beforeCommand(tmSender, permissions, locale) }
            .filter { it == Decision.BLOCK }
            .firstOrNull() == null // Short circuits
    }
}