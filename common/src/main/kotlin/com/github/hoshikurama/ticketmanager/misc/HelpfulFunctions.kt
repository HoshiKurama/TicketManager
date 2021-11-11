package com.github.hoshikurama.ticketmanager.misc

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

suspend fun <A, B> Iterable<A>.pMap(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async { f(it) } }.awaitAll()
}

suspend fun <A> Iterable<A>.pForEach(f: suspend (A) -> Unit): Unit = coroutineScope {
    forEach { launch { f(it) } }
}

suspend fun <A> Iterable<A>.pFilter(f: suspend (A) -> Boolean): List<A> = coroutineScope {
    val result = mutableListOf<A>()
    val channel = Channel<Pair<Boolean, A>>(100_000)

    pForEach { channel.send(f(it) to it) }
    channel.close()

    for ((passed, value) in channel)
        if (passed) result.add(value)

    result
}

fun <T> T.notEquals(t: T) = this != t

inline fun <T> tryOrNull(function: () -> T): T? =
    try { function() }
    catch (e: Exception) { e.printStackTrace(); null }