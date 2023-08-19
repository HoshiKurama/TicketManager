package com.github.hoshikurama.ticketmanager.commonse.datas

import com.github.hoshikurama.ticketmanager.api.common.IntActor

/**
 * This data must be kept around, even between plugin reloads
 */
object GlobalState {
    @Volatile var databaseType: String? = null

    val ticketCounter = IntActor()

    @Volatile var dataInitializationComplete: Boolean = false
    @Volatile var databaseSelected: Boolean = false

    val isPluginLocked: Boolean
        get() = !dataInitializationComplete || !databaseSelected
}