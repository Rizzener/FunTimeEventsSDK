package net.funtimeevents.util;

import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TextDisplayUtil {

    private static volatile Method getTextMethod;
    private static final Map<Class<?>, Boolean> TEXT_DISPLAY_CLASS_CACHE = new ConcurrentHashMap<>();
    private static volatile Class<?> RESOLVED_TEXT_DISPLAY_CLASS;

    private TextDisplayUtil() {
    }

    public static boolean isTextDisplay(Entity entity) {
        Class<?> resolved = RESOLVED_TEXT_DISPLAY_CLASS;
        if (resolved != null && resolved.isInstance(entity)) {
            return true;
        }
        return TEXT_DISPLAY_CLASS_CACHE.computeIfAbsent(entity.getClass(), cls -> {
            String simple = cls.getSimpleName().toLowerCase();
            boolean isDisplay = simple.contains("textdisplay") || simple.contains("text_display");
            if (isDisplay) RESOLVED_TEXT_DISPLAY_CLASS = cls;
            return isDisplay;
        });
    }

    public static String getDisplayText(Entity entity) {
        if (isTextDisplay(entity)) {
            try {
                if (getTextMethod == null) {
                    getTextMethod = entity.getClass().getMethod("getText");
                }
                if (getTextMethod != null) {
                    var result = getTextMethod.invoke(entity);
                    if (result instanceof Text text) {
                        String raw = TextUtil.tryGetRawText(text);
                        return (raw != null ? raw : text.getString()).trim();
                    }
                }
            } catch (Exception e) {
                FteLogger.debug(FteLogger.TRACK, "getDisplayText failed: " + e.getMessage());
            }
        }
        var name = entity.getCustomName();
        if (name == null) return null;
        String raw = TextUtil.tryGetRawText(name);
        return (raw != null ? raw : name.getString()).trim();
    }
}
