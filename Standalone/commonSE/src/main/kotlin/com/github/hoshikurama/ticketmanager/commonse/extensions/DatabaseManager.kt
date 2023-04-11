package com.github.hoshikurama.ticketmanager.commonse.extensions

import com.github.hoshikurama.ticketmanager.api.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.commonse.datas.GlobalPluginState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

const val defaultDatabase = "cached_h2"

object DatabaseManager {

    private val map: ConcurrentHashMap<String, () -> AsyncDatabase> = ConcurrentHashMap()
    @Volatile private lateinit var activeDatabase: AsyncDatabase

    fun register(databaseName: String, builder: () -> AsyncDatabase) {

    }

    fun register(databaseName: String, builder: Supplier<AsyncDatabase>) {
        register(databaseName) { builder.get() }
    }

    suspend internal fun activateNewDatabase(desiredDatabase: String): Unit = coroutineScope { // Note: This will initiate the listen
        GlobalPluginState.isDatabaseLoaded = false

        // Wait for up to 30 seconds before the plugin is forced to accept a default Cached_H2 implementation
        // Check each second for if the desired databaseBuilder is in the map
        var secondsRuns = 0
        while (map[desiredDatabase.lowercase()] == null) {
            if (secondsRuns >= 30) break
            secondsRuns += 1
            delay(1000)
        }

        // Run final database or default
        val result = map[desiredDatabase]
        val data = if (result != null) result.invoke()
        else result[desiredDatabase]
    }


    /*
    Goal: Load TicketManager but have global variable to denote when database is loaded.
    Users will have 30 second window to register.
    On register command, if desired db type is loaded, then immediately use that and set plugin var to true
    Else wait until default implementation occurs.

    Notes:
    - DB reload still possible if extension has own reload method

     */
}