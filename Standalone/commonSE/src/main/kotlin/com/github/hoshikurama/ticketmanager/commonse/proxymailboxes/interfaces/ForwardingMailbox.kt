package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.interfaces

import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Intended for sending one thing and being read by other servers. Only exposes stuff needed by TM:SE
 */
interface ForwardingMailbox<T> {
    val channelListener: ReceiveChannel<T>

    fun forward(t: T)
}