package com.github.hoshikurama.ticketmanager.commonpde

import com.github.hoshikurama.ticketmanager.common.UpdateChecker
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.notExists

abstract class ProxyBridge(private val dataFolder: Path) {
    val updateChecker = AtomicReference<UpdateChecker>()

    fun onInitialization() {
        doSpecialtyBeforeStart()
        registerChannels()

        // Configuration Initialization

        // Generate Data Folder
        if (dataFolder.notExists())
            dataFolder.toFile().mkdir()

        // Generate Config File
        val configPath = dataFolder.resolve("config.yml")
        if (configPath.notExists()) {
            updateConfig(::loadInternalConfig) { listOf() }
        }

        // Read Config
        val (playerConfigMap, internalConfigMap) = listOf(
            Files.readAllLines(configPath, Charsets.UTF_8),
            loadInternalConfig(),
        ).map { c ->
            c.asSequence()
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
        }

        val autoUpdateConfig = playerConfigMap["Auto_Update_Config"]?.toBooleanStrictOrNull()
            ?: internalConfigMap["Auto_Update_Config"]!!.toBooleanStrict()
        val updateCheckFrequency = playerConfigMap["Update_Check_Duration_Hours"]?.toLongOrNull()
            ?: internalConfigMap["Update_Check_Duration_Hours"]!!.toLong()
        val allowUpdateChecking = playerConfigMap["Allow_Update_Checking"]?.toBooleanStrictOrNull()
            ?: internalConfigMap["Allow_Update_Checking"]!!.toBooleanStrict()

        // Auto update config
        if (autoUpdateConfig)
            updateConfig(::loadInternalConfig) { Files.readAllLines(configPath, Charsets.UTF_8) }

        // Set update checker
        if (allowUpdateChecking) {
            setNewUpdateCheck()
            scheduleRepeatCheck(updateCheckFrequency)
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

    protected abstract fun scheduleRepeatCheck(frequencyHours: Long)
    protected abstract fun cancelNextCheck()

    protected abstract fun setNewUpdateCheck()

    private fun updateConfig(
        loadInternalConfig: () -> List<String>,
        loadPlayerConfig: () -> List<String>,
    ) {
        val isComment: (String) -> Boolean = { it.startsWith("#") }
        val getKey: (String) -> String = { it.split(":")[0] }

        val externalConfig = loadPlayerConfig() //NOTE: This will not work with future Sponge support
        val externalIdentifiers = externalConfig
            .filterNot(isComment)
            .map(getKey)

        val newValues = loadInternalConfig().map { str ->
            if (!isComment(str) && getKey(str) in externalIdentifiers)
                externalConfig.first { it.startsWith(getKey(str))}
            else str
        }

        // Write Config file
        val writer = dataFolder.resolve("config.yml").toFile().bufferedWriter()
        newValues.forEachIndexed { index, str ->
            writer.write(str)

            if (index != newValues.lastIndex)
                writer.newLine()
        }
        writer.close()
    }
}