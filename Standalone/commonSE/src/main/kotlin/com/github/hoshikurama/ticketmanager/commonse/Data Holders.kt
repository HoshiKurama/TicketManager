package com.github.hoshikurama.ticketmanager.commonse

import com.github.hoshikurama.ticketmanager.api.registry.playerjoin.PlayerJoinExtension
import com.github.hoshikurama.ticketmanager.api.registry.precommand.PreCommandExtension

data class PlayerJoinExtensionHolder(
    val syncExtensions: List<PlayerJoinExtension>,
    val asyncExtensions: List<PlayerJoinExtension>,
)

data class PreCommandExtensionHolder(
    val deciders: List<PreCommandExtension.SyncDecider>,
    val syncAfters: List<PreCommandExtension.SyncAfter>,
    val asyncAfters: List<PreCommandExtension.AsyncAfter>,
)