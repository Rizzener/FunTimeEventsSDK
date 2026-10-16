package net.funtimeevents.poc.platform;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;

public final class MinecraftText {
    private MinecraftText() {}

    public static String raw(Component text) {
        return text.getContents() instanceof PlainTextContents.LiteralContents literal ? literal.text() : null;
    }

    public static String hover(Component text) {
        Component value = HoverAccess.value(text);
        if (value != null) {
            String raw = raw(value);
            return raw != null ? raw : value.getString();
        }
        for (Component sibling : text.getSiblings()) {
            String found = hoverOrNull(sibling);
            if (found != null) return found;
        }
        return "";
    }

    private static String hoverOrNull(Component text) {
        Component value = HoverAccess.value(text);
        if (value != null) {
            String raw = raw(value);
            return raw != null ? raw : value.getString();
        }
        for (Component sibling : text.getSiblings()) {
            String found = hoverOrNull(sibling);
            if (found != null) return found;
        }
        return null;
    }
}
