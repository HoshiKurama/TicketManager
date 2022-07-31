package com.github.hoshikurama.ticketmanager.spigot

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.milkbowl.vault.permission.Permission
import org.bukkit.plugin.java.JavaPlugin

class SpigotPlugin : JavaPlugin() {
    private lateinit var tmPlugin: TMPluginImpl
    private lateinit var perms: Permission
    private lateinit var adventure: BukkitAudiences

    override fun onEnable() {
        // Find Vault Plugin
        server.servicesManager.getRegistration(Permission::class.java)?.provider
            ?.let { perms = it }
            ?: pluginLoader.disablePlugin(this)

        // Grabs Adventure BukkitAudiences object
        adventure = BukkitAudiences.create(this)

        tmPlugin = TMPluginImpl(
            spigotPlugin = this,
            perms = perms,
            adventure = adventure,
        )
        tmPlugin.enableTicketManager()
    }

    override fun onDisable() {
        tmPlugin.disablePlugin()
        adventure.close()
    }
}