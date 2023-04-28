package com.github.hoshikurama.ticketmanager.api.ticket


/**
 * Creation location of a ticket. Nullable values account for all creator types.
 * @property server specified by the plugin configuration file (for proxy networks)
 * @see FromPlayer
 * @see FromConsole
 */
sealed interface TicketCreationLocation {
    val server: String?

    /**
     * Denotes the creation location of a player ticket. All values are not null except for the following:
     * @property server non-networked servers can still have a null server
     * @property world world ticket is created in
     * @property x x-block position
     * @property y y-block position
     * @property z z-block position
     */
    @JvmRecord
    data class FromPlayer(
        override val server: String?,
        val world: String,
        val x: Int,
        val y: Int,
        val z: Int,
    ) : TicketCreationLocation {
        override fun toString(): String = "${server ?: ""} $world $x $y $z".trimStart()  //TODO MOVE INTO EXTENSION FUNCTION FOR DATABASE
    }

    /**
     * Denotes the creation location of a console ticket. Values should always be null except for the following:
     * @property server networked servers may have a non-null value
     */
    @JvmRecord
    data class FromConsole(
        override val server: String?
    ) : TicketCreationLocation {
        override fun toString(): String = "${server ?: ""} ${""} ${""} ${""} ${""}".trimStart() //TODO MOVE INTO EXTENSION FUNCTION FOR DATABASE
    }
}