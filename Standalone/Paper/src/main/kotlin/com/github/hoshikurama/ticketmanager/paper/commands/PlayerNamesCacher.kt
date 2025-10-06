package com.github.hoshikurama.ticketmanager.paper.commands

import com.github.hoshikurama.tmcoroutine.TMCoroutine
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.Arrays
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import org.bukkit.command.ConsoleCommandSender as BukkitConsole

class OfflinePlayerNamesCacher {
    private val playersUpdaterJob: Job
    @Volatile private var isWriting = false
    @Volatile private var playersINTERNAL: Set<String> = getUpdatedPlayers()

    private val suggestionsCache = ConcurrentHashMap<UUID, Collection<String>>()
    private val partialNameCache = ConcurrentHashMap<UUID, String>()
    private val jobDeleterCache = ConcurrentHashMap<UUID, Job>()

    private val consoleUUID = UUID.randomUUID()
    private val nullSenderUUID = UUID.randomUUID()

    init {
        playersUpdaterJob = TMCoroutine.Global.launch {
            runCatching { // This just prevents "error" being logged
                if (!isActive) return@launch // Cooperative coroutine
                delay(5.minutes)

                // Get players then lock variable while assigning (multithread access)
                val playersTemp = getUpdatedPlayers()
                isWriting = true
                playersINTERNAL = playersTemp
                isWriting = false
            }
        }
    }

    suspend fun getSuggestions(playerUUID: UUID, partialName: String): Collection<String> {
        jobDeleterCache[playerUUID]?.cancel() // Cancel jobDeleter task if present (prevents state issues)

        if (!suggestionsCache.containsKey(playerUUID)) {
            // If user not in typing cache...
            if (partialName == "") suggestionsCache[playerUUID] = getPlayersSafely()
            else suggestionsCache[playerUUID] = getPlayersSafely().filter { it.startsWith(partialName) }
        } else {
            // user is now known to be in cache
            val cachedPartialName = partialNameCache[playerUUID]!!
            val cachedSuggestions = suggestionsCache[playerUUID]!!

            suggestionsCache[playerUUID] = when {
                partialName == "" -> getPlayersSafely()                                             // ""
                partialName.length == 1 || partialName.startsWith(cachedPartialName) ->     // "" -> "H" or "Hosh" -> "Hoshi"
                    cachedSuggestions.filter { it.startsWith(partialName) }

                else -> getPlayersSafely().filter { it.startsWith(partialName) }            // "Hoshi" -> "Hosh" or catch all
            }
        }

        partialNameCache[playerUUID] = partialName
        jobDeleterCache[playerUUID] = generateRemovalJob(playerUUID)

        return suggestionsCache[playerUUID]!!
    }

    suspend fun getSuggestions(sender: BukkitCommandSender, partialName: String): Collection<String> = when (sender) {
        is BukkitConsole -> getSuggestions(consoleUUID, partialName)
        is BukkitPlayer -> getSuggestions(sender.uniqueId, partialName)
        else -> when ((this::class).toString()) {
            "class io.papermc.paper.brigadier.NullCommandSender" -> getSuggestions(nullSenderUUID, partialName)
            else -> throw Exception("Unsupported Entity Type!:\n Class Type: \"${this::class}\"")
        }
    }

    suspend fun contains(username: String): Boolean = username in getPlayersSafely()



    @OptIn(ExperimentalTime::class)
    private fun getUpdatedPlayers(): Set<String> {
        val timeCutoff = (Clock.System.now() - 60.days).epochSeconds

        return Arrays.stream(Bukkit.getOfflinePlayers())
            .parallel()
            .filter { it.lastSeen > timeCutoff && it.name != null }
            .map(OfflinePlayer::getName)
            .collect(Collectors.toSet<String>())
    }

    private suspend fun getPlayersSafely(): Set<String> = coroutineScope {
        while (isWriting) {
            delay(10.milliseconds)
        }

        return@coroutineScope playersINTERNAL
    }

    private fun generateRemovalJob(playerUUID: UUID): Job = TMCoroutine.Global.launch {
        runCatching { // This just prevents "error" being logged
            if (!isActive) return@launch // Cooperative coroutine
            delay(10.seconds)

            suggestionsCache.remove(playerUUID)
            partialNameCache.remove(playerUUID)
            jobDeleterCache.remove(playerUUID)
        }
    }
}