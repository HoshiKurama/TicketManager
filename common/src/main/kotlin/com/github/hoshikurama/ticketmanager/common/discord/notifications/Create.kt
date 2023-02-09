package com.github.hoshikurama.ticketmanager.common.discord.notifications

import com.github.hoshikurama.ticketmanager.common.CommonKeywords
import com.github.hoshikurama.ticketmanager.common.discord.DiscordTarget
import discord4j.core.spec.EmbedCreateFields
class Create(
    private val user: DiscordTarget,
    private val ticketID: String,
    private val comment: String,
) : DiscordNotification {
    override fun encode(): ByteArray = createByteArrayMessage {
        writeUTF(DiscordNotification.Type.CREATE.toString())
        writeUTF(user.toString())
        writeUTF(ticketID)
        writeUTF(comment)
    }

    override fun createEmbedField(keywords: CommonKeywords): EmbedCreateFields.Field {
        return EmbedCreateFields.Field.of(
            keywords.discordOnCreate
                .replace("%user%", user.name)
                .replace("%num%", ticketID),
            comment,
            false,
        )
    }
}