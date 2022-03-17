package com.github.hoshikurama.ticketmanager.data
/*

import com.github.hoshikurama.ticketmanager.ticket.BasicTicket
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.nio.charset.StandardCharsets
import java.util.*

abstract class VelocityDataMapper(
    val server: UUID,
    val messageType: MessageType,
    val rawData: String,
) {
    abstract fun parseToByteArray(): ByteArray
    abstract fun decodeFromByteArray(): VelocityDataMapper
}

enum class MessageType {
    ASSIGN,
    CLOSENOCOMMENT,
    CLOSEWITHCOMMENT,
    MASSCLOSE,
    COMMENT,
    CREATE,
    SETPRIORITY,
    REOPEN,
}

// silent
// creatorAlertPerm
// massNotifyPerm

// Sender -> senderUUID,
// ticket -> ticketCreatorUUID
// creator -> creatorUUID


class Assign(
    val silent: Boolean,
    val senderUUID: UUID?,
    val ticketCreatorUUID: UUID?,
    val assignmentID: String,
    val shownAssignment: String,
    val userName: String,
) {
    fun encode(): String = Gson().toJson(this)

    companion object {
        fun decode(str: String): Assign = Gson().fromJson(str, Assign::class.java)
    }
}

class CloseWithComment(
    val silent: Boolean,
    val senderUUID: UUID?,
    val ticketCreatorUUID: UUID?,
    val ticketID: String,
    val creatorName: String,
    val message: String,
) {
    fun encode(): String = Gson().toJson(this)

    companion object {
        fun decode(str: String): CloseWithComment = Gson().fromJson(str, CloseWithComment::class.java)
    }
}

class CloseNoComment(
    val silent: Boolean,
    val senderUUID: UUID?,
    val ticketCreatorUUID: UUID?,
    val ticketID: String,
    val creatorName: String,
) {
    fun encode(): String = Gson().toJson(this)

    companion object {
        fun decode(str: String): CloseNoComment = Gson().fromJson(str, CloseNoComment::class.java)
    }
}

class MassClose(
    val silent: Boolean,
    val senderUUID: UUID?,
    val ticketCreatorUUID: UUID?,
    val lowerID: String,
    val upperID: String,
    val senderName: String,
) {
    fun encode(): String = Gson().toJson(this)

    companion object {
        fun decode(str: String): MassClose= Gson().fromJson(str, MassClose::class.java)
    }
}

class Comment(
    val silent: Boolean,
    val senderUUID: UUID?,
    val ticketCreatorUUID: UUID?,
    val ticketID: String,
    val userName: String,
    val message: String,
) {
    fun encode(): String = Gson().toJson(this)

    companion object {
        fun decode(str: String): Comment = Gson().fromJson(str, Comment::class.java)
    }
}

class Create(
    val silent: Boolean,
    val senderUUID: UUID?,
    val ticketCreatorUUID: UUID?,
    val ticketID: String,
    val userName: String,
    val message: String,
) {
    fun encode(): String = Gson().toJson(this)

    companion object {
        fun decode(str: String): Create = Gson().fromJson(str, Create::class.java)
    }
}

class SetPriority(
    val silent: Boolean,
    val senderUUID: UUID?,
    val ticketCreatorUUID: UUID?,
    val ticketID: String,
    val newPriority: BasicTicket.Priority,
    val userName: String,
) {
    fun encode(): String = Gson().toJson(this)

    companion object {
        fun decode(str: String): SetPriority = Gson().fromJson(str, SetPriority::class.java)
    }
}

class Reopen(
    val silent: Boolean,
    val senderUUID: UUID?,
    val ticketCreatorUUID: UUID?,
    val ticketID: String,
    val userName: String,
) {
    fun encode(): String = Gson().toJson(this)

    companion object {
        fun decode(str: String): Reopen = Gson().fromJson(str, Reopen::class.java)
    }
}


 */