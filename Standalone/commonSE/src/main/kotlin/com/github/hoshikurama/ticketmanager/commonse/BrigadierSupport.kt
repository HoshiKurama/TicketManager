package com.github.hoshikurama.ticketmanager.commonse

/*
import com.github.hoshikurama.ticketmanager.commonse.platform.Sender
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType.integer
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode

fun buildBrigadierBuilder(locale: TMLocale): LiteralCommandNode<Sender> {

    val standardAssign = literal<Sender>(locale.commandWordAssign) // /ticket assign <ID> <Assignment...>
        .requires {
            it.has("ticketmanager.command.assign") ||
                    it.has("ticketmanager.command.*") ||
                    it.has("ticketmanager.manage") ||
                    it.has("ticketmanager.*")
        }
        .then(argument(locale.)

        )


    literal<Sender>(locale.commandBase)
        .then(standardAssign)


        .build()
}

fun test() {
    val dispatcher: CommandDispatcher<CommandSourceStack> = CommandDispatcher()

    dispatcher.register(
        literal("foo")
            .then(
                argument("bar", integer())
                    .executes { c ->
                        System.out.println("Bar is " + getInteger(c, "bar"))
                        1
                    }
            )
            .executes(Command<com.github.hoshikurama.ticketmanager.commonse.literal.S?> { c: CommandContext<com.github.hoshikurama.ticketmanager.commonse.literal.S?>? ->
                println("Called foo with no arguments")
                1
            })
    )
}

 */
