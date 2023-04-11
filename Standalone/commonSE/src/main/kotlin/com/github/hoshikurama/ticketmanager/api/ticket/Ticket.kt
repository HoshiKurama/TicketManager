package com.github.hoshikurama.ticketmanager.api.ticket

/**
 * Tickets are the foundation of TicketManager. They contain ALL data related to a given request. Tickets may be built
 * using separate data, but all fields must be properly assigned prior to object use. They should be immutable and thread-safe.
 *
 * Tickets should not be held onto for an extended period of time as they reflect the state at the time of creation.
 * @property id Unique value referencing a particular ticket
 * @property creator Ticket creator
 * @property priority Priority level
 * @property status Status (open/closed)
 * @property assignedTo Ticket assignment. Null values indicate an assignment to nobody
 * @property creatorStatusUpdate Used internally to indicate if the creator has seen the last change to their ticket.
 * @property actions Chronological list of modifications made to the initial ticket.
 */
interface Ticket {
    val id: Long
    val creator: Creator
    val priority: Priority
    val status: Status
    val assignedTo: String?
    val creatorStatusUpdate: Boolean
    val actions: List<Action>

    /**
     * Creates a new ticket with the supplied actions replacing any previous ones.
     * @param actions List of new actions to override any previous ones
     * @return Ticket after applied action. The default Ticket implementation is to create a new, immutable one with
     * the applied change.
     */
    operator fun plus(actions: List<Action>): Ticket

    /**
     * Appends a single Action to the ticket.
     * @param action Action to append
     * @return Ticket after applied action. The default Ticket implementation is to create a new, immutable one with
     * the applied change.
     */
    operator fun plus(action: Action): Ticket

    /**
     * Creation location of a ticket. Nullable values account for all creator types.
     * @property server specified by the plugin configuration file (for proxy networks)
     * @property world world ticket is created in
     * @property x x-block position
     * @property y y-block position
     * @property z z-block position
     * @see FromPlayer
     * @see FromConsole
     */
    sealed interface CreationLocation {
        val server: String?
        val world: String?
        val x: Int?
        val y: Int?
        val z: Int?

        /**
         * Denotes the creation location of a player ticket. All values are not null except for the following:
         * @property server non-networked servers can still have a null server
         */
        interface FromPlayer: CreationLocation {
            override val server: String?
            override val world: String
            override val x: Int
            override val y: Int
            override val z: Int
        }

        /**
         * Denotes the creation location of a console ticket. Values should always be null except for the following:
         * @property server networked servers may have a non-null value
         */
        interface FromConsole: CreationLocation
    }

    /**
     * Encapsulates the priority level of a ticket. There are 5 levels, and level NORMAL should be default
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

    /**
     * Represents any action done upon a ticket, including creation. Actions should be immutable and thread-safe.
     * Users wishing to implement a new database type should have an auto-incrementing internal value.
     * @property type contains the action type and any data associated with that particular action type.
     * @property user user who performed the action on the ticket
     * @property location location where user performed the action on the ticket
     * @property timestamp Epoch time for when the action was performed
     */
    interface Action {
        val type: Type
        val user: Creator
        val location: CreationLocation
        val timestamp: Long


        /**
         * Represents an Action type and its associated data.
         */
        sealed interface Type {

            /**
             * TypeEnum exists to facilitate consistent database writing and reading. Any pattern matching should
             * be performed with the Type interface enclosing this structure. Recognized types:
             * @see ASSIGN
             * @see CLOSE
             * @see COMMENT
             * @see OPEN
             * @see REOPEN
             * @see SET_PRIORITY
             * @SEE MASS_CLOSE
             */
            enum class TypeEnum {
                ASSIGN, CLOSE, COMMENT, OPEN, REOPEN, SET_PRIORITY, MASS_CLOSE
            }

            /**
             * @return Associated TypeEnum equivalent for database reads/writes.
             */
            fun getTypeEnum(): TypeEnum

            /**
             * Represents and contains the data for an assignment
             * @property assignment target assignment. Null indicates it was unassigned
             */
            interface ASSIGN: Type {
                override fun getTypeEnum(): TypeEnum = TypeEnum.ASSIGN
                val assignment: String?
            }

            /**
             * Represents a ticket close
             */
            interface CLOSE: Type {
                override fun getTypeEnum(): TypeEnum = TypeEnum.CLOSE
            }

            /**
             * Represents and contains the data for a comment
             * @property comment comment left by a user
             */
            interface COMMENT: Type {
                override fun getTypeEnum(): TypeEnum = TypeEnum.COMMENT
                val comment: String
            }

            /**
             * Represents and contains the data for an initial ticket creation
             */
            interface OPEN: Type {
                override fun getTypeEnum(): TypeEnum = TypeEnum.OPEN
                val message: String
            }

            /**
             * Represents a ticket being reopened
             */
            interface REOPEN: Type {
                override fun getTypeEnum(): TypeEnum = TypeEnum.REOPEN
            }

            /**
             * Represents and contains the data for a change in priority
             * @property priority new priority assigned to ticket
             */
            interface SET_PRIORITY: Type {
                override fun getTypeEnum(): TypeEnum = TypeEnum.SET_PRIORITY
                val priority: Priority
            }

            /**
             * Represents a mass close by a user
             */
            interface MASS_CLOSE: Type {
                override fun getTypeEnum(): TypeEnum = TypeEnum.MASS_CLOSE
            }
        }
    }
}