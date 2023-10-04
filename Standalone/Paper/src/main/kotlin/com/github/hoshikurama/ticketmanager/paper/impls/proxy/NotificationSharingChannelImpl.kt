package com.github.hoshikurama.ticketmanager.paper.impls.proxy

import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.NotificationSharingChannel
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.function.Consumer

class NotificationSharingChannelImpl(private val plugin: Plugin): NotificationSharingChannel() {
    override fun relayToProxy(inputArray: ByteArray) {
        Bukkit.getScheduler().runTask(plugin, Consumer {
            plugin.server.sendPluginMessage(plugin, Server2Proxy.NotificationSharing.waterfallString(), inputArray)
        })
    }
}