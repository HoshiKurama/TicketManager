package com.github.hoshikurama.ticketmanager.core

import com.github.hoshikurama.ticketmanager.core.ticket.Ticket
import discord4j.common.util.Snowflake
import discord4j.core.DiscordClient
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.EmbedCreateSpec
import discord4j.discordjson.json.MessageData
import discord4j.rest.util.Color
import reactor.core.publisher.Mono


class Discord private constructor(
    val state: DiscordState,
    private val channelSnowflake: Snowflake,
    private val locale: TMLocale,
    private val client: DiscordClient,
) {

    class DiscordState(
        val notifyOnAssign: Boolean,
        val notifyOnClose: Boolean,
        val notifyOnCloseAll: Boolean,
        val notifyOnComment: Boolean,
        val notifyOnCreate: Boolean,
        val notifyOnReopen: Boolean,
        val notifyOnPriorityChange: Boolean,
    )

    companion object {
        fun create(
            notifyOnAssign: Boolean,
            notifyOnClose: Boolean,
            notifyOnCloseAll: Boolean,
            notifyOnComment: Boolean,
            notifyOnCreate: Boolean,
            notifyOnReopen: Boolean,
            notifyOnPriorityChange: Boolean,
            token: String,
            channelID: Long,
            locale: TMLocale,
        ): Discord {
            val state = DiscordState(
                notifyOnAssign,
                notifyOnClose,
                notifyOnCloseAll,
                notifyOnComment,
                notifyOnCreate,
                notifyOnReopen,
                notifyOnPriorityChange,
            )
            val channelSnowflake = Snowflake.of(channelID)
            return Discord(state, channelSnowflake, locale, DiscordClient.create(token))
        }
    }

    private inline fun createEmbedMessage(buildFunc: EmbedCreateSpec.Builder.() -> EmbedCreateSpec.Builder): Mono<MessageData> {
        return client.getChannelById(channelSnowflake)
            .createMessage(
                EmbedCreateSpec.builder()
                    .color(Color.of(23, 173, 152))
                    .title("TicketManager")
                    .buildFunc()
                    .build()
                    .asRequest()
            )
    }

    fun assignUpdate(user: String, ticketID: String, assignment: String?) {
        val fixedAssignment = assignment ?: locale.miscNobody

        createEmbedMessage {
            addField(
                EmbedCreateFields.Field.of(
                    locale.discordOnAssign
                        .replace("%user%", user)
                        .replace("%num%", ticketID),
                    fixedAssignment,
                    false
                )
            )
        }.subscribe()
    }

    fun closeUpdate(user: String, ticketID: String, comment: String? = null) {
        createEmbedMessage {
            addField(
                EmbedCreateFields.Field.of(
                    locale.discordOnClose
                        .replace("%user%", user)
                        .replace("%num%", ticketID),
                    comment ?: "⠀",
                    false,
                )
            )
        }.subscribe()
    }

    fun closeAllUpdate(user: String, lower: String, upper: String) {
        createEmbedMessage {
            addField(
                EmbedCreateFields.Field.of(
                    locale.discordOnCloseAll.replace("%user%", user),
                    "#$lower - #$upper",
                    false
                )
            )
        }.subscribe()
    }

    fun commentUpdate(user: String, ticketID: String, comment: String) {
        createEmbedMessage {
            addField(
                EmbedCreateFields.Field.of(
                    locale.discordOnComment
                        .replace("%user%", user)
                        .replace("%num%", ticketID),
                    comment,
                    false,
                )
            )
        }.subscribe()
    }

    fun createUpdate(user: String, ticketID: String, comment: String) {
        createEmbedMessage {
            addField(
                EmbedCreateFields.Field.of(
                    locale.discordOnCreate
                        .replace("%user%", user)
                        .replace("%num%", ticketID),
                    comment,
                    false,
                )
            )
        }.subscribe()
    }

    fun reopenUpdate(user: String, ticketID: String) {
        createEmbedMessage {
            addField(
                EmbedCreateFields.Field.of(
                    locale.discordOnReopen
                        .replace("%user%", user)
                        .replace("%num%", ticketID),
                    "⠀",
                    false,
                )
            )
        }.subscribe()
    }

    fun priorityChangeUpdate(user: String, ticketID: String, priority: Ticket.Priority) {
        createEmbedMessage {
            addField(
                EmbedCreateFields.Field.of(
                    locale.discordOnPriorityChange
                        .replace("%user%", user)
                        .replace("%num%", ticketID),
                    priority.toLocaledWord(locale),
                    false
                )
            )
        }.subscribe()
    }
}

