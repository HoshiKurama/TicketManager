package com.github.hoshikurama.ticketmanager.common.discord.notifications

import com.github.hoshikurama.ticketmanager.common.CommonKeywords
import com.github.hoshikurama.ticketmanager.common.discord.DiscordTarget
import discord4j.core.spec.EmbedCreateFields

class CloseAll(
    private val user: DiscordTarget,
    private val lower: String,
    private val upper: String,
) : DiscordNotification {
    override fun encode(): ByteArray = createByteArrayMessage {
        writeUTF(DiscordNotification.Type.CLOSEALL.toString())
        writeUTF(user.toString())
        writeUTF(lower)
        writeUTF(upper)
    }

    override fun createEmbedField(keywords: CommonKeywords): EmbedCreateFields.Field {
        return EmbedCreateFields.Field.of(
            keywords.discordOnCloseAll.replace("%user%", user.name),
            "#$lower - #$upper",
            false
        )
    }
}