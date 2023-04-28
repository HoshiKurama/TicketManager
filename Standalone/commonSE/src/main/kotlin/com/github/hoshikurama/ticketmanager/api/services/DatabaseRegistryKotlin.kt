package com.github.hoshikurama.ticketmanager.api.services

import com.github.hoshikurama.ticketmanager.api.database.AsyncDatabase

/**
 * Represents functions accessible for Kotlin users to register a database.
 * TicketManager does not reload extensions, so utilizing TicketManager's reload function
 * requires users to submit a builder function which reads the extension's config file.
 * Alternatively, extensions may implement their own reload function to re-call the registration function.
 * As a newer registration with the same name overwrites the previous function, users may perform
 * TicketManager's reload function to load the data.
 *
 * Users of Java should see the DatabaseRegistryJava interface
 */
interface DatabaseRegistryKotlin {

    /**
     * Registers a builder function into TicketManager.
     * @param databaseName lowercase name users must input into the TicketManager config file to select this option
     * @param builder function to build the database
     */
    fun register(databaseName: String, builder: () -> AsyncDatabase)
}