package com.github.hoshikurama.ticketmanager.paper

import org.bukkit.plugin.java.JavaPlugin

class PaperPlugin : JavaPlugin() {

    private lateinit var tmPlugin: TMPluginImpl

    override fun onEnable() {
        // Note: No need to check for LuckPerms as it is handled by the paper-plugin.yml

        // Other stuff
        tmPlugin = TMPluginImpl(this)
        tmPlugin.enableTicketManager()
    }

    override fun onDisable() {
        tmPlugin.disableTicketManager()
    }
}