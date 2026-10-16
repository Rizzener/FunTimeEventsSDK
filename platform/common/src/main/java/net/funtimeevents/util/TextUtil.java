package net.funtimeevents.util;

import net.funtimeevents.internal.platform.MinecraftText;
import net.minecraft.network.chat.Component;

public final class TextUtil {
    private TextUtil() {}
    public static String tryGetRawText(Component text) { return MinecraftText.raw(text); }
}
