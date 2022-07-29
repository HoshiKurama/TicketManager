package com.github.hoshikurama.ticketmanager.common

import java.util.*

const val mainPluginVersion = "8.3.0"
const val bridgePluginVersion = "1.2"

const val bukkitMetricsKey = 11033
const val velocityBridgeKey = 15470
const val waterfallBridgeKey = 15473

val randServerIdentifier: UUID = UUID.randomUUID() // For proxies

/*
Things to do when updating version:
    - UPDATING MAIN PLUGIN:
        1. Update main plugin version above.
        2. Update each config file (config.yml)
        3. Update each plugin version (plugin.yml)
        4. Update root build.gradle.kts

    - UPDATING BRIDGE:
        1. Update bridge plugin version above.
        2. Update each config file (velocity-plugin.json)
        3. Update each plugin version ()
 */
