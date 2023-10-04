package com.github.hoshikurama.ticketmanager.commonse.misc

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.time.Instant

typealias TicketPredicate = (Ticket) -> Boolean

fun Permission.has(sender: CommandSender.Active, permission: String, consolePermission: Boolean) = when (sender) {
    is CommandSender.OnlinePlayer -> has(sender, permission)
    is CommandSender.OnlineConsole -> consolePermission
}

fun Long.toLargestRelativeTime(activeLocale: Locale): String {
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

fun relTimeToEpochSecond(relTime: String, activeLocale: Locale): Long {
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

// MiniMessage helper functions
fun String.parseMiniMessage(vararg template: TagResolver) = MiniMessage.miniMessage().deserialize(this, *template)
fun String.parseMiniMessage() = MiniMessage.miniMessage().deserialize(this)
infix fun String.templated(string: String) = Placeholder.parsed(this, string)
infix fun String.templated(component: Component) = Placeholder.component(this, component)
operator fun Component.plus(other: Component) = append(other)

// Other
fun pushErrors(
    platform: PlatformFunctions,
    permission: Permission,
    locale: Locale,
    exception: Exception,
    consoleErrorMessage: (Locale) -> String,
) {
    // Logs error
    platform.pushErrorToConsole(consoleErrorMessage(locale))
    exception.printStackTrace()

    // Pushes other messages to other players
    platform.getAllOnlinePlayers()
        .filter { permission.has(it, "ticketmanager.notify.error.message") }
        .forEach { locale.warningsInternalError.run(it::sendMessage) }
}