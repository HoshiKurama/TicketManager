package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.ticketmanager.implapi.commands.CommandSender
import com.github.hoshikurama.ticketmanager.implapi.events.DatabaseWriteCompleteEventAsync
import com.github.hoshikurama.ticketmanager.implapi.events.TicketModificationEventAsync
import com.github.hoshikurama.ticketmanager.implapi.ticket.TicketAction
import com.github.hoshikurama.ticketmanager.implapi.ticket.TicketCreator
import com.github.hoshikurama.ticketmanager.commonse.platform.events.EventBuilder
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class EventImpl {
}

class EventBuilderImpl: EventBuilder() {

    override fun buildDatabaseWriteCompleteEvent(
        activeCommandSender: CommandSender.Active,
        ticketAction: TicketAction,
        ticketID: Long
    ): DatabaseWriteCompleteEventAsync {
        return DBWriteCompleteEventAsyncImpl(activeCommandSender, ticketAction, ticketID)
    }

    override fun buildTicketModificationEvent(
        activeCommandSender: CommandSender.Active,
        ticketCreator: TicketCreator,
        modification: TicketAction,
        wasSilent: Boolean
    ): TicketModificationEventAsync {
        return TicketModificationEventAsyncImpl(activeCommandSender, ticketCreator, modification, wasSilent)
    }
}

class DBWriteCompleteEventAsyncImpl(
    override val activeCommandSender: CommandSender.Active,
    override val ticketAction: TicketAction,
    override val ticketID: Long
): Event(true), DatabaseWriteCompleteEventAsync {
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
    override val ticketCreator: TicketCreator,
    override val modification: TicketAction,
    override val wasSilent: Boolean
): Event(true), TicketModificationEventAsync {
    companion object {
        val handlerList = HandlerList()
    }

    override fun getHandlers(): HandlerList = handlerList
    override fun callEventTM() {
        super.callEvent()
    }
}