package com.github.hoshikurama.ticketmanager.commonse.pipeline

import com.github.hoshikurama.ticketmanager.common.randServerIdentifier
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.misc.*
import com.github.hoshikurama.ticketmanager.commonse.platform.Sender
import com.github.hoshikurama.ticketmanager.commonse.ticket.Creator
import com.github.hoshikurama.ticketmanager.commonse.ticket.Ticket
import com.google.common.io.ByteArrayDataInput
import com.google.common.io.ByteArrayDataOutput
import com.google.common.io.ByteStreams
import net.kyori.adventure.text.Component

sealed class Notification {
    abstract val sendCreatorMSG: Boolean
    abstract val sendSenderMSG: Boolean
    abstract val sendMassNotify: Boolean
    abstract val sender: Creator
    abstract val creator: Creator
    abstract val generateCreatorMSG: ((TMLocale) -> Component)
    abstract val generateSenderMSG: ((TMLocale) -> Component)
    abstract val generateMassNotify: ((TMLocale) -> Component)

    abstract val creatorAlertPerm: String
    abstract val massNotifyPerm: String

    enum class MessageType {
        ASSIGN, CLOSEWITHCOMMENT, CLOSEWITHOUTCOMMENT, MASSCLOSE, COMMENT, CREATE, REOPEN, SETPRIORITY,
    }

    fun generateStandardOutput(messageType: MessageType): ByteArrayDataOutput {
        @Suppress("UnstableApiUsage")
        val output = ByteStreams.newDataOutput()

        output.writeUTF(randServerIdentifier.toString())
        output.writeUTF(messageType.name)
        output.writeUTF(sender.toString())
        output.writeUTF(creator.toString())
        output.writeBoolean(sendCreatorMSG)
        output.writeBoolean(sendSenderMSG)
        output.writeBoolean(sendMassNotify)

        return output
    }

    abstract fun encodeForProxy(): ByteArray

    // Specific Classes Begin Here //
    class Assign(
        override val sendCreatorMSG: Boolean,
        override val sendSenderMSG: Boolean,
        override val sendMassNotify: Boolean,
        override val sender: Creator,
        override val creator: Creator,

        private val argID: String,
        private val argAssigned: String,
        private val argUser: String,

        private val argAssignedIsConsole: Boolean,
        private val argUserIsConsole: Boolean,
        private val argAssignedIsNobody: Boolean
    ) : Notification() {

        override val creatorAlertPerm = "ticketmanager.notify.change.assign"
        override val massNotifyPerm = "ticketmanager.notify.massNotify.assign"

        override val generateCreatorMSG = { locale: TMLocale -> locale.notifyTicketModificationEvent.parseMiniMessage("id" templated argID) }
        override val generateMassNotify = { locale: TMLocale ->
            locale.notifyTicketAssignEvent.parseMiniMessage(
                "user" templated (when {
                    argAssignedIsNobody -> locale.miscNobody
                    argAssignedIsConsole -> locale.consoleName
                    else -> argAssigned
                }),
                "id" templated argID,
                "assigned" templated (if (argAssignedIsConsole) locale.consoleName else argAssigned),
            )
        }
        override val generateSenderMSG = { locale: TMLocale ->
            locale.notifyTicketAssignSuccess.parseMiniMessage(
                "id" templated argID,
                "assigned" templated (if (argAssignedIsConsole) locale.consoleName else argAssigned)
            )
        }

        override fun encodeForProxy(): ByteArray {
            val output = generateStandardOutput(MessageType.ASSIGN)

            output.writeUTF(argID)
            output.writeUTF(argAssigned)
            output.writeUTF(argUser)
            output.writeBoolean(argAssignedIsConsole)
            output.writeBoolean(argUserIsConsole)
            output.writeBoolean(argAssignedIsNobody)

            return output.toByteArray()
        }

        companion object {
            private const val massNotifyPerm = "ticketmanager.notify.massNotify.assign"

            fun build(silent: Boolean, ticket: Ticket, sender: Sender, argID: String, argAssigned: String, argUser: String, argAssignedIsConsole: Boolean, argUserIsConsole: Boolean, argAssignedIsNobody: Boolean): Assign {
                val (sendMassNotify, sendSenderMSG, sendCreatorMSG) = generateSendVars(silent, ticket, sender, massNotifyPerm)
                return Assign(sendCreatorMSG, sendSenderMSG, sendMassNotify, sender.toCreator(), ticket.creator, argID, argAssigned, argUser, argAssignedIsConsole, argUserIsConsole, argAssignedIsNobody)
            }

            fun fromByteArray(input: ByteArrayDataInput): Assign {
                val (sender, creator, sendCreatorMSG, sendSenderMSG, sendMassNotify) = StandardInput(input)
                return Assign(sendCreatorMSG, sendSenderMSG, sendMassNotify, sender, creator,
                    argID = input.readUTF(),
                    argAssigned = input.readUTF(),
                    argUser = input.readUTF(),
                    argAssignedIsConsole = input.readBoolean(),
                    argUserIsConsole = input.readBoolean(),
                    argAssignedIsNobody = input.readBoolean(),
                )
            }
        }
    }

