package com.github.hoshikurama.ticketmanager.commonse.platform.events

import com.github.hoshikurama.ticketmanager.api.common.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.common.events.AbstractAsyncTicketModifyEvent
import com.github.hoshikurama.ticketmanager.api.common.ticket.Action
import com.github.hoshikurama.ticketmanager.api.common.ticket.Creator


interface EventBuilder {

    fun buildTicketModifyEvent(
        activeCommandSender: CommandSender.Active,
        ticketCreator: Creator,
        modification: Action,
        ticketNumber: Long,
        wasSilent: Boolean,
    ) : AbstractAsyncTicketModifyEvent
}