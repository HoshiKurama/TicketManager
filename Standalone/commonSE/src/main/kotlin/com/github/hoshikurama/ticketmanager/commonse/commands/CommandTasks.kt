package com.github.hoshikurama.ticketmanager.commonse.commands

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.events.*
import com.github.hoshikurama.ticketmanager.api.impl.TicketManager
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.database.utils.DBResult
import com.github.hoshikurama.ticketmanager.api.registry.database.utils.Option
import com.github.hoshikurama.ticketmanager.api.registry.database.utils.SearchConstraints
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.ticket.*
import com.github.hoshikurama.ticketmanager.common.mainPluginVersion
import com.github.hoshikurama.ticketmanager.commonse.TMPlugin
import com.github.hoshikurama.ticketmanager.commonse.misc.*
import com.github.hoshikurama.ticketmanager.commonse.misc.kyoriComponentDSL.buildComponent
import com.github.hoshikurama.ticketmanager.commonse.misc.kyoriComponentDSL.onHover
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.NotificationSharingMailbox
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.TeleportJoinMailbox
import com.github.hoshikurama.tmcoroutine.ChanneledCounter
import com.github.hoshikurama.tmcoroutine.TMCoroutine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.event.HoverEvent.showText
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration

/**
 * CommandTasks includes the command logic. This does NOT check for permissions or validity of commands. Thus, commands
 * must be parsed and permissions must be checked somewhere else. Use composition.
 */