    class CloseWithComment(
        override val sendCreatorMSG: Boolean,
        override val sendSenderMSG: Boolean,
        override val sendMassNotify: Boolean,
        override val sender: Creator,
        override val creator: Creator,

        private val argID: String,
        private val argUser: String,
        private val argMessage: String,

        private val argUserIsConsole: Boolean
    ) : Notification() {

        override val creatorAlertPerm = "ticketmanager.notify.change.close"
        override val massNotifyPerm = "ticketmanager.notify.massNotify.close"

        override val generateCreatorMSG = { locale: TMLocale -> locale.notifyTicketModificationEvent.parseMiniMessage("id" templated argID) }
        override val generateSenderMSG = { locale: TMLocale -> locale.notifyTicketCloseWCommentSuccess.parseMiniMessage("id" templated argID) }
        override val generateMassNotify = { locale: TMLocale ->
            locale.notifyTicketCloseWCommentEvent.parseMiniMessage(
                "user" templated (if (argUserIsConsole) locale.consoleName else argUser),
                "id" templated argID,
                "message" templated argMessage,
            )
        }

        override fun encodeForProxy(): ByteArray {
            val output = generateStandardOutput(MessageType.CLOSEWITHCOMMENT)

            output.writeUTF(argID)
            output.writeUTF(argUser)
            output.writeUTF(argMessage)
            output.writeBoolean(argUserIsConsole)

            return output.toByteArray()
        }

        companion object {
            private const val massNotifyPerm = "ticketmanager.notify.massNotify.close"

            fun build(silent: Boolean, ticket: Ticket, sender: Sender, argID: String, argUser: String, argMessage: String, argUserIsConsole: Boolean): CloseWithComment {
                val (sendMassNotify, sendSenderMSG, sendCreatorMSG) = generateSendVars(silent, ticket, sender, massNotifyPerm)
                return CloseWithComment(sendCreatorMSG, sendSenderMSG, sendMassNotify, sender.toCreator(), ticket.creator, argID, argUser, argMessage, argUserIsConsole)
            }

            fun fromByteArray(input: ByteArrayDataInput): CloseWithComment {
                val (sender, creator, sendCreatorMSG, sendSenderMSG, sendMassNotify) = StandardInput(input)
                return CloseWithComment(sendCreatorMSG, sendSenderMSG, sendMassNotify, sender, creator,
                    argID = input.readUTF(),
                    argUser = input.readUTF(),
                    argMessage = input.readUTF(),
                    argUserIsConsole = input.readBoolean(),
                )
            }
        }
    }

