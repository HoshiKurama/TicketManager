package com.github.hoshikurama.ticketmanager.commonse.extensions

import com.github.hoshikurama.ticketmanager.api.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.services.DatabaseRegistryJava
import com.github.hoshikurama.ticketmanager.api.services.DatabaseRegistryKotlin
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.datas.ConfigState
import com.github.hoshikurama.ticketmanager.commonse.datas.GlobalState
import com.github.hoshikurama.ticketmanager.commonse.misc.pushErrors
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

const val defaultDatabase = "cached_h2"


// Note: Safe to use at any point
object DatabaseManager : DatabaseRegistryJava, DatabaseRegistryKotlin {

    private val map: ConcurrentHashMap<String, () -> AsyncDatabase> = ConcurrentHashMap()
    @Volatile lateinit var activeDatabase: AsyncDatabase internal set


    override fun register(databaseName: String, builder: () -> AsyncDatabase) {
        map[databaseName.lowercase()] = builder
    }

    override fun register(databaseName: String, builder: Supplier<AsyncDatabase>) {
        register(databaseName) { builder.get() }
    }

    // Note: Function below will initiate the listen
    internal suspend fun activateNewDatabase(
        desiredDatabase: String,
        platformFunctions: PlatformFunctions,
        configState: ConfigState,
    ): Deferred<AsyncDatabase> = coroutineScope {
        // Wait for up to 30 seconds before the plugin is forced to accept a default Cached_H2 implementation
        // Check each second for if the desired databaseBuilder is in the map
        var secondsRun = 0
        while (map[desiredDatabase.lowercase()] == null) {
            if (secondsRun >= 30) break
            secondsRun += 1
            delay(1000)
        }

        // Get final database or default
        return@coroutineScope async {
            try {
                map[desiredDatabase]?.invoke() ?: map[defaultDatabase]!!.invoke()
            } catch (e: Exception) {
                pushErrors(platformFunctions, configState, e, TMLocale::consoleErrorBadDatabase)
                map[defaultDatabase]!!.invoke()
            }
        }
    }
}