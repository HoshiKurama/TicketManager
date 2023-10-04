package com.github.hoshikurama.ticketmanager.commonse.registrydefaults.database

import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.database.DatabaseExtension
import com.github.hoshikurama.ticketmanager.api.registry.database.types.AsyncDatabase
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class DefaultDatabaseExtension : DatabaseExtension {

    override suspend fun load(tmDirectory: Path, config: Config, locale: Locale): AsyncDatabase {
        return CachedH2(tmDirectory.absolutePathString())
    }
}