package com.github.hoshikurama.ticketmanager.common

import com.google.common.io.ByteStreams

object ProxyUpdate {
    fun encodeProxyMsg(serverName: String): ByteArray {
        val array = ByteStreams.newDataOutput()
        array.writeUTF(serverName)
        return array.toByteArray()
    }

    fun decodeProxyMsg(input: ByteArray): String {
        return ByteStreams.newDataInput(input).readUTF()
    }

    fun encodeServerMsg(curVer: String, latestVer: String): ByteArray {
        val array = ByteStreams.newDataOutput()
        array.writeUTF(curVer)
        array.writeUTF(latestVer)
        return array.toByteArray()
    }

    fun decodeServerMsg(input: ByteArray): ProxyUpdateDecodedServer {
        val array = ByteStreams.newDataInput(input)
        val curVer = array.readUTF()
        val latestVer = array.readUTF()
        return ProxyUpdateDecodedServer(curVer, latestVer)
    }
}

data class ProxyUpdateDecodedServer(val curVer: String, val latestVer: String)