package com.github.hoshikurama.ticketmanager.commonse.old.misc.kyoriComponentDSL

import net.kyori.adventure.extra.kotlin.plus
import net.kyori.adventure.text.*

@Suppress("Unused")
fun buildComponent(init: ComponentBuilding.() -> Unit): Component {
    val componentBuilder = ComponentBuilding()
    componentBuilder.init()
    return componentBuilder.component
}

@Suppress("Unused")
class ComponentBuilding internal constructor() {
    internal var component: Component = Component.empty()
        private set

    @Suppress("Unused")
    fun blockNBT(builder: BlockNBTComponent.Builder.() -> Unit) {
        component += net.kyori.adventure.extra.kotlin.blockNBT(builder)
    }

    @Suppress("Unused")
    fun text(builder: TextComponent.Builder.() -> Unit) {
        component += net.kyori.adventure.extra.kotlin.text(builder)
    }

    @Suppress("Unused")
    fun entityNBT(builder: EntityNBTComponent.Builder.() -> Unit) {
        component += net.kyori.adventure.extra.kotlin.entityNBT(builder)
    }

    @Suppress("Unused")
    fun keybind(builder: KeybindComponent.Builder.() -> Unit) {
        component += net.kyori.adventure.extra.kotlin.keybind(builder)
    }

    @Suppress("Unused")
    fun score(builder: ScoreComponent.Builder.() -> Unit) {
        component += net.kyori.adventure.extra.kotlin.score(builder)
    }

    @Suppress("Unused")
    fun selector(builder: SelectorComponent.Builder.() -> Unit) {
        component += net.kyori.adventure.extra.kotlin.selector(builder)
    }

    @Suppress("Unused")
    fun storageNBT(builder: StorageNBTComponent.Builder.() -> Unit) {
        component += net.kyori.adventure.extra.kotlin.storageNBT(builder)
    }

    @Suppress("Unused")
    fun translatable(builder: TranslatableComponent.Builder.() -> Unit) {
        component += net.kyori.adventure.extra.kotlin.translatable(builder)
    }

    @Suppress("Unused")
    fun append(addition: () -> Component) {
        component += addition()
    }

    @Suppress("Unused")
    fun append(that: Component) {
        component += that
    }
}