class CommandTasks(
    private val config: Config,
    private val locale: Locale,
    private val database: AsyncDatabase,
    private val platform: PlatformFunctions,
    private val permission: Permission,
    private val ticketCounter: ChanneledCounter,
    private val notificationSharingMailbox: NotificationSharingMailbox,
    private val teleportJoinMailbox: TeleportJoinMailbox,
) {
    private val tmEventBus = TicketManager.EventBus

    // /ticket assign <ID> <Assignment>
    suspend fun assign(
        sender: CommandSender.Active,
        assignment: Assignment,
        ticket: Ticket,
        silent: Boolean,
    ): MessageNotification<CommandSender.Active> {
        return assignVariationWriter(sender, assignment, ticket.creator, ticket.id, silent)
    }

    // /ticket claim <ID>
    suspend fun claim(
        sender: CommandSender.Active,
        ticket: Ticket,
        silent: Boolean,
    ): MessageNotification<CommandSender.Active> {
        return assignVariationWriter(sender, sender.asAssignment(), ticket.creator, ticket.id, silent)
    }

    // /ticket close <ID> [Comment...]
    suspend fun closeWithComment(
        sender: CommandSender.Active,
        ticket: Ticket,
        comment: String,
        silent: Boolean,
    ): MessageNotification<CommandSender.Active> {
        val action = ActionInfo(sender.asCreator(), sender.getLocAsTicketLoc()).CloseWithComment(comment)

        tmEventBus.internal.callAsync(TicketCloseWithCommentEvent(sender, silent, ticket.creator, ticket.id, action))

        // Run all database calls and event
        TMCoroutine.Supervised.launch {
            launch { database.insertActionAsync(ticket.id, action) }
            launch { database.setStatusAsync(ticket.id, Ticket.Status.CLOSED) }
            launch {
                val newCreatorStatusUpdate = (ticket.creator != sender.asCreator()) && config.allowUnreadTicketUpdates
                if (newCreatorStatusUpdate != ticket.creatorStatusUpdate)
                    database.setCreatorStatusUpdateAsync(ticket.id, newCreatorStatusUpdate)
            }
        }

        return MessageNotification.CloseWithComment.newActive(
            isSilent = silent,
            commandSender = sender,
            ticketCreator = ticket.creator,
            closingMessage = action.comment,
            ticketID = ticket.id,
            permissions = permission,
        )
    }


    // /ticket close <ID>
    suspend fun closeWithoutComment(
        sender: CommandSender.Active,
        ticket: Ticket,
        silent: Boolean,
    ): MessageNotification<CommandSender.Active> {
        val action = ActionInfo(sender.asCreator(), sender.getLocAsTicketLoc()).CloseWithoutComment()

        tmEventBus.internal.callAsync(TicketCloseWithoutCommentEvent(sender, silent, ticket.creator, ticket.id, action))

        // Run all database calls and event
        TMCoroutine.Supervised.launch {
            launch { database.insertActionAsync(ticket.id, action) }
            launch { database.setStatusAsync(ticket.id, Ticket.Status.CLOSED) }
            launch {
                val newCreatorStatusUpdate = (ticket.creator != sender.asCreator()) && config.allowUnreadTicketUpdates
                if (newCreatorStatusUpdate != ticket.creatorStatusUpdate)
                    database.setCreatorStatusUpdateAsync(ticket.id, newCreatorStatusUpdate)
            }
        }

        return MessageNotification.CloseWithoutComment.newActive(
            isSilent = silent,
            commandSender = sender,
            ticketCreator = ticket.creator,
            ticketID = ticket.id,
            permissions = permission,
        )

    }

    // /ticket closeall <Lower ID> <Upper ID>
    suspend fun closeAll(
        sender: CommandSender.Active,
        lowerBound: Long,
        upperBound: Long,
        silent: Boolean,
    ): MessageNotification<CommandSender.Active> {
        val action = ActionInfo(sender.asCreator(), sender.getLocAsTicketLoc()).MassClose()

        tmEventBus.internal.callAsync(TicketMassCloseEvent(sender, action, silent, lowerBound, upperBound))

        TMCoroutine.Supervised.launch {
            database.massCloseTicketsAsync(lowerBound, upperBound, sender.asCreator(), sender.getLocAsTicketLoc())
        }

        return MessageNotification.MassClose.newActive(
            isSilent = silent,
            commandSender = sender,
            lowerBound = lowerBound,
            upperBound = upperBound,
            permissions = permission,
        )
    }

    // /ticket comment <ID> <Comment…>
    suspend fun comment(
        sender: CommandSender.Active,
        ticket: Ticket,
        silent: Boolean,
        comment: String,
    ): MessageNotification<CommandSender.Active> {
        val action = ActionInfo(sender.asCreator(), sender.getLocAsTicketLoc()).Comment(comment)

        tmEventBus.internal.callAsync(TicketCommentEvent(sender, silent, ticket.creator, ticket.id, action))

        // Database Stuff + Event
        TMCoroutine.Supervised.launch {
            launch { database.insertActionAsync(ticket.id, action) }
            launch {
                val newCreatorStatusUpdate = (ticket.creator != sender.asCreator()) && config.allowUnreadTicketUpdates
                if (newCreatorStatusUpdate != ticket.creatorStatusUpdate)
                    database.setCreatorStatusUpdateAsync(ticket.id, newCreatorStatusUpdate)
            }
        }

        return MessageNotification.Comment.newActive(
            isSilent = silent,
            commandSender = sender,
            ticketCreator = ticket.creator,
            ticketID = ticket.id,
            comment = comment,
            permissions = permission,
        )

    }

    // /ticket create <Message…>
    suspend fun create(
        sender: CommandSender.Active,
        message: String,
    ): MessageNotification<CommandSender.Active> {

        val initTicket = Ticket(
            id = -1L,
            creator = sender.asCreator(),
            actions = listOf(ActionInfo(sender.asCreator(), sender.getLocAsTicketLoc()).Open(message)),
            priority = Ticket.Priority.NORMAL,
            status = Ticket.Status.OPEN,
            assignedTo = Assignment.Nobody,
            creatorStatusUpdate = false,
        )

        // Inserts ticket and receives ID
        val id = database.insertNewTicketAsync(initTicket)
        ticketCounter.increment()

        tmEventBus.internal.callAsync(TicketCreateEvent(sender, initTicket.creator, id, initTicket.actions[0] as ActionInfo.Open))

        return MessageNotification.Create.newActive(
            isSilent = false,
            commandSender = sender,
            ticketCreator = initTicket.creator,
            ticketID = id,
            message = message,
            permissions = permission,
        )
    }

    // /ticket list [Page]
    suspend fun list(
        sender: CommandSender.Active,
        requestedPage: Int
    ) {
        val tickets = database.getOpenTicketsAsync(requestedPage, 8)
        createGeneralList(locale.listHeader, tickets, locale.run { "/$commandBase $commandWordList " })
            .run(sender::sendMessage)
    }

    // /ticket listassigned [Page]
    suspend fun listAssigned(
        sender: CommandSender.Active,
        requestedPage: Int,
    ) {
        val groups = if (sender is CommandSender.OnlinePlayer)
            permission.groupNamesOf(sender)
                .map(Assignment::PermissionGroup)
        else listOf()

        val tickets = database.getOpenTicketsAssignedToAsync(requestedPage,8, listOf(sender.asAssignment()) + groups)

        createGeneralList(locale.listAssignedHeader, tickets, locale.run { "/$commandBase $commandWordListAssigned " })
            .run(sender::sendMessage)
    }

    // /ticket listunassigned [Page]
    suspend fun listUnassigned(
        sender: CommandSender.Active,
        requestedPage: Int,
    ) {
        val tickets = database
            .getOpenTicketsNotAssignedAsync(requestedPage, 8)

        createGeneralList(locale.listUnassignedHeader, tickets, locale.run { "/$commandBase $commandWordListUnassigned " })
            .run(sender::sendMessage)
    }

    // /ticket help
    fun help(sender: CommandSender.Active) {

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
        val commandNodes = locale.run {
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
            .filter { c -> c.permissions.any { permission.has(sender, it, consolePermission = true) } }

        val hasSilentPerm = permission.has(sender, "ticketmanager.commandArg.silence", consolePermission = true)
        val component = buildComponent {
            locale.run {
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
                        "command" templated "$commandBase ${it.command}",
                        "params" templated argsComponent,
                    ).append(it.explanation.parseMiniMessage())
                }
                    .reduce(Component::append)
                    .let(this@buildComponent::append)
            }
        }

        sender.sendMessage(component)
    }

    suspend fun reopen(
        sender: CommandSender.Active,
        silent: Boolean,
        ticket: Ticket,
    ): MessageNotification<CommandSender.Active> {
        val action = ActionInfo(sender.asCreator(), sender.getLocAsTicketLoc()).Reopen()

        tmEventBus.internal.callAsync(TicketReopenEvent(sender, ticket.creator, silent, ticket.id, action))

        // Write to database
        TMCoroutine.Supervised.launch {
            launch { database.insertActionAsync(ticket.id, action) }
            launch { database.setStatusAsync(ticket.id, Ticket.Status.OPEN) }
            launch {
                val newCreatorStatusUpdate = (ticket.creator != sender.asCreator()) && config.allowUnreadTicketUpdates
                if (newCreatorStatusUpdate != ticket.creatorStatusUpdate)
                    database.setCreatorStatusUpdateAsync(ticket.id, newCreatorStatusUpdate)
            }
        }

        return MessageNotification.Reopen.newActive(silent, sender, ticket.creator, ticket.id, permission)
    }

    // /ticket history [User] [Page]
    suspend fun history(
        sender: CommandSender.Active,
        checkedCreator: Creator,
        requestedPage: Int,
    ) {
        val (results, pageCount, resultCount, returnedPage) = database
            .searchDatabaseAsync(SearchConstraints(
                creator = Option(SearchConstraints.Symbol.EQUALS, checkedCreator),
                requestedPage = requestedPage,
            ), 9)
        val targetName = checkedCreator.attemptName()

        // Component Builder...
        val sentComponent = buildComponent {
            // Header
            locale.historyHeader.parseMiniMessage(
                "name" templated targetName,
                "count" templated "$resultCount"
            ).let(this::append)

            if (results.isNotEmpty()) {
                results.forEach { t ->
                    val id = "${t.id}"
                    val status = t.status.toLocaledWord(locale)
                    val comment = trimCommentToSize(
                        comment = (t.actions[0] as ActionInfo.Open).message,
                        preSize = id.length + status.length + locale.historyFormattingSize,
                        maxSize = locale.historyMaxLineSize
                    )

                    val entry = locale.historyEntry
                        .replace("%SCC%", t.status.getHexColour(locale))
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
                    val command =
                        if (sender.asCreator() == checkedCreator)
                            locale.run { "/$commandBase $commandWordHistory " }
                        else locale.run { "/$commandBase $commandWordHistory $targetName " }

                    append(buildPageComponent(returnedPage, pageCount, command))
                }
            }
        }

        sender.sendMessage(sentComponent)
    }

    // /ticket search <Params>
    suspend fun search(
        sender: CommandSender.Active,
        searchParameters: SearchConstraints,
        useNewFormat: Boolean,
        newRawArgumentString: String? = null,    // Always not-null when used
        oldRawArgs: List<String>? = null,   // Always not-null when used
    ) {
        // Beginning of execution
        sender.sendMessage(locale.searchQuerying.parseMiniMessage())

        // Results calculation and destructuring
        val (results, pageCount, resultCount, returnedPage) =
            database.searchDatabaseAsync(searchParameters, 9)

        // Component Builder...
        val sentComponent = buildComponent {
            // Initial header
            append(locale.searchHeader.parseMiniMessage("size" templated "$resultCount"))

            // Adds entries
            if (results.isNotEmpty()) {
                results.forEach {
                    val time = it.actions[0].timestamp.toLargestRelativeTime(locale)
                    val comment = trimCommentToSize(
                        comment = (it.actions[0] as ActionInfo.Open).message,
                        preSize = locale.searchFormattingSize + time.length,
                        maxSize = locale.searchMaxLineSize,
                    )

                    locale.searchEntry
                        .replace("%PCC%", it.priority.getHexColour(locale))
                        .replace("%SCC%", it.status.getHexColour(locale))
                        .parseMiniMessage(
                            "id" templated "${it.id}",
                            "status" templated it.status.toLocaledWord(locale),
                            "creator" templated it.creator.attemptName(),
                            "assignment" templated it.assignedTo.toLocalizedName(locale),
                            "world" templated ((it.actions[0].location as? ActionLocation.FromPlayer)?.world ?: ""),
                            "time" templated time,
                            "comment" templated comment,
                        )
                        .hoverEvent(showText(Component.text(locale.clickViewTicket)))
                        .clickEvent(ClickEvent.runCommand(locale.run { "/$commandBase $commandWordView ${it.id}" }))
                        .let(this::append)
                }

                // Implement pages if needed
                if (pageCount > 1) {
                    buildPageComponent(returnedPage, pageCount, kotlin.run {

                        if (useNewFormat) {
                            val argsFilterPage = newRawArgumentString!!.replace("&& ${locale.searchPage} = \\d+".toRegex(), "")
                            "$argsFilterPage && page = "
                        } else {
                            val constraintCmdArgs = oldRawArgs!!
                                .map { it.split(":", limit = 2) }
                                .filter { it[0] != locale.searchPage }
                                .joinToString(" ") { (k, v) -> "$k:$v" }

                            "/${locale.commandBase} ${locale.commandWordSearch} $constraintCmdArgs ${locale.searchPage}:"

                        }
                    }).let(this::append)
                }
            }
        }
        sender.sendMessage(sentComponent)
    }

    // /ticket setpriority <ID> <Level>
    suspend fun setPriority(
        sender: CommandSender.Active,
        priority: Ticket.Priority,
        ticket: Ticket,
        silent: Boolean,
    ): MessageNotification<CommandSender.Active> {
        val action = ActionInfo(sender.asCreator(), sender.getLocAsTicketLoc()).SetPriority(priority)

        tmEventBus.internal.callAsync(TicketSetPriorityEvent(sender, silent, ticket.creator, ticket.id, action))

        // Database calls
        TMCoroutine.Supervised.launch {
            launch { database.insertActionAsync(ticket.id, action) }
            launch { database.setPriorityAsync(ticket.id, priority) }
        }

        return MessageNotification.SetPriority.newActive(silent, sender, ticket.creator, ticket.id, priority, permission)
    }

    // /ticket teleport <ID>
    fun teleport(sender: CommandSender.Active, ticket: Ticket) {
        val location = ticket.actions[0].location

        // Unable to teleport. Do nothing
        if (sender !is CommandSender.OnlinePlayer || location !is ActionLocation.FromPlayer) return

        if (config.proxyOptions == null && location.server == null)
            platform.teleportToTicketLocSameServer(sender, location)

        else if (config.proxyOptions != null && config.proxyOptions!!.serverName != null && location.server != null) {
            if (config.proxyOptions!!.serverName == location.server) {
                platform.teleportToTicketLocSameServer(sender, location)
            } else {
                // teleportToTicketLocDiffServer() is useless now since proxy stuff handles this.
                teleportJoinMailbox.forward2Hub(sender.uuid to location)
            }
        }
    }

    // /ticket unassign <ID>
    suspend fun unAssign(
        sender: CommandSender.Active,
        ticket: Ticket,
        silent: Boolean,
    ) : MessageNotification<CommandSender.Active> {
        return assignVariationWriter(sender, Assignment.Nobody, ticket.creator, ticket.id, silent)
    }

    // /ticket version
    fun version(
        sender: CommandSender.Active,
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
                clickEvent(ClickEvent.openUrl(locale.wikiLink))
                onHover { showText(Component.text(locale.clickWiki)) }
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
    suspend fun view(
        sender: CommandSender.Active,
        ticket: Ticket,
    ) {
        val baseComponent = buildTicketInfoComponent(ticket)

        val newCreatorStatusUpdate = (ticket.creator != sender.asCreator()) && config.allowUnreadTicketUpdates
        if (newCreatorStatusUpdate != ticket.creatorStatusUpdate) {
            TMCoroutine.Supervised.launch { database.setCreatorStatusUpdateAsync(ticket.id, false) }
            val readEvent = TicketReadReceiptEvent(sender, ticket.id)
            tmEventBus.internal.callAsync(readEvent)
        }

        val entries = ticket.actions.asSequence()
            .filter { it is ActionInfo.Comment || it is ActionInfo.Open || it is ActionInfo.CloseWithComment }
            .map {
                locale.viewComment.parseMiniMessage(
                    "user" templated it.user.attemptName(),
                    "comment" templated when (it) {
                        is ActionInfo.Comment -> it.comment
                        is ActionInfo.Open -> it.message
                        is ActionInfo.CloseWithComment -> it.comment
                        else -> throw Exception("Impossible to reach")
                    }
                )
            }
            .reduce(Component::append)

        sender.sendMessage(baseComponent.append(entries))
    }

    // /ticket viewdeep <ID>
    suspend fun viewDeep(
        sender: CommandSender.Active,
        ticket: Ticket,
    ) {
        val baseComponent = buildTicketInfoComponent(ticket)

        val newCreatorStatusUpdate = (ticket.creator != sender.asCreator()) && config.allowUnreadTicketUpdates
        if (newCreatorStatusUpdate != ticket.creatorStatusUpdate) {
            TMCoroutine.Supervised.launch { database.setCreatorStatusUpdateAsync(ticket.id, false) }
            val readEvent = TicketReadReceiptEvent(sender, ticket.id)
            tmEventBus.internal.callAsync(readEvent)
        }

        fun formatDeepAction(action: Action): List<Component> {
            val templatedUser = "user" templated action.user.attemptName()
            val templatedTime = "time" templated action.timestamp.toLargestRelativeTime(locale)

            return when(action) {
                is ActionInfo.Open -> listOf(locale.viewDeepComment.parseMiniMessage(templatedUser, templatedTime, "comment" templated action.message))
                is ActionInfo.Assign -> listOf(locale.viewDeepAssigned.parseMiniMessage(templatedUser, templatedTime, "assignment" templated action.assignment.toLocalizedName(locale)))
                is ActionInfo.CloseWithComment -> listOf(
                    locale.viewDeepComment.parseMiniMessage(templatedUser, templatedTime, "comment" templated action.comment),
                    locale.viewDeepClose.parseMiniMessage(templatedUser, templatedTime))
                is ActionInfo.CloseWithoutComment -> listOf(locale.viewDeepClose.parseMiniMessage(templatedUser, templatedTime))
                is ActionInfo.Comment -> listOf(locale.viewDeepComment.parseMiniMessage(templatedUser, templatedTime, "comment" templated action.comment))
                is ActionInfo.MassClose -> listOf(locale.viewDeepMassClose.parseMiniMessage(templatedUser, templatedTime))
                is ActionInfo.Reopen -> listOf(locale.viewDeepReopen.parseMiniMessage(templatedUser, templatedTime))
                is ActionInfo.SetPriority -> listOf(
                    locale.viewDeepSetPriority.replace("%PCC%", action.priority.getHexColour(locale))
                        .parseMiniMessage(templatedUser,templatedTime, "priority" templated action.priority.toLocaledWord(locale))
                )
            }
        }

        ticket.actions.asSequence()
            .map(::formatDeepAction)
            .map { it.reduce(Component::append) }
            .reduce(Component::append)
            .run(baseComponent::append)
            .run(sender::sendMessage)
    }

    // Global coroutine prevents this very job from ending
    suspend fun reload(sender: CommandSender.Active): Job = TMCoroutine.Global.launch {

        // Announce Intentions
        platform.massNotify(
            permission = "ticketmanager.notify.info",
            message = locale.informationReloadInitiated.parseMiniMessage("user" templated sender.getUsername(locale))
        )

        TMPlugin.activeInstance.disableTicketManager(false)
        TMPlugin.activeInstance.enableTicketManager()

        platform.massNotify("ticketmanager.notify.info", locale.informationReloadSuccess.parseMiniMessage())

        if (!permission.has(sender, "ticketmanager.notify.info", consolePermission = true))
            sender.sendMessage(locale.informationReloadSuccess.parseMiniMessage())
    }

    private suspend fun assignVariationWriter(
        sender: CommandSender.Active,
        assignment: Assignment,
        creator: Creator,
        ticketID: Long,
        silent: Boolean,
    ): MessageNotification<CommandSender.Active> {
        val insertedAction = ActionInfo(sender.asCreator(), sender.getLocAsTicketLoc()).Assign(assignment)

        tmEventBus.internal.callAsync(TicketAssignEvent(sender, silent, creator, ticketID, insertedAction))

        // Writes to database
        TMCoroutine.Supervised.launch {
            launch { database.setAssignmentAsync(ticketID, assignment) }
            launch { database.insertActionAsync(ticketID, insertedAction) }
        }

        return MessageNotification.Assign.newActive(
            isSilent = silent,
            assignment = assignment,
            commandSender = sender,
            ticketCreator = creator,
            ticketID = ticketID,
            permissions = permission,
        )
    }

    fun executeNotifications(
        commandSender: CommandSender.Active,
        message: MessageNotification<CommandSender.Active>
    ) {
        message.run {
            //  Send to other servers if needed
            if (config.proxyOptions != null)
                notificationSharingMailbox.forward2Hub(message)

            if (sendSenderMSG) generateSenderMSG(locale).run(commandSender::sendMessage)

            if (sendCreatorMSG && ticketCreator is Creator.User) {
                val creatorPlayer = platform.buildPlayer((ticketCreator as Creator.User).uuid)

                if (creatorPlayer != null
                    && permission.has(creatorPlayer, creatorAlertPerm)
                    && !permission.has(creatorPlayer, massNotifyPerm)
                ) {
                    generateCreatorMSG(locale).run(creatorPlayer::sendMessage)
                }
            }

            if (sendMassNotify) platform.massNotify(massNotifyPerm, generateMassNotify(locale))
        }
    }

// Private Functions

    private fun buildTicketInfoComponent(ticket: Ticket) = buildComponent {
        append(locale.viewHeader.parseMiniMessage("id" templated "${ticket.id}"))
        append(locale.viewSep1.parseMiniMessage())
        append(locale.viewCreator.parseMiniMessage("creator" templated ticket.creator.attemptName()))
        append(locale.viewAssignedTo.parseMiniMessage("assignment" templated
                ticket.assignedTo.let { if (it is Assignment.Nobody) "" else it.toLocalizedName(locale) })
        )
        locale.viewPriority.replace("%PCC%", ticket.priority.getHexColour(locale))
            .parseMiniMessage("priority" templated ticket.priority.toLocaledWord(locale))
            .let(this::append)
        locale.viewStatus.replace("%SCC%", ticket.status.getHexColour(locale))
            .parseMiniMessage("status" templated ticket.status.toLocaledWord(locale))
            .let(this::append)

        val locationString = ticket.actions[0].location.let {
            if (config.proxyOptions == null)
                it.stringFormat()
                    .split(" ")
                    .drop(1)
                    .joinToString(" ")
            else it.stringFormat()
        }
        locale.viewLocation.parseMiniMessage("location" templated locationString)
            .let {
                if (ticket.actions[0].location is ActionLocation.FromPlayer) // If it is a player ticket
                    it.hoverEvent(showText(Component.text(locale.clickTeleport)))
                        .clickEvent(ClickEvent.runCommand(locale.run { "/$commandBase $commandWordTeleport ${ticket.id}" }))
                else it
            }
            .let(this::append)

        append(locale.viewSep2.parseMiniMessage())
    }

    private fun createGeneralList(
        headerFormat: String,
        results: DBResult,
        baseCommand: String,
    ): Component {
        val (tickets, totalPages, _, returnedPage) = results

        return buildComponent {
            append(headerFormat.parseMiniMessage())

            if (tickets.isNotEmpty()) {
                tickets.forEach { append(createListEntry(it)) }

                if (totalPages > 1)
                    append(buildPageComponent(returnedPage, totalPages, baseCommand))

            }
        }
    }

    private fun buildPageComponent(
        curPage: Int,
        pageCount: Int,
        baseCommand: String,
    ): Component {

        fun Component.addForward(): Component {
            return clickEvent(ClickEvent.runCommand(baseCommand + "${curPage + 1}"))
                .hoverEvent(HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, Component.text(locale.clickNextPage)))
        }

        fun Component.addBack(): Component {
            return clickEvent(ClickEvent.runCommand(baseCommand + "${curPage - 1}"))
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
    ): Component {
        val id = "${ticket.id}"
        val creatorName = ticket.creator.attemptName()
        val fixedAssign = ticket.assignedTo.let { if (it is Assignment.Nobody) "" else it.toLocalizedName(locale) }
        val pcc = ticket.priority.getHexColour(locale)

        val fixedComment = trimCommentToSize(
            comment = (ticket.actions[0] as ActionInfo.Open).message,
            preSize = locale.listFormattingSize + id.length + creatorName.length + fixedAssign.length,
            maxSize = 58,
        )

        return locale.listEntry.replace("%PCC%", pcc)
            .parseMiniMessage(
                "id" templated id,
                "creator" templated creatorName,
                "assignment" templated fixedAssign,
                "comment" templated fixedComment,
            )
            .hoverEvent(showText(Component.text(locale.clickViewTicket)))
            .clickEvent(ClickEvent.runCommand(locale.run { "/$commandBase $commandWordView ${ticket.id}" }))
    }

    private fun Creator.attemptName() = when (this) {
        is Creator.User -> platform.nameFromUUIDOrNull(uuid) ?: "???"
        is Creator.Console -> locale.consoleName
        else -> "???"
    }
}

private fun trimCommentToSize(comment: String, preSize: Int, maxSize: Int): String {
    return if (comment.length + preSize > maxSize) "${comment.substring(0,maxSize-preSize-3)}..."
    else comment
}

private fun ActionLocation.stringFormat() = when (this) {
    is ActionLocation.FromPlayer -> "${server ?: ""} $world $x $y $z".trimStart()
    is ActionLocation.FromConsole -> "${server ?: ""}    ".trimStart()
}