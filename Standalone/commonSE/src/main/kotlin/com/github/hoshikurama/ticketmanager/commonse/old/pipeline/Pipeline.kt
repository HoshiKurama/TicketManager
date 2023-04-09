package com.github.hoshikurama.ticketmanager.commonse.old.pipeline

import com.github.hoshikurama.ticketmanager.commonse.old.platform.Sender

interface Pipeline {
    fun executeAsync(sender: Sender, args: List<String>)
}