package com.github.hoshikurama.ticketmanager.misc

import com.github.hoshikurama.ticketmanager.misc.TypeSafeStream.Companion.asTypeSafeStream
import kotlinx.coroutines.flow.*
import java.util.*

fun <T> List<T>.asParallelStream() = asTypeSafeStream().parallel()
fun <T> Sequence<T>.asParallelStream() = asTypeSafeStream().parallel()

// ONLY use these when you MUST use suspending function
suspend fun <T, U> Iterable<T>.parallelFlowMap(f: suspend (T) -> U) = asFlow().buffer(10_000).map(f).toList()
suspend fun <T> Iterable<T>.parallelFlowForEach(f: suspend (T) -> Unit) =  asFlow().buffer(10_000).collect(f)

fun <T: Any> Optional<T?>.unwrapOrNull(): T? = if (isPresent) get() else null

fun <T> T.notEquals(t: T) = this != t

inline fun <T> tryOrNull(function: () -> T): T? =
    try { function() }
    catch (e: Exception) { e.printStackTrace(); null }