package com.github.hoshikurama.ticketmanager.fabric.server.commands

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.registry.precommand.PreCommandExtension
import com.github.hoshikurama.ticketmanager.api.ticket.Assignment
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import com.github.hoshikurama.ticketmanager.commonse.PreCommandExtensionHolder
import com.github.hoshikurama.ticketmanager.commonse.commands.CommandTasks
import com.github.hoshikurama.ticketmanager.commonse.commands.MessageNotification
import com.github.hoshikurama.ticketmanager.commonse.misc.has
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.misc.templated
import com.github.hoshikurama.ticketmanager.fabric.server.PlayerNameUUIDStorage
import com.github.hoshikurama.ticketmanager.fabric.server.impls.FabricConsole
import com.github.hoshikurama.ticketmanager.fabric.server.impls.FabricPlayer
import com.github.hoshikurama.tmcoroutine.TMCoroutine
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType.getInteger
import com.mojang.brigadier.arguments.IntegerArgumentType.integer
import com.mojang.brigadier.arguments.LongArgumentType.getLong
import com.mojang.brigadier.arguments.LongArgumentType.longArg
import com.mojang.brigadier.arguments.StringArgumentType.*
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.SuggestionProvider
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.minecraft.command.CommandSource
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource

class Brigadier(
    private val config: Config,
    private val locale: Locale,
    private val database: AsyncDatabase,
    private val permissions: Permission,
    private val commandTasks: CommandTasks,
    private val platform: PlatformFunctions,
    private val preCommandExtensionHolder: PreCommandExtensionHolder,
    private val adventure: FabricServerAudiences,
    private val cache: PlayerNameUUIDStorage,
) {

    // Ticket Errors

    private fun ticketIsOpen(ticket: Ticket, ignored: CommandSender.Active): Error? {
        return if (ticket.status != Ticket.Status.OPEN)
            locale.brigadierTicketAlreadyClosed
                .parseMiniMessage()
                .run(::Error)
        else null
    }
    private fun userDualityError(basePerm: String) = { ticket: Ticket, sender: CommandSender.Active ->
        val hasAll = permissions.has(sender, "$basePerm.all", true)
        val hasOwn = permissions.has(sender, "$basePerm.own", true)

        val canRunCommand = hasAll || (hasOwn && ticket.creator == sender.asCreator())
        if (!canRunCommand)
            locale.brigadierNotYourTicket
                .parseMiniMessage()
                .run(::Error)
        else null
    }

    // Argument Suggestions

    private val suggestOpenTicketIDs = SuggestionProvider<ServerCommandSource> { _, builder ->
        TMCoroutine.Supervised.async { database.getOpenTicketIDsAsync().map(Long::toString) }
            .asCompletableFuture()
            .thenComposeAsync { CommandSource.suggestMatching(it, builder) }
    }
    private val suggestOfflinePlayers = SuggestionProvider<ServerCommandSource> { _, builder ->
        CommandSource.suggestMatching(cache.allNames(), builder)
    }
    private val suggestPermissionGroups = SuggestionProvider<ServerCommandSource> { _, builder ->
        CommandSource.suggestMatching(permissions.allGroupNames(), builder)
    }
    private fun suggestDualityOpenIDs(basePerm: String) = SuggestionProvider { ctx, builder ->
        TMCoroutine.Supervised.async {
            val tmSender = ctx.source.toTMSender()

            val results = if (permissions.has(tmSender, "$basePerm.all", true)) database.getOpenTicketIDsAsync()
            else database.getOpenTicketIDsForUser(tmSender.asCreator())

            results.map(Long::toString)
        }
            .asCompletableFuture()
            .thenComposeAsync { CommandSource.suggestMatching(it, builder) }
    }


    // Other Checks

    private fun checkPlayerNameInCache(nodeName: String) = { ctx: CommandContext<ServerCommandSource> ->
        getString(ctx, nodeName).run(cache::uuidOrNull) ?: throw CommandSyntaxException(PlayerNameNotFoundInStorageException) {
            "Username was not found in storage. Please enter a username of a player who has logged onto the server."
        }; Unit
    }
    private fun checkStringIsInPermissionGroup(nodeName: String) = { ctx: CommandContext<ServerCommandSource> ->
        val isValidGroup = getString(ctx, nodeName) in permissions.allGroupNames()
        if (!isValidGroup) throw CommandSyntaxException(InvalidPermissionGroupNameException) {
            "Please use an existing permission group name."
        }; Unit
    }

    // Command generator

    fun generateCommands(
        dispatcher: CommandDispatcher<ServerCommandSource>,
        //environment: RegistrationEnvironment,
        //registryAccess: CommandRegistryAccess,
    ) {

        // /ticket create <Comment...>
        kotlin.run {
            val commentArg = argument(locale.parameterComment, greedyString())
                .executeTMMessage { tmSender, ctx ->
                    commandTasks.create(tmSender,
                        message = getString(ctx, locale.parameterComment)
                    )
                }
            val createLiteral = literal(locale.commandWordCreate)
                .requires { source -> hasPermissions(source, "ticketmanager.command.create") }
                .then(commentArg)
            dispatcher.register(literal(locale.commandBase).then(createLiteral))
        }

        // /ticket assign <ID> user <Player>
        kotlin.run {
            val playerArgName = "<${locale.parameterLiteralPlayer}>"
            val playerArg = argument(playerArgName, string())
                .suggests(suggestOfflinePlayers)
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsOpen),
                    otherPreAsync = OtherRequests(checkPlayerNameInCache(playerArgName)),
                    commandTask = { tmSender, ticket, ctx ->
                        commandTasks.assign(tmSender,
                            assignment = getString(ctx, playerArgName)
                                .run(Assignment::Player),
                            ticket = ticket,
                            silent = false,
                        )
                    })
            val userLiteral = literal(locale.parameterLiteralPlayer)
                .then(playerArg)
            val idArg = argument(locale.parameterID, longArg())
                .suggests(suggestOpenTicketIDs)
                .then(userLiteral)
            val assignLiteral = literal(locale.commandWordAssign)
                .requires { source -> hasPermissions(source, "ticketmanager.command.assign") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(assignLiteral))
        }

        // /ticket assign <ID> group <Group>
        kotlin.run {
            val groupArgName = "<${locale.parameterLiteralGroup}>"
            val groupArg = argument(groupArgName, string())
                .suggests(suggestPermissionGroups)
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsOpen),
                    otherPreAsync = OtherRequests(checkStringIsInPermissionGroup(groupArgName)),
                    commandTask = { tmSender, ticket, ctx ->
                        commandTasks.assign(tmSender,
                            assignment = getString(ctx, groupArgName)
                                .run(Assignment::PermissionGroup),
                            ticket = ticket,
                            silent = false,
                        )
                    })
            val groupLiteral = literal(locale.parameterLiteralGroup)
                .then(groupArg)
            val idArg = argument(locale.parameterID, longArg())
                .suggests(suggestOpenTicketIDs)
                .then(groupLiteral)
            val assignLiteral = literal(locale.commandWordAssign)
                .requires { source -> hasPermissions(source, "ticketmanager.command.assign") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(assignLiteral))
        }

        // /ticket assign <ID> phrase <phrase...>
        kotlin.run {
            val phraseArgName = "<${locale.parameterLiteralPhrase}>"
            val phraseArg = argument(phraseArgName, greedyString())
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsOpen),
                    commandTask = { tmSender, ticket, ctx ->
                        commandTasks.assign(tmSender,
                            assignment = getString(ctx, phraseArgName)
                                .run(Assignment::Phrase),
                            ticket = ticket,
                            silent = false,
                        )
                    })
            val phraseLiteral = literal(locale.parameterLiteralPhrase)
                .then(phraseArg)
            val idArg = argument(locale.parameterID, longArg())
                .suggests(suggestOpenTicketIDs)
                .then(phraseLiteral)
            val assignLiteral = literal(locale.commandWordAssign)
                .requires { source -> hasPermissions(source, "ticketmanager.command.assign") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(assignLiteral))
        }

        // /ticket s.assign <ID> player <Player>
        kotlin.run {
            val playerArgName = "<${locale.parameterLiteralPlayer}>"
            val playerArg = argument(playerArgName, string())
                .suggests(suggestOfflinePlayers)
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsOpen),
                    otherPreAsync = OtherRequests(checkPlayerNameInCache(playerArgName)),
                    commandTask = { tmSender, ticket, ctx ->
                        commandTasks.assign(tmSender,
                            assignment = getString(ctx, playerArgName)
                                .run(Assignment::Player),
                            ticket = ticket,
                            silent = true,
                        )
                    })
            val userLiteral = literal(locale.parameterLiteralPlayer)
                .then(playerArg)
            val idArg = argument(locale.parameterID, longArg())
                .suggests(suggestOpenTicketIDs)
                .then(userLiteral)
            val assignLiteral = literal(locale.commandWordSilentAssign)
                .requires { hasPermissions(it, "ticketmanager.command.assign", "ticketmanager.commandArg.silence") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(assignLiteral))
        }

        // /ticket s.assign <ID> group <Group>
        kotlin.run {
            val groupArgName = "<${locale.parameterLiteralGroup}>"
            val groupArg = argument(groupArgName, string())
                .suggests(suggestPermissionGroups)
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsOpen),
                    otherPreAsync = OtherRequests(checkStringIsInPermissionGroup(groupArgName)),
                    commandTask = { tmSender, ticket, ctx ->
                        commandTasks.assign(tmSender,
                            assignment = getString(ctx, groupArgName)
                                .run(Assignment::PermissionGroup),
                            ticket = ticket,
                            silent = true,
                        )
                    })
            val groupLiteral = literal(locale.parameterLiteralGroup)
                .then(groupArg)
            val idArg = argument(locale.parameterID, longArg())
                .suggests(suggestOpenTicketIDs)
                .then(groupLiteral)
            val assignLiteral = literal(locale.commandWordSilentAssign)
                .requires { hasPermissions(it, "ticketmanager.command.assign", "ticketmanager.commandArg.silence") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(assignLiteral))
        }

        // /ticket s.assign <ID> phrase <phrase...>
        kotlin.run {
            val phraseArgName = "<${locale.parameterLiteralPhrase}>"
            val phraseArg = argument(phraseArgName, greedyString())
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsOpen),
                    commandTask = { tmSender, ticket, ctx ->
                        commandTasks.assign(tmSender,
                            assignment = getString(ctx, phraseArgName)
                                .run(Assignment::Phrase),
                            ticket = ticket,
                            silent = true,
                        )
                    })
            val phraseLiteral = literal(locale.parameterLiteralPhrase)
                .then(phraseArg)
            val idArg = argument(locale.parameterID, longArg())
                .suggests(suggestOpenTicketIDs)
                .then(phraseLiteral)
            val assignLiteral = literal(locale.commandWordSilentAssign)
                .requires { hasPermissions(it, "ticketmanager.command.assign", "ticketmanager.commandArg.silence") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(assignLiteral))
        }

        // /ticket unassign <ID>
        kotlin.run {
             val idArg = argument(locale.parameterID, longArg())
                 .suggests(suggestOpenTicketIDs)
                 .executeTMMessageWithTicket(
                     ticketRequest = TicketRequest(::ticketIsOpen),
                     commandTask = { tmSender, ticket, _ ->
                         commandTasks.unAssign(tmSender,
                             ticket = ticket,
                             silent = false,
                         )
                     }
                 )
            val unAssignLiteral = literal(locale.commandWordUnassign)
                .requires { hasPermissions(it, "ticketmanager.command.assign") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(unAssignLiteral))
        }

        // /ticket s.unassign <ID>
        kotlin.run {
            val idArg = argument(locale.parameterID, longArg())
                .suggests(suggestOpenTicketIDs)
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest( ::ticketIsOpen),
                    commandTask = { tmSender, ticket, _ ->
                        commandTasks.unAssign(tmSender,
                            ticket = ticket,
                            silent = true,
                        )
                    }
                )
            val unAssignLiteral = literal(locale.commandWordSilentUnassign)
                .requires { hasPermissions(it, "ticketmanager.command.assign", "ticketmanager.commandArg.silence") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(unAssignLiteral))
        }

        // /ticket claim <ID>
        kotlin.run {
            val idArg = argument(locale.parameterID, longArg())
                .suggests(suggestOpenTicketIDs)
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsOpen),
                    commandTask = { tmSender, ticket, _ ->
                        commandTasks.claim(tmSender,
                            ticket = ticket,
                            silent = false
                        )
                    }
                )
            val claimLiteral = literal(locale.commandWordClaim)
                .requires { hasPermissions(it, "ticketmanager.command.assign") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(claimLiteral))
        }

        // /ticket s.claim <ID>
        kotlin.run {
            val idArg = argument(locale.parameterID, longArg())
                .suggests(suggestOpenTicketIDs)
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsOpen),
                    commandTask = { tmSender, ticket, _ ->
                        commandTasks.claim(tmSender,
                            ticket = ticket,
                            silent = true
                        )
                    }
                )
            val claimLiteral = literal(locale.commandWordSilentClaim)
                .requires { hasPermissions(it, "ticketmanager.command.assign", "ticketmanager.commandArg.silence") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(claimLiteral))
        }

        // /ticket close <ID>
        kotlin.run {
            val argID = argument(locale.parameterID, longArg())
                .suggests(suggestDualityOpenIDs("ticketmanager.command.close"))
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsOpen, userDualityError("ticketmanager.command.close")),
                    commandTask = { tmSender, ticket, _ ->
                        commandTasks.closeWithoutComment(tmSender,
                            ticket = ticket,
                            silent = false
                        )
                    })
            val closeLiteral = literal(locale.commandWordClose)
                .requires { hasOneDualityPermission(it, "ticketmanager.command.close") }
                .then(argID)
            dispatcher.register(literal(locale.commandBase).then(closeLiteral))
        }

        // /ticket s.close <ID>
        kotlin.run {
            val argID = argument(locale.parameterID, longArg())
                .suggests(suggestDualityOpenIDs("ticketmanager.command.close"))
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsOpen, userDualityError("ticketmanager.command.close")),
                    commandTask = { tmSender, ticket, _ ->
                        commandTasks.closeWithoutComment(tmSender,
                            ticket = ticket,
                            silent = true
                        )
                    })
            val closeLiteral = literal(locale.commandWordSilentClose)
                .requires { hasOneDualityPermission(it, "ticketmanager.command.close") &&
                        hasPermissions(it, "ticketmanager.commandArg.silence")
                }
                .then(argID)
            dispatcher.register(literal(locale.commandBase).then(closeLiteral))
        }

        // /ticket close <ID> [Comment...]
        kotlin.run {
            val commentArg = argument(locale.parameterComment, greedyString())
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsOpen, userDualityError("ticketmanager.command.close")),
                    commandTask = { tmSender, ticket, ctx ->
                        commandTasks.closeWithComment(tmSender,
                            ticket = ticket,
                            comment = getString(ctx, locale.parameterComment),
                            silent = false
                        )
                    })
            val argID = argument(locale.parameterID, longArg())
                .suggests(suggestDualityOpenIDs("ticketmanager.command.close"))
                .then(commentArg)
            val closeLiteral = literal(locale.commandWordClose)
                .requires { hasOneDualityPermission(it, "ticketmanager.command.close") }
                .then(argID)
            dispatcher.register(literal(locale.commandBase).then(closeLiteral))
        }

        // /ticket s.close <ID> [Comment...]
        kotlin.run {
            val commentArg = argument(locale.parameterComment, greedyString())
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsOpen, userDualityError("ticketmanager.command.close")),
                    commandTask = { tmSender, ticket, ctx ->
                        commandTasks.closeWithComment(tmSender,
                            ticket = ticket,
                            comment = getString(ctx, locale.parameterComment),
                            silent = true
                        )
                    })
            val argID = argument(locale.parameterID, longArg())
                .suggests(suggestDualityOpenIDs("ticketmanager.command.close"))
                .then(commentArg)
            val closeLiteral = literal(locale.commandWordSilentClose)
                .requires { hasOneDualityPermission(it, "ticketmanager.command.close") &&
                        hasPermissions(it, "ticketmanager.commandArg.silence")
                }.then(argID)
            dispatcher.register(literal(locale.commandBase).then(closeLiteral))
        }

        // /ticket closeall <Lower ID> <Upper ID>
        kotlin.run {
            val upperIDArg = argument(locale.parameterUpperID, longArg())
                .executeTMMessage { tmSender, ctx ->
                    commandTasks.closeAll(tmSender,
                        lowerBound = getLong(ctx, locale.parameterLowerID),
                        upperBound = getLong(ctx, locale.parameterUpperID),
                        silent = false
                    )
                }
            val lowerIDArg = argument(locale.parameterLowerID, longArg())
                .then(upperIDArg)
            val closeAllLiteral = literal(locale.commandWordCloseAll)
                .requires { hasPermissions(it, "ticketmanager.command.closeAll") }
                .then(lowerIDArg)
            dispatcher.register(literal(locale.commandBase).then(closeAllLiteral))
        }

        // /ticket s.closeall <Lower ID> <Upper ID>
        kotlin.run {
            val upperIDArg = argument(locale.parameterUpperID, longArg())
                .executeTMMessage { tmSender, ctx ->
                    commandTasks.closeAll(tmSender,
                        lowerBound = getLong(ctx, locale.parameterLowerID),
                        upperBound = getLong(ctx, locale.parameterUpperID),
                        silent = true
                    )
                }
            val lowerIDArg = argument(locale.parameterLowerID, longArg())
                .then(upperIDArg)
            val closeAllLiteral = literal(locale.commandWordSilentCloseAll)
                .requires { hasPermissions(it, "ticketmanager.command.closeAll", "ticketmanager.commandArg.silence") }
                .then(lowerIDArg)
            dispatcher.register(literal(locale.commandBase).then(closeAllLiteral))
        }

        // /ticket comment <ID> <Comment…>
        kotlin.run {
            val commentArg = argument(locale.parameterComment, greedyString())
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsOpen, userDualityError("ticketmanager.command.comment")),
                    commandTask = { tmSender, ticket, ctx ->
                        commandTasks.comment(tmSender,
                            ticket = ticket,
                            comment = getString(ctx, locale.parameterComment),
                            silent = false
                        )
                    })
            val idArg = argument(locale.parameterID, longArg())
                .suggests(suggestDualityOpenIDs("ticketmanager.command.comment"))
                .then(commentArg)
            val commentLiteral = literal(locale.commandWordComment)
                .requires { hasOneDualityPermission(it, "ticketmanager.command.comment") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(commentLiteral))
        }

        // /ticket s.comment <ID> <Comment…>
        kotlin.run {
            val commentArg = argument(locale.parameterComment, greedyString())
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsOpen, userDualityError("ticketmanager.command.comment")),
                    commandTask = { tmSender, ticket, ctx ->
                        commandTasks.comment(tmSender,
                            ticket = ticket,
                            comment = getString(ctx, locale.parameterComment),
                            silent = true
                        )
                    })
            val idArg = argument(locale.parameterID, longArg())
                .suggests(suggestDualityOpenIDs("ticketmanager.command.comment"))
                .then(commentArg)
            val commentLiteral = literal(locale.commandWordSilentComment)
                .requires { hasOneDualityPermission(it, "ticketmanager.command.comment") &&
                        hasPermissions(it, "ticketmanager.commandArg.silence")
                }.then(idArg)
            dispatcher.register(literal(locale.commandBase).then(commentLiteral))
        }

        // /ticket list [Page]
        kotlin.run {
            val pageArg = argument(locale.parameterPage, integer())
                .executeTMAction { tmSender, ctx ->
                    commandTasks.list(tmSender,
                        requestedPage = getInteger(ctx, locale.parameterPage),
                    )
                }
            val listLiteral = literal(locale.commandWordList)
                .requires { hasPermissions(it, "ticketmanager.command.list") }
                .then(pageArg)
            dispatcher.register(literal(locale.commandBase).then(listLiteral))
        }

        // /ticket list
        dispatcher.register(literal(locale.commandBase)
            .then(literal(locale.commandWordList)
                .requires { hasPermissions(it, "ticketmanager.command.list") }
                .executeTMAction { tmSender, _ -> commandTasks.list(tmSender, 1,) }
            )
        )

        // /ticket listassigned [Page]
        kotlin.run {
            val pageArg = argument(locale.parameterPage, integer())
                .executeTMAction { tmSender, ctx ->
                    commandTasks.listAssigned(tmSender,
                        requestedPage = getInteger(ctx, locale.parameterPage),
                    )
                }
            val listLiteral = literal(locale.commandWordListAssigned)
                .requires { hasPermissions(it, "ticketmanager.command.list") }
                .then(pageArg)
            dispatcher.register(literal(locale.commandBase).then(listLiteral))
        }

        // /ticket listassigned
        kotlin.run {
            val pageArg = argument(locale.parameterPage, integer())
                .executeTMAction { tmSender, _ ->
                    commandTasks.listAssigned(tmSender,
                        requestedPage = 1,
                    )
                }
            val listLiteral = literal(locale.commandWordListAssigned)
                .requires { hasPermissions(it, "ticketmanager.command.list") }
                .then(pageArg)
            dispatcher.register(literal(locale.commandBase).then(listLiteral))
        }

// TODO FINISH REMAINING COMMANDS


//commandTask = { tmSender, ticket, ctx -> }
//dispatcher.register(literal(locale.commandBase).then())

    }

    /*

        // /ticket listassigned
        commandAPICommand(locale.commandBase) {
            literalArgument(locale.commandWordListAssigned) {
                withPermission("ticketmanager.command.list")
            }
            executeTMAction { tmSender, _ ->
                commandTasks.listAssigned(tmSender, 1)
            }
        }
     */


    // Other stuff

    private fun ServerCommandSource.toTMSender(): CommandSender.Active {
        return if (isExecutedByPlayer) FabricPlayer(player!!, config.proxyOptions?.serverName)
        else FabricConsole(config.proxyOptions?.serverName, adventure)
    }

    private fun hasPermissions(source: ServerCommandSource, vararg permission: String): Boolean {
        return when (val user = source.toTMSender()) {
            is CommandSender.OnlinePlayer -> permission.all { permissions.has(user, it) }
            is CommandSender.OnlineConsole -> true
        }
    }

    private fun hasOneDualityPermission(source: ServerCommandSource, basePerm: String): Boolean {
        return hasPermissions(source, "$basePerm.all") || hasPermissions(source, "$basePerm.own")
    }



    private suspend fun shouldRunCommand(tmSender: CommandSender.Active): Boolean {
        return preCommandExtensionHolder.deciders.asFlow()
            .map { it.beforeCommand(tmSender, permissions, locale) }
            .filter { it == PreCommandExtension.SyncDecider.Decision.BLOCK }
            .firstOrNull() == null // Short circuits
    }

    private suspend fun runOnCommandExtensions(tmSender: CommandSender.Active) = coroutineScope {
        preCommandExtensionHolder.asyncAfters.forEach {     // Launch async post-commands
            launch { it.afterCommand(tmSender, permissions, locale) }
        }

        preCommandExtensionHolder.syncAfters.forEach {      // Call sync post-commands
            it.afterCommand(tmSender, permissions, locale)
        }
    }


    // Inline executors

    private inline fun <T : ArgumentBuilder<ServerCommandSource, T>> ArgumentBuilder<ServerCommandSource, T>.executeTMMessage(
        crossinline commandTask: suspend (tmSender: CommandSender.Active, ctx: CommandContext<ServerCommandSource>) -> MessageNotification<CommandSender.Active>?
    ) = executes { ctx ->
        val tmSender = ctx.source.toTMSender()

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

    private inline fun <T : ArgumentBuilder<ServerCommandSource, T>> ArgumentBuilder<ServerCommandSource, T>.executeTMAction(
        crossinline commandTask: suspend (tmSender: CommandSender.Active, ctx: CommandContext<ServerCommandSource>) -> Unit
    ) = executes { ctx ->
        val tmSender = ctx.source.toTMSender()

        TMCoroutine.Supervised.launch {
            if (!shouldRunCommand(tmSender)) return@launch

            launch { runOnCommandExtensions(tmSender) }
            launch { commandTask(tmSender, ctx) }
        }
        Command.SINGLE_SUCCESS
    }



    private inner class TicketRequest(val nodeName: String = locale.parameterID, val ticketChecks: List<(Ticket, CommandSender.Active) -> Error?>) {
        constructor(vararg ticketChecks: (Ticket, CommandSender.Active) -> Error?): this(ticketChecks = ticketChecks.asList())
    }
    private data class OtherRequests(val checks: List<(CommandContext<ServerCommandSource>) -> Unit>) {
        constructor(vararg checks: (CommandContext<ServerCommandSource>) -> Unit): this(checks.asList())
    }

    private fun checkTicket(ticketRequest: TicketRequest, ctx: CommandContext<ServerCommandSource>) = TMCoroutine.Supervised.async {
        val id = getLong(ctx, ticketRequest.nodeName)
        val ticketOrNull = database.getTicketOrNullAsync(id)

        // Filter Invalid Ticket
        val ticket = ticketOrNull ?: return@async locale.brigadierInvalidID
            .parseMiniMessage("id" templated id.toString())
            .run(::Error)

        // Other Checks
        val sender = ctx.source.toTMSender()
        val failPoint = ticketRequest.ticketChecks.mapNotNull { it(ticket, sender) }
        if (failPoint.isNotEmpty()) failPoint.first() else Success(ticket)
    }

    private inline fun <T : ArgumentBuilder<ServerCommandSource, T>> ArgumentBuilder<ServerCommandSource, T>.executeTMMessageWithTicket(
        crossinline commandTask: suspend (tmSender: CommandSender.Active, ticket: Ticket, ctx: CommandContext<ServerCommandSource>) -> MessageNotification<CommandSender.Active>?,
        ticketRequest: TicketRequest,
        otherPreAsync: OtherRequests? = null,
    ) = executes { ctx ->
        otherPreAsync?.checks?.forEach { it(ctx) }

        val tmSender = ctx.source.toTMSender()
        val ticketResult = checkTicket(ticketRequest, ctx)

        TMCoroutine.Supervised.launch {
            if (!shouldRunCommand(tmSender)) return@launch

            val ticket = when (val result = ticketResult.await()) {
                is Success -> result.ticket
                is Error -> result.errorComponent
                    .run(tmSender::sendMessage)
                    .run { return@launch }
            }

            launch { runOnCommandExtensions(tmSender) }
            launch {
                commandTask(tmSender, ticket, ctx)?.let {
                    commandTasks.executeNotifications(tmSender, it)
                }
            }
        }
        Command.SINGLE_SUCCESS
    }
}