package com.github.hoshikurama.ticketmanager

import com.github.hoshikurama.ticketmanager.ticket.BasicTicket
import com.github.hoshikurama.ticketmanager.ticket.toLocaledWord
import dev.kord.common.Color
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.rest.builder.message.EmbedBuilder
import kotlinx.coroutines.CoroutineDispatcher

class Discord(
    val state: DiscordState,
    private val channelSnowflake: Snowflake,
    private val locale: TMLocale,
    private val kord: Kord,

    ) {

    companion object {
        suspend fun create(
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
            asyncDispatcher: CoroutineDispatcher,
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
            val channelSnowflake = Snowflake(channelID)

            val kord = Kord(token) {
                defaultDispatcher = asyncDispatcher
            }

            return Discord(state, channelSnowflake, locale, kord)
        }
    }

    class DiscordState(
        val notifyOnAssign: Boolean,
        val notifyOnClose: Boolean,
        val notifyOnCloseAll: Boolean,
        val notifyOnComment: Boolean,
        val notifyOnCreate: Boolean,
        val notifyOnReopen: Boolean,
        val notifyOnPriorityChange: Boolean,
    )

    suspend fun assignUpdate(user: String, ticketID: String, assignment: String?) {
        val fixedAssignment = assignment ?: locale.miscNobody

        kord.rest.channel.createMessage(channelSnowflake) {
            embed {
                buildBasicEmbed()
                field {
                    name = locale.discordOnAssign
                        .replace("%user%", user)
                        .replace("%num%", ticketID)
                    value = fixedAssignment
                }
            }
        }
    }

    suspend fun closeUpdate(user: String, ticketID: String, comment: String? = null) {
        kord.rest.channel.createMessage(channelSnowflake) {
            embed {
                buildBasicEmbed()
                field {
                    name = locale.discordOnClose
                        .replace("%user%", user)
                        .replace("%num%", ticketID)
                    comment?.also { value = it }
                }
            }
        }
    }

    suspend fun closeAllUpdate(user: String, lower: String, upper: String) {
        kord.rest.channel.createMessage(channelSnowflake) {
            embed {
                buildBasicEmbed()
                field {
                    name = locale.discordOnCloseAll.replace("%user%", user)
                    value = "#$lower - #$upper"
                }
            }
        }
    }

    suspend fun commentUpdate(user: String, ticketID: String, comment: String) {
        kord.rest.channel.createMessage(channelSnowflake) {
            embed {
                buildBasicEmbed()
                field {
                    name = locale.discordOnComment
                        .replace("%user%", user)
                        .replace("%num%", ticketID)
                    value = comment
                }
            }
        }
    }

    suspend fun createUpdate(user: String, ticketID: String, comment: String) {
        kord.rest.channel.createMessage(channelSnowflake) {
            embed {
                buildBasicEmbed()
                field {
                    name = locale.discordOnCreate
                        .replace("%user%", user)
                        .replace("%num%", ticketID)
                    value = comment
                }
            }
        }
    }

    suspend fun reopenUpdate(user: String, ticketID: String) {
        kord.rest.channel.createMessage(channelSnowflake) {
            embed {
                buildBasicEmbed()
                field {
                    name = locale.discordOnReopen
                        .replace("%user%", user)
                        .replace("%num%", ticketID)
                }
            }
        }
    }

    suspend fun priorityChangeUpdate(user: String, ticketID: String, priority: BasicTicket.Priority) {
        kord.rest.channel.createMessage(channelSnowflake) {
            embed {
                buildBasicEmbed()
                field {
                    name = locale.discordOnPriorityChange
                        .replace("%user%", user)
                        .replace("%num%", ticketID)
                    value = priority.toLocaledWord(locale)
                }
            }
        }
    }

    private fun EmbedBuilder.buildBasicEmbed() {
        color = Color(23,173,152)
        title = "TicketManager"
        thumbnail { url = "https://www.spigotmc.org/data/resource_icons/91/91178.jpg?1623276923" }
    }

    suspend fun login() = kord.login()

    suspend fun shutdown() = kord.shutdown()
}

