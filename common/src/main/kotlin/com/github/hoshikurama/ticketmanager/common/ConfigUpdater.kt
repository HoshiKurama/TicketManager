package com.github.hoshikurama.ticketmanager.common

fun updateConfig(
    loadPlayerConfig: () -> List<String>,
    loadInternalConfig: () -> List<String>,
    writeNewConfig: (List<String>) -> Unit,
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

    writeNewConfig(newValues)
}