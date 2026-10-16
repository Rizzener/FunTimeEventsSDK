package net.funtimeevents.poc.platform;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

public final class HoverAccess {
    private HoverAccess() {}
    public static Component value(Component text) {
        var hover = text.getStyle().getHoverEvent();
        return hover == null ? null : hover.getValue(HoverEvent.Action.SHOW_TEXT);
    }
}
