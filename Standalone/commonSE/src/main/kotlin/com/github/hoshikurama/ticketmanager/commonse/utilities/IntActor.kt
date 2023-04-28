package com.github.hoshikurama.ticketmanager.commonse.utilities

import com.github.hoshikurama.ticketmanager.commonse.TMCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.actor

class IntActor {
    @OptIn(ObsoleteCoroutinesApi::class)
    private val actor = TMCoroutine.permanentScope.actor<NumberOperations> {
        var counter = 0

        for (msg in channel) {
            when (msg) {
                is NumberOperations.Increment -> counter += 1
                is NumberOperations.Decrement -> counter -= 1
                is NumberOperations.Get -> msg.response.complete(counter)
                is NumberOperations.Reset -> counter = 0
            }
        }
    }

    suspend fun increment(): Unit = actor.send(NumberOperations.Increment)

    suspend fun decrement(): Unit = actor.send(NumberOperations.Decrement)

    suspend fun reset(): Unit = actor.send(NumberOperations.Reset)

    suspend fun get(): Int {
        val msg = NumberOperations.Get(CompletableDeferred())
        actor.send(msg)
        return msg.response.await()
    }
}

sealed interface NumberOperations {
    object Increment : NumberOperations
    object Decrement : NumberOperations
    object Reset : NumberOperations
    class Get(val response: CompletableDeferred<Int>) : NumberOperations
}