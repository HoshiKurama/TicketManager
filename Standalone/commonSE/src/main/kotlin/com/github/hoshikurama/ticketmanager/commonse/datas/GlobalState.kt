package com.github.hoshikurama.ticketmanager.commonse.datas

import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.utilities.IntActor

/**
 * This data must be kept around, even between plugin reloads
 */
object GlobalState {
    @Volatile internal var isPluginLocked: Boolean = true

    internal val ticketCounter = IntActor()
}