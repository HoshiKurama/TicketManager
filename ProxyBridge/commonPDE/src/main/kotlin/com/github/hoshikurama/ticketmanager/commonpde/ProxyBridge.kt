package com.github.hoshikurama.ticketmanager.commonpde

import com.github.hoshikurama.ticketmanager.common.UpdateChecker
import com.github.hoshikurama.ticketmanager.common.discord.Discord
import com.github.hoshikurama.ticketmanager.common.supportedLocales
import com.github.hoshikurama.ticketmanager.common.updateConfig
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.exists
import kotlin.io.path.pathString

abstract class ProxyBridge {
    val updateChecker = AtomicReference<UpdateChecker>()

    private lateinit var discordSettings: Discord.Settings

    var discord: Discord? = null
    lateinit var locale: Locale

    abstract val dataDirectory: Path
    fun onInitialization() {


        doSpecialtyBeforeStart()
        registerChannels()

        // Configuration Initialization
        if (!configExists()) {

            // Generate data directory
            if (!dataDirectoryExists())
                writeDataDirectory()

            // Generate Config
            updateConfig({ listOf() }, ::loadInternalConfig, ::writeConfig)
        }

        val configMap = loadExternalConfig()
            .asSequence()
            .filterNot { it.startsWith("#") }
            .map { it.split(": ", limit = 2) }
            .map { it[0] to StringBuilder(it[1]) }
            .onEach { (_, sb) ->
                listOf(sb.lastIndex, 0)
                    .filter { sb[it] == '\"' || sb[it] == '\'' }
                    .forEach(sb::deleteCharAt)
            }
            .map { it.first to it.second.toString() }
            .toMap()
        val autoUpdateConfig = configMap["Auto_Update_Config"]?.toBoolean() ?: true
        val updateCheckFrequency = configMap["Update_Check_Duration_Hours"]?.toLong() ?: 12L
        val allowUpdateChecking = configMap["Allow_Update_Checking"]?.toBoolean() ?: true
        val preferredLocale = configMap["Preferred_Locale"] ?: "en_CA"
        val enableAVC = configMap["Enable_Advanced_Visual_Control"]?.toBoolean() ?: false


        // Generate AVC files if requested
        try {
            if (enableAVC) {
                File("$dataDirectory/locales")
                    .let { if (!it.exists()) it.mkdir() }

                supportedLocales
                    .filterNot { Paths.get("$dataDirectory/locales/$it.yml").exists() }
                    .forEach {
                        this@ProxyBridge::class.java.classLoader
                            .getResourceAsStream("locales/visual/$it.yml")!!
                            .use { input -> Files.copy(input, Paths.get("$dataDirectory/locales/$it.yml")) }
                    }
            }
        } catch (e: Exception) { e.printStackTrace() }

        // Build Locale and assign
        locale = preferredLocale.lowercase()
            .let { if (supportedLocales.contains(it)) it else "en_ca" }
            .let { it to Locale.buildLocaleFromInternal(it) }
            .let { (id,internal) -> if (enableAVC) Locale.buildLocaleFromExternal(id, dataDirectory.pathString, internal) else internal }

        // Auto update config
        if (autoUpdateConfig)
            updateConfig(::loadExternalConfig, ::loadInternalConfig, ::writeConfig)

        // Set update checker
        if (allowUpdateChecking) {
            setNewUpdateCheck()
            scheduleRepeatCheck(updateCheckFrequency)
        }

        // Discord
        discordSettings = Discord.Settings(
            notifyOnAssign = configMap["Discord_Notify_On_Assign"]?.toBoolean() ?: true,
            notifyOnClose = configMap["Discord_Notify_On_Close"]?.toBoolean() ?: true,
            notifyOnCloseAll = configMap["Discord_Notify_On_Close_All"]?.toBoolean() ?: true,
            notifyOnComment = configMap["Discord_Notify_On_Comment"]?.toBoolean() ?: true,
            notifyOnCreate = configMap["Discord_Notify_On_Create"]?.toBoolean() ?: true,
            notifyOnReopen = configMap["Discord_Notify_On_Reopen"]?.toBoolean() ?: true,
            notifyOnPriorityChange = configMap["Discord_Notify_On_Priority_Change"]?.toBoolean() ?: true,
            forwardToProxy = true,
        )
        discord = if (configMap["Use_Discord_Bot"]?.toBoolean() == true) {
            try {
                Discord(
                    token = configMap["Discord_Bot_Token"] ?: "0",
                    channelID = configMap["Discord_Channel_ID"]?.toLong() ?: -1L,
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else null
    }

    fun onShutdown() {
        unregisterChannels()
        cancelNextCheck()
    }

    private fun loadInternalConfig(): List<String> {
        return this::class.java.classLoader
            .getResourceAsStream("config.yml")
            ?.let(InputStream::reader)
            ?.let(InputStreamReader::readLines) ?: emptyList()
    }


    protected abstract fun registerChannels()
    protected abstract fun unregisterChannels()
    protected abstract fun doSpecialtyBeforeStart()

    protected abstract fun loadExternalConfig(): List<String>

    protected abstract fun configExists(): Boolean
    protected abstract fun writeConfig(list: List<String>)
    protected abstract fun dataDirectoryExists(): Boolean
    protected abstract fun writeDataDirectory()

    protected abstract fun scheduleRepeatCheck(frequencyHours: Long)
    protected abstract fun cancelNextCheck()

    protected abstract fun setNewUpdateCheck()
}