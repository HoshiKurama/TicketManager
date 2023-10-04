package com.github.hoshikurama.ticketmanager.commonse.registrydefaults.config

import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.config.ConfigExtension
import com.github.hoshikurama.tmcore.TMCoroutine
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Path
import kotlin.io.path.notExists
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val CONFIG_NAME = "config.yml"

class DefaultConfigExtension : ConfigExtension {

    override suspend fun load(tmDirectory: Path): Config {
        val configPath = tmDirectory.resolve(CONFIG_NAME)

        if (tmDirectory.notExists()) {
            configPath.parent.toFile().mkdirs()

            updateConfig(
                loadRawInternalConfig = {
                    LoadHelper.loadInternal()(CONFIG_NAME)
                        .map(InputStream::reader)
                        .map(InputStreamReader::readLines)
                        .getOrThrow()
                },
                loadRawPlayerConfig = { listOf() },
                outputFilePath = configPath
            )
        }

        val configMap = LoadHelper.loadExternal(configPath)
            .map(LoadHelper.inputToConfigMap)
            .apply { getOrThrow() }


        val proxyResult = configMap.map { map ->
            val serverName = map["Proxy_Server_Name"] as String
            val enableProxy = map["Enable_Proxy"] as? Boolean ?: false
            val pbeAllowUpdateCheck = map["Allow_Proxy_UpdateChecking"] as? Boolean ?: true

            if (serverName.isNotBlank() && enableProxy) {
                object : Config.Proxy {
                    override val pbeAllowUpdateCheck = pbeAllowUpdateCheck
                    override val serverName: String = serverName
                }
            } else null
        }

        val visualResult = configMap.map { map ->
            object : Config.Visuals {
                override val consistentColourCode = map["Colour_Code"] as? String ?: "&3"
                override val enableAVC: Boolean = map["Enable_Advanced_Visual_Control"] as? Boolean ?: false
                override val requestedLocale: String = map["Preferred_Locale"] as? String ?: "en_ca"
            }
        }

        val cooldownResult = configMap.map { map ->
            val enableCooldowns = map["Use_Cooldowns"] as? Boolean ?: false
            if (!enableCooldowns) return@map null

            object : Config.Cooldown {
                override val duration: Duration = (map["Cooldown_Time"] as? Number ?: 1).toInt().seconds
            }
        }

        val autoUpdateConfig = configMap
            .map { it["Auto_Update_Config"] as Boolean }
            .getOrDefault(true)

        if (autoUpdateConfig) TMCoroutine.Global.launch {
            updateConfig(
                loadRawInternalConfig = {
                    LoadHelper.loadInternal()(CONFIG_NAME)
                        .map(InputStream::reader)
                        .map(InputStreamReader::readLines)
                        .getOrThrow()
                },
                loadRawPlayerConfig = {
                    FileHelper.readAllLines(configPath).getOrThrow()
                },
                outputFilePath = configPath
            )
        }

        return object : Config {
            override val allowUnreadTicketUpdates = configMap
                .map { it.getValue("Allow_Unread_Ticket_Updates") as Boolean }
                .getOrDefault(true)
            override val autoUpdateConfig = configMap
                .map { it.getValue("Auto_Update_Config") as Boolean }
                .getOrDefault(true)
            override val checkForPluginUpdates = configMap
                .map { it.getValue("Allow_UpdateChecking") as Boolean }
                .getOrDefault(true)
            override val visualOptions = visualResult.getOrThrow()
            override val proxyOptions = proxyResult.getOrDefault(null)
            override val cooldownOptions = cooldownResult.getOrDefault(null)
        }
    }
}

