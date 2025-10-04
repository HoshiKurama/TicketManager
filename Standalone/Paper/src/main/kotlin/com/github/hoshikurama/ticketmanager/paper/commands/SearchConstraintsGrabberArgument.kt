package com.github.hoshikurama.ticketmanager.paper.commands

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.utils.Option
import com.github.hoshikurama.ticketmanager.api.registry.database.utils.SearchConstraints
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.ticket.Assignment
import com.github.hoshikurama.ticketmanager.api.ticket.Creator
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.misc.templated
import com.github.hoshikurama.ticketmanager.paper.impls.PaperConsole
import com.github.hoshikurama.ticketmanager.paper.impls.PaperPlayer
import com.github.hoshikurama.tmcoroutine.TMCoroutine
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import kotlinx.coroutines.future.asCompletableFuture
import net.kyori.adventure.text.Component
import java.util.concurrent.CompletableFuture
import com.github.hoshikurama.ticketmanager.api.registry.database.utils.SearchConstraints.Symbol as SCSymbol
import org.bukkit.command.ConsoleCommandSender as BukkitConsole

typealias BukkitPlayer = org.bukkit.entity.Player
typealias BukkitCommandSender = org.bukkit.command.CommandSender


sealed interface SearchConstraintsResult {
    data class Success(val searchConstraints: SearchConstraints): SearchConstraintsResult
    data class Fail(val message: Component): SearchConstraintsResult
}


class SearchConstraintsGrabberArgument : CustomArgumentType<SearchConstraintsResult, String> {
    private val locale: Locale
        get() = CommandReferences.locale
    private val config: Config
        get() = CommandReferences.config
    private val platform: PlatformFunctions
        get() = CommandReferences.platform
    private val permissions: Permission
        get() = CommandReferences.permissions

    companion object {
        fun get(ctx: CommandContext<CommandSourceStack>, name: String): SearchConstraintsResult {
            return ctx.getArgument(name, SearchConstraintsResult::class.java)
        }
    }

