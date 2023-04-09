package com.github.hoshikurama.ticketmanager.commonse.datas

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * This data must be kept around, even between plugin reloads
 */
class GlobalPluginState {
    val coroutineJobCount = AtomicInteger(0)
    val pluginLocked = AtomicBoolean(true)
    val isDatabaseLoaded = AtomicBoolean(false)
    val ticketCountMetrics = AtomicInteger(0)
}