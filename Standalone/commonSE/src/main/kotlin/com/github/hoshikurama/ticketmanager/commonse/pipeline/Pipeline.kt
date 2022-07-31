package com.github.hoshikurama.ticketmanager.commonse.pipeline

import com.github.hoshikurama.ticketmanager.commonse.platform.Sender

interface Pipeline {
    fun execute(sender: Sender, args: List<String>)
}