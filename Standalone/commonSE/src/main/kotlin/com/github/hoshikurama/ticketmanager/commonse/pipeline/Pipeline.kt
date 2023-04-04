package com.github.hoshikurama.ticketmanager.commonse.pipeline

import com.github.hoshikurama.ticketmanager.commonse.platform.Sender

interface Pipeline {
    fun executeAsync(sender: Sender, args: List<String>)
}