    class CloseWithoutComment(
        override val sendCreatorMSG: Boolean,
        override val sendSenderMSG: Boolean,
        override val sendMassNotify: Boolean,
        override val sender: Creator,
        override val creator: Creator,

        private val argID: String,
        private val argUser: String,
        private val argUserIsConsole: Boolean,
    ) : Notification() {

        override val creatorAlertPerm = "ticketmanager.notify.change.close"
        override val massNotifyPerm = "ticketmanager.notify.massNotify.close"

        override val generateCreatorMSG = { locale: TMLocale -> locale.notifyTicketModificationEvent.parseMiniMessage("id" templated argID) }
        override val generateSenderMSG = { locale: TMLocale -> locale.notifyTicketCloseSuccess.parseMiniMessage("id" templated argID) }
        override val generateMassNotify = { locale: TMLocale ->
            locale.notifyTicketCloseEvent.parseMiniMessage(
                "user" templated (if (argUserIsConsole) locale.consoleName else argUser),
                "id" templated argID,
            )
        }

        override fun encodeForProxy(): ByteArray {
            val output = generateStandardOutput(MessageType.CLOSEWITHOUTCOMMENT)

            output.writeUTF(argID)
            output.writeUTF(argUser)
            output.writeBoolean(argUserIsConsole)

            return output.toByteArray()
        }

        companion object {
            private const val massNotifyPerm = "ticketmanager.notify.massNotify.close"

            fun build(silent: Boolean, ticket: Ticket, sender: Sender, argID: String, argUser: String, argUserIsConsole: Boolean): CloseWithoutComment {
                val (sendMassNotify, sendSenderMSG, sendCreatorMSG) = generateSendVars(silent, ticket, sender, massNotifyPerm)
                return CloseWithoutComment(sendCreatorMSG, sendSenderMSG, sendMassNotify, sender.toCreator(), ticket.creator, argID, argUser, argUserIsConsole)
            }

            fun fromByteArray(input: ByteArrayDataInput): CloseWithoutComment {
                val (sender, creator, sendCreatorMSG, sendSenderMSG, sendMassNotify) = StandardInput(input)
                return CloseWithoutComment(sendCreatorMSG, sendSenderMSG, sendMassNotify, sender, creator,
                    argID = input.readUTF(),
                    argUser = input.readUTF(),
                    argUserIsConsole = input.readBoolean(),
                )
            }
        }
    }

    class MassClose(
        override val sendCreatorMSG: Boolean,
        override val sendSenderMSG: Boolean,
        override val sendMassNotify: Boolean,
        override val sender: Creator,
        override val creator: Creator,

        private val argLower: String,
        private val argUpper: String,
        private val argUser: String,

        private val argUserIsConsole: Boolean,
    ) : Notification() {

        override val creatorAlertPerm = "ticketmanager.notify.change.massClose"
        override val massNotifyPerm = "ticketmanager.notify.massNotify.massClose"

        override val generateCreatorMSG = { locale: TMLocale -> locale.notifyTicketModificationEvent.parseMiniMessage("id" templated "???") }
        override val generateMassNotify = { locale: TMLocale ->
            locale.notifyTicketMassCloseEvent.parseMiniMessage(
                "user" templated (if (argUserIsConsole) locale.consoleName else argUser),
                "lower" templated argLower,
                "upper" templated argUpper,
            )
        }
        override val generateSenderMSG = { locale: TMLocale ->
            locale.notifyTicketMassCloseSuccess.parseMiniMessage(
                "lower" templated argLower,
                "upper" templated argUpper,
            )
        }

        override fun encodeForProxy(): ByteArray {
            val output = generateStandardOutput(MessageType.MASSCLOSE)

            output.writeUTF(argLower)
            output.writeUTF(argUpper)
            output.writeUTF(argUser)
            output.writeBoolean(argUserIsConsole)

            return output.toByteArray()
        }

        companion object {
            private const val massNotifyPerm = "ticketmanager.notify.massNotify.massClose"

            fun build(silent: Boolean, ticket: Ticket, sender: Sender, argLower: String, argUpper: String, argUser: String, argUserIsConsole: Boolean): MassClose {
                val (sendMassNotify, sendSenderMSG, sendCreatorMSG) = generateSendVars(silent, ticket, sender, massNotifyPerm)
                return MassClose(sendCreatorMSG, sendSenderMSG, sendMassNotify, sender.toCreator(), ticket.creator, argLower, argUpper, argUser, argUserIsConsole)
            }

            fun fromByteArray(input: ByteArrayDataInput): MassClose {
                val (sender, creator, sendCreatorMSG, sendSenderMSG, sendMassNotify) = StandardInput(input)
                return MassClose(sendCreatorMSG, sendSenderMSG, sendMassNotify, sender, creator,
                    argLower = input.readUTF(),
                    argUpper = input.readUTF(),
                    argUser = input.readUTF(),
                    argUserIsConsole = input.readBoolean(),
                )
            }
        }
    }

