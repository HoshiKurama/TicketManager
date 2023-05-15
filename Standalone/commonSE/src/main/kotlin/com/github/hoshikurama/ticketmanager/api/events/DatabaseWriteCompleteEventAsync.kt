package com.github.hoshikurama.ticketmanager.api.events

import com.github.hoshikurama.ticketmanager.api.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.ticket.TicketAction

/**
 * Event fires when after all database writes for a particular command are complete (ignoring
 * database functions which return a non-void/unit value). Given that TicketManager will fire in-game
 * notifications before database writes are complete, this function is particularly useful if developers
 * need to perform an action where it is crucial that the database write is completed first.
 *
 * NOTE: This event will always fire asynchronously.
 * @property activeCommandSender Active instance of the command sender, which can be used to send messages.
 * @property ticketAction Data regarding the modification made to the ticket.
 * @property ticketID ID of affected ticket. If the ticket ID is not relevant, then -1 is returned instead.
 */

interface DatabaseWriteCompleteEventAsync {
    val activeCommandSender: CommandSender.Active
    val ticketAction: TicketAction
    val ticketID: Long

    fun callEventTM()
}