package com.github.hoshikurama.ticketmanager.spigot.impls.proxy

import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.NotificationSharingMailbox
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.function.Consumer

class NotificationSharingChannelImpl(private val plugin: Plugin): NotificationSharingMailbox() {
    override fun relayToProxy(inputArray: ByteArray) {
        Bukkit.getScheduler().runTask(plugin, Consumer {
            plugin.server.sendPluginMessage(plugin, Server2Proxy.NotificationSharing.waterfallString(), inputArray)
        })
    }
}