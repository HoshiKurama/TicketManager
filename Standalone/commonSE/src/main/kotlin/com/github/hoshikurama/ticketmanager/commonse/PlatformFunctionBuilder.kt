package com.github.hoshikurama.ticketmanager.commonse

import com.github.hoshikurama.ticketmanager.api.PlatformFunctions
import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.permission.Permission

fun interface PlatformFunctionBuilder {
    fun build(permission: Permission, config: Config): PlatformFunctions
}