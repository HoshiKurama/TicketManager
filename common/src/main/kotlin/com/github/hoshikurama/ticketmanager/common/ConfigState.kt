package com.github.hoshikurama.ticketmanager.common

import com.github.hoshikurama.ticketmanager.common.databases.Database
import com.github.hoshikurama.ticketmanager.common.databases.SQLite
import kotlinx.coroutines.*
import java.time.Instant
import java.util.*
import kotlin.coroutines.CoroutineContext

class ConfigState(
    val cooldowns: Cooldown,
    val database: Database,
    val localeHandler: LocaleHandler,
    val allowUnreadTicketUpdates: Boolean,
    val pluginUpdateAvailable: Deferred<Pair<String, String>?>
) {

    companion object {
        suspend inline fun createPluginState(
            crossinline database: () -> Database?,
            crossinline cooldown: () -> Cooldown?,
            crossinline localeHandler: suspend () -> LocaleHandler?,
            crossinline allowUnreadTicketUpdates: () -> Boolean?,
            crossinline checkForPluginUpdate: () -> Boolean?,
            crossinline pluginVersion: () -> String,
            absolutePathToPluginFolder: String,
            context: CoroutineContext
        ) = withContext(context) {

            val deferredCooldown = async { tryOrDefault(cooldown, Cooldown(false, 0)) }
            val deferredAllowUnreadUpdates = async { tryOrDefault(allowUnreadTicketUpdates, true)
            }
            val deferredDatabase = async {
                tryOrDefault(
                    attempted = { database()?.apply { initialiseDatabase() } },
                    default = SQLite(absolutePathToPluginFolder)
                )
            }

            val deferredPluginUpdate = async {
                val shouldCheck = checkForPluginUpdate()
                if (shouldCheck == true) {
                    val curVersion = pluginVersion()
                    val latestVersion = UpdateChecker(91178).getLatestVersion()
                        .run { this ?: curVersion }

                    val curVersSplit = curVersion.split(".").map(String::toInt)
                    val latestVersSplit = latestVersion.split(".").map(String::toInt)

                    for (i in 0..latestVersSplit.lastIndex) {
                        if (curVersSplit[i] > latestVersSplit[i])
                            return@async null
                    }
                    return@async Pair(curVersion, latestVersion)
                }
                return@async null
            }

            val deferredLocaleHandler = async {
                tryOrDefaultSuspend(localeHandler,
                    LocaleHandler.buildLocalesAsync(
                        mainColourCode = "&3",
                        preferredLocale = "en_ca",
                        console_Locale = "en_ca",
                        forceLocale = false,
                        context = context
                    )
                )
            }

            ConfigState(
                cooldowns = deferredCooldown.await(),
                database = deferredDatabase.await(),
                localeHandler = deferredLocaleHandler.await(),
                allowUnreadTicketUpdates = deferredAllowUnreadUpdates.await(),
                pluginUpdateAvailable = deferredPluginUpdate
            )
        }
    }


    class Cooldown(
        private val enabled: Boolean,
        private val duration: Long,
    ) {
        @OptIn(ObsoleteCoroutinesApi::class)
        private val threadContext = newSingleThreadContext("Cooldown") //This WILL be replaced in the future
        private val map = mutableMapOf<UUID, Long>()

        suspend fun checkAndSetAsync(uuid: UUID?): Boolean {
            return withContext(threadContext) {
                if (!enabled || uuid == null) return@withContext false

                val curTime = Instant.now().epochSecond
                val applies = map[uuid]?.let { it <= curTime } ?: false
                return@withContext if (applies) true
                else map.put(uuid, duration + curTime).run { true }
            }
        }

        suspend fun filterMapAsync() {
            withContext(threadContext) {
                map.forEach {
                    if (it.value > Instant.now().epochSecond)
                        map.remove(it.key)
                }
            }
        }
    }
}

inline fun <T> tryOrDefault(attempted: () -> T?, default: T): T =
    tryOrNull(attempted).run { this ?: default }

inline fun <T> tryOrNull(function: () -> T): T? =
    try { function() }
    catch (e: Exception) { e.printStackTrace(); null }

suspend inline fun <T> tryOrDefaultSuspend(crossinline attempted: suspend () -> T?, default: T): T =
    tryOrNullSuspend(attempted).run { this ?: default }

suspend inline fun <T> tryOrNullSuspend(crossinline function: suspend () -> T): T? =
    try { function() }
    catch (ignored: Exception) { null }

