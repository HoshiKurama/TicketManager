package com.github.hoshikurama.ticketmanager

import java.util.*

const val metricsKey = 11033
const val pluginVersion = "8.2.1"

val randServerIdentifier: UUID = UUID.randomUUID() // For proxies

/*
Things to do when updating version:
1. Update the plugin version above.
2. Update each config.yml
3. Update each plugin.yml
4. Update root build.gradle.kts
5. Update Velocity version velocity-plugin.json
 */