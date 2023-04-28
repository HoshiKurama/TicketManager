package com.github.hoshikurama.ticketmanager.commonse.platform

import com.github.hoshikurama.ticketmanager.api.events.DatabaseWriteCompleteEventAsync
import com.github.hoshikurama.ticketmanager.api.events.TicketModificationEventAsync
import com.github.hoshikurama.ticketmanager.api.ticket.TicketAction
import com.github.hoshikurama.ticketmanager.api.ticket.TicketCreator
import com.github.hoshikurama.ticketmanager.api.commands.CommandSender

abstract class EventBuilder {

    abstract fun buildDatabaseWriteCompleteEvent(
        activeCommandSender: CommandSender.Active,
        ticketAction: TicketAction,
        ticketID: Long
    ): DatabaseWriteCompleteEventAsync

    abstract fun buildTicketModificationEvent(
        commandSenderInfo: CommandSender.Active,
        ticketCreator: TicketCreator,
        modification: TicketAction,
        wasSilent: Boolean,
    ): TicketModificationEventAsync
}