package com.github.hoshikurama.ticketmanager.spigot

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.luckperms.api.LuckPerms
import org.bukkit.plugin.java.JavaPlugin

class SpigotPlugin : JavaPlugin() {
    private lateinit var tmPlugin: TMPluginImpl
    private lateinit var adventure: BukkitAudiences

    override fun onEnable() {
        // Find LuckPerms Plugin
        server.servicesManager.getRegistration(LuckPerms::class.java)?.provider
            ?: pluginLoader.disablePlugin(this)

        // Grabs Adventure BukkitAudiences object
        adventure = BukkitAudiences.create(this)

        tmPlugin = TMPluginImpl(this, adventure)
        tmPlugin.enableTicketManager()
    }

    override fun onDisable() {
        tmPlugin.disableTicketManager()
        adventure.close()
    }
}