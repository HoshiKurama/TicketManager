package com.github.hoshikurama.ticketmanager.commonse.registrydefaults.permission

import com.github.hoshikurama.ticketmanager.api.CommandSender
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission
import com.github.hoshikurama.ticketmanager.api.registry.permission.PermissionExtension
import com.github.hoshikurama.ticketmanager.commonse.utilities.asDeferredThenAwait
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.model.group.Group

class LuckPerms : PermissionExtension {
    private val luckPerms =
        try { LuckPermsProvider.get() }
        catch (e: NoClassDefFoundError) {
            println("You do not have LuckPerms installed! Please download LuckPerms or an alternative permission extension.")
            throw e
        }

    override suspend fun load(): Permission {
        val allGroups = luckPerms.groupManager.run {
            loadAllGroups().asDeferredThenAwait()
            loadedGroups.map(Group::getName)
        }

        return object : Permission {
            override fun allGroupNames(): List<String> = allGroups

            override fun groupNamesOf(player: CommandSender.OnlinePlayer): List<String> {
                val lpUser = luckPerms.userManager.getUser(player.uuid) ?: return emptyList()

                return lpUser.getInheritedGroups(lpUser.queryOptions)
                    .map(Group::getName)
            }

            override fun has(player: CommandSender.OnlinePlayer, permission: String): Boolean {
                return luckPerms.userManager
                    .getUser(player.uuid)
                    ?.cachedData
                    ?.permissionData
                    ?.checkPermission(permission)
                    ?.asBoolean() ?: false
            }
        }
    }
}