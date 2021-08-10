package com.github.hoshikurama.ticketmanager.spigot

import com.github.hoshikurama.componentDSL.formattedContent
import com.github.hoshikurama.ticketmanager.common.TMLocale
import com.github.hoshikurama.ticketmanager.common.hooks.CommandPipeline
import com.github.hoshikurama.ticketmanager.common.hooks.Console
import com.github.hoshikurama.ticketmanager.common.hooks.Player
import com.github.hoshikurama.ticketmanager.common.hooks.TicketManagerPlugin
import com.github.hoshikurama.ticketmanager.common.ticket.BasicTicket
import com.github.shynixn.mccoroutine.SuspendingCommandExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.kyori.adventure.extra.kotlin.text
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import java.util.*

class SpigotCommandPipeline(
    private val pluginData: TicketManagerPlugin<SpigotPlugin>,
    private val perms: Permission,
    private val adventure: BukkitAudiences,
) : CommandPipeline<SpigotPlugin>(pluginData), SuspendingCommandExecutor {

    override suspend fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        val tmSender =
            if (sender is org.bukkit.entity.Player) SpigotPlayer(sender, pluginData, perms, adventure)
            else SpigotConsole(pluginData.configState.localeHandler.consoleLocale, adventure)

        return execute(tmSender, args.toList())
    }


    override fun pushMassNotify(permission: String, localeMsg: (TMLocale) -> Component) {
        adventure.console().sendMessage(localeMsg(pluginData.configState.localeHandler.consoleLocale))

        Bukkit.getOnlinePlayers().asSequence()
            .map { SpigotPlayer(it, pluginData, perms, adventure) }
            .filter { it.has(permission) }
            .forEach { localeMsg(it.locale).run(it::sendMessage) }
    }

    override fun buildPlayer(uuid: UUID): Player = Bukkit.getPlayer(uuid)!!.run { SpigotPlayer(this, pluginData, perms, adventure) }

    override fun getOnlinePlayers(): Flow<Player> = flow {
        Bukkit.getOnlinePlayers().asSequence()
            .map { SpigotPlayer(it, pluginData, perms, adventure) }
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
        val paperPlayer = player as SpigotPlayer

        world?.run {
            val location = Location(this, loc.x.toDouble(), loc.y.toDouble(), loc.z.toDouble())
            paperPlayer.sPlayer.teleport(location)
        }
    }
}

class SpigotPlayer(
    val sPlayer: org.bukkit.entity.Player,
    pluginData: TicketManagerPlugin<SpigotPlugin>,
    private val perms: Permission,
    private val adventure: BukkitAudiences
) : Player() {
    override val locale = pluginData.configState.localeHandler.getOrDefault(sPlayer.locale)
    override val name = sPlayer.name
    override val uniqueID = sPlayer.uniqueId
    override val location = kotlin.run {
        val loc = sPlayer.location
        BasicTicket.TicketLocation(loc.world!!.name, loc.blockX, loc.blockY, loc.blockZ)
    }
    override val permissionGroups: List<String> by lazy {
        perms.getPlayerGroups(sPlayer).toList()
    }

    override fun sendMessage(msg: String) {
        text { formattedContent(msg) }
            .run(::sendMessage)
    }

    override fun sendMessage(component: Component) {
        adventure.player(sPlayer).sendMessage(component)
    }

    override fun has(permission: String) = perms.has(sPlayer, permission)
}

class SpigotConsole(
    consoleLocale: TMLocale,
    private val adventure: BukkitAudiences
): Console() {
    override val locale = consoleLocale
    override val name = locale.consoleName

    override fun sendMessage(msg: String) {
        text { formattedContent(msg) }
            .run(::sendMessage)
    }

    override fun sendMessage(component: Component) {
        adventure.console().sendMessage(component)
    }
}