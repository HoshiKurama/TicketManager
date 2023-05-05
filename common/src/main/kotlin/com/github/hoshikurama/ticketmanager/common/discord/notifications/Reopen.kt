package com.github.hoshikurama.ticketmanager.common.discord.notifications

/*
import com.github.hoshikurama.ticketmanager.common.CommonKeywords
import com.github.hoshikurama.ticketmanager.common.discord.DiscordTarget
import discord4j.core.spec.EmbedCreateFields

class Reopen(
    private val user: DiscordTarget,
    private val ticketID: String,
) : DiscordNotification {
    override fun encode(): ByteArray = createByteArrayMessage {
        writeUTF(DiscordNotification.Type.REOPEN.toString())
        writeUTF(user.toString())
        writeUTF(ticketID)
    }

    override fun createEmbedField(keywords: CommonKeywords): EmbedCreateFields.Field {
        return EmbedCreateFields.Field.of(
            keywords.discordOnReopen
                .replace("%user%", user.name)
                .replace("%num%", ticketID),
            "⠀",
            false,
        )
    }
}

 */