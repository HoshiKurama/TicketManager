package com.github.hoshikurama.ticketmanager.paper.commands

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.registry.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import com.github.hoshikurama.ticketmanager.commonse.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.commonse.misc.templated
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import net.kyori.adventure.text.Component

// locale.parameterID is always the argument name
class TicketGrabberArgument(
    vararg val otherChecks: (Ticket, CommandSender.Active) -> TicketGrabber.Error?
): CustomArgumentType.Converted<TicketGrabber, Long> {

    override fun convert(nativeType: Long): TicketGrabber {
        return TicketGrabber(nativeType, otherChecks)
    }

    override fun getNativeType(): ArgumentType<Long> {
        return LongArgumentType.longArg()
    }
}

class TicketGrabber(
    private val ticketID: Long,
    private val otherChecks: Array<out (Ticket, CommandSender.Active) -> Error?>
) {
    private val database: AsyncDatabase
        get() = CommandReferences.database
    private val locale: Locale
        get() = CommandReferences.locale

    sealed interface Result
    class Error(val errorComponent: Component): Result
    class Success(val ticket: Ticket): Result

    suspend fun get(sender: CommandSender.Active): Result {
        val ticketOrNull = database.getTicketOrNullAsync(ticketID)

        // Filter Invalid Ticket
        val ticket = ticketOrNull ?: return locale.brigadierInvalidID
            .parseMiniMessage("id" templated ticketID.toString())
            .run(::Error)

        // Other Checks
        val failPoint = otherChecks.mapNotNull { it(ticket, sender) }

       return if (failPoint.isNotEmpty()) failPoint.first() else Success(ticket)
    }
}