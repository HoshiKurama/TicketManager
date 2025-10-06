package com.github.hoshikurama.ticketmanager.paper.commands

import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.ticket.Assignment
import com.github.hoshikurama.tmcoroutine.TMCoroutine
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.CompletableFuture

class UserAssignmentGrabberArgument(
    private val playerNamesCacher: OfflinePlayerNamesCacher
) : CustomArgumentType.Converted<UserAssignmentGrabber, String> {
    private val locale: Locale
        get() = CommandReferences.locale

    companion object {
        fun get(ctx: CommandContext<CommandSourceStack>, name: String): UserAssignmentGrabber {
            return ctx.getArgument(name, UserAssignmentGrabber::class.java)
        }
    }

    override fun convert(nativeType: String): UserAssignmentGrabber {
        return UserAssignmentGrabber(nativeType, playerNamesCacher)
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

            if (locale.consoleName.startsWith(builder.remaining))
                builder.suggest(locale.consoleName)

            builder.build()
        }.asCompletableFuture()
    }
}

// This gets around issue that generic must be of type Any
class UserAssignmentGrabber(
    private val username: String,
    private val playerNamesCacher: OfflinePlayerNamesCacher,
) {
    suspend fun retrieveOrNull(): Assignment? {
        return when {
            username == CommandReferences.locale.consoleName -> Assignment.Console
            playerNamesCacher.contains(username) -> Assignment.Player(username)
            else -> null
        }
    }
}