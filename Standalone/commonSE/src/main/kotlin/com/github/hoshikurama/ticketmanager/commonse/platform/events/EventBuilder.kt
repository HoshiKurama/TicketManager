package com.github.hoshikurama.ticketmanager.commonse.platform.events

import com.github.hoshikurama.ticketmanager.api.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.events.DatabaseWriteCompleteEventAsync
import com.github.hoshikurama.ticketmanager.api.events.TicketModificationAsyncEvent
import com.github.hoshikurama.ticketmanager.api.ticket.Action
import com.github.hoshikurama.ticketmanager.api.ticket.ActionLocation
import com.github.hoshikurama.ticketmanager.api.ticket.Creator

abstract class EventBuilder : TMCallableEvent {

    abstract fun buildDatabaseWriteCompleteEvent(
        activeCommandSender: CommandSender.Active,
        ticketAction: Action,
        ticketID: Long
    ): DatabaseWriteCompleteEventAsyncCallable

    abstract fun buildTicketModificationEvent(
        activeCommandSender: CommandSender.Active,
        ticketCreator: Creator,
        modification: Action,
        wasSilent: Boolean,
    ): TicketModificationAsyncEventCallable
}

interface TMCallableEvent {
    fun callEventTM()
}

interface DatabaseWriteCompleteEventAsyncCallable : DatabaseWriteCompleteEventAsync, TMCallableEvent
interface TicketModificationAsyncEventCallable : TicketModificationAsyncEvent, TMCallableEvent