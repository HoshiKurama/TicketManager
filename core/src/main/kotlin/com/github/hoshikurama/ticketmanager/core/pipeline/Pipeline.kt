package com.github.hoshikurama.ticketmanager.core.pipeline

import com.github.hoshikurama.ticketmanager.core.platform.Sender

interface Pipeline {
    fun execute(sender: Sender, args: List<String>)
}