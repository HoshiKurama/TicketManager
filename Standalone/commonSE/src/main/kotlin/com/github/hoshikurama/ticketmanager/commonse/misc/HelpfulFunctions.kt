package com.github.hoshikurama.ticketmanager.commonse.misc

import com.github.hoshikurama.ticketmanager.commonse.misc.TypeSafeStream.Companion.asTypeSafeStream
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import java.util.*

@Suppress("Unused")
fun <T> List<T>.asParallelStream() = asTypeSafeStream().parallel()
@Suppress("Unused")
fun <T> Sequence<T>.asParallelStream() = asTypeSafeStream().parallel()

// ONLY use these when you MUST use suspending function
@Suppress("Unused")
suspend fun <T, U> Iterable<T>.parallelFlowMap(f: suspend (T) -> U) = asFlow().buffer(10_000).map(f).toList()
@Suppress("Unused")
suspend fun <T> Iterable<T>.parallelFlowForEach(f: suspend (T) -> Unit) =  asFlow().buffer(10_000).collect(f)

@Suppress("Unused")
fun <T: Any> Optional<T?>.unwrapOrNull(): T? = if (isPresent) get() else null

@Suppress("Unused")
fun <T> T.notEquals(t: T) = this != t

@Suppress("Unused")
inline fun <T> tryOrNull(function: () -> T): T? =
    try { function() }
    catch (e: Exception) { e.printStackTrace(); null }