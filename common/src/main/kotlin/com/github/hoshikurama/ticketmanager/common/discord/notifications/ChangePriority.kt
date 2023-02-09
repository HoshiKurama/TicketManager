package com.github.hoshikurama.ticketmanager.common.discord.notifications

import com.github.hoshikurama.ticketmanager.common.CommonKeywords
import com.github.hoshikurama.ticketmanager.common.discord.DiscordTarget
import discord4j.core.spec.EmbedCreateFields

class ChangePriority(
    private val user: DiscordTarget,
    private val ticketID: String,
    private val priorityByte: Int,
) : DiscordNotification {
    override fun encode(): ByteArray = createByteArrayMessage {
        writeUTF(DiscordNotification.Type.CHANGEPRIORITY.toString())
        writeUTF(user.toString())
        writeUTF(ticketID)
        writeByte(priorityByte)
    }

    override fun createEmbedField(keywords: CommonKeywords): EmbedCreateFields.Field {
        return EmbedCreateFields.Field.of(
            keywords.discordOnPriorityChange
                .replace("%user%", user.name)
                .replace("%num%", ticketID),
            when (priorityByte) {
                1 -> keywords.priorityLowest
                2 -> keywords.priorityLow
                3 -> keywords.priorityNormal
                4 -> keywords.priorityHigh
                5 -> keywords.priorityHighest
                else -> throw Exception("Invalid Priority Level while attempting to create Discord embed field")},
            false
        )
    }
}