package com.github.hoshikurama.ticketmanager.sponge

import com.github.hoshikurama.ticketmanager.commonse.TMPlugin
import com.github.hoshikurama.ticketmanager.commonse.misc.ConfigParameters
import org.spongepowered.plugin.builtin.jvm.Plugin
import java.util.concurrent.TimeUnit

@Plugin("ticketmanager")
class TMPluginImpl : TMPlugin(

) {//https://forums.spongepowered.org/t/the-commands-api-the-future/21809

    override fun performSyncBefore() {
        TODO("Not yet implemented")
    }

    override fun performAsyncTaskTimer(frequency: Long, duration: TimeUnit, action: () -> Unit) {
        TODO("Not yet implemented")
    }

    override fun configExists(): Boolean {
        TODO("Not yet implemented")
    }

    override fun generateConfig() {
        TODO("Not yet implemented")
    }

    override fun reloadConfig() {
        TODO("Not yet implemented")
    }

    override fun readConfig(): ConfigParameters {
        TODO("Not yet implemented")
    }

    override fun loadInternalConfig(): List<String> {
        TODO("Not yet implemented")
    }

    override fun loadPlayerConfig(): List<String> {
        TODO("Not yet implemented")
    }

    override fun writeNewConfig(entries: List<String>) {
        TODO("Not yet implemented")
    }

    override fun registerProcesses() {
        TODO("Not yet implemented")
    }

    override fun unregisterProcesses() {
        TODO("Not yet implemented")
    }

}