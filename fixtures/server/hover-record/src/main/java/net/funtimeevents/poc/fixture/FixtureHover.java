package net.funtimeevents.poc.fixture;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

final class FixtureHover {
    static MutableComponent attach(MutableComponent text, Component hover) {
        return text.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hover)));
    }
}
