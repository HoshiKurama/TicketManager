package com.github.hoshikurama.ticketmanager.commonse.data

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class GlobalPluginState {
    val jobCount = AtomicInteger(0)
    val pluginLocked = AtomicBoolean(true)
    val ticketCountMetrics = AtomicInteger(0)
}