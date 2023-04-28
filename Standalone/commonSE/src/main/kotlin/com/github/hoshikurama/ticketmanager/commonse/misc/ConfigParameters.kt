package com.github.hoshikurama.ticketmanager.commonse.misc

import java.nio.file.Path

data class ConfigParameters(
    val pluginFolderPath: Path,
    val dbTypeAsStr: String?,
    val allowCooldowns: Boolean?,
    val cooldownSeconds: Long?,
    val localedColourCode: String?,
    val selectedLocale: String?,
    val allowUnreadTicketUpdates: Boolean?,
    val checkForPluginUpdates: Boolean?,
    val printModifiedStacktrace: Boolean?,
    val printFullStacktrace: Boolean?,
    val enableAdvancedVisualControl: Boolean?,
    val enableProxyMode: Boolean?,
    val proxyServerName: String?,
    val autoUpdateConfig: Boolean?,
    val allowProxyUpdateChecks: Boolean?,
    val proxyUpdateFrequency: Long?,
    val pluginUpdateFrequency: Long?,
)