package com.github.hoshikurama.ticketmanager.commonse.misc

import kotlinx.coroutines.*
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.Exception

object TMCoroutine {
    private val commonPoolDispatcher = ForkJoinPool.commonPool().asCoroutineDispatcher() //Must initialize first

    private val commonPoolScopeRef = AtomicReference(generateNewScope())
    private val activeJobCountInternal = AtomicInteger(0)

    private val commonPoolScope: CoroutineScope
        get() = commonPoolScopeRef.get()

    internal val activeJobCount
        get() = activeJobCountInternal.get()

    internal fun runAsync(
        function: suspend CoroutineScope.() -> Unit,
        error: (Exception.() -> Unit)? = null,
    ) {
        // No need to add job to anything. Supervisor can unilaterally cancel
        commonPoolScope.launch(commonPoolDispatcher) {
            activeJobCountInternal.getAndIncrement()
            try { function() }
            catch (e: Exception) { if (error != null) error(e) }
            finally { activeJobCountInternal.getAndDecrement() }
        }
    }

    internal fun runAsync(
        function: suspend CoroutineScope.() -> Unit,
    ) {
        // No need to add job to anything. Supervisor can unilaterally cancel
        commonPoolScope.launch(commonPoolDispatcher) {
            activeJobCountInternal.getAndIncrement()
            try { function() }
            catch (e: Exception) { e.printStackTrace() }
            finally { activeJobCountInternal.getAndDecrement() }
        }
    }

    internal fun cancelTasks(reason: String) {
        commonPoolScopeRef.get().cancel(reason)
    }

    internal fun beginNewScope() {
        commonPoolScopeRef.set(generateNewScope())
        activeJobCountInternal.set(0)
    }
    private fun generateNewScope() = CoroutineScope(SupervisorJob() + commonPoolDispatcher)
}