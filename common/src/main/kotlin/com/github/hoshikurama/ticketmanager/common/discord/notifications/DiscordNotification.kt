package com.github.hoshikurama.ticketmanager.common.discord.notifications

import com.github.hoshikurama.ticketmanager.common.CommonKeywords
import com.github.hoshikurama.ticketmanager.common.discord.decodeToDiscordTarget
import com.google.common.io.ByteArrayDataOutput
import com.google.common.io.ByteStreams
import discord4j.core.spec.EmbedCreateFields

interface DiscordNotification {
    
    enum class Type {
        ASSIGN, CLOSE, CLOSEALL, COMMENT, CREATE, REOPEN, CHANGEPRIORITY
    }

    fun encode(): ByteArray
    fun createEmbedField(keywords: CommonKeywords): EmbedCreateFields.Field

    companion object {
        fun decode(array: ByteArray, keywords: CommonKeywords): DiscordNotification {
            @Suppress("UnstableApiUsage")
            val input = ByteStreams.newDataInput(array)

            fun computeUser() = decodeToDiscordTarget(keywords, input.readUTF())

            return when (input.readUTF().run(Type::valueOf)) {
                Type.ASSIGN -> Assign(
                    user = computeUser(),
                    ticketID = input.readUTF(),
                    assignment = computeUser(),
                )
                Type.CLOSE -> Close(
                    user = computeUser(),
                    ticketID = input.readUTF(),
                    comment = input.readUTF(),
                )
                Type.CLOSEALL -> CloseAll(
                    user = computeUser(),
                    lower = input.readUTF(),
                    upper = input.readUTF(),
                )
                Type.COMMENT -> Comment(
                    user = computeUser(),
                    ticketID = input.readUTF(),
                    comment = input.readUTF(),
                )
                Type.CREATE -> Create(
                    user = computeUser(),
                    ticketID = input.readUTF(),
                    comment = input.readUTF(),
                )
                Type.REOPEN -> Reopen(
                    user = computeUser(),
                    ticketID = input.readUTF(),
                )
                Type.CHANGEPRIORITY -> ChangePriority(
                    user = computeUser(),
                    ticketID = input.readUTF(),
                    priorityByte = input.readInt()
                )
            }
        }
    }
}

@Suppress("UnstableApiUsage")
inline fun createByteArrayMessage(f: ByteArrayDataOutput.() -> Unit): ByteArray = ByteStreams.newDataOutput()
    .apply(f)
    .run(ByteArrayDataOutput::toByteArray)