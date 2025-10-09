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
import java.util.Arrays
import java.util.Optional
import java.util.concurrent.CompletableFuture

class OfflinePlayerGrabberArgument(
    private val playerNamesCacher: OfflinePlayerNamesCacher
) : CustomArgumentType.Converted<OfflinePlayerGrabber, String> {

    override fun convert(nativeType: String): OfflinePlayerGrabber {
        return OfflinePlayerGrabber(nativeType, playerNamesCacher)
    }

    override fun getNativeType(): ArgumentType<String> {
        return StringArgumentType.string()
    }

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val source = context.source as CommandSourceStack

        return TMCoroutine.Supervised.async {
            playerNamesCacher.getSuggestions(source.sender, builder.remaining)
                .forEach(builder::suggest)
            builder.build()
        }.asCompletableFuture()
    }
}

class OfflinePlayerGrabber(
    private val requestedName: String,
    private val playerNamesCacher: OfflinePlayerNamesCacher,
) {
    sealed interface Result
    data class ValidPlayer(val player: OfflinePlayer) : Result
    data object ErrorInvalidName : Result

    suspend fun retrieve(): Result {
        if (playerNamesCacher.contains(requestedName))
            ValidPlayer(Bukkit.getOfflinePlayer(requestedName))

        val playerHasLoggedOnBefore = Arrays.stream(Bukkit.getOfflinePlayers())
            .parallel()
            .filter { it.name == requestedName }
            .findAny()
            .asNullable()

        return playerHasLoggedOnBefore?.run(::ValidPlayer) ?: ErrorInvalidName
    }
}

private fun <T> Optional<T>.asNullable(): T? {
    return if (isPresent) get() else null
}