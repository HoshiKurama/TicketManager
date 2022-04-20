package com.github.hoshikurama.ticketmanager.misc

import com.github.hoshikurama.ticketmanager.TMLocale
import com.github.hoshikurama.ticketmanager.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.misc.kyoriComponentDSL.buildComponent
import com.github.hoshikurama.ticketmanager.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.ticket.Console
import com.github.hoshikurama.ticketmanager.ticket.Creator
import com.github.hoshikurama.ticketmanager.ticket.Ticket
import com.github.hoshikurama.ticketmanager.ticket.User
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.time.Instant
import java.util.*

typealias TicketPredicate = (Ticket) -> Boolean


val supportedLocales = listOf("de_de", "en_ca", "en_uk", "en_us")

fun byteToPriority(byte: Byte) = when (byte.toInt()) {
    1 -> Ticket.Priority.LOWEST
    2 -> Ticket.Priority.LOW
    3 -> Ticket.Priority.NORMAL
    4 -> Ticket.Priority.HIGH
    5 -> Ticket.Priority.HIGHEST
    else -> Ticket.Priority.NORMAL
}

fun priorityToHexColour(priority: Ticket.Priority, locale: TMLocale) = when (priority) {
    Ticket.Priority.LOWEST -> locale.priorityColourLowestHex
    Ticket.Priority.LOW -> locale.priorityColourLowHex
    Ticket.Priority.NORMAL -> locale.priorityColourNormalHex
    Ticket.Priority.HIGH -> locale.priorityColourHighHex
    Ticket.Priority.HIGHEST -> locale.priorityColourHighestHex
}

fun statusToHexColour(status: Ticket.Status, locale: TMLocale) = when (status) {
    Ticket.Status.OPEN -> locale.statusColourOpenHex
    Ticket.Status.CLOSED -> locale.statusColourClosedHex
}

fun Long.toLargestRelativeTime(locale: TMLocale): String {
    val timeAgo = Instant.now().epochSecond - this

    return when {
        timeAgo >= 31556952L -> (timeAgo / 31556952L).toString() + locale.timeYears
        timeAgo >= 604800L ->(timeAgo / 604800L).toString() + locale.timeWeeks
        timeAgo >= 86400L ->(timeAgo / 86400L).toString() + locale.timeDays
        timeAgo >= 3600L ->(timeAgo / 3600L).toString() + locale.timeHours
        timeAgo >= 60L ->(timeAgo / 60L).toString() + locale.timeMinutes
        else -> timeAgo.toString() + locale.timeSeconds
    }
}

fun relTimeToEpochSecond(relTime: String, locale: TMLocale): Long {
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
                    locale.searchTimeSecond -> 1L
                    locale.searchTimeMinute -> 60L
                    locale.searchTimeHour -> 3600L
                    locale.searchTimeDay -> 86400L
                    locale.searchTimeWeek -> 604800L
                    locale.searchTimeYear -> 31556952L
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
fun generateModifiedStacktrace(e: Exception, locale: TMLocale) = buildComponent {
    val cause = e.cause

    // Builds Header
    append(locale.stacktraceLine1.parseMiniMessage())
    append(locale.stacktraceLine2.parseMiniMessage("exception" templated (cause?.javaClass?.simpleName ?: "???")))
    append(locale.stacktraceLine3.parseMiniMessage("message" templated (cause?.message ?: "?")))
    append(locale.stacktraceLine4.parseMiniMessage())

    // Adds stacktrace entries
    cause?.stackTrace
        ?.filter { it.className.contains("com.github.hoshikurama.ticketmanager") }
        ?.map {
            locale.stacktraceEntry.parseMiniMessage(
                "method" templated it.methodName,
                "file" templated (it.fileName ?: "?"),
                "line" templated "${it.lineNumber}"
            )
        }
        ?.forEach(this::append)
}

fun mapToCreatorOrNull(string: String): Creator? {
    val sep = string.split(".")
    return when (sep[0]) {
        "USER" -> User(UUID.fromString(sep[1]))
        "CONSOLE" -> Console
        else -> null
    }
}

/*
fun <T> List<CompletableFuture<T>>.flatten(): CompletableFuture<List<T>> {
    return CompletableFuture.allOf(*this.toTypedArray())
        .thenApplyAsync { this.map { it.join() } }
}
 */

fun pushErrors(
    platform: PlatformFunctions,
    instanceState: InstancePluginState,
    exception: Exception,
    consoleErrorMessage: (TMLocale) -> String,
) {
    // Logs error
    platform.pushErrorToConsole(consoleErrorMessage(instanceState.localeHandler.consoleLocale))
    // Pushes full stacktrace to console
    if (instanceState.printFullStacktrace)
        exception.printStackTrace()

    // Pushed modified stacktrace to console if requested
    if (instanceState.printModifiedStacktrace)
        platform.getConsoleAudience().sendMessage(generateModifiedStacktrace(exception, instanceState.localeHandler.consoleLocale))

    // Pushes other messages to other players
    val onlinePlayers = platform.getAllOnlinePlayers(instanceState.localeHandler)

    onlinePlayers.asParallelStream()
        .filter { it.has("ticketmanager.notify.error.stacktrace") }
        .forEach { generateModifiedStacktrace(exception, it.locale).run(it::sendMessage) }

    onlinePlayers.asParallelStream()
        .filter { it.has("ticketmanager.notify.error.message") && !it.has("ticketmanager.notify.error.stacktrace") }
        .forEach { it.locale.warningsInternalError.run(it::sendMessage) }
}