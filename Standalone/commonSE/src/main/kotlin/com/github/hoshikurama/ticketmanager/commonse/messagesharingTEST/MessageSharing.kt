package com.github.hoshikurama.ticketmanager.commonse.messagesharingTEST

interface MessageSharing {

    fun relay2Hub(data: ByteArray, channelName: String)

    suspend fun unload(trueShutdown: Boolean)
}