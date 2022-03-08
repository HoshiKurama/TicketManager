package com.github.hoshikurama.ticketmanager.pipeline

import com.github.hoshikurama.ticketmanager.platform.Sender

interface Pipeline {
    fun execute(sender: Sender, args: List<String>)
}