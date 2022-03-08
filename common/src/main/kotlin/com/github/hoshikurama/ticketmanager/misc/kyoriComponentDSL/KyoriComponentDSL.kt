package com.github.hoshikurama.ticketmanager.misc.kyoriComponentDSL

import net.kyori.adventure.extra.kotlin.plus
import net.kyori.adventure.text.*

fun buildComponent(init: ComponentBuilding.() -> Unit): Component {
    val componentBuilder = ComponentBuilding()
    componentBuilder.init()
    return componentBuilder.component
}

class ComponentBuilding internal constructor() {
    internal var component: Component = Component.empty()
        private set

    fun blockNBT(builder: BlockNBTComponent.Builder.() -> Unit) {
        component += net.kyori.adventure.extra.kotlin.blockNBT(builder)
    }

    fun text(builder: TextComponent.Builder.() -> Unit) {
        component += net.kyori.adventure.extra.kotlin.text(builder)
    }

    fun entityNBT(builder: EntityNBTComponent.Builder.() -> Unit) {
        component += net.kyori.adventure.extra.kotlin.entityNBT(builder)
    }

    fun keybind(builder: KeybindComponent.Builder.() -> Unit) {
        component += net.kyori.adventure.extra.kotlin.keybind(builder)
    }

    fun score(builder: ScoreComponent.Builder.() -> Unit) {
        component += net.kyori.adventure.extra.kotlin.score(builder)
    }

    fun selector(builder: SelectorComponent.Builder.() -> Unit) {
        component += net.kyori.adventure.extra.kotlin.selector(builder)
    }

    fun storageNBT(builder: StorageNBTComponent.Builder.() -> Unit) {
        component += net.kyori.adventure.extra.kotlin.storageNBT(builder)
    }

    fun translatable(builder: TranslatableComponent.Builder.() -> Unit) {
        component += net.kyori.adventure.extra.kotlin.translatable(builder)
    }

    fun append(addition: () -> Component) {
        component += addition()
    }

    fun append(that: Component) {
        component += that
    }
}