    override fun parse(reader: StringReader): SearchConstraintsResult {
        val currentInput = getGreedyString(reader)

        if (currentInput.isBlank() || currentInput.trimEnd().endsWith("&&"))
            return SearchConstraints(requestedPage = 1).run(SearchConstraintsResult::Success) // This prevents random check at beginning

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
            .findAll(currentInput)
            .map(MatchResult::value)
            .map { it.split(" ", limit = 3) }
            .forEach { (keyword, symbol, value) ->
                when (keyword) {
                    locale.searchCreator -> creator = when (symbol) {
                        "=" -> Option(SCSymbol.EQUALS, value.attemptNameToTicketCreator())
                        "!=" -> Option(SCSymbol.NOT_EQUALS, value.attemptNameToTicketCreator())
                        else -> return locale.brigadierSearchBadSymbol1.parseMiniMessage(
                            "symbol" templated symbol,
                            "keyword" templated keyword,
                        ).run(SearchConstraintsResult::Fail)
                    }

                    locale.searchAssigned -> assigned = when (symbol) {
                        "=",
                        "!=" -> {
                            val (type, value) = value.split(" ", limit = 2)
                                .let { it[0] to it.getOrNull(1) }

                            val assignment = when (type) {
                                locale.miscNobody -> Assignment.Nobody
                                locale.consoleName -> Assignment.Console
                                locale.parameterLiteralPlayer -> Assignment.Player(value!!)
                                locale.parameterLiteralGroup -> Assignment.PermissionGroup(value!!)
                                locale.parameterLiteralPhrase -> Assignment.Phrase(value!!)
                                else -> return locale.brigadierInvalidAssignment.parseMiniMessage(
                                    "assignment" templated (value ?: "???")
                                ).run(SearchConstraintsResult::Fail)
                            }

                            when (symbol) {
                                "=" -> Option(SCSymbol.EQUALS, assignment)
                                "!=" -> Option(SCSymbol.NOT_EQUALS, assignment)
                                else -> throw Exception("Impossible")
                            }
                        }
                        else -> return locale.brigadierSearchBadSymbol1.parseMiniMessage(
                            "symbol" templated symbol,
                            "keyword" templated keyword,
                        ).run(SearchConstraintsResult::Fail)
                    }
                    locale.searchLastClosedBy -> lastClosedBy = when (symbol) {
                        "=" -> Option(SCSymbol.EQUALS, value.attemptNameToTicketCreator())
                        "!=" -> Option(SCSymbol.NOT_EQUALS, value.attemptNameToTicketCreator())
                        else -> return locale.brigadierSearchBadSymbol1.parseMiniMessage(
                            "symbol" templated symbol,
                            "keyword" templated keyword,
                        ).run(SearchConstraintsResult::Fail)
                    }
                    locale.searchClosedBy -> closedBy = when (symbol) {
                        "=" -> Option(SCSymbol.EQUALS, value.attemptNameToTicketCreator())
                        "!=" -> Option(SCSymbol.NOT_EQUALS, value.attemptNameToTicketCreator())
                        else -> return locale.brigadierSearchBadSymbol1.parseMiniMessage(
                            "symbol" templated symbol,
                            "keyword" templated keyword,
                        ).run(SearchConstraintsResult::Fail)
                    }
                    locale.searchWorld -> world = when (symbol) {
                        "=" -> Option(SCSymbol.EQUALS, value)
                        "!=" -> Option(SCSymbol.NOT_EQUALS, value)
                        else -> return locale.brigadierSearchBadSymbol1.parseMiniMessage(
                            "symbol" templated symbol,
                            "keyword" templated keyword,
                        ).run(SearchConstraintsResult::Fail)
                    }
                    locale.searchStatus -> {
                        val ticketStatus = when (value) {
                            locale.statusOpen -> Ticket.Status.OPEN
                            locale.statusClosed -> Ticket.Status.CLOSED
                            else -> return locale.brigadierSearchBadStatus.parseMiniMessage(
                                "status" templated value,
                                "open" templated Ticket.Status.OPEN.name,
                                "closed" templated Ticket.Status.CLOSED.name,
                            ).run(SearchConstraintsResult::Fail)
                        }
                        status = when (symbol) {
                            "=" -> Option(SCSymbol.EQUALS, ticketStatus)
                            "!=" -> Option(SCSymbol.NOT_EQUALS, ticketStatus)
                            else -> return locale.brigadierSearchBadSymbol1.parseMiniMessage(
                                "symbol" templated symbol,
                                "keyword" templated keyword,
                            ).run(SearchConstraintsResult::Fail)
                        }
                    }
                    locale.searchPriority -> priority = when (symbol) {
                        "=", "!=", "<", ">" -> {
                            val priority = when (value) {
                                "1", locale.priorityLowest -> Ticket.Priority.LOWEST
                                "2", locale.priorityLow -> Ticket.Priority.LOW
                                "3", locale.priorityNormal -> Ticket.Priority.NORMAL
                                "4", locale.priorityHigh -> Ticket.Priority.HIGH
                                "5", locale.priorityHighest -> Ticket.Priority.HIGHEST
                                else -> return locale.brigadierInvalidPriority.parseMiniMessage(
                                    "priority" templated value
                                ).run(SearchConstraintsResult::Fail)
                            }
                            when (symbol) {
                                "=" -> Option(SCSymbol.EQUALS, priority)
                                "!=" -> Option(SCSymbol.NOT_EQUALS, priority)
                                "<" -> Option(SCSymbol.LESS_THAN, priority)
                                ">" -> Option(SCSymbol.GREATER_THAN, priority)
                                else -> throw Exception("Impossible")
                            }
                        }
                        else -> return locale.brigadierSearchBadSymbol2.parseMiniMessage(
                            "symbol" templated symbol,
                            "keyword" templated keyword
                        ).run(SearchConstraintsResult::Fail)
                    }

                    locale.searchKeywords -> {
                        val foundSearches = value.split(" || ")
                        keywords = when (symbol) {
                            "=" -> Option(SCSymbol.EQUALS, foundSearches)
                            "!=" -> Option(SCSymbol.NOT_EQUALS, foundSearches)
                            else -> return locale.brigadierSearchBadSymbol1.parseMiniMessage(
                                "symbol" templated symbol,
                                "keyword" templated keyword,
                            ).run(SearchConstraintsResult::Fail)
                        }
                    }
                    locale.searchTime -> {
                        val epochTime = "\\b\\d+\\s+\\w+\\b".toRegex()
                            .findAll(value)
                            .map(MatchResult::value)
                            .map { it.split(" ", limit = 2).run { get(0).toLong() to get(1) } }
                            .fold(0L) {acc, (timeVal, timeUnit) ->
                                val timeUnitMultiplier = when (timeUnit) {
                                    locale.timeSeconds.trimStart() -> 1L
                                    locale.timeMinutes.trimStart() -> 60L
                                    locale.timeHours.trimStart() -> 3600L
                                    locale.timeDays.trimStart() -> 86400L
                                    locale.timeWeeks.trimStart() -> 604800L
                                    locale.timeYears.trimStart() -> 31556952L
                                    else -> return locale.brigadierInvalidTimeUnit.parseMiniMessage(
                                        "timeunit" templated timeUnit
                                    ).run(SearchConstraintsResult::Fail)
                                }
                                acc + timeVal * timeUnitMultiplier
                            }
                        creationTime = when (symbol) {
                            "<" -> Option(SCSymbol.LESS_THAN, epochTime)
                            ">" -> Option(SCSymbol.GREATER_THAN, epochTime)
                            else -> return locale.brigadierSearchBadSymbol3.parseMiniMessage(
                                "symbol" templated symbol,
                                "keyword" templated keyword,
                            ).run(SearchConstraintsResult::Fail)
                        }
                    }
                    locale.searchPage -> page = value.toIntOrNull()
                        ?: return locale.brigadierBadPageNumber.parseMiniMessage().run(SearchConstraintsResult::Fail)
                    else -> return locale.brigadierBadSearchConstraint.parseMiniMessage(
                        "keyword" templated keyword
                    ).run(SearchConstraintsResult::Fail)
                }
            }

        return SearchConstraints(creator, assigned, priority, status, closedBy, lastClosedBy, world, creationTime, keywords, page)
            .run(SearchConstraintsResult::Success)
    }

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        return TMCoroutine.Global.async {
            @Suppress("UNCHECKED_CAST")
            val sender = (context as? CommandContext<CommandSourceStack>)
                ?.source?.sender ?: throw Exception("Invalid context!")
            val curArgsSet = builder.remaining.split(" && ").last().split(" ")


            val newBuilder = builder.createOffset(builder.start + builder.remaining.lastIndexOf(" ") + 1)
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
                            platform.getOnlineSeenPlayerNames(sender.toTMSender()) + listOf(locale.consoleName)
                        else listOf("&&")

                    locale.searchAssigned -> when (curArgsSet.size) {
                        3 -> listOf(locale.consoleName, locale.miscNobody, locale.parameterLiteralPlayer, locale.parameterLiteralGroup, locale.parameterLiteralPhrase)
                        4 -> when (curArgsSet[2]) {
                            locale.parameterLiteralPhrase -> listOf("${locale.parameterLiteralPhrase}...")
                            locale.parameterLiteralGroup -> permissions.allGroupNames()
                            locale.parameterLiteralPlayer -> platform.getOnlineSeenPlayerNames(sender.toTMSender())
                            else -> listOf("&&")
                        }
                        else -> if (curArgsSet[2] == locale.parameterLiteralPhrase) listOf("${locale.parameterLiteralPhrase}...", "&&")
                                else listOf("&&")
                    }

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
            }

            // "somethingHere "
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

            newBuilder.build()
        }.asCompletableFuture()
    }

    override fun getNativeType(): ArgumentType<String> {
        return StringArgumentType.greedyString()
    }

    private fun getGreedyString(reader: StringReader): String {
        val text = reader.remaining
        reader.cursor = reader.totalLength
        return text
    }

    private fun String.attemptNameToTicketCreator(): Creator {
        return if (this == locale.consoleName) Creator.Console
        else platform.offlinePlayerNameToUUIDOrNull(this)
            ?.run(Creator::User)
            ?: Creator.UUIDNoMatch
    }

    private fun BukkitCommandSender.toTMSender(): CommandSender.Active = when (this) {
        is BukkitConsole -> PaperConsole(config.proxyOptions?.serverName)
        is BukkitPlayer -> PaperPlayer(this, config.proxyOptions?.serverName)
        else -> when ((this::class).toString()) {
            "class io.papermc.paper.brigadier.NullCommandSender" -> PaperConsole(config.proxyOptions?.serverName) // I guess this is something Paper does...
            else -> throw Exception("Unsupported Entity Type!:\n Class Type: \"${this::class}\"")
        }
    }
}