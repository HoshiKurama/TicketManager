package com.github.hoshikurama.ticketmanager.commonse.datas

import com.github.hoshikurama.ticketmanager.commonse.utilities.IntActor

/**
 * This data must be kept around, even between plugin reloads
 */
object GlobalState {
    //@Volatile internal var isPluginLocked: Boolean = true
    @Volatile var databaseType: String? = null

    val ticketCounter = IntActor()

    @Volatile var dataInitializationComplete: Boolean = false
    @Volatile var databaseSelected: Boolean = false
    val isPluginLocked: Boolean
        get() = dataInitializationComplete && databaseSelected
}