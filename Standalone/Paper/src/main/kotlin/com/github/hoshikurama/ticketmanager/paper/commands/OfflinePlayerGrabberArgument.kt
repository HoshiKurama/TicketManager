package com.github.hoshikurama.ticketmanager.paper.commands

import com.github.hoshikurama.tmcoroutine.TMCoroutine
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import kotlinx.coroutines.future.asCompletableFuture
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.concurrent.CompletableFuture
import org.bukkit.entity.Player as BukkitPlayer

class OfflinePlayerGrabberArgument(
    private val useOnlinePlayersOnly: Boolean
) : CustomArgumentType.Converted<OfflinePlayerGrabber, String> {

    override fun convert(nativeType: String): OfflinePlayerGrabber {
        return OfflinePlayerGrabber(nativeType)
    }

    override fun getNativeType(): ArgumentType<String> {
        return StringArgumentType.string()
    }

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val ctx = context.source as CommandSourceStack

        val isVisible = when (val sender = ctx.sender) {
            is BukkitPlayer -> { player: BukkitPlayer -> sender.canSee(player) }
            else -> { _: BukkitPlayer -> true }
        }

        return TMCoroutine.Supervised.async {
            if (useOnlinePlayersOnly) {
                Bukkit.getOnlinePlayers()
                    .asSequence()
                    .filter { it.name.startsWith(builder.remaining) }
                    .filter(isVisible)
                    .map(BukkitPlayer::getName)
                    .forEach(builder::suggest)

            } else {
                Bukkit.getOfflinePlayers()
                    .asSequence()
                    .mapNotNull(OfflinePlayer::getName)
                    .filter { it.startsWith(builder.remaining) }
                    .forEach(builder::suggest)
            }
            builder.build()
        }.asCompletableFuture()
    }
}

class OfflinePlayerGrabber(
    private val requestedName: String
) {
    sealed interface Result
    data class ValidPlayer(val player: OfflinePlayer) : Result
    data object ErrorInvalidName : Result

    suspend fun retrieve(): Result {
        // Doing this in an off thread because documentation says it can potentially block
        val offlinePlayerDeferred = TMCoroutine.Global.async { Bukkit.getOfflinePlayer(requestedName) }
        val offlinePlayer = offlinePlayerDeferred.await()
        return if (offlinePlayer.name == null) ErrorInvalidName else ValidPlayer(offlinePlayer)
    }
}