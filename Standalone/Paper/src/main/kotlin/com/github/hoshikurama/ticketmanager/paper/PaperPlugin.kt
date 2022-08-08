package com.github.hoshikurama.ticketmanager.paper

import net.milkbowl.vault.permission.Permission
import org.bukkit.plugin.java.JavaPlugin

class PaperPlugin : JavaPlugin() {

    private lateinit var tmPlugin: TMPluginImpl
    private lateinit var perms: Permission

    override fun onEnable() {
        // Find Vault plugin
        server.servicesManager.getRegistration(Permission::class.java)?.provider
            ?.let { perms = it }
            ?: pluginLoader.disablePlugin(this)

        // Other stuff
        tmPlugin = TMPluginImpl(this, perms)
        tmPlugin.enableTicketManager()
    }

    override fun onDisable() {
        tmPlugin.disablePlugin()
    }
}