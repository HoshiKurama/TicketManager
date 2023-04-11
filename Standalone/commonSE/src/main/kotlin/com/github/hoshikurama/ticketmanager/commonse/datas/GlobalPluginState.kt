package com.github.hoshikurama.ticketmanager.commonse.datas

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * This data must be kept around, even between plugin reloads
 */
object GlobalPluginState {
    val coroutineJobCount = AtomicInteger(0)
    val pluginLocked = AtomicBoolean(true)

    var isDatabaseLoaded: Boolean
        get() = isDatabaseLoadedInternal.get()
        set(value) = isDatabaseLoadedInternal.set(value)

    private val isDatabaseLoadedInternal = AtomicBoolean(false)

    val ticketCountMetrics = AtomicInteger(0)
}