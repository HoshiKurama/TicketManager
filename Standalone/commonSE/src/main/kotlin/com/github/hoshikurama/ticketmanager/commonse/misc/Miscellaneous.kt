package com.github.hoshikurama.ticketmanager.commonse.misc

import com.github.hoshikurama.ticketmanager.commonse.datas.ConfigState
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.misc.kyoriComponentDSL.buildComponent
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.utilities.asParallelStream
import com.github.hoshikurama.ticketmanager.commonse.utilities.tryOrNull
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.time.Instant
import java.util.concurrent.CompletableFuture

typealias TicketPredicate = (Ticket) -> Boolean

fun Long.toLargestRelativeTime(activeLocale: TMLocale): String {
    val timeAgo = Instant.now().epochSecond - this

    return when {
        timeAgo >= 31556952L -> (timeAgo / 31556952L).toString() + activeLocale.timeYears
        timeAgo >= 604800L ->(timeAgo / 604800L).toString() + activeLocale.timeWeeks
        timeAgo >= 86400L ->(timeAgo / 86400L).toString() + activeLocale.timeDays
        timeAgo >= 3600L ->(timeAgo / 3600L).toString() + activeLocale.timeHours
        timeAgo >= 60L ->(timeAgo / 60L).toString() + activeLocale.timeMinutes
        else -> timeAgo.toString() + activeLocale.timeSeconds
    }
}

fun relTimeToEpochSecond(relTime: String, activeLocale: TMLocale): Long {
    var seconds = 0L
    var index = 0
    val unprocessed = StringBuilder(relTime)

    while (unprocessed.isNotEmpty() && index != unprocessed.lastIndex + 1) {
        unprocessed[index].toString().toByteOrNull()
            // If number...
            ?.apply { index++ }
        // If not a number...
            ?: run {
                val number = if (index == 0) 0
                else unprocessed.substring(0, index).toLong()

                seconds += number * when (unprocessed[index].toString()) {
                    activeLocale.searchTimeSecond -> 1L
                    activeLocale.searchTimeMinute -> 60L
                    activeLocale.searchTimeHour -> 3600L
                    activeLocale.searchTimeDay -> 86400L
                    activeLocale.searchTimeWeek -> 604800L
                    activeLocale.searchTimeYear -> 31556952L
                    else -> 0L
                }

                unprocessed.delete(0, index+1)
                index = 0
            }
    }

    return Instant.now().epochSecond - seconds
}

fun stringToStatusOrNull(str: String) = tryOrNull { Ticket.Status.valueOf(str) }

// MiniMessage helper functions
fun String.parseMiniMessage(vararg template: TagResolver) = MiniMessage.miniMessage().deserialize(this, *template)
fun String.parseMiniMessage() = MiniMessage.miniMessage().deserialize(this)
infix fun String.templated(string: String) = Placeholder.parsed(this, string)
infix fun String.templated(component: Component) = Placeholder.component(this, component)
operator fun Component.plus(other: Component) = append(other)

// Other
fun generateModifiedStacktrace(e: Exception, activeLocale: TMLocale) = buildComponent {
    val cause = e.cause

    // Builds Header
    append(activeLocale.stacktraceLine1.parseMiniMessage())
    append(activeLocale.stacktraceLine2.parseMiniMessage("exception" templated (cause?.javaClass?.simpleName ?: "???")))
    append(activeLocale.stacktraceLine3.parseMiniMessage("message" templated (cause?.message ?: "?")))
    append(activeLocale.stacktraceLine4.parseMiniMessage())

    // Adds stacktrace entries
    cause?.stackTrace
        ?.filter { it.className.contains("com.github.hoshikurama.ticketmanager") }
        ?.map {
            activeLocale.stacktraceEntry.parseMiniMessage(
                "method" templated it.methodName,
                "file" templated (it.fileName ?: "?"),
                "line" templated "${it.lineNumber}"
            )
        }
        ?.forEach(this::append)
}

fun <T> List<CompletableFuture<T>>.flatten(): CompletableFuture<List<T>> {
    return CompletableFuture.allOf(*this.toTypedArray())
        .thenApplyAsync { this.map { it.join() } }
}

fun pushErrors(
    platform: PlatformFunctions,
    instanceState: ConfigState,
    activeLocale: TMLocale,
    exception: Exception,
    consoleErrorMessage: (TMLocale) -> String,
) {
    // Logs error
    platform.pushErrorToConsole(consoleErrorMessage(activeLocale))
    // Pushes full stacktrace to console
    if (instanceState.printFullStacktrace)
        exception.printStackTrace()

    // Pushed modified stacktrace to console if requested
    if (instanceState.printModifiedStacktrace)
        platform.getConsoleAudience().sendMessage(generateModifiedStacktrace(exception, activeLocale))

    // Pushes other messages to other players
    val onlinePlayers = platform.getAllOnlinePlayers()

    onlinePlayers.asParallelStream()
        .filter { it.has("ticketmanager.notify.error.stacktrace") }
        .forEach { generateModifiedStacktrace(exception, activeLocale).run(it::sendMessage) }

    onlinePlayers.asParallelStream()
        .filter { it.has("ticketmanager.notify.error.message") && !it.has("ticketmanager.notify.error.stacktrace") }
        .forEach { activeLocale.warningsInternalError.run(it::sendMessage) }
}