package com.github.hoshikurama.ticketmanager.commonse.commands

import com.github.hoshikurama.ticketmanager.api.commands.CommandSender
import com.github.hoshikurama.ticketmanager.api.ticket.Assignment
import com.github.hoshikurama.ticketmanager.api.ticket.Creator
import com.github.hoshikurama.ticketmanager.api.ticket.Ticket
import com.github.hoshikurama.ticketmanager.common.randServerIdentifier
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.misc.*
import com.google.common.io.ByteArrayDataInput
import com.google.common.io.ByteArrayDataOutput
import com.google.common.io.ByteStreams
import net.kyori.adventure.text.Component

typealias InfoSender = CommandSender.Info

// NOTE: sendSenderMSG does not check if the ticket creator has the valid permission. Make sure pipeline understands this
sealed interface NotifyParams<out T: InfoSender> {
    val sendCreatorMSG: Boolean
    val sendSenderMSG: Boolean
    val sendMassNotify: Boolean

    val commandSender: T
    val ticketCreator: Creator
}

class StandardParams<out T: InfoSender>(
    override val sendCreatorMSG: Boolean,
    override val sendSenderMSG: Boolean,
    override val sendMassNotify: Boolean,
    override val commandSender: T,
    override val ticketCreator: Creator,
) : NotifyParams<T> {

    companion object {
        // For Composition
        fun build(
            isSilent: Boolean,
            ticketCreator: Creator,
            commandSender: CommandSender.Active,
            massNotifyPerm: String,
        ): StandardParams<CommandSender.Active> {
            return StandardParams(
                sendMassNotify = !isSilent,
                sendSenderMSG = !commandSender.has(massNotifyPerm) || isSilent,
                sendCreatorMSG = commandSender.asCreator() != ticketCreator
                        && ticketCreator !is Creator.Console
                        && !isSilent,
                commandSender = commandSender,
                ticketCreator = ticketCreator,
            )
        }

        fun build(activeRef: ByteArrayDataInput) = StandardParams(
            commandSender = InfoCSString(activeRef.readUTF()).asCommandSender(),
            ticketCreator = CreatorString(activeRef.readUTF()).asTicketCreator(),
            sendCreatorMSG = activeRef.readBoolean(),
            sendSenderMSG = activeRef.readBoolean(),
            sendMassNotify = activeRef.readBoolean(),
        )
    }

    fun encodeStandard(messageType: MessageNotification.MessageType): ByteArrayDataOutput {
        @Suppress("UnstableApiUsage")

        return ByteStreams.newDataOutput().apply {
            writeUTF(randServerIdentifier.toString())
            writeUTF(messageType.name)
            writeUTF(commandSender.asInfoString())
            writeUTF(ticketCreator.asString())
            writeBoolean(sendCreatorMSG)
            writeBoolean(sendSenderMSG)
            writeBoolean(sendMassNotify)
        }
    }
}

// Main Interface
sealed interface MessageNotification<out T: InfoSender>: NotifyParams<T> {
    fun generateCreatorMSG(activeLocale: TMLocale): Component
    fun generateSenderMSG(activeLocale: TMLocale): Component
    fun generateMassNotify(activeLocale: TMLocale): Component
    fun encodeForProxy(): ByteArray

    val creatorAlertPerm: String
    val massNotifyPerm: String

    enum class MessageType {
        ASSIGN, CLOSEWITHCOMMENT, CLOSEWITHOUTCOMMENT, MASSCLOSE, COMMENT, CREATE, REOPEN, SETPRIORITY,
    }

//                Specific Classes

    class Assign<out T: InfoSender>(
        private val standardParams: StandardParams<T>,
        private val assignment: Assignment,
        private val ticketID: Long
    ) : MessageNotification<T>, NotifyParams<T> by standardParams {

        override fun generateCreatorMSG(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketModificationEvent
                .parseMiniMessage("id" templated "$ticketID")
        }

        override fun generateMassNotify(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketAssignEvent.parseMiniMessage(
                "user" templated commandSender.getUsername(activeLocale),
                "id" templated "$ticketID",
                "assigned" templated assignment.toLocalizedName(activeLocale),
            )
        }

        override fun generateSenderMSG(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketAssignSuccess.parseMiniMessage(
                "id" templated "$ticketID",
                "assigned" templated assignment.toLocalizedName(activeLocale),
            )
        }

        override val creatorAlertPerm: String
            get() = Assign.creatorAlertPerm

        override val massNotifyPerm: String
            get() = Assign.massNotifyPerm

        override fun encodeForProxy(): ByteArray {
            val input = standardParams.encodeStandard(MessageType.ASSIGN)
            input.writeUTF(assignment.asString())
            input.writeLong(ticketID)
            return input.toByteArray()
        }

        companion object {
            private const val creatorAlertPerm = "ticketmanager.notify.change.assign"
            private const val massNotifyPerm = "ticketmanager.notify.massNotify.assign"

            fun newActive(
                isSilent: Boolean,
                assignment: Assignment,
                commandSender: CommandSender.Active,
                ticketCreator: Creator,
                ticketID: Long,
            ): Assign<CommandSender.Active> {
                val standardParams = StandardParams.build(isSilent, ticketCreator, commandSender, massNotifyPerm)
                return Assign(standardParams, assignment, ticketID)
            }

            fun decode(input: ByteArrayDataInput): Assign<CommandSender.Info> {
                val standardParams = StandardParams.build(input)
                return Assign(standardParams,
                    assignment = AssignmentString(input.readUTF()).asAssignmentType(),
                    ticketID = input.readLong(),
                )
            }
        }
    }

    class CloseWithComment<out T: InfoSender>(
        private val standardParams: StandardParams<T>,
        private val closingMessage: String,
        private val ticketID: Long,
    ) : MessageNotification<T>, NotifyParams<T> by standardParams {

        override fun generateCreatorMSG(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketModificationEvent
                .parseMiniMessage("id" templated "$ticketID")
        }

        override fun generateSenderMSG(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketCloseWCommentSuccess
                .parseMiniMessage("id" templated "$ticketID")
        }

        override fun generateMassNotify(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketCloseWCommentEvent.parseMiniMessage(
                "user" templated commandSender.getUsername(activeLocale),
                "id" templated "$ticketID",
                "message" templated closingMessage,
            )
        }

        override val creatorAlertPerm: String
            get() = CloseWithComment.creatorAlertPerm

        override val massNotifyPerm: String
            get() = CloseWithComment.massNotifyPerm

        override fun encodeForProxy(): ByteArray {
            val output = standardParams.encodeStandard(MessageType.CLOSEWITHCOMMENT)
            output.writeLong(ticketID)
            output.writeUTF(closingMessage)
            return output.toByteArray()
        }

        companion object {
            private const val creatorAlertPerm = "ticketmanager.notify.change.close"
            private const val massNotifyPerm = "ticketmanager.notify.massNotify.close"

            fun newActive(
                isSilent: Boolean,
                commandSender: CommandSender.Active,
                ticketCreator: Creator,
                closingMessage: String,
                ticketID: Long,
            ): CloseWithComment<CommandSender.Active> {
                val standardParams = StandardParams.build(isSilent, ticketCreator, commandSender, massNotifyPerm)
                return CloseWithComment(standardParams, closingMessage, ticketID)
            }

            fun decode(input: ByteArrayDataInput): CloseWithComment<CommandSender.Info> {
                val standardParams = StandardParams.build(input)
                return CloseWithComment(standardParams,
                    ticketID = input.readLong(),
                    closingMessage = input.readUTF(),
                )
            }
        }
    }

    class CloseWithoutComment<out T: InfoSender>(
        private val standardParams: StandardParams<T>,
        private val ticketID: Long,
    ) : MessageNotification<T>, NotifyParams<T> by standardParams {

        override fun generateCreatorMSG(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketModificationEvent
                .parseMiniMessage("id" templated "$ticketID")
        }

        override fun generateSenderMSG(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketCloseSuccess
                .parseMiniMessage("id" templated "$ticketID")
        }

        override fun generateMassNotify(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketCloseEvent.parseMiniMessage(
                "user" templated commandSender.getUsername(activeLocale),
                "id" templated "$ticketID",
            )
        }

        override val creatorAlertPerm: String
            get() = CloseWithoutComment.creatorAlertPerm

        override val massNotifyPerm: String
            get() = CloseWithoutComment.massNotifyPerm

        override fun encodeForProxy(): ByteArray {
            val output = standardParams.encodeStandard(MessageType.CLOSEWITHOUTCOMMENT)
            output.writeLong(ticketID)
            return output.toByteArray()
        }

        companion object {
            private const val creatorAlertPerm = "ticketmanager.notify.change.close"
            private const val massNotifyPerm = "ticketmanager.notify.massNotify.close"

            fun newActive(
                isSilent: Boolean,
                commandSender: CommandSender.Active,
                ticketCreator: Creator,
                ticketID: Long,
            ): CloseWithoutComment<CommandSender.Active> {
                val standardParams = StandardParams.build(isSilent, ticketCreator, commandSender, massNotifyPerm)
                return CloseWithoutComment(standardParams, ticketID)
            }

            fun decode(input: ByteArrayDataInput): CloseWithoutComment<CommandSender.Info> {
                val standardParams = StandardParams.build(input)
                return CloseWithoutComment(standardParams, input.readLong())
            }
        }
    }

    class MassClose<out T: InfoSender>(
        private val standardParams: StandardParams<T>,
        private val lowerBound: Long,
        private val upperBound: Long,
    ) : MessageNotification<T>, NotifyParams<T> by standardParams {

        override fun generateCreatorMSG(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketModificationEvent
                .parseMiniMessage("id" templated "???")
        }

        override fun generateMassNotify(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketMassCloseEvent.parseMiniMessage(
                "user" templated commandSender.getUsername(activeLocale),
                "lower" templated "$lowerBound",
                "upper" templated "$upperBound",
            )
        }

        override fun generateSenderMSG(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketMassCloseSuccess.parseMiniMessage(
                "lower" templated "$lowerBound",
                "upper" templated "$upperBound",
            )
        }

        override val creatorAlertPerm: String
            get() = MassClose.creatorAlertPerm

        override val massNotifyPerm: String
            get() = MassClose.massNotifyPerm

        override fun encodeForProxy(): ByteArray {
            val output = standardParams.encodeStandard(MessageType.MASSCLOSE)
            output.writeLong(lowerBound)
            output.writeLong(upperBound)
            return output.toByteArray()
        }

        companion object {
            private const val creatorAlertPerm = "ticketmanager.notify.change.massClose"
            private const val massNotifyPerm = "ticketmanager.notify.massNotify.massClose"

            fun newActive(
                isSilent: Boolean,
                commandSender: CommandSender.Active,
                lowerBound: Long,
                upperBound: Long,
            ): MassClose<CommandSender.Active> {
                val standardParams = StandardParams.build(
                    isSilent,
                    Creator.DummyCreator,
                    commandSender,
                    massNotifyPerm
                )
                return MassClose(standardParams, lowerBound, upperBound)
            }

            fun decode(input: ByteArrayDataInput): MassClose<InfoSender> {
                val standardParams = StandardParams.build(input)
                return MassClose(standardParams,
                    lowerBound = input.readLong(),
                    upperBound = input.readLong(),
                )
            }
        }
    }

    class Comment<out T: InfoSender>(
        private val standardParams: StandardParams<T>,
        private val ticketID: Long,
        private val comment: String,
    ) : MessageNotification<T>, NotifyParams<T> by standardParams {

        override fun generateCreatorMSG(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketModificationEvent
                .parseMiniMessage("id" templated "$ticketID")
        }

        override fun generateMassNotify(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketCommentEvent.parseMiniMessage(
                "user" templated commandSender.getUsername(activeLocale),
                "id" templated "$ticketID",
                "message" templated comment,
            )
        }

        override fun generateSenderMSG(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketCommentSuccess
                .parseMiniMessage("id" templated "$ticketID")
        }

        override val creatorAlertPerm: String
            get() = Comment.creatorAlertPerm

        override val massNotifyPerm: String
            get() = Comment.massNotifyPerm

        override fun encodeForProxy(): ByteArray {
            val output = standardParams.encodeStandard(MessageType.COMMENT)
            output.writeLong(ticketID)
            output.writeUTF(comment)
            return output.toByteArray()
        }

        companion object {
            private const val creatorAlertPerm = "ticketmanager.notify.change.comment"
            private const val massNotifyPerm = "ticketmanager.notify.massNotify.comment"

            fun newActive(
                isSilent: Boolean,
                commandSender: CommandSender.Active,
                ticketCreator: Creator,
                ticketID: Long,
                comment: String,
            ): Comment<CommandSender.Active> {
                val standardParams = StandardParams.build(isSilent, ticketCreator, commandSender, massNotifyPerm)
                return Comment(standardParams, ticketID, comment)
            }

            fun decode(input: ByteArrayDataInput): Comment<CommandSender.Info> {
                val standardParams = StandardParams.build(input)
                return Comment(standardParams,
                    ticketID = input.readLong(),
                    comment = input.readUTF(),
                )
            }
        }
    }

    class Create<out T: InfoSender>(
        private val standardParams: StandardParams<T>,
        private val ticketID: Long,
        private val message: String,
    ) : MessageNotification<T>, NotifyParams<T> by standardParams {

        override fun generateCreatorMSG(activeLocale: TMLocale): Component {
            throw Exception("This should never run. Use SenderMSG instead.")
        }

        override fun generateMassNotify(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketCreationEvent.parseMiniMessage(
                "user" templated commandSender.getUsername(activeLocale),
                "id" templated "$ticketID",
                "message" templated message,
            )
        }

        override fun generateSenderMSG(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketCreationSuccess
                .parseMiniMessage("id" templated "$ticketID")
        }

        override val creatorAlertPerm: String
            get() = Create.creatorAlertPerm

        override val massNotifyPerm: String
            get() = Create.massNotifyPerm

        override fun encodeForProxy(): ByteArray {
            val output = standardParams.encodeStandard(MessageType.CREATE)
            output.writeLong(ticketID)
            output.writeUTF(message)
            return output.toByteArray()
        }

        companion object {
            private const val creatorAlertPerm = "ticketmanager.NO NODE"
            private const val massNotifyPerm = "ticketmanager.notify.massNotify.create"

            fun newActive(
                isSilent: Boolean,
                commandSender: CommandSender.Active,
                ticketCreator: Creator,
                ticketID: Long,
                message: String,
            ): Create<CommandSender.Active> {
                val standardParams = StandardParams.build(isSilent, ticketCreator, commandSender, massNotifyPerm)
                return Create(standardParams, ticketID, message)
            }

            fun decode(input: ByteArrayDataInput): Create<CommandSender.Info> {
                val standardParams = StandardParams.build(input)
                return Create(standardParams,
                    ticketID = input.readLong(),
                    message = input.readUTF(),
                )
            }
        }
    }

    class Reopen<out T: InfoSender>(
        private val standardParams: StandardParams<T>,
        private val ticketID: Long,
    ) : MessageNotification<T>, NotifyParams<T> by standardParams {

        override fun generateCreatorMSG(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketModificationEvent
                .parseMiniMessage("id" templated "$ticketID")
        }

        override fun generateMassNotify(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketReopenEvent.parseMiniMessage(
                "user" templated commandSender.getUsername(activeLocale),
                "id" templated "$ticketID",
            )
        }

        override fun generateSenderMSG(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketReopenSuccess
                .parseMiniMessage("id" templated "$ticketID")
        }

        override val creatorAlertPerm: String
            get() = Reopen.creatorAlertPerm

        override val massNotifyPerm: String
            get() = Reopen.massNotifyPerm

        override fun encodeForProxy(): ByteArray {
            val output = standardParams.encodeStandard(MessageType.REOPEN)
            output.writeLong(ticketID)
            return output.toByteArray()
        }

        companion object {
            private const val creatorAlertPerm = "ticketmanager.notify.change.reopen"
            private const val massNotifyPerm = "ticketmanager.notify.massNotify.reopen"

            fun newActive(
                isSilent: Boolean,
                commandSender: CommandSender.Active,
                ticketCreator: Creator,
                ticketID: Long,
            ): Reopen<CommandSender.Active> {
                val standardParams = StandardParams.build(isSilent, ticketCreator, commandSender, massNotifyPerm)
                return Reopen(standardParams, ticketID)
            }

            fun decode(input: ByteArrayDataInput): Reopen<CommandSender.Info> {
                val standardParams = StandardParams.build(input)
                return Reopen(standardParams, input.readLong())
            }
        }
    }

    class SetPriority<out T: InfoSender>(
        private val standardParams: StandardParams<T>,
        private val ticketID: Long,
        private val priority: Ticket.Priority,
    ) : MessageNotification<T>, NotifyParams<T> by standardParams {

        override fun generateCreatorMSG(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketModificationEvent
                .parseMiniMessage("id" templated "$ticketID")
        }

        override fun generateMassNotify(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketSetPriorityEvent
                .replace("%PCC%", priority.getHexColour(activeLocale))
                .parseMiniMessage(
                    "user" templated commandSender.getUsername(activeLocale),
                    "id" templated "$ticketID",
                    "priority" templated priority.toLocaledWord(activeLocale),
                )
        }

        override fun generateSenderMSG(activeLocale: TMLocale): Component {
            return activeLocale.notifyTicketSetPrioritySuccess
                .parseMiniMessage("id" templated "$ticketID")
        }

        override val creatorAlertPerm: String
            get() = SetPriority.creatorAlertPerm

        override val massNotifyPerm: String
            get() = SetPriority.massNotifyPerm

        override fun encodeForProxy(): ByteArray {
            val output = standardParams.encodeStandard(MessageType.SETPRIORITY)
            output.writeLong(ticketID)
            output.writeByte(priority.asByte().toInt())
            return output.toByteArray()
        }

        companion object {
            private const val creatorAlertPerm = "ticketmanager.notify.change.priority"
            private const val massNotifyPerm = "ticketmanager.notify.massNotify.priority"

            fun newActive(
                isSilent: Boolean,
                commandSender: CommandSender.Active,
                ticketCreator: Creator,
                ticketID: Long,
                ticketPriority: Ticket.Priority
            ): SetPriority<CommandSender.Active> {
                val standardParams = StandardParams.build(isSilent, ticketCreator, commandSender, massNotifyPerm)
                return SetPriority(standardParams, ticketID, ticketPriority)
            }

            fun decode(input: ByteArrayDataInput): SetPriority<CommandSender.Info> {
                val standardParams = StandardParams.build(input)
                return SetPriority(standardParams,
                    ticketID = input.readLong(),
                    priority = input.readByte().toPriority(),
                )
            }
        }
    }
}