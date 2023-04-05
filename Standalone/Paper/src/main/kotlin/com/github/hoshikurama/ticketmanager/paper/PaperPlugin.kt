package com.github.hoshikurama.ticketmanager.paper

import net.luckperms.api.LuckPerms
import org.bukkit.plugin.java.JavaPlugin

class PaperPlugin : JavaPlugin() {

    private lateinit var tmPlugin: TMPluginImpl

    override fun onEnable() {
        // Find LuckPerms plugin
        server.servicesManager.getRegistration(LuckPerms::class.java)?.provider
            ?: pluginLoader.disablePlugin(this)

        // Other stuff
        tmPlugin = TMPluginImpl(this)
        tmPlugin.enableTicketManager()
    }

    override fun onDisable() {
        tmPlugin.disablePlugin()
    }
}