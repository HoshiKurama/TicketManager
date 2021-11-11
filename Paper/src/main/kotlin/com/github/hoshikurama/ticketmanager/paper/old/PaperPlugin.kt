package com.github.hoshikurama.ticketmanager.paper.old

import com.github.shynixn.mccoroutine.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.asyncDispatcher
import com.github.shynixn.mccoroutine.minecraftDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import net.milkbowl.vault.permission.Permission

class PaperPlugin : SuspendingJavaPlugin() {

    lateinit var tmPlugin: TMPluginPaperImpl
    lateinit var perms: Permission

    override fun onEnable() {
        // Find Vault plugin
        server.servicesManager.getRegistration(Permission::class.java)?.provider
            ?.let { perms = it }
            ?: pluginLoader.disablePlugin(this)

        tmPlugin = TMPluginPaperImpl(this, perms, minecraftDispatcher as CoroutineDispatcher, asyncDispatcher as CoroutineDispatcher)
        tmPlugin.enableTicketManager()
    }

    override suspend fun onDisableAsync() {
        tmPlugin.disablePluginAsync()
    }
}