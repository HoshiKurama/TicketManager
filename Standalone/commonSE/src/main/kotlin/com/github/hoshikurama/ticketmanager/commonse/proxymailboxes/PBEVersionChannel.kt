package com.github.hoshikurama.ticketmanager.commonse.proxymailboxes

import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.base.AbstractHandshakeMailbox
import com.google.common.io.ByteStreams

abstract class PBEVersionChannel : AbstractHandshakeMailbox<String, String>() {

    final override fun encodeInput(input: String): ByteArray {
        return ByteStreams.newDataOutput()
            .apply { writeUTF(input) }
            .toByteArray()
    }

    final override fun decodeOutput(outputArray: ByteArray): String {
        return ByteStreams.newDataInput(outputArray).readUTF()
    }
}