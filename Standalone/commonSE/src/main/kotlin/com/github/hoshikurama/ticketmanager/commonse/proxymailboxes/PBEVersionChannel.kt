package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes

import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.base.AbstractHandshakeMailbox
import com.google.common.io.ByteStreams

abstract class PBEVersionChannel : AbstractHandshakeMailbox<Unit, String>() {

    final override fun encodeInput(input: Unit): ByteArray {
        return byteArrayOf() // Empty intentionally
    }

    final override fun decodeOutput(outputArray: ByteArray): String {
        return ByteStreams.newDataInput(outputArray).readUTF()
    }
}