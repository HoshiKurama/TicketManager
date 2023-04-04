package com.github.hoshikurama.ticketmanager.commonse.misc

import com.github.hoshikurama.ticketmanager.commonse.misc.TypeSafeStream.Companion.asTypeSafeStream
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.asDeferred
import java.util.*
import java.util.concurrent.CompletableFuture

@Suppress("Unused")
fun <T> List<T>.asParallelStream() = asTypeSafeStream().parallel()
@Suppress("Unused")
fun <T> Sequence<T>.asParallelStream() = asTypeSafeStream().parallel()

fun <T: Any> Optional<T?>.unwrapOrNull(): T? = if (isPresent) get() else null
fun <T: Any> Optional<T?>.tK(): T? = unwrapOrNull()


@Suppress("Unused")
fun <T> T.notEquals(t: T) = this != t

@Suppress("Unused")
inline fun <T> tryOrNull(function: () -> T): T? =
    try { function() }
    catch (e: Exception) { e.printStackTrace(); null }

suspend inline fun <T> CompletableFuture<T>.asDeferredThenAwait(): T = asDeferred().await()