package com.github.hoshikurama.ticketmanager.fabric.server

import com.google.common.collect.HashBiMap
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists

// NOTE: This should only be created once.
// TODO: This should eventually be replaced with a cache
class PlayerNameUUIDStorage(private val filePath: Path) {
    private val map: HashBiMap<UUID, String> = HashBiMap.create<UUID, String>()

    companion object {
        private val serialization = MapSerializer(UUIDSerializer, String.serializer())
        private var instancesCreated = 0
    }

    init {
        if (instancesCreated++ > 1)
            throw Exception("PlayerNameUUIDStorage cannot be instantiated more than once!")

        // Load values from JSON file if exists or leave empty
        if (filePath.exists()) {
            val reader = filePath.toFile().bufferedReader()
            Json.decodeFromString(serialization, reader.readText())
                .forEach { (uuid, name) -> map[uuid] = name }
            reader.close()
        }

        ServerPlayConnectionEvents.INIT.register { handler, _ ->
            map[handler.player.uuid] = handler.player.gameProfile.name
        }
    }

    fun writeToFile() {
        val str = Json.encodeToString(serialization, map)
        Files.writeString(filePath, str)
    }

    @Synchronized fun nameOrNull(uuid: UUID): String? = map[uuid]
    @Synchronized fun uuidOrNull(username: String): UUID? = map.inverse()[username]
    @Synchronized fun allNames(): List<String> = map.inverse().keys.toList()
}