    class Comment(
        override val sendCreatorMSG: Boolean,
        override val sendSenderMSG: Boolean,
        override val sendMassNotify: Boolean,
        override val sender: Creator,
        override val creator: Creator,

        private val argID: String,
        private val argUser: String,
        private val argMessage: String,

        private val argUserIsConsole: Boolean,
    ) : Notification() {

        override val creatorAlertPerm = "ticketmanager.notify.change.comment"
        override val massNotifyPerm = "ticketmanager.notify.massNotify.comment"

        override val generateCreatorMSG = { locale: TMLocale -> locale.notifyTicketModificationEvent.parseMiniMessage("id" templated argID) }
        override val generateSenderMSG = { locale: TMLocale -> locale.notifyTicketCommentSuccess.parseMiniMessage("id" templated argID) }
        override val generateMassNotify = { locale: TMLocale ->
            locale.notifyTicketCommentEvent.parseMiniMessage(
                "user" templated (if (argUserIsConsole) locale.consoleName else argUser),
                "id" templated argID,
                "message" templated argMessage,
            )
        }

        override fun encodeForProxy(): ByteArray {
            val output = generateStandardOutput(MessageType.COMMENT)

            output.writeUTF(argID)
            output.writeUTF(argUser)
            output.writeUTF(argMessage)
            output.writeBoolean(argUserIsConsole)

            return output.toByteArray()
        }

        companion object {
            private const val massNotifyPerm = "ticketmanager.notify.massNotify.comment"

            fun build(silent: Boolean, ticket: Ticket, sender: Sender, argID: String, argUser: String, argMessage: String, argUserIsConsole: Boolean): Comment {
                val (sendMassNotify, sendSenderMSG, sendCreatorMSG) = generateSendVars(silent, ticket, sender, massNotifyPerm)
                return Comment(sendCreatorMSG, sendSenderMSG, sendMassNotify, sender.toCreator(), ticket.creator, argID, argUser, argMessage, argUserIsConsole)
            }

            fun fromByteArray(input: ByteArrayDataInput): Comment {
                val (sender, creator, sendCreatorMSG, sendSenderMSG, sendMassNotify) = StandardInput(input)
                return Comment(sendCreatorMSG, sendSenderMSG, sendMassNotify, sender, creator,
                    argID = input.readUTF(),
                    argUser = input.readUTF(),
                    argMessage = input.readUTF(),
                    argUserIsConsole = input.readBoolean(),
                )
            }
        }
    }

    class Create(
        override val sendCreatorMSG: Boolean,
        override val sendSenderMSG: Boolean,
        override val sendMassNotify: Boolean,
        override val sender: Creator,
        override val creator: Creator,

        private val argID: String,
        private val argUser: String,
        private val argMessage: String,

        private val argUserIsConsole: Boolean,
    ) : Notification() {

        override val creatorAlertPerm = "ticketmanager.NO NODE"
        override val massNotifyPerm = "ticketmanager.notify.massNotify.create"

        override val generateCreatorMSG = { _: TMLocale -> throw Exception("This should never run") }
        override val generateSenderMSG = { locale: TMLocale -> locale.notifyTicketCreationSuccess.parseMiniMessage("id" templated argID) }
        override val generateMassNotify = { locale: TMLocale ->
            locale.notifyTicketCreationEvent.parseMiniMessage(
                "user" templated (if (argUserIsConsole) locale.consoleName else argUser),
                "id" templated argID,
                "message" templated argMessage,
            )
        }

        override fun encodeForProxy(): ByteArray {
            val output = generateStandardOutput(MessageType.CREATE)

            output.writeUTF(argID)
            output.writeUTF(argUser)
            output.writeUTF(argMessage)
            output.writeBoolean(argUserIsConsole)

            return output.toByteArray()
        }

        companion object {
            private const val massNotifyPerm = "ticketmanager.notify.massNotify.create"

            fun build(silent: Boolean, ticket: Ticket, sender: Sender, argID: String, argUser: String, argMessage: String, argUserIsConsole: Boolean): Create {
                val (sendMassNotify, sendSenderMSG, sendCreatorMSG) = generateSendVars(silent, ticket, sender, massNotifyPerm)
                return Create(sendCreatorMSG, sendSenderMSG, sendMassNotify, sender.toCreator(), ticket.creator, argID, argUser, argMessage, argUserIsConsole)
            }

            fun fromByteArray(input: ByteArrayDataInput): Create {
                val (sender, creator, sendCreatorMSG, sendSenderMSG, sendMassNotify) = StandardInput(input)
                return Create(sendCreatorMSG, sendSenderMSG, sendMassNotify, sender, creator,
                    argID = input.readUTF(),
                    argUser = input.readUTF(),
                    argMessage = input.readUTF(),
                    argUserIsConsole = input.readBoolean(),
                )
            }
        }
    }

    class Reopen(
        override val sendCreatorMSG: Boolean,
        override val sendSenderMSG: Boolean,
        override val sendMassNotify: Boolean,
        override val sender: Creator,
        override val creator: Creator,

        private val argID: String,
        private val argUser: String,

        private val argUserIsConsole: Boolean,
    ) : Notification() {

        override val creatorAlertPerm = "ticketmanager.notify.change.reopen"
        override val massNotifyPerm = "ticketmanager.notify.massNotify.reopen"

        override val generateCreatorMSG = { locale: TMLocale -> locale.notifyTicketModificationEvent.parseMiniMessage("id" templated argID) }
        override val generateSenderMSG = { locale: TMLocale -> locale.notifyTicketReopenSuccess.parseMiniMessage("id" templated argID) }
        override val generateMassNotify = { locale: TMLocale ->
            locale.notifyTicketReopenEvent.parseMiniMessage(
                "user" templated (if (argUserIsConsole) locale.consoleName else argUser),
                "id" templated argID,
            )
        }

        override fun encodeForProxy(): ByteArray {
            val output = generateStandardOutput(MessageType.REOPEN)

            output.writeUTF(argID)
            output.writeUTF(argUser)
            output.writeBoolean(argUserIsConsole)

            return output.toByteArray()
        }

        companion object {
            private const val massNotifyPerm = "ticketmanager.notify.massNotify.reopen"

            fun build(silent: Boolean, ticket: Ticket, sender: Sender, argID: String, argUser: String, argUserIsConsole: Boolean): Reopen {
                val (sendMassNotify, sendSenderMSG, sendCreatorMSG) = generateSendVars(silent, ticket, sender, massNotifyPerm)
                return Reopen(sendCreatorMSG, sendSenderMSG, sendMassNotify, sender.toCreator(), ticket.creator, argID, argUser, argUserIsConsole)
            }

            fun fromByteArray(input: ByteArrayDataInput): Reopen {
                val (sender, creator, sendCreatorMSG, sendSenderMSG, sendMassNotify) = StandardInput(input)
                return Reopen(sendCreatorMSG, sendSenderMSG, sendMassNotify, sender, creator,
                    argID = input.readUTF(),
                    argUser = input.readUTF(),
                    argUserIsConsole = input.readBoolean(),
                )
            }
        }
    }

    class SetPriority(
        override val sendCreatorMSG: Boolean,
        override val sendSenderMSG: Boolean,
        override val sendMassNotify: Boolean,
        override val sender: Creator,
        override val creator: Creator,

        private val argUser: String,
        private val argID: String,
        private val argPriority: Ticket.Priority,

        private val argUserIsConsole: Boolean,
    ) : Notification() {

        override val creatorAlertPerm = "ticketmanager.notify.change.priority"
        override val massNotifyPerm = "ticketmanager.notify.massNotify.priority"

        override val generateCreatorMSG = { locale: TMLocale -> locale.notifyTicketModificationEvent.parseMiniMessage("id" templated argID) }
        override val generateSenderMSG = { locale: TMLocale -> locale.notifyTicketSetPrioritySuccess.parseMiniMessage("id" templated argID) }
        override val generateMassNotify = { locale: TMLocale ->
            locale.notifyTicketSetPriorityEvent
                .replace("%PCC%", priorityToHexColour(argPriority, locale))
                .parseMiniMessage(
                    "user" templated (if (argUserIsConsole) locale.consoleName else argUser),
                    "id" templated argID,
                    "priority" templated argPriority.toLocaledWord(locale),
                )
        }

        override fun encodeForProxy(): ByteArray {
            val output = generateStandardOutput(MessageType.SETPRIORITY)

            output.writeUTF(argUser)
            output.writeUTF(argID)
            output.writeInt(argPriority.level.toInt())
            output.writeBoolean(argUserIsConsole)

            return output.toByteArray()
        }

        companion object {
            private const val massNotifyPerm = "ticketmanager.notify.massNotify.priority"

            fun build(silent: Boolean, ticket: Ticket, sender: Sender, argUser: String, argID: String, argPriority: Ticket.Priority, argUserIsConsole: Boolean): SetPriority {
                val (sendMassNotify, sendSenderMSG, sendCreatorMSG) = generateSendVars(silent, ticket, sender, massNotifyPerm)
                return SetPriority(sendCreatorMSG, sendSenderMSG, sendMassNotify, sender.toCreator(), ticket.creator, argUser, argID, argPriority, argUserIsConsole)
            }

            fun fromByteArray(input: ByteArrayDataInput): SetPriority {
                val (sender, creator, sendCreatorMSG, sendSenderMSG, sendMassNotify) = StandardInput(input)
                return SetPriority(sendCreatorMSG, sendSenderMSG, sendMassNotify, sender, creator,
                    argUser = input.readUTF(),
                    argID = input.readUTF(),
                    argPriority = byteToPriority(input.readInt().toByte()),
                    argUserIsConsole = input.readBoolean(),
                )
            }
        }
    }


}


private fun generateSendVars(silent: Boolean, ticket: Ticket, sender: Sender, massNotifyPerm: String) = Triple(
    first = !silent,
    second = !sender.has(massNotifyPerm) || silent,
    third = sender.toCreator() != ticket.creator
            && ticket.creator != ConsoleObject
            && !silent
)

private data class StandardInput(val sender: Creator, val creator: Creator, val sendCreatorMSG: Boolean, val sendSenderMSG: Boolean, val sendMassNotify: Boolean) {
    constructor(input: ByteArrayDataInput): this(
        sender = mapToCreatorOrNull(input.readUTF())!!,
        creator = mapToCreatorOrNull(input.readUTF())!!,
        sendCreatorMSG = input.readBoolean(),
        sendSenderMSG = input.readBoolean(),
        sendMassNotify = input.readBoolean(),
    )
}