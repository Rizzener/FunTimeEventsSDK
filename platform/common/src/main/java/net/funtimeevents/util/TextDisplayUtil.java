package net.funtimeevents.util;

import net.funtimeevents.internal.platform.mixin.TextDisplayAccessor;
import net.funtimeevents.internal.tracker.TrackerMetrics;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

public final class TextDisplayUtil {
    private TextDisplayUtil() {}
    public static String getDisplayText(Entity entity) {
        if (!(entity instanceof Display.TextDisplay)) return null;
        TrackerMetrics.displayRead();
        var text = ((TextDisplayAccessor) entity).fte$getText();
        String raw = TextUtil.tryGetRawText(text);
        return raw != null ? raw : text.getString();
    }
}
