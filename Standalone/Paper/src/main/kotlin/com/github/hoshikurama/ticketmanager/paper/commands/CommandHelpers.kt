package com.github.hoshikurama.ticketmanager.paper.commands

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands

// Easy Creators
fun literal(literal: String) = Commands.literal(literal)
fun greedyStringArgument(name: String) = Commands.argument(name, StringArgumentType.greedyString())
fun longArgument(name: String) = Commands.argument(name, LongArgumentType.longArg())
fun intArgument(name: String) = Commands.argument(name, IntegerArgumentType.integer())

// Permissions
fun <U : ArgumentBuilder<CommandSourceStack, U>> ArgumentBuilder<CommandSourceStack, U>.permission(permission: String): U {
    return requires { it.sender.hasPermission(permission) }
}
fun <U : ArgumentBuilder<CommandSourceStack, U>> ArgumentBuilder<CommandSourceStack, U>.allPermissions(vararg permissions: String): U {
    return requires { permissions.all(it.sender::hasPermission) }
}
fun <U : ArgumentBuilder<CommandSourceStack, U>> ArgumentBuilder<CommandSourceStack, U>.anyPermissions(vararg permissions: String): U {
    return requires { permissions.any(it.sender::hasPermission) }
}