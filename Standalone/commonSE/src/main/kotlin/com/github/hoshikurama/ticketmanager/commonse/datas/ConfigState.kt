package com.github.hoshikurama.ticketmanager.commonse.datas

import com.github.hoshikurama.ticketmanager.common.UpdateChecker

class ConfigState(
    @Volatile var pluginUpdate: UpdateChecker,

    val allowUnreadTicketUpdates: Boolean,
    val printModifiedStacktrace: Boolean,
    val printFullStacktrace: Boolean,

    // Proxy
    val enableProxyMode: Boolean,
    val proxyServerName: String?,
    val allowProxyUpdatePings: Boolean,
    @Volatile var proxyUpdate: Pair<String, String>?
)