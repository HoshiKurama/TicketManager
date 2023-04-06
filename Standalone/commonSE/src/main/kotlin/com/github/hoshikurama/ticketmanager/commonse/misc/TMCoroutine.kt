package com.github.hoshikurama.ticketmanager.commonse.misc

import kotlinx.coroutines.*
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.Exception

object TMCoroutine {
    private val commonPoolDispatcher = ForkJoinPool.commonPool().asCoroutineDispatcher() //Must initialize first

    private val withSupervisorRef = AtomicReference(generateNewScope())
    private val withoutSupervisorRef = AtomicReference(CoroutineScope(commonPoolDispatcher))

    private val activeJobCountInternal = AtomicInteger(0)

    private val withSupervisor: CoroutineScope
        get() = withSupervisorRef.get()
    private val withoutSupervisor: CoroutineScope
        get() = withoutSupervisorRef.get()

    internal val activeJobCount
        get() = activeJobCountInternal.get()


    internal fun asyncNoSupervisor(
        function: suspend CoroutineScope.() -> Unit,
    ): Job {
        // NOTE: Coroutine must also agree to a cancellation
        return withoutSupervisor.async {
            activeJobCountInternal.getAndIncrement()
            try { function() }
            catch (e: Exception) { e.printStackTrace() }
            finally { activeJobCountInternal.getAndDecrement() }
        }
    }
    internal fun runAsync(
        function: suspend CoroutineScope.() -> Unit,
    ) {
        // No need to add job to anything. Supervisor can unilaterally cancel
        withSupervisor.launch {
            activeJobCountInternal.getAndIncrement()
            try { function() }
            catch (e: Exception) { e.printStackTrace() }
            finally { activeJobCountInternal.getAndDecrement() }
        }
    }

    internal fun cancelTasks(reason: String) {
        withSupervisorRef.get().cancel(reason)
    }

    internal fun beginNewScope() {
        withSupervisorRef.set(generateNewScope())
        activeJobCountInternal.set(0)
    }
    private fun generateNewScope() = CoroutineScope(SupervisorJob() + commonPoolDispatcher)
}