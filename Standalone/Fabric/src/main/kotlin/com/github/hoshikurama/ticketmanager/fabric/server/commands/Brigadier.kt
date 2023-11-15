package com.github.hoshikurama.ticketmanager.fabric.server.commands

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.database.utils.Option
import com.github.hoshikurama.ticketmanager.api.registry.database.utils.SearchConstraints
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.registry.precommand.PreCommandExtension
import com.github.hoshikurama.ticketmanager.api.ticket.ActionLocation
import com.github.hoshikurama.ticketmanager.api.ticket.Assignment
import com.github.hoshikurama.ticketmanager.api.ticket.Creator
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
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.minecraft.command.CommandSource
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource

private typealias SCSymbol = SearchConstraints.Symbol

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
    @Suppress("UNUSED_PARAMETER") // Included for function reference
    private fun ticketIsOpen(ticket: Ticket, ignored: CommandSender.Active): Error? {
        return if (ticket.status != Ticket.Status.OPEN)
            locale.brigadierTicketAlreadyClosed
                .parseMiniMessage()
                .run(::Error)
        else null
    }
    @Suppress("UNUSED_PARAMETER") // Included for function reference
    private fun ticketIsClosed(ticket: Ticket, ignored: CommandSender.Active): Error? {
        return if (ticket.status != Ticket.Status.CLOSED)
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

    fun generateCommands(dispatcher: CommandDispatcher<ServerCommandSource>) {

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
        dispatcher.register(literal(locale.commandBase)
            .then(literal(locale.commandWordListAssigned)
                .requires { hasPermissions(it, "ticketmanager.command.list") }
                .executeTMAction { tmSender, _ ->
                    commandTasks.listAssigned(tmSender,
                        requestedPage = 1,
                    )
                }
            )
        )

        // /ticket listunassigned [Page]
        kotlin.run {
            val pageArg = argument(locale.parameterPage, integer())
                .executeTMAction { tmSender, ctx ->
                    commandTasks.listUnassigned(tmSender,
                        requestedPage = getInteger(ctx, locale.parameterPage),
                    )
                }
            val listLiteral = literal(locale.commandWordListUnassigned)
                .requires { hasPermissions(it, "ticketmanager.command.list") }
                .then(pageArg)
            dispatcher.register(literal(locale.commandBase).then(listLiteral))
        }

        // /ticket listunassigned
        dispatcher.register(literal(locale.commandBase)
            .then(literal(locale.commandWordListUnassigned)
                .requires { hasPermissions(it, "ticketmanager.command.list") }
                .executeTMAction { tmSender, _ ->
                    commandTasks.listUnassigned(tmSender,
                        requestedPage = 1
                    )
                }
            )
        )

        // /ticket help
        dispatcher.register(literal(locale.commandBase)
            .then(literal(locale.commandWordHelp)
                .requires { hasPermissions(it, "ticketmanager.command.help") }
                .executeTMAction { tmSender, _ ->
                    commandTasks.help(tmSender)
                }
            )
        )

        // /ticket reopen <ID>
        kotlin.run {
            val idArg = argument(locale.parameterID, longArg())
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsClosed),
                    commandTask = { tmSender, ticket, _ ->
                        commandTasks.reopen(tmSender,
                            ticket = ticket,
                            silent = false
                        )
                    })
            val reopenLiteral = literal(locale.commandWordReopen)
                .requires { hasPermissions(it, "ticketmanager.command.reopen") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(reopenLiteral))
        }

        // /ticket s.reopen <ID>
        kotlin.run {
            val idArg = argument(locale.parameterID, longArg())
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsClosed),
                    commandTask = { tmSender, ticket, _ ->
                        commandTasks.reopen(tmSender,
                            ticket = ticket,
                            silent = true
                        )
                    })
            val reopenLiteral = literal(locale.commandWordSilentReopen)
                .requires { hasPermissions(it, "ticketmanager.command.reopen", "ticketmanager.commandArg.silence") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(reopenLiteral))
        }

        // /ticket version
        dispatcher.register(literal(locale.commandBase)
            .then(literal(locale.commandWordVersion)
                .executeTMAction { tmSender, _ ->
                    commandTasks.version(tmSender)
                }
            )
        )

        // /ticket teleport <ID>
        kotlin.run {
            val idArg = argument(locale.parameterID, longArg())
                .executeTMActionWithTicket(
                    commandTask = { tmSender, ticket, _ -> commandTasks.teleport(tmSender, ticket) },
                    ticketRequest = TicketRequest({ ticket, sender ->
                        val location = ticket.actions[0].location

                        // Only teleport to player tickets
                        if (location !is ActionLocation.FromPlayer)
                            return@TicketRequest locale.brigadierConsoleLocTP.parseMiniMessage().run(::Error)

                        when (location.server == config.proxyOptions?.serverName) {
                            true -> if (!permissions.has(sender, "ticketmanager.command.teleport", false))
                                return@TicketRequest Error(locale.brigadierNoTPSameServer.parseMiniMessage())
                            false -> when (config.proxyOptions != null) {
                                false -> return@TicketRequest Error(locale.brigadierNoTPProxyDisabled.parseMiniMessage())
                                true -> if (!permissions.has(sender, "ticketmanager.command.proxyteleport", false))
                                    return@TicketRequest Error(locale.brigadierNoTPDiffServer.parseMiniMessage())
                            }
                        }
                        null
                    }),
                )
            val teleportLiteral = literal(locale.commandWordTeleport)
                .requires { hasPermissions(it, "ticketmanager.command.teleport") ||
                        hasPermissions(it, "ticketmanager.command.proxyteleport")
                }.then(idArg)
            dispatcher.register(literal(locale.commandBase).then(teleportLiteral))
        }

        // /ticket view <ID>
        kotlin.run {
            val idArg = argument(locale.parameterID, longArg())
                .executeTMActionWithTicket(
                    ticketRequest = TicketRequest(userDualityError("ticketmanager.command.view")),
                    commandTask = { tmSender, ticket, _ -> commandTasks.view(tmSender, ticket) }
                )
            val viewLiteral = literal(locale.commandWordView)
                .requires { hasOneDualityPermission(it, "ticketmanager.command.view") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(viewLiteral))
        }

        // /ticket viewdeep <ID>
        kotlin.run {
            val idArg = argument(locale.parameterID, longArg())
                .executeTMActionWithTicket(
                    ticketRequest = TicketRequest(userDualityError("ticketmanager.command.viewdeep")),
                    commandTask = { tmSender, ticket, _ -> commandTasks.viewDeep(tmSender, ticket) }
                )
            val viewDeepLiteral = literal(locale.commandWordDeepView)
                .requires { hasOneDualityPermission(it, "ticketmanager.command.viewdeep") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(viewDeepLiteral))
        }

        // /ticket setpriority <ID> <Priority>
        kotlin.run {
            val priorityArg = argument(locale.searchPriority, string())
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsOpen),
                    commandTask = { tmSender, ticket, ctx ->
                        commandTasks.setPriority(tmSender,
                            ticket = ticket,
                            priority = getString(ctx, locale.searchPriority)
                                .run(::numberOrWordToPriority),
                            silent = false
                        )
                    },
                    otherPreAsync = OtherRequests({ ctx ->
                        val str = getString(ctx, locale.searchPriority)
                        when (str) {
                            locale.priorityLowest, locale.priorityLow, locale.priorityNormal,
                            locale.priorityHigh, locale.priorityHighest, "1", "2", "3", "4", "5" -> Unit
                            else -> throw CommandSyntaxException(InvalidPriorityAssignmentException) { "Please select from one of the tab completions." }
                        }
                    })
                )
            val idArg = argument(locale.parameterID, longArg())
                .suggests(suggestOpenTicketIDs)
                .then(priorityArg)
            val setPriorityLiteral = literal(locale.commandWordSetPriority)
                .requires { hasPermissions(it, "ticketmanager.command.setPriority") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(setPriorityLiteral))
        }

        // /ticket s.setpriority <ID> <Priority>
        kotlin.run {
            val priorityArg = argument(locale.searchPriority, string())
                .executeTMMessageWithTicket(
                    ticketRequest = TicketRequest(::ticketIsOpen),
                    commandTask = { tmSender, ticket, ctx ->
                        commandTasks.setPriority(tmSender,
                            ticket = ticket,
                            priority = getString(ctx, locale.searchPriority)
                                .run(::numberOrWordToPriority),
                            silent = true
                        )
                    },
                    otherPreAsync = OtherRequests({ ctx ->
                        val str = getString(ctx, locale.searchPriority)
                        when (str) {
                            locale.priorityLowest, locale.priorityLow, locale.priorityNormal,
                            locale.priorityHigh, locale.priorityHighest, "1", "2", "3", "4", "5" -> Unit
                            else -> throw CommandSyntaxException(InvalidPriorityAssignmentException) { "Please select from one of the tab completions." }
                        }
                    })
                )
            val idArg = argument(locale.parameterID, longArg())
                .suggests(suggestOpenTicketIDs)
                .then(priorityArg)
            val setPriorityLiteral = literal(locale.commandWordSilentSetPriority)
                .requires { hasPermissions(it, "ticketmanager.command.setPriority", "ticketmanager.commandArg.silence") }
                .then(idArg)
            dispatcher.register(literal(locale.commandBase).then(setPriorityLiteral))
        }

        // /ticket history
        dispatcher.register(literal(locale.commandBase)
            .then(literal(locale.commandWordHistory)
                .requires { hasOneDualityPermission(it, "ticketmanager.command.history") }
                .executeTMAction { tmSender, _ ->
                    commandTasks.history(tmSender,
                        checkedCreator = tmSender.asCreator(),
                        requestedPage = 1
                    )
                }
            )
        )

        // /ticket history Console
        kotlin.run {
            val consoleLiteral = literal(locale.consoleName)
                .executeTMAction { tmSender, _ ->
                    commandTasks.history(tmSender,
                        checkedCreator = Creator.Console,
                        requestedPage = 1
                    )
                }
            val historyLiteral = literal(locale.commandWordHistory)
                .requires { hasPermissions(it, "ticketmanager.command.history.all") }
                .then(consoleLiteral)
            dispatcher.register(literal(locale.commandBase).then(historyLiteral))
        }

        // /ticket history Console [Page]
        kotlin.run {
            val pageArg = argument(locale.parameterPage, integer())
                .executeTMAction { tmSender, ctx ->
                    commandTasks.history(tmSender,
                        checkedCreator = Creator.Console,
                        requestedPage = getInteger(ctx, locale.parameterPage)
                    )
                }
            val consoleLiteral = literal(locale.consoleName)
                .then(pageArg)
            val historyLiteral = literal(locale.commandWordHistory)
                .requires { hasPermissions(it, "ticketmanager.command.history.all") }
                .then(consoleLiteral)
            dispatcher.register(literal(locale.commandBase).then(historyLiteral))
        }

        // /ticket history [Page]
        kotlin.run {
            val pageArg = argument(locale.parameterPage, integer())
                .executeTMAction { tmSender, ctx ->
                    commandTasks.history(tmSender,
                        checkedCreator = tmSender.asCreator(),
                        requestedPage = getInteger(ctx, locale.parameterPage)
                    )
                }
            val historyLiteral = literal(locale.commandWordHistory)
                .requires { hasOneDualityPermission(it, "ticketmanager.command.history") }
                .then(pageArg)
            dispatcher.register(literal(locale.commandBase).then(historyLiteral))
        }

        // /ticket history [User]
        kotlin.run {
            val userArg = argument(locale.parameterUser, string())
                .executeTMAction(otherPreAsync = OtherRequests(checkPlayerNameInCache(locale.parameterUser))) { tmSender, ctx ->
                    commandTasks.history(tmSender,
                        checkedCreator = getString(ctx, locale.parameterUser)
                            .run(cache::uuidOrNull)!!
                            .run(Creator::User),
                        requestedPage = 1
                    )
                }
            val historyLiteral = literal(locale.commandWordHistory)
                .requires { hasPermissions(it, "ticketmanager.command.history.all") }
                .then(userArg)
            dispatcher.register(literal(locale.commandBase).then(historyLiteral))
        }

        // /ticket history [User] [Page]
        kotlin.run {
            val pageArg = argument(locale.parameterPage, integer())
                .executeTMAction(otherPreAsync = OtherRequests(checkPlayerNameInCache(locale.parameterUser))) { tmSender, ctx ->
                    commandTasks.history(tmSender,
                        checkedCreator = getString(ctx, locale.parameterUser)
                            .run(cache::uuidOrNull)!!
                            .run(Creator::User),
                        requestedPage = getInteger(ctx, locale.parameterPage)
                    )
                }
            val userArg = argument(locale.parameterUser, string())
                .then(pageArg)
            val historyLiteral = literal(locale.commandWordHistory)
                .requires { hasPermissions(it, "ticketmanager.command.history.all") }
                .then(userArg)
            dispatcher.register(literal(locale.commandBase).then(historyLiteral))
        }

        // /ticket search where <Params...>
        kotlin.run {
            val paramsArgs = argument(locale.parameterConstraints, greedyString())
                .suggests { context, builder ->  // Remove everything behind last &&
                    val curNodeInput = builder.input.split(" ", limit = 4)[3]
                    val curArgsSet = curNodeInput.split(" && ").last().split(" ")

                    val newBuilder = builder.createOffset(builder.start + curNodeInput.lastIndexOf(" ") + 1)
                    val keywordList = listOf(
                        locale.searchAssigned,
                        locale.searchCreator,
                        locale.searchKeywords,
                        locale.searchPriority,
                        locale.searchStatus,
                        locale.searchWorld,
                        locale.searchClosedBy,
                        locale.searchLastClosedBy,
                        locale.searchTime,
                    )


                    if (curArgsSet.size > 2 && curArgsSet[0] in keywordList) {
                        when (curArgsSet[0]) {
                            locale.searchCreator, locale.searchClosedBy, locale.searchLastClosedBy ->
                                if (curArgsSet.size == 3)
                                    platform.getOnlineSeenPlayerNames(context.source.toTMSender()) + listOf(locale.consoleName)
                                else listOf("&&")

                            locale.searchAssigned ->
                                if (curArgsSet.size == 3)
                                    listOf(locale.consoleName, locale.miscNobody, locale.parameterLiteralPlayer, locale.parameterLiteralGroup, locale.parameterLiteralPhrase)
                                else if (curArgsSet.size > 3 && curArgsSet[2] == locale.parameterLiteralPhrase)
                                    listOf("${locale.parameterLiteralPhrase}...", "&&")
                                else listOf("&&")

                            locale.searchPriority ->
                                if (curArgsSet.size == 3) {
                                    listOf("1", "2", "3", "4", "5", locale.priorityLowest, locale.priorityLow,
                                        locale.priorityNormal, locale.priorityHigh, locale.priorityHighest,
                                    )
                                } else listOf("&&")

                            locale.searchStatus ->
                                if (curArgsSet.size == 3)
                                    listOf(locale.statusOpen, locale.statusClosed)
                                else listOf("&&")

                            locale.searchWorld ->
                                if (curArgsSet.size == 3)
                                    platform.getWorldNames()
                                else listOf("&&")

                            locale.searchTime ->
                                if (curArgsSet.size % 2 == 1)
                                    if (curArgsSet.size > 4)
                                        listOf("&&", "<${locale.searchTime}>")
                                    else listOf( "<${locale.searchTime}>")
                                else listOf(locale.timeSeconds.trimStart(), locale.timeMinutes.trimStart(), locale.timeHours.trimStart(),
                                    locale.timeDays.trimStart(), locale.timeWeeks.trimStart(), locale.timeYears.trimStart(),
                                )
                            locale.searchKeywords -> {
                                if (curArgsSet.size == 3 || curArgsSet[curArgsSet.lastIndex-1] == "||")
                                    listOf("<${locale.searchKeywords}>")
                                else listOf("||", "&&", "<${locale.searchKeywords}>")
                            }
                            else -> throw Exception("Impossible")
                        }
                            .filter { it.startsWith(curArgsSet.last()) }
                            .forEach(newBuilder::suggest)
                    }               // "somethingHere "
                    else if (curArgsSet.size == 2 && curArgsSet[0] in keywordList) {
                        when (curArgsSet[0]) {
                            locale.searchCreator,
                            locale.searchAssigned,
                            locale.searchLastClosedBy,
                            locale.searchClosedBy,
                            locale.searchWorld,
                            locale.searchStatus,
                            locale.searchKeywords -> listOf("=", "!=")
                            locale.searchPriority -> listOf("=", "!=", "<", ">")
                            locale.searchTime -> listOf("<", ">")
                            else -> throw Exception("Reach Impossible")
                        }
                            .filter { it.startsWith(curArgsSet.last()) }
                            .forEach(newBuilder::suggest)
                    }
                    else keywordList
                        .filter { it.startsWith(curArgsSet.last()) }
                        .forEach(newBuilder::suggest)

                    newBuilder.buildFuture()
                }
                .executes { ctx ->
                    val tmSender = ctx.source.toTMSender()

                    val searchParameters = kotlin.run function@{
                        val curNodeInput = ctx.input.split(" ", limit = 4)[3]
                        if (curNodeInput.isBlank() || curNodeInput.trimEnd().endsWith("&&"))
                            return@function SearchConstraints(requestedPage = 1) // This prevents random check at beginning

                        // Temp variables to assign
                        var creator: Option<Creator>? = null
                        var assigned: Option<Assignment>? = null
                        var priority: Option<Ticket.Priority>? = null
                        var status: Option<Ticket.Status>? = null
                        var closedBy: Option<Creator>? = null
                        var lastClosedBy: Option<Creator>? = null
                        var world: Option<String>? = null
                        var creationTime: Option<Long>? = null
                        var keywords: Option<List<String>>? = null
                        var page = 1

                        // This enforces "key = value(s)... and ..." syntax and filters out bad stuff
                        "(\\b\\w+\\s*(?:=|!=|<|>)\\s*.*?)(?=\\s+&&\\s+|\$)".toRegex()
                            .findAll(curNodeInput)
                            .map(MatchResult::value)
                            .map { it.split(" ", limit = 3) }
                            .forEach { (keyword, symbol, value) ->
                                when (keyword) {
                                    locale.searchCreator -> creator = when (symbol) {
                                        "=" -> Option(SCSymbol.EQUALS, value.attemptNameToTicketCreator())
                                        "!=" -> Option(SCSymbol.NOT_EQUALS, value.attemptNameToTicketCreator())
                                        else -> throw buildBadTMSyntaxException(locale.brigadierSearchBadSymbol1
                                            .parseMiniMessage(
                                                "symbol" templated symbol,
                                                "keyword" templated keyword,
                                            )
                                        )
                                    }

                                    locale.searchAssigned -> assigned = when (symbol) {
                                        "=" -> Option(SCSymbol.EQUALS, value.searchToAssignment())
                                        "!=" -> Option(SCSymbol.NOT_EQUALS, value.searchToAssignment())
                                        else -> throw buildBadTMSyntaxException(
                                            locale.brigadierSearchBadSymbol1.parseMiniMessage(
                                                "symbol" templated symbol,
                                                "keyword" templated keyword,
                                            )
                                        )
                                    }

                                    locale.searchLastClosedBy -> lastClosedBy = when (symbol) {
                                        "=" -> Option(SCSymbol.EQUALS, value.attemptNameToTicketCreator())
                                        "!=" -> Option(SCSymbol.NOT_EQUALS, value.attemptNameToTicketCreator())
                                        else -> throw buildBadTMSyntaxException(
                                            locale.brigadierSearchBadSymbol1.parseMiniMessage(
                                                "symbol" templated symbol,
                                                "keyword" templated keyword,
                                            )
                                        )
                                    }

                                    locale.searchClosedBy -> closedBy = when (symbol) {
                                        "=" -> Option(SCSymbol.EQUALS, value.attemptNameToTicketCreator())
                                        "!=" -> Option(SCSymbol.NOT_EQUALS, value.attemptNameToTicketCreator())
                                        else -> throw buildBadTMSyntaxException(
                                            locale.brigadierSearchBadSymbol1.parseMiniMessage(
                                                "symbol" templated symbol,
                                                "keyword" templated keyword,
                                            )
                                        )
                                    }

                                    locale.searchWorld -> world = when (symbol) {
                                        "=" -> Option(SCSymbol.EQUALS, value)
                                        "!=" -> Option(SCSymbol.NOT_EQUALS, value)
                                        else -> throw buildBadTMSyntaxException(
                                            locale.brigadierSearchBadSymbol1.parseMiniMessage(
                                                "symbol" templated symbol,
                                                "keyword" templated keyword,
                                            )
                                        )
                                    }

                                    locale.searchStatus -> {
                                        val ticketStatus = when (value) {
                                            locale.statusOpen -> Ticket.Status.OPEN
                                            locale.statusClosed -> Ticket.Status.CLOSED
                                            else -> throw buildBadTMSyntaxException(
                                                locale.brigadierSearchBadStatus.parseMiniMessage(
                                                    "status" templated value,
                                                    "open" templated Ticket.Status.OPEN.name,
                                                    "closed" templated Ticket.Status.CLOSED.name,
                                                )
                                            )
                                        }
                                        status = when (symbol) {
                                            "=" -> Option(SCSymbol.EQUALS, ticketStatus)
                                            "!=" -> Option(SCSymbol.NOT_EQUALS, ticketStatus)
                                            else -> throw buildBadTMSyntaxException(
                                                locale.brigadierSearchBadSymbol1.parseMiniMessage(
                                                    "symbol" templated symbol,
                                                    "keyword" templated keyword,
                                                )
                                            )
                                        }
                                    }

                                    locale.searchPriority -> priority = when (symbol) {
                                        "=" -> Option(SCSymbol.EQUALS, numberOrWordToPriority(value))
                                        "!=" -> Option(SCSymbol.NOT_EQUALS, numberOrWordToPriority(value))
                                        "<" -> Option(SCSymbol.LESS_THAN, numberOrWordToPriority(value))
                                        ">" -> Option(SCSymbol.GREATER_THAN, numberOrWordToPriority(value))
                                        else -> throw buildBadTMSyntaxException(
                                            locale.brigadierSearchBadSymbol2.parseMiniMessage(
                                                "symbol" templated symbol,
                                                "keyword" templated keyword,
                                            )
                                        )
                                    }

                                    locale.searchKeywords -> {
                                        val foundSearches = value.split(" || ")
                                        keywords = when (symbol) {
                                            "=" -> Option(SCSymbol.EQUALS, foundSearches)
                                            "!=" -> Option(SCSymbol.NOT_EQUALS, foundSearches)
                                            else -> throw buildBadTMSyntaxException(
                                                locale.brigadierSearchBadSymbol1.parseMiniMessage(
                                                    "symbol" templated symbol,
                                                    "keyword" templated keyword,
                                                )
                                            )
                                        }
                                    }

                                    locale.searchTime -> {
                                        val epochTime = "\\b\\d+\\s+\\w+\\b".toRegex()
                                            .findAll(value)
                                            .map(MatchResult::value)
                                            .map { it.split(" ", limit = 2).run { get(0).toLong() to get(1) } }
                                            .fold(0L) { acc, (timeVal, timeUnit) ->
                                                acc + timeVal * timeUnitToMultiplier(
                                                    timeUnit
                                                )
                                            }
                                        creationTime = when (symbol) {
                                            "<" -> Option(SCSymbol.LESS_THAN, epochTime)
                                            ">" -> Option(SCSymbol.GREATER_THAN, epochTime)
                                            else -> throw buildBadTMSyntaxException(
                                                locale.brigadierSearchBadSymbol3.parseMiniMessage(
                                                    "symbol" templated symbol,
                                                    "keyword" templated keyword,
                                                )
                                            )
                                        }
                                    }

                                    locale.searchPage -> page = value.toIntOrNull()
                                        ?: throw buildBadTMSyntaxException(locale.brigadierBadPageNumber.parseMiniMessage())

                                    else -> throw buildBadTMSyntaxException(
                                        locale.brigadierBadSearchConstraint.parseMiniMessage(
                                            "keyword" templated keyword
                                        )
                                    )
                                }
                            }
                        SearchConstraints(creator, assigned, priority, status, closedBy, lastClosedBy, world, creationTime, keywords, page)
                    }

                    TMCoroutine.Supervised.launch {
                        if (!shouldRunCommand(tmSender)) return@launch

                        launch { runOnCommandExtensions(tmSender) }
                        launch {
                            commandTasks.search(tmSender,
                                searchParameters = searchParameters,
                                useNewFormat = true,
                                newRawArgumentString = ctx.input,
                            )
                        }
                    }
                    Command.SINGLE_SUCCESS
                }

            val whereLiteral = literal(locale.parameterNewSearchIndicator)
                .then(paramsArgs)
            val searchLiteral = literal(locale.commandWordSearch)
                .requires { hasPermissions(it, "ticketmanager.command.search") }
                .then(whereLiteral)
            dispatcher.register(literal(locale.commandBase).then(searchLiteral))
        }
    }

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
    private fun String.attemptNameToTicketCreator(): Creator {
        return if (this == locale.consoleName) Creator.Console
        else platform.offlinePlayerNameToUUIDOrNull(this)
            ?.run(Creator::User)
            ?: Creator.UUIDNoMatch
    }

    private fun String.searchToAssignment(): Assignment {
        val (type, value) = this.split(" ", limit = 2)
            .let { it[0] to it.getOrNull(1) }

        return when (type) {
            locale.miscNobody -> Assignment.Nobody
            locale.consoleName -> Assignment.Console
            locale.parameterLiteralPlayer -> Assignment.Player(value!!)
            locale.parameterLiteralGroup -> Assignment.PermissionGroup(value!!)
            locale.parameterLiteralPhrase -> Assignment.Phrase(value!!)
            else -> throw CommandSyntaxException(InvalidAssignmentTypeException) {
                locale.brigadierInvalidAssignment
                    .parseMiniMessage("assignment" templated (value ?: "???"))
                    .let { it as? TextComponent }
                    ?.content()
            }
        }
    }

    private fun timeUnitToMultiplier(timeUnit: String) = when (timeUnit) {
        locale.timeSeconds.trimStart() -> 1L
        locale.timeMinutes.trimStart() -> 60L
        locale.timeHours.trimStart() -> 3600L
        locale.timeDays.trimStart() -> 86400L
        locale.timeWeeks.trimStart() -> 604800L
        locale.timeYears.trimStart() -> 31556952L
        else -> throw CommandSyntaxException(InvalidTimeUnitException) {
            locale.brigadierInvalidTimeUnit
                .parseMiniMessage("timeunit" templated timeUnit)
                .let { it as? TextComponent }
                ?.content()
        }
    }

    private fun numberOrWordToPriority(input: String): Ticket.Priority = when (input) {
        "1", locale.priorityLowest -> Ticket.Priority.LOWEST
        "2", locale.priorityLow -> Ticket.Priority.LOW
        "3", locale.priorityNormal -> Ticket.Priority.NORMAL
        "4", locale.priorityHigh -> Ticket.Priority.HIGH
        "5", locale.priorityHighest -> Ticket.Priority.HIGHEST
        else -> throw CommandSyntaxException(InvalidPriorityAssignmentException) {
            locale.brigadierInvalidPriority
                .parseMiniMessage("priority" templated input)
                .let { it as? TextComponent }
                ?.content()
        }
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

    private fun buildBadTMSyntaxException(component: Component): CommandSyntaxException {
        return CommandSyntaxException(BadTMSyntaxException) {
            (component as? TextComponent)?.content()
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
        otherPreAsync: OtherRequests? = null,
        crossinline commandTask: suspend (tmSender: CommandSender.Active, ctx: CommandContext<ServerCommandSource>) -> Unit
    ) = executes { ctx ->
        otherPreAsync?.checks?.forEach { it(ctx) }
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

    private inline fun <T : ArgumentBuilder<ServerCommandSource, T>> ArgumentBuilder<ServerCommandSource, T>.executeTMActionWithTicket(
        crossinline commandTask: suspend (tmSender: CommandSender.Active, ticket: Ticket, ctx: CommandContext<ServerCommandSource>) -> Unit,
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
            launch { commandTask(tmSender, ticket, ctx) }
        }
        Command.SINGLE_SUCCESS
    }
}