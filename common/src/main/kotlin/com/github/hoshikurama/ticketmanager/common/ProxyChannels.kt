package com.github.hoshikurama.ticketmanager.common

private const val STD_NAMESPACE = "ticketmanager"
interface ProxyChannel {
    val namespace: String
    val name: String

    fun waterfallString() = "$namespace:$name"
}

sealed interface S2PChannel : ProxyChannel
sealed interface P2SChannel : ProxyChannel

/* =================
   Proxy Channels
================= */

object Server2Proxy {
    data object NotificationSharing : S2PChannel { // NOTE: Forwarding
        override val namespace = STD_NAMESPACE
        override val name = "s2p_notification_sharing"
    }

    data object Teleport : S2PChannel { // Note: Forwarding
        override val namespace = STD_NAMESPACE
        override val name = "s2p_teleport"
    }

    data object ProxyVersionRequest : S2PChannel { // Note: Requesting
        override val namespace = STD_NAMESPACE
        override val name = "s2p_proxy_version_request"
    }
}

object Proxy2Server {
    data object NotificationSharing : P2SChannel { // NOTE: Forwarding
        override val namespace = STD_NAMESPACE
        override val name = "p2s_notification_sharing"
    }

    data object Teleport : P2SChannel {
        override val namespace = STD_NAMESPACE
        override val name = "p2s_teleport"
    }

    data object ProxyVersionRequest : P2SChannel { // Note: Requesting
        override val namespace = STD_NAMESPACE
        override val name = "p2s_proxy_version_request"
    }
}