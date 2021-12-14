package com.github.hoshikurama.ticketmanager.misc

import com.github.hoshikurama.componentDSL.buildComponent
import com.github.hoshikurama.componentDSL.formattedContent
import com.github.hoshikurama.ticketmanager.TMLocale
import com.github.hoshikurama.ticketmanager.data.InstancePluginState
import com.github.hoshikurama.ticketmanager.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.ticket.BasicTicket
import com.github.hoshikurama.ticketmanager.ticket.FullTicket
import net.kyori.adventure.extra.kotlin.text
import java.time.Instant

typealias FullTicketPredicate = (FullTicket) -> Boolean

fun byteToPriority(byte: Byte) = when (byte.toInt()) {
    1 -> BasicTicket.Priority.LOWEST
    2 -> BasicTicket.Priority.LOW
    3 -> BasicTicket.Priority.NORMAL
    4 -> BasicTicket.Priority.HIGH
    5 -> BasicTicket.Priority.HIGHEST
    else -> BasicTicket.Priority.NORMAL
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

fun stringToStatusOrNull(str: String) = tryOrNull { BasicTicket.Status.valueOf(str) }

fun toColouredAdventure(s: String) = text { formattedContent(s) }

fun generateModifiedStacktrace(e: Exception, locale: TMLocale) = buildComponent {
    // Builds header
    listOf(
        locale.stacktraceLine1,
        locale.stacktraceLine2.replace("%exception%", e.javaClass.simpleName),
        locale.stacktraceLine3.replace("%message%", e.message ?: "?"),
        locale.stacktraceLine4,
    )
        .forEach { text { formattedContent(it) } }

    // Adds stacktrace entries
    e.stackTrace
        .filter { it.className.startsWith("com.github.hoshikurama.ticketmanager") }
        .map {
            locale.stacktraceEntry
                .replace("%method%", it.methodName)
                .replace("%file%", it.fileName ?: "?")
                .replace("%line%", "${it.lineNumber}")
        }
        .forEach { text { formattedContent(it) } }
}

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
    val onlinePlayers = platform.getOnlinePlayers(instanceState.localeHandler)
    onlinePlayers.asParallelStream()
        .filter { it.has("ticketmanager.notify.error.stacktrace") }
        .forEach { generateModifiedStacktrace(exception, it.locale).run(it::sendMessage) }

    onlinePlayers.asParallelStream()
        .filter { it.has("ticketmanager.notify.error.message") && !it.has("ticketmanager.notify.error.stacktrace") }
        .forEach { it.locale.warningsInternalError.run(it::sendMessage) }
}