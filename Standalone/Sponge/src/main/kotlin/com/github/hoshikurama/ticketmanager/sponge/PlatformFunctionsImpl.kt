package com.github.hoshikurama.ticketmanager.sponge

import com.github.hoshikurama.ticketmanager.commonse.LocaleHandler
import com.github.hoshikurama.ticketmanager.commonse.TMLocale
import com.github.hoshikurama.ticketmanager.commonse.platform.PlatformFunctions
import com.github.hoshikurama.ticketmanager.commonse.platform.Player
import com.github.hoshikurama.ticketmanager.commonse.platform.Sender
import com.github.hoshikurama.ticketmanager.commonse.ticket.Ticket
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.luckperms.api.LuckPerms
import org.spongepowered.api.Sponge
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
        val player = Sponge.serviceProvider().provide(UserStorageService::class.java)
    }

    override fun getAllOnlinePlayers(localeHandler: LocaleHandler): List<Player> {
        return Sponge.server().onlinePlayers().map { SpongePlayer(it, lp, localeHandler, serverName) }
    }

    override fun stripColour(msg: String): String {
        return msg
    }

    override fun offlinePlayerNameToUUIDOrNull(name: String): UUID? {
        TODO("Not yet implemented")
    }

    override fun nameFromUUID(uuid: UUID): String {

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
        TODO("Not yet implemented")
    }

    override fun getOfflinePlayerNames(): List<String> {
        TODO("Not yet implemented")
    }

    override fun getOnlineSeenPlayerNames(sender: Sender): List<String> {
        TODO("Not yet implemented")
    }

    override fun getWorldNames(): List<String> {
        TODO("Not yet implemented")
    }
}

typealias ActualSpongePlayer = org.spongepowered.api.entity.living.player.Player

fun LuckPerms.toLPPlayerOnline(player: ActualSpongePlayer) = getPlayerAdapter(ActualSpongePlayer::class.java).getUser(player)