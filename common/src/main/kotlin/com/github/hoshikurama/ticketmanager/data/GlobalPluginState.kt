package com.github.hoshikurama.ticketmanager.data

import com.github.hoshikurama.ticketmanager.misc.MutexControlled
import kotlinx.coroutines.CoroutineDispatcher

class GlobalPluginState(
    val asyncDispatcher: CoroutineDispatcher,
    val mainDispatcher: CoroutineDispatcher,
) {
    val jobCount = MutexControlled(0)
    val pluginLocked = MutexControlled(true)
    val ticketCountMetrics = MutexControlled(0)
}