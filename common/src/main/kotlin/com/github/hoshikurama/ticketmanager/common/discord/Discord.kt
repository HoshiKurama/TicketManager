package com.github.hoshikurama.ticketmanager.common.discord

/*
import com.github.hoshikurama.ticketmanager.common.discord.notifications.DiscordNotification
import discord4j.common.util.Snowflake
import discord4j.core.DiscordClient
import discord4j.core.spec.EmbedCreateSpec
import discord4j.rest.util.Color

class Discord(
    token: String,
    channelID: Long,
) {
    private val channelSnowflake: Snowflake = Snowflake.of(channelID)
    private val client: DiscordClient = DiscordClient.create(token)

    class Settings(
        val notifyOnAssign: Boolean,
        val notifyOnClose: Boolean,
        val notifyOnCloseAll: Boolean,
        val notifyOnComment: Boolean,
        val notifyOnCreate: Boolean,
        val notifyOnReopen: Boolean,
        val notifyOnPriorityChange: Boolean,
        val forwardToProxy: Boolean,
    )
    fun sendMessage(type: DiscordNotification, keywords: CommonKeywords) {
        client.login()
        client.getChannelById(channelSnowflake)
            .createMessage(
                EmbedCreateSpec.builder()
                    .color(Color.of(23, 173, 152))
                    .title("TicketManager")
                    .addField(type.createEmbedField(keywords))
                    .build()
                    .asRequest())
            .subscribe()
    }
}

 */