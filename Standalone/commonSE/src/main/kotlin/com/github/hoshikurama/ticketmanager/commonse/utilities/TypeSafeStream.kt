package com.github.hoshikurama.ticketmanager.commonse.utilities

import java.util.*
import java.util.stream.Stream
import kotlin.streams.asSequence
import kotlin.streams.asStream

class TypeSafeStream<T>(val stream: Stream<T>) {
    companion object {
        @Suppress("UNUSED") fun <T> List<T>.asTypeSafeStream() = TypeSafeStream(stream())
        @Suppress("UNUSED") fun <T> Sequence<T>.asTypeSafeStream() = TypeSafeStream(asStream())
        @Suppress("UNUSED") fun <T> MutableCollection<T>.asTypeSafeStream() = TypeSafeStream(stream())
    }

    // Stream type and Stream conversions
    @Suppress("UNUSED") fun parallel() = TypeSafeStream(stream.parallel() as Stream<T>)
    @Suppress("UNUSED") fun sequential() = TypeSafeStream(stream.sequential() as Stream<T>)
    @Suppress("UNUSED") fun asSequence() = stream.asSequence()


    // Stream Collection to Kotlin collections
    @Suppress("UNUSED") fun toList(): List<T> = stream.toList()
    @Suppress("UNUSED") fun toMutableList(): MutableList<T> = stream.toList()
    @Suppress("UNUSED") fun toSet(): Set<T> = toList().toSet()
    @Suppress("UNUSED") fun toMutableSet(): MutableSet<T> = toMutableList().toMutableSet()


    // Core operations that wrap over Streams
    @Suppress("UNUSED") fun allMatch(predicate: (T) -> Boolean) = stream.allMatch(predicate)

    @Suppress("UNUSED") fun anyMatch(predicate: (T) -> Boolean) = stream.anyMatch(predicate)

    @Suppress("UNUSED") operator fun plus(other: TypeSafeStream<T>) = TypeSafeStream(Stream.concat(stream, other.stream) as Stream<T>)

    @Suppress("UNUSED") fun count() = stream.count()

    @Suppress("UNUSED") fun distinct() = TypeSafeStream(stream.distinct() as Stream<T>)

    @Suppress("UNUSED") fun filter(predicate: (T) -> Boolean) = TypeSafeStream(stream.filter(predicate) as Stream<T>)

    @Suppress("UNUSED") fun findAny() = stream.findAny().unwrapOptionalOrNull()

    @Suppress("UNUSED") fun findFirst() = stream.findFirst().unwrapOptionalOrNull()

    @Suppress("UNUSED", "UNCHECKED_CAST") fun <R> flatMap(transform: (T) -> Stream<R>) = TypeSafeStream(stream.flatMap(transform) as Stream<Stream<R>>)

    @Suppress("UNUSED") fun forEach(action: (T) -> Unit) = stream.forEach(action)

    @Suppress("UNUSED") fun forEachOrdered(action: (T) -> Unit) = stream.forEachOrdered(action)

    @Suppress("UNUSED") fun limit(maxSize: Long) = TypeSafeStream(stream.limit(maxSize) as Stream<T>)

    @Suppress("UNUSED") fun <R> map(transform: (T) -> R) = TypeSafeStream(stream.map(transform) as Stream<R>)

    @Suppress("UNUSED") fun max(comparator: Comparator<T>) = stream.max(comparator).unwrapOptionalOrNull()

    @Suppress("UNUSED") fun min(comparator: Comparator<T>) = stream.min(comparator).unwrapOptionalOrNull()

    @Suppress("UNUSED") fun noneMatch(predicate: (T) -> Boolean) = stream.noneMatch(predicate)

    @Suppress("UNUSED") fun peek(action: (T) -> Unit) = TypeSafeStream(stream.peek(action) as Stream<T>)

    @Suppress("UNUSED") fun reduce(accumulator: (T,T) -> T) = stream.reduce(accumulator).unwrapOptionalOrNull()

    @Suppress("UNUSED") fun reduce(identity: T, accumulator: (T,T) -> T) = stream.reduce(identity, accumulator) ?: null

    @Suppress("UNUSED") fun reduce(identity: T, accumulator: (T, T) -> T, combiner: (T, T) -> T) = stream.reduce(identity, accumulator, combiner) ?: null

    @Suppress("UNUSED") fun skip(n: Long) = TypeSafeStream(stream.skip(n) as Stream<T>)

    @Suppress("UNUSED") fun sorted() = TypeSafeStream(stream.sorted() as Stream<T>)

    @Suppress("UNUSED") fun sorted(comparator: Comparator<T>) = TypeSafeStream(stream.sorted(comparator) as Stream<T>)
}

private fun <T> Optional<T>.unwrapOptionalOrNull() = if (isPresent) get() else null

// Unique things added in
@Suppress("UNUSED", "UNCHECKED_CAST") // I'm so sorry... I have no clue how to put this into the class
fun <T: Any> TypeSafeStream<T?>.filterNotNull() = TypeSafeStream(stream.filter { it != null } as Stream<T>)

@Suppress("UNUSED", "UNCHECKED_CAST") // I'm so sorry... I have no clue how to put this into the class
fun <T, R: Any> TypeSafeStream<T>.mapNotNull(transform: (T) -> R?) = TypeSafeStream(stream.map(transform).filter { it != null } as Stream<R>)

@Suppress("UNUSED", "UNCHECKED_CAST")
inline fun <reified I, T> TypeSafeStream<T>.filterIsInstance() = TypeSafeStream(stream.filter { it is I } as Stream<I>)