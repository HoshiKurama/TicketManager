package com.github.hoshikurama.ticketmanager.common.discord.notifications
/*
import com.github.hoshikurama.ticketmanager.common.CommonKeywords
import com.github.hoshikurama.ticketmanager.common.discord.DiscordTarget
import discord4j.core.spec.EmbedCreateFields

class Assign(
    private val user: DiscordTarget,
    private val ticketID: String,
    private val assignment: DiscordTarget,
) : DiscordNotification {
  override fun encode(): ByteArray = createByteArrayMessage {
      writeUTF(DiscordNotification.Type.ASSIGN.toString())
      writeUTF(user.toString())
      writeUTF(ticketID)
      writeUTF(assignment.toString())
  }

    override fun createEmbedField(keywords: CommonKeywords): EmbedCreateFields.Field {
        return EmbedCreateFields.Field.of(
            keywords.discordOnAssign
                .replace("%user%", user.name)
                .replace("%num%", ticketID),
            assignment.name,
            false,
        )
    }
}

 */