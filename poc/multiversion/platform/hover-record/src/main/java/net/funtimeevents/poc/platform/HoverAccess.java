package net.funtimeevents.poc.platform;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

public final class HoverAccess {
    private HoverAccess() {}
    public static Component value(Component text) {
        return text.getStyle().getHoverEvent() instanceof HoverEvent.ShowText hover ? hover.value() : null;
    }
}
