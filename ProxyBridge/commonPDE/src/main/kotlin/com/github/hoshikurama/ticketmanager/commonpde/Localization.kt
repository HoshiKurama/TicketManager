package com.github.hoshikurama.ticketmanager.commonpde

import com.github.hoshikurama.ticketmanager.common.CommonKeywords
import org.yaml.snakeyaml.Yaml
import java.nio.file.Paths
import kotlin.io.path.inputStream

class Locale(
    override val consoleName: String,
    override val miscNobody: String,
    override val discordOnAssign: String,
    override val discordOnClose: String,
    override val discordOnCloseAll: String,
    override val discordOnComment: String,
    override val discordOnCreate: String,
    override val discordOnReopen: String,
    override val discordOnPriorityChange: String,
    override val priorityLowest: String,
    override val priorityLow: String,
    override val priorityNormal: String,
    override val priorityHigh: String,
    override val priorityHighest: String,
    override val notifyPluginUpdate: String,
) : CommonKeywords {

    companion object {
        private fun loadYMLFrom(location: String): Map<String, String> =
            this::class.java.classLoader
                .getResourceAsStream(location)
                .let { Yaml().load(it) }

        private fun inlinePlaceholders(str: String?, tmHeader: String) = str
            ?.replace("%TMHeader", tmHeader)
            ?.replace("%nl%", "\n")

        fun buildLocaleFromInternal(localeID: String): Locale {
            val yml = loadYMLFrom("locales/$localeID.yml")

            val uniformHeader = yml["Uniform_Header"]!!
            fun inlineHeaders(key: String) = inlinePlaceholders(yml[key], uniformHeader)

            return Locale(
                consoleName = yml["Console_Name"]!!,
                miscNobody = yml["Nobody"]!!,
                discordOnAssign = yml["Discord_OnAssign"]!!,
                discordOnClose = yml["Discord_OnClose"]!!,
                discordOnCloseAll = yml["Discord_OnCloseAll"]!!,
                discordOnComment = yml["Discord_OnComment"]!!,
                discordOnCreate = yml["Discord_OnCreate"]!!,
                discordOnReopen = yml["Discord_OnReopen"]!!,
                discordOnPriorityChange = yml["Discord_OnPriorityChange"]!!,
                priorityLowest = yml["Priority_Lowest"]!!,
                priorityLow = yml["Priority_Low"]!!,
                priorityNormal = yml["Priority_Normal"]!!,
                priorityHigh = yml["Priority_High"]!!,
                priorityHighest = yml["Priority_Highest"]!!,
                notifyPluginUpdate = inlineHeaders("Notify_Event_PluginUpdate")!!,
            )
        }

        fun buildLocaleFromExternal(
            localeID: String,
            rootFileLocation: String,
            internalVersion: Locale
        ): Locale {
            val yml: Map<String, String> = try {
                Paths.get("$rootFileLocation/locales/$localeID.yml")
                    .inputStream()
                    .let { Yaml().load(it) }
            } catch (e: Exception) {
                mapOf()
            }

            fun inlineHeaders(key: String) = inlinePlaceholders(yml[key], key)

            return Locale(
                consoleName = yml["Console_Name"] ?: internalVersion.consoleName,
                miscNobody = yml["Nobody"] ?: internalVersion.miscNobody,
                discordOnAssign = yml["Discord_OnAssign"] ?: internalVersion.discordOnAssign,
                discordOnClose = yml["Discord_OnClose"] ?: internalVersion.discordOnClose,
                discordOnCloseAll = yml["Discord_OnCloseAll"] ?: internalVersion.discordOnCloseAll,
                discordOnComment = yml["Discord_OnComment"] ?: internalVersion.discordOnComment,
                discordOnCreate = yml["Discord_OnCreate"] ?: internalVersion.discordOnCreate,
                discordOnReopen = yml["Discord_OnReopen"] ?: internalVersion.discordOnReopen,
                discordOnPriorityChange = yml["Discord_OnPriorityChange"] ?: internalVersion.discordOnPriorityChange,
                priorityLowest = yml["Priority_Lowest"] ?: internalVersion.priorityLowest,
                priorityLow = yml["Priority_Low"] ?: internalVersion.priorityLow,
                priorityNormal = yml["Priority_Normal"] ?: internalVersion.priorityNormal,
                priorityHigh = yml["Priority_High"] ?: internalVersion.priorityHigh,
                priorityHighest = yml["Priority_Highest"] ?: internalVersion.priorityHighest,
                notifyPluginUpdate = inlineHeaders("Notify_Event_PluginUpdate") ?: internalVersion.notifyPluginUpdate,
            )
        }
    }
}