package com.github.hoshikurama.ticketmanager.commonse.registrydefaults.config

import com.github.hoshikurama.ticketmanager.api.utilities.Result
import org.yaml.snakeyaml.Yaml
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

internal inline fun updateConfig(
    loadRawInternalConfig: () -> List<String>,
    loadRawPlayerConfig: () -> List<String>,
    outputFilePath: Path,
) {
    val isComment: (String) -> Boolean = { it.startsWith("#") }
    val getKey: (String) -> String = { it.split(":")[0] }

    val externalConfig = loadRawPlayerConfig()
    val externalIdentifiers = externalConfig
        .filterNot(isComment)
        .map(getKey)

    val newValues = loadRawInternalConfig().map { str ->
        if (!isComment(str) && getKey(str) in externalIdentifiers)
            externalConfig.first { it.startsWith(getKey(str))}
        else str
    }

    // Write Config file
    val writer = outputFilePath.toFile().bufferedWriter()
    newValues.forEachIndexed { index, str ->
        writer.write(str)

        if (index != newValues.lastIndex)
            writer.newLine()
    }
    writer.close()
}


internal object LoadHelper {
    val inputToConfigMap: (InputStream) -> Map<String, Any> = { Yaml().load(it) }


    fun loadInternal(classLoader: ClassLoader = this::class.java.classLoader): (String) -> Result<InputStream> = { resourcePath ->
        try {
            resourcePath.replace("\\","/") // Evidently you can't use \ for resource paths on Windows
                .run(classLoader::getResourceAsStream)!!
                .run { Result.Success(this) }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    fun loadExternal(path: Path): Result<InputStream> {
        return try {
            path.run(Files::newInputStream)
                .run { Result.Success(this) }
        } catch (e: Exception) { Result.Failure(e) }
    }
}


object FileHelper {
    fun readAllLines(filePath: Path): Result<List<String>> {
        return try {
            Files.readAllLines(filePath, Charsets.UTF_8)
                .let { Result.Success(it) }
        } catch (e: Exception) { Result.Failure(e) }
    }
}