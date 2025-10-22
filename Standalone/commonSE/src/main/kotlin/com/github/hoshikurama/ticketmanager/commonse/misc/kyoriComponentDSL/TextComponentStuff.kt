package com.github.hoshikurama.ticketmanager.commonse.misc.kyoriComponentDSL

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent

@Suppress("Unused")
fun <T> TextComponent.Builder.onHover(init: HoverEventBuilder.() -> HoverEvent<T>): TextComponent.Builder {
    val event = HoverEventBuilder().init()
    return hoverEvent(event)
}

class HoverEventBuilder internal constructor() {
    @Suppress("Unused") fun showText(text: Component) = HoverEvent.hoverEvent(HoverEvent.Action.SHOW_TEXT, text)
    @Suppress("Unused") fun showItem(item: HoverEvent.ShowItem) = HoverEvent.hoverEvent(HoverEvent.Action.SHOW_ITEM, item)
    @Suppress("Unused") fun showEntity(entity: HoverEvent.ShowEntity) = HoverEvent.hoverEvent(HoverEvent.Action.SHOW_ENTITY, entity)
}
