package com.github.hoshikurama.ticketmanager.api.ticket

import com.github.hoshikurama.ticketmanager.api.commands.CommandSender
import com.github.hoshikurama.ticketmanager.commonse.misc.asCreator
import java.time.Instant

/**
 * Represents any action done upon a ticket, including creation. Actions are immutable and thread-safe.
 * @property type contains the action type and any data associated with that particular action type.
 * @property user user who performed the action on the ticket
 * @property location location where user performed the action on the ticket
 * @property timestamp Epoch time for when the action was performed
 */
class TicketAction(
    val type: Type,
    val user: TicketCreator,
    val location: TicketCreationLocation,
    val timestamp: Long = Instant.now().epochSecond
) {
    constructor(type: Type, activeSender: CommandSender.Active): this(
        type = type,
        user = activeSender.asCreator(),
        location = activeSender.getLocAsTicketLoc(),
    )

    /**
     * All actions have a type. Some types carry additional relevant information. All implementors of Type represent
     * all different types of Ticket modifications.
     */
    sealed class Type(val asEnum: AsEnum) {
        /**
         * TypeEnum exists to facilitate consistent database writing and reading. Any pattern matching should
         * be performed with the Type interface. Recognized types:
         * @see ASSIGN
         * @see CLOSE
         * @see COMMENT
         * @see OPEN
         * @see REOPEN
         * @see SET_PRIORITY
         * @see MASS_CLOSE
         */
        enum class AsEnum {
            ASSIGN, CLOSE, CLOSE_WITH_COMMENT, COMMENT, OPEN, REOPEN, SET_PRIORITY, MASS_CLOSE
        }
    }

    /**
     * Ticket assignment action
     */
    class Assign(val assignment: TicketAssignmentType) : Type(AsEnum.ASSIGN)

    /**
     * Ticket comment action
     */
    class Comment(val comment: String) : Type(AsEnum.COMMENT)

    /**
     * Closing ticket action. Note: This is a pure close.
     */
    object CloseWithoutComment : Type(AsEnum.CLOSE)

    /**
     * Closes a ticket while also leaving a comment.
     */
    class CloseWithComment(val comment: String) : Type(AsEnum.CLOSE_WITH_COMMENT)

    /**
     * Opening ticket. Note: the initial message is contained here.
     */
    class Open(val message: String) : Type(AsEnum.OPEN)

    /**
     * Re-open ticket action.
     */
    object Reopen : Type(AsEnum.REOPEN)

    /**
     * Priority-Change ticket action.
     */
    class SetPriority(val priority: Ticket.Priority) : Type(AsEnum.SET_PRIORITY)

    /**
     * Mass-Close ticket action.
     */
    object MassClose : Type(AsEnum.MASS_CLOSE)
}