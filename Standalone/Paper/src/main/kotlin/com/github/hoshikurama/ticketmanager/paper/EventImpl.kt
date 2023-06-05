package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.api.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.ticket.Action
import com.github.hoshikurama.ticketmanager.api.ticket.Creator
import com.github.hoshikurama.ticketmanager.commonse.platform.events.DatabaseWriteCompleteEventAsyncCallable
import com.github.hoshikurama.ticketmanager.commonse.platform.events.EventBuilder
import com.github.hoshikurama.ticketmanager.commonse.platform.events.TicketModificationAsyncEventCallable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class EventImpl {
}

class EventBuilderImpl: EventBuilder() {

    override fun buildDatabaseWriteCompleteEvent(
        activeCommandSender: CommandSender.Active,
        ticketAction: Action,
        ticketID: Long
    ): DatabaseWriteCompleteEventAsyncCallable {
        return DBWriteCompleteEventAsyncImpl(activeCommandSender, ticketAction, ticketID)
    }

    override fun buildTicketModificationEvent(
        activeCommandSender: CommandSender.Active,
        ticketCreator: Creator,
        modification: Action,
        wasSilent: Boolean
    ): TicketModificationAsyncEventCallable {
        return TicketModificationEventAsyncImpl(activeCommandSender, ticketCreator, modification, wasSilent)
    }

    override fun callEventTM() {
        TODO("Not yet implemented")
    }
}

class DBWriteCompleteEventAsyncImpl(
    override val activeCommandSender: CommandSender.Active,
    override val ticketAction: Action,
    override val ticketID: Long
): Event(true), DatabaseWriteCompleteEventAsyncCallable {
    companion object {
        val handlerList = HandlerList()
    }

    override fun getHandlers(): HandlerList = handlerList
    override fun callEventTM() {
        super.callEvent()
    }
}

class TicketModificationEventAsyncImpl(
    override val commandSender: CommandSender.Active,
    override val ticketCreator: Creator,
    override val modification: Action,
    override val wasSilent: Boolean
): Event(true), TicketModificationAsyncEventCallable {
    companion object {
        val handlerList = HandlerList()
    }

    override fun getHandlers(): HandlerList = handlerList
    override fun callEventTM() {
        super.callEvent()
    }
}