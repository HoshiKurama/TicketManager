package com.github.hoshikurama.ticketmanager.commonse.extensions

import com.github.hoshikurama.ticketmanager.api.common.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.common.services.DatabaseRegistry
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.datas.ConfigState
import com.github.hoshikurama.ticketmanager.commonse.misc.pushErrors
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

const val defaultDatabase = "CACHED_H2"


// Note: Safe to use at any point
object DatabaseManager : DatabaseRegistry {

    private val map: ConcurrentHashMap<String, () -> AsyncDatabase> = ConcurrentHashMap()
    @Volatile lateinit var activeDatabase: AsyncDatabase internal set


    override fun register1(databaseName: String, builder: () -> AsyncDatabase) {
        map[databaseName] = builder
    }

    // Note: Function below will initiate the listen
    internal suspend fun activateNewDatabase(
        desiredDatabase: String,
        platformFunctions: PlatformFunctions,
        configState: ConfigState,
        activeLocale: TMLocale,
    ): Unit = coroutineScope {
        // Wait for up to 30 seconds before the plugin is forced to accept a default Cached_H2 implementation
        // Check each second for if the desired databaseBuilder is in the map
        var secondsRun = 0
        while (map[desiredDatabase] == null) {
            if (secondsRun >= 30) break
            secondsRun += 1
            delay(1000)
        }

        // Get final database or default
        activeDatabase = try {
            map[desiredDatabase]?.invoke() ?: map[defaultDatabase]!!.invoke()
        } catch (e: Exception) {
            pushErrors(platformFunctions, configState, activeLocale, e, TMLocale::consoleErrorBadDatabase)
            map[defaultDatabase]!!.invoke()
        }

        activeDatabase.initializeDatabase()
    }
}