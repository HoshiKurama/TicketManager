package com.github.hoshikurama.ticketmanager.paper

import com.github.hoshikurama.componentDSL.formattedContent
import com.github.hoshikurama.ticketmanager.common.TMLocale
import com.github.hoshikurama.ticketmanager.common.hooks.*
import com.github.hoshikurama.ticketmanager.common.ticket.BasicTicket
import com.github.shynixn.mccoroutine.SuspendingCommandExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.kyori.adventure.extra.kotlin.text
import net.kyori.adventure.text.Component
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import java.util.*

class PaperCommandPipeline(
    private val pluginData: TicketManagerPlugin<PaperPlugin>,
    private val perms: Permission,
) : CommandPipeline<PaperPlugin>(pluginData), SuspendingCommandExecutor {


    override suspend fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val agnosticSender: Sender =
            if (sender is org.bukkit.entity.Player) PaperPlayer(sender, pluginData, perms)
            else PaperConsole(pluginData.configState.localeHandler.consoleLocale)

        return execute(agnosticSender, args.toList())
    }


    override fun pushMassNotify(permission: String, localeMsg: (TMLocale) -> Component) {
        Bukkit.getConsoleSender().sendMessage(localeMsg(pluginData.configState.localeHandler.consoleLocale))

        Bukkit.getOnlinePlayers().asSequence()
            .map { PaperPlayer(it, pluginData, perms) }
            .filter { it.has(permission) }
            .forEach { localeMsg(it.locale).run(it::sendMessage) }
    }

    override fun buildPlayer(uuid: UUID): Player? = Bukkit.getPlayer(uuid)?.run { PaperPlayer(this, pluginData, perms) }

    override fun getOnlinePlayers(): Flow<Player> = flow {
        Bukkit.getOnlinePlayers().asSequence()
            .map { PaperPlayer(it, pluginData, perms) }
            .forEach { emit(it) }
    }

    override fun stripColour(msg: String) = ChatColor.stripColor(msg)!!

    override fun offlinePlayerNameToUUIDOrNull(name: String): UUID? {
        return Bukkit.getOfflinePlayers().asSequence()
            .firstOrNull { it.name?.equals(name) ?: false }
            ?.run { uniqueId }
    }

    override fun uuidToName(uuid: UUID?, locale: TMLocale): String {
        if (uuid == null) return locale.consoleName
        return uuid.run(Bukkit::getOfflinePlayer).name ?: "UUID"
    }

    override fun teleportToTicketLocation(player: Player, loc: BasicTicket.TicketLocation) {
        val world = Bukkit.getWorld(loc.world)
        val paperPlayer = player as PaperPlayer

        world?.run {
            val location = Location(this, loc.x.toDouble(), loc.y.toDouble(), loc.z.toDouble())
            paperPlayer.pPlayer.teleport(location)
        }
    }
}

class PaperPlayer(
    val pPlayer: org.bukkit.entity.Player,
    pluginData: TicketManagerPlugin<PaperPlugin>,
    private val perms: Permission,
) : Player() {
    override val locale = pluginData.configState.localeHandler.getOrDefault(pPlayer.locale().toString())
    override val name = pPlayer.name
    override val uniqueID = pPlayer.uniqueId
    override val location = kotlin.run {
        val loc = pPlayer.location
        BasicTicket.TicketLocation(loc.world.name, loc.blockX, loc.blockY, loc.blockZ)
    }
    override val permissionGroups: List<String> by lazy {
        perms.getPlayerGroups(pPlayer).toList()
    }

    override fun sendMessage(msg: String) {
        text { formattedContent(msg) }
            .run(::sendMessage)
    }

    override fun sendMessage(component: Component) {
        pPlayer.sendMessage(component)
    }

    override fun has(permission: String) = perms.has(pPlayer, permission)
}

class PaperConsole(
    consoleLocale: TMLocale
) : Console() {
    override val locale = consoleLocale
    override val name = locale.consoleName

    override fun sendMessage(msg: String) {
        text { formattedContent(msg) }
            .run(::sendMessage)
    }

    override fun sendMessage(component: Component) {
        Bukkit.getConsoleSender().sendMessage(component)
    }
}