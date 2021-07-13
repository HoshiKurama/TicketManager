package com.github.hoshikurama.ticketmanager.common

class PluginState {
    val jobCount = MutexControlled(0)
    val pluginLocked = MutexControlled(true)
    val ticketCountMetrics = MutexControlled(0)
}