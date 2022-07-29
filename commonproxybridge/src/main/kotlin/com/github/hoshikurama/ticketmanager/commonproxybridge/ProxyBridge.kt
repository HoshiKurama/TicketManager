package com.github.hoshikurama.ticketmanager.commonproxybridge

import com.github.hoshikurama.ticketmanager.common.UpdateChecker
import com.github.hoshikurama.ticketmanager.common.updateConfig
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicReference

abstract class ProxyBridge {
    val updateChecker = AtomicReference<UpdateChecker>()

    fun onInitialization() {
        doSpecialtyBeforeStart()
        registerChannels()

        // Configuration Initialization
        kotlin.run {
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
                .map { it.split(":", limit = 2).map(String::trim) }
                .map { it[0] to it[1] }
                .toMap()
            val autoUpdateConfig = configMap["Auto_Update_Config"]?.toBoolean() ?: true
            val updateCheckFrequency = configMap["Update_Check_Duration_Hours"]?.toLong() ?: 12L
            val allowUpdateChecking = configMap["Allow_Update_Checking"]?.toBoolean() ?: true

            // Auto update config
            if (autoUpdateConfig)
               updateConfig(::loadExternalConfig, ::loadInternalConfig, ::writeConfig)

            // Set update checker
            if (allowUpdateChecking) {
                setNewUpdateCheck()
                scheduleRepeatCheck(updateCheckFrequency)
            }
        }
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