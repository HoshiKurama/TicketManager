package com.github.hoshikurama.ticketmanager.commonse.registrydefaults.precommand

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.registry.precommand.PreCommandExtension
import com.github.hoshikurama.ticketmanager.api.registry.precommand.PreCommandExtension.SyncDecider.Decision
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.TimeSource

class Cooldown(private val cooldownDuration: Duration) : PreCommandExtension.SyncDecider {
    val map = ConcurrentHashMap<UUID, TimeSource.Monotonic.ValueTimeMark>()

    override suspend fun beforeCommand(
        sender: CommandSender.Active,
        permission: Permission,
        locale: Locale
    ): Decision {
        // Returns if user is console (ie: not player)
        val player = sender as? CommandSender.OnlinePlayer ?: return Decision.CONTINUE

        if (permission.has(sender, "ticketmanager.commandArg.cooldown.override"))
            return Decision.CONTINUE

        val elapsed = map[player.uuid]?.elapsedNow()

        // Not in cooldowns or past cooldown time~
        if (elapsed == null || elapsed > cooldownDuration) {
            map[player.uuid] = TimeSource.Monotonic.markNow()
            return Decision.CONTINUE
        }

        map[player.uuid] = TimeSource.Monotonic.markNow()
        locale.
    }

}