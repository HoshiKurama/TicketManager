package com.github.hoshikurama.ticketmanager.common

private const val STD_NAMESPACE = "ticketmanager"
interface ProxyChannel {
    val namespace: String
    val name: String

    fun waterfallString() = "$namespace:$name"
}

interface S2PChannel : ProxyChannel
interface P2SChannel : ProxyChannel

/* =================
   Proxy Channels
================= */

object Server2Proxy {
    object NotificationSharing : S2PChannel {
        override val namespace = STD_NAMESPACE
        override val name = "s2p_notification_sharing"
    }

    object Teleport : S2PChannel {
        override val namespace = STD_NAMESPACE
        override val name = "s2p_teleport"
    }

    object ProxyVersionRequest : S2PChannel {
        override val namespace = STD_NAMESPACE
        override val name = "s2p_proxy_version_request"
    }
}

object Proxy2Server {
    object NotificationSharing : P2SChannel {
        override val namespace = STD_NAMESPACE
        override val name = "p2s_notification_sharing"
    }

    object Teleport : P2SChannel {
        override val namespace = STD_NAMESPACE
        override val name = "p2s_teleport"
    }

    object ProxyVersionRequest : P2SChannel {
        override val namespace = STD_NAMESPACE
        override val name = "p2s_proxy_version_request"
    }
}