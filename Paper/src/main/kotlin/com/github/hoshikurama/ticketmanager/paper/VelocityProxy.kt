package com.github.hoshikurama.ticketmanager.paper

/*
import com.github.hoshikurama.ticketmanager.TMLocale
import com.github.hoshikurama.ticketmanager.data.*
import com.github.hoshikurama.ticketmanager.misc.parseMiniMessage
import com.github.hoshikurama.ticketmanager.misc.priorityToHexColour
import com.github.hoshikurama.ticketmanager.misc.templated
import com.github.hoshikurama.ticketmanager.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.platform.Player
import com.github.hoshikurama.ticketmanager.platform.Sender
import com.github.hoshikurama.ticketmanager.platform.nonCreatorMadeChange
import com.google.common.io.ByteStreams
import com.google.gson.Gson
import net.kyori.adventure.text.Component
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.*

class VelocityProxy(
    private val serverUUID: UUID,
    private val platform: PlatformFunctions,
    private val instanceState: InstancePluginState
    ): PluginMessageListener {

    override fun onPluginMessageReceived(channel: String, player: org.bukkit.entity.Player, message: ByteArray) {

        if (!channel.equals("ticketmanager:notification_relay", ignoreCase = true)) return

        val mapper: VelocityDataMapper = ByteStreams.newDataInput(message).readUTF()
            .let { Gson().fromJson(it, VelocityDataMapper::class.java) }

        if (mapper.server != serverUUID) return

        val mappedNotifyParams = when (mapper.messageType) {

            MessageType.ASSIGN -> Assign.decode(mapper.rawData).run {
                MappedNotifyParams(
                    silent = silent,
                    senderUUID = senderUUID,
                    creatorUUID = ticketCreatorUUID,
                    senderLambda = {
                        it.notifyTicketAssignSuccess.parseMiniMessage(
                            "ID" templated assignmentID,
                            "Assigned" templated shownAssignment
                        )
                    },
                    massNotifyLambda = {
                        it.notifyTicketAssignEvent.parseMiniMessage(
                            "User" templated userName,
                            "ID" templated assignmentID,
                            "Assigned" templated shownAssignment,
                        )
                    },
                    creatorLambda = null,
                    creatorAlertPerm = "ticketmanager.notify.change.assign",
                    massNotifyPerm = "ticketmanager.notify.massNotify.assign"
                )
            }

            MessageType.CLOSENOCOMMENT -> CloseNoComment.decode(mapper.rawData).run {
                MappedNotifyParams(
                    silent = silent,
                    senderUUID = senderUUID,
                    creatorUUID = ticketCreatorUUID,
                    creatorLambda = { it.notifyTicketModificationEvent.parseMiniMessage("ID" templated ticketID) },
                    senderLambda = { it.notifyTicketCloseSuccess.parseMiniMessage("ID" templated ticketID) },
                    massNotifyLambda = {
                        it.notifyTicketCloseEvent.parseMiniMessage(
                            "User" templated creatorName,
                            "ID" templated ticketID,
                        )
                    },
                    massNotifyPerm = "ticketmanager.notify.massNotify.close",
                    creatorAlertPerm = "ticketmanager.notify.change.close"
                )
            }

            MessageType.CLOSEWITHCOMMENT -> CloseWithComment.decode(mapper.rawData).run {
                MappedNotifyParams(
                    silent = silent,
                    senderUUID = senderUUID,
                    creatorUUID = ticketCreatorUUID,
                    senderLambda = { it.notifyTicketCloseWCommentSuccess.parseMiniMessage("ID" templated ticketID) },
                    creatorLambda = { it.notifyTicketModificationEvent.parseMiniMessage("ID" templated ticketID) },
                    massNotifyLambda = {
                        it.notifyTicketCloseWCommentEvent.parseMiniMessage(
                            "User" templated creatorName,
                            "ID" templated ticketID,
                            "Message" templated this.message,
                        )
                    },
                    massNotifyPerm = "ticketmanager.notify.massNotify.close",
                    creatorAlertPerm = "ticketmanager.notify.change.close"
                )
            }

            MessageType.MASSCLOSE -> MassClose.decode(mapper.rawData).run {
                MappedNotifyParams(
                    silent = silent,
                    senderUUID = senderUUID,
                    creatorUUID = ticketCreatorUUID,
                    creatorLambda = null,
                    senderLambda = {
                        it.notifyTicketMassCloseSuccess.parseMiniMessage(
                            "Lower" templated lowerID,
                            "Upper" templated upperID,
                        )
                    },
                    massNotifyLambda = {
                        it.notifyTicketMassCloseEvent.parseMiniMessage(
                            "User" templated senderName,
                            "Lower" templated lowerID,
                            "Upper" templated upperID,
                        )
                    },
                    massNotifyPerm = "ticketmanager.notify.massNotify.massClose",
                    creatorAlertPerm = "ticketmanager.notify.change.massClose"
                )
            }

            MessageType.COMMENT -> Comment.decode(mapper.rawData).run {
                MappedNotifyParams(
                    silent = silent,
                    senderUUID = senderUUID,
                    creatorUUID = ticketCreatorUUID,
                    creatorLambda = { it.notifyTicketModificationEvent.parseMiniMessage("ID" templated ticketID) },
                    senderLambda = { it.notifyTicketCommentSuccess.parseMiniMessage("ID" templated ticketID) },
                    massNotifyLambda = {
                        it.notifyTicketCommentEvent.parseMiniMessage(
                            "User" templated userName,
                            "ID" templated ticketID,
                            "Message" templated this.message,
                        )
                    },
                    massNotifyPerm = "ticketmanager.notify.massNotify.comment",
                    creatorAlertPerm = "ticketmanager.notify.change.comment"
                )
            }

            MessageType.CREATE -> Create.decode(mapper.rawData).run {
                MappedNotifyParams(
                    silent = false,
                    senderUUID = senderUUID,
                    creatorUUID = ticketCreatorUUID,
                    creatorLambda = null,
                    senderLambda = { it.notifyTicketCreationSuccess.parseMiniMessage("ID" templated ticketID) },
                    massNotifyLambda = {
                        it.notifyTicketCreationEvent.parseMiniMessage(
                            "User" templated userName,
                            "ID" templated ticketID,
                            "Message" templated this.message
                        )
                    },
                    creatorAlertPerm = "ticketmanager.NO NODE",
                    massNotifyPerm = "ticketmanager.notify.massNotify.create",
                )
            }

            MessageType.REOPEN -> Reopen.decode(mapper.rawData).run {
                MappedNotifyParams(
                    silent = silent,
                    senderUUID = senderUUID,
                    creatorUUID = ticketCreatorUUID,
                    creatorLambda = { it.notifyTicketModificationEvent.parseMiniMessage("ID" templated ticketID) },
                    senderLambda = { it.notifyTicketReopenSuccess.parseMiniMessage("ID" templated ticketID) },
                    massNotifyLambda = {
                        it.notifyTicketReopenEvent.parseMiniMessage(
                            "User" templated userName,
                            "ID" templated ticketID,
                        )
                    },
                    creatorAlertPerm = "ticketmanager.notify.change.reopen",
                    massNotifyPerm = "ticketmanager.notify.massNotify.reopen",
                )
            }

            MessageType.SETPRIORITY -> SetPriority.decode(mapper.rawData).run {
                MappedNotifyParams(
                    silent = silent,
                    senderUUID = senderUUID,
                    creatorUUID = ticketCreatorUUID,
                    creatorLambda = null,
                    senderLambda = { it.notifyTicketSetPrioritySuccess.parseMiniMessage("ID" templated ticketID) },
                    massNotifyLambda = {
                        it.notifyTicketSetPriorityEvent
                            .replace("%PCC%", priorityToHexColour(newPriority, it))
                            .parseMiniMessage(
                                "User" templated userName,
                                "ID" templated ticketID,
                                "Priority" templated newPriority.toLocaledWord(it),
                            )
                    },
                    creatorAlertPerm = "ticketmanager.notify.change.priority",
                    massNotifyPerm =  "ticketmanager.notify.massNotify.priority"
                )
            }
        }

        // Sends message
        mappedNotifyParams.run {
            if (sendSenderMSG)
                senderLambda!!(sender.locale)
                    .run(sender::sendMessage)

            if (sendCreatorMSG)
                creator?.let { creatorLambda!!(it.locale) }
                    ?.run(creator::sendMessage)

            if (sendMassNotifyMSG)
                platform.massNotify(instanceState.localeHandler, massNotifyPerm, massNotifyLambda!!)
        }

    }

    private inner class MappedNotifyParams(
        silent: Boolean,
        senderUUID: UUID?,
        creatorUUID: UUID?,
        creatorAlertPerm: String,
        val massNotifyPerm: String,
        val senderLambda: ((TMLocale) -> Component)?,
        val creatorLambda: ((TMLocale) -> Component)?,
        val massNotifyLambda: ((TMLocale) -> Component)?,
    ) {
        val creator: Player? = creatorUUID?.run { platform.buildPlayer(this, instanceState.localeHandler) }
        val sender: Sender = senderUUID?.run { platform.buildPlayer(this, instanceState.localeHandler) } ?: PaperConsole(instanceState.localeHandler.consoleLocale)
        val sendSenderMSG: Boolean = (!sender.has(massNotifyPerm) || silent)
                && senderLambda != null
        val sendCreatorMSG: Boolean = sender.nonCreatorMadeChange(creatorUUID)
                && !silent && (creator != null)
                && creator.has(creatorAlertPerm)
                && creator.has(massNotifyPerm).run { !this }
                && creatorLambda != null
        val sendMassNotifyMSG: Boolean = !silent
                && massNotifyLambda != null
    }
}
 */



/*




      /*
      public class ExampleMainDriver extends JavaPlugin implements PluginMessageListener
{

    @Override
    public void onEnable()
    {

        getServer().getMessenger().registerIncomingPluginChannel( this, "my:channel", this ); // we register the incoming channel
        // you can register outgoing channel if you want to send messages to the proxy
        // getServer().getMessenger().registerOutgoingPluginChannel( this, "my:channel" );
        getLogger().info( "<pluginName> driver enabled successfully." );
    }


    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] bytes)
    {

        ByteArrayDataInput in = ByteStreams.newDataInput( bytes );
        String subChannel = in.readUTF();
        if ( subChannel.equalsIgnoreCase( "MySubChannel" ) )
        {
            String data1 = in.readUTF();
            int data2 = in.readInt();

            // do things with the data
        }
    }
}
       */
 */