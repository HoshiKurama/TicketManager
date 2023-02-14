package com.github.hoshikurama.ticketmanager.sponge

import com.github.hoshikurama.ticketmanager.commonse.LocaleHandler
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.misc.unwrapOrNull
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.platform.Player
import com.github.hoshikurama.ticketmanager.commonse.platform.Sender
import com.github.hoshikurama.ticketmanager.commonse.ticket.Ticket
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.luckperms.api.LuckPerms
import net.luckperms.api.model.group.Group
import org.spongepowered.api.Sponge
import org.spongepowered.api.entity.living.player.server.ServerPlayer
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class PlatformFunctionsImpl(
    private val logger: Logger,
    private val lp: LuckPerms,
    private val serverName: String?,
): PlatformFunctions {
    override fun massNotify(localeHandler: LocaleHandler, permission: String, localeMsg: (TMLocale) -> Component) {
        getAllOnlinePlayers(localeHandler)
            .filter { it.has(permission) }
            .forEach { localeMsg(it.locale).run(it::sendMessage) }

        localeMsg(localeHandler.consoleLocale).run(getConsoleAudience()::sendMessage)
    }

    override fun buildPlayer(uuid: UUID, localeHandler: LocaleHandler): Player? {
        return Sponge.server().player(uuid)
            .map { SpongePlayer(it, lp, localeHandler, serverName) }
            .unwrapOrNull()
    }

    override fun getAllOnlinePlayers(localeHandler: LocaleHandler): List<Player> {
        return Sponge.server().onlinePlayers().map { SpongePlayer(it, lp, localeHandler, serverName) }
    }

    override fun offlinePlayerNameToUUIDOrNull(name: String): UUID? {
        return Sponge.server().player(name).unwrapOrNull()?.uniqueId()
    }

    override fun nameFromUUID(uuid: UUID): String {
        return Sponge.server().player(uuid).unwrapOrNull()?.name() ?: "UUID"
    }

    override fun teleportToTicketLocSameServer(player: Player, loc: Ticket.TicketLocation) {
        TODO("Not yet implemented")
    }

    override fun teleportToTicketLocDiffServer(player: Player, loc: Ticket.TicketLocation) {
        TODO("Not yet implemented")
    }

    override fun relayMessageToProxy(channel: String, encodedMessage: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun getConsoleAudience(): Audience {
        return Sponge.systemSubject()
    }

    override fun pushInfoToConsole(message: String) {
        logger.log(Level.INFO, message)
    }

    override fun pushWarningToConsole(message: String) {
        logger.log(Level.WARNING, message)
    }

    override fun pushErrorToConsole(message: String) {
        logger.log(Level.SEVERE, message)
    }

    override fun getPermissionGroups(): List<String> {
        return lp.groupManager.loadedGroups.map(Group::getName)
    // NOTE: I know this only does the loaded groups, but I don't
    // want to deal with CompletableFutures for this. It's unnecessary
    }

    override fun getOfflinePlayerNames(): List<String> {
        return Sponge.server().onlinePlayers().map(ServerPlayer::name)
    // NOTE: I don't know how to grab offline player list, so this will suffice.
    }

    override fun getOnlineSeenPlayerNames(sender: Sender): List<String> {

        return when(sender) {
            is com.github.hoshikurama.ticketmanager.commonse.platform.Console ->
                Sponge.server().onlinePlayers().map(ServerPlayer::name)
            is Player -> {
                val sender = Sponge.server().player(sender.uniqueID).unwrapOrNull()!! // Known to be online
                Sponge.server().onlinePlayers()
                    .filter(sender::canSee)
                    .map(ServerPlayer::name)
            }
        }
    }

    override fun getWorldNames(): List<String> {
        return emptyList() // NOTE: Worlds currently unsupported because sponge doesn't let me get the name
    }
}

typealias ActualSpongePlayer = org.spongepowered.api.entity.living.player.Player

fun LuckPerms.toLPPlayerOnline(player: ActualSpongePlayer) = getPlayerAdapter(ActualSpongePlayer::class.java).getUser(player)