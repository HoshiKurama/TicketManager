package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.api.common.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.common.events.AbstractAsyncTicketModifyEvent
import com.github.hoshikurama.ticketmanager.api.common.ticket.Action
import com.github.hoshikurama.ticketmanager.api.common.ticket.Creator
import com.github.hoshikurama.ticketmanager.api.paper.events.AsyncTicketModifyEvent
import com.github.hoshikurama.ticketmanager.commonse.platform.events.EventBuilder

class EventBuilderImpl : EventBuilder {

    override fun buildTicketModifyEvent(
        activeCommandSender: CommandSender.Active,
        ticketCreator: Creator,
        modification: Action,
        wasSilent: Boolean
    ): AbstractAsyncTicketModifyEvent {
        return AsyncTicketModifyEvent(activeCommandSender, ticketCreator, modification, wasSilent)
    }
}