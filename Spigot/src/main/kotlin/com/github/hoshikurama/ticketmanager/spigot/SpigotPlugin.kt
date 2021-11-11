package com.github.hoshikurama.ticketmanager.spigot

import com.github.shynixn.mccoroutine.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.asyncDispatcher
import com.github.shynixn.mccoroutine.minecraftDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.milkbowl.vault.permission.Permission

class SpigotPlugin : SuspendingJavaPlugin() {
    private lateinit var tmPlugin: TMPluginSpigotImpl
    private lateinit var perms: Permission
    private lateinit var adventure: BukkitAudiences

    override fun onEnable() {
        // Find Vault Plugin
        server.servicesManager.getRegistration(Permission::class.java)?.provider
            ?.let { perms = it }
            ?: pluginLoader.disablePlugin(this)

        // Grabs Adventure BukkitAudiences object
        adventure = BukkitAudiences.create(this)

        tmPlugin = TMPluginSpigotImpl(
            spigotPlugin = this,
            perms = perms,
            adventure = adventure,
            mainDispatcher = minecraftDispatcher as CoroutineDispatcher,
            asyncDispatcher = asyncDispatcher as CoroutineDispatcher,
        )
        tmPlugin.enableTicketManager()
    }

    override suspend fun onDisableAsync() {
        tmPlugin.disablePluginAsync()
        adventure.close()
    }
}