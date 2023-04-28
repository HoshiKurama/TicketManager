package com.github.hoshikurama.ticketmanager.commonse

import com.github.hoshikurama.ticketmanager.commonse.utilities.IntActor
import kotlinx.coroutines.*
import java.util.concurrent.ForkJoinPool

/**
 * Handles all Coroutine stuff for TicketManager. This class is immediately available
 */
object TMCoroutine {
    // Variables
    private val commonPoolDispatcher = ForkJoinPool.commonPool().asCoroutineDispatcher()
    internal val permanentScope = CoroutineScope(commonPoolDispatcher)
    @Volatile private var supervisedScope = generateSupervisedScope()

    private val supervisedScopeCounterActor = IntActor()

    // Actor Stuff

    // Functions
    fun launchGlobal(f: suspend CoroutineScope.() -> Unit): Job {
        return permanentScope.launch {
            try { f() }
            catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun <T> asyncGlobal(f: suspend CoroutineScope.() -> T): Deferred<T> {
        return permanentScope.async { f() }
    }

    fun launchSupervised(f: suspend CoroutineScope.() -> Unit): Job {
        return supervisedScope.launch {
            supervisedScopeCounterActor.increment()
            try { f() }
            catch (e: Exception) { throw e }
            finally { supervisedScopeCounterActor.decrement() }
        }
    }

    internal suspend fun getSupervisedJobCount(): Int {
        return supervisedScopeCounterActor.get()
    }

    internal suspend fun cancelTasks(reason: String) {
        supervisedScope.cancel(reason)
        supervisedScope = generateSupervisedScope()
        supervisedScopeCounterActor.reset()
    }
    private fun generateSupervisedScope() = CoroutineScope(SupervisorJob() + commonPoolDispatcher)
}