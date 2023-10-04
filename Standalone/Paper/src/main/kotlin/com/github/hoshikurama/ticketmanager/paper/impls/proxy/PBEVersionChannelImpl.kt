package com.github.hoshikurama.ticketmanager.paper.impls.proxy

import com.github.hoshikurama.ticketmanager.common.Server2Proxy
import com.github.hoshikurama.ticketmanager.commonse.proxymailboxes.PBEVersionChannel
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.function.Consumer

class PBEVersionChannelImpl(private val plugin: Plugin): PBEVersionChannel() {
    override fun relayToProxy(inputArray: ByteArray) {
        Bukkit.getScheduler().runTask(plugin, Consumer {
            plugin.server.sendPluginMessage(plugin, Server2Proxy.ProxyVersionRequest.waterfallString(), inputArray)
        })
    }
}