package com.github.hoshikurama.ticketmanager.api.ticket

/**
 * Tickets are the foundation of TicketManager. They contain all data related to a given request. Tickets are both
 * immutable and thread-safe. Additionally, they should not be held onto for an extended period of time as they
 * reflect the state at the time of creation.
 * @property id Unique value referencing a particular ticket
 * @property creator Ticket creator
 * @property priority Priority level
 * @property status Status (open/closed)
 * @property assignedTo Ticket assignment.
 * @property creatorStatusUpdate Used internally to indicate if the creator has seen the last change to their ticket.
 * @property actions Chronological list of modifications made to the initial ticket.
 */
class Ticket(
    val id: Long = -1L,
    val creator: TicketCreator,
    val priority: Priority = Priority.NORMAL,
    val status: Status = Status.OPEN,
    val assignedTo: TicketAssignmentType = TicketAssignmentType.Nobody,
    val creatorStatusUpdate: Boolean = false,
    val actions: List<TicketAction> = listOf()
) {
    /**
     * Creates a new ticket with the supplied actions replacing any previous ones.
     * @param actions List of new actions to override any previous ones
     * @return New ticket with the applied change
     */
    operator fun plus(actions: List<TicketAction>): Ticket {
        return Ticket(id, creator, priority, status, assignedTo, creatorStatusUpdate, actions)
    }

    /**
     * Appends a single action to the ticket.
     * @param action Action to append
     * @return New ticket with the action appended
     */
    operator fun plus(action: TicketAction): Ticket {
        return Ticket(id, creator, priority, status, assignedTo, creatorStatusUpdate, this.actions + action)
    }

    /**
     * Encapsulates the priority level of a ticket.
     */
    enum class Priority(val level: Byte) {
        LOWEST(1),
        LOW(2),
        NORMAL(3),
        HIGH(4),
        HIGHEST(5);
    }

    /**
     * Encapsulates the status of a ticket, which is either open or closed
     */
    enum class Status {
        OPEN, CLOSED;
    }
}