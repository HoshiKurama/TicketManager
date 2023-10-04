package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.interfaces

/**
 * Intended for when a single input and output are expected. Only exposes stuff needed by TM:SE
 */
interface HandshakeMailbox<Input, Output> {
    suspend fun request(input: Input): Output
}