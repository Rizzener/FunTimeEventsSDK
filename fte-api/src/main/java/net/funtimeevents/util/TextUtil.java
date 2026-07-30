package net.funtimeevents.util;

import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TextUtil {

    private static volatile Method GET_CONTENT_METHOD;
    private static volatile Method LITERAL_STRING_METHOD;
    private static final Map<Class<?>, Boolean> PLAIN_TEXT_CACHE = new ConcurrentHashMap<>();

    private TextUtil() {
    }

    public static String tryGetRawText(Text text) {
        try {
            if (GET_CONTENT_METHOD == null) {
                GET_CONTENT_METHOD = Text.class.getMethod("getContent");
            }
            Object content = GET_CONTENT_METHOD.invoke(text);
            if (content == null) return null;

            Class<?> cls = content.getClass();
            Boolean isPlain = PLAIN_TEXT_CACHE.computeIfAbsent(cls, c -> {
                String name = c.getName();
                return name.contains("PlainTextContent") || name.contains("Literal");
            });

            if (Boolean.TRUE.equals(isPlain)) {
                if (LITERAL_STRING_METHOD == null)
                    LITERAL_STRING_METHOD = cls.getMethod("string");
                return (String) LITERAL_STRING_METHOD.invoke(content);
            }
        } catch (Exception e) {
            FteLogger.debug(FteLogger.TRACK, "tryGetRawText failed: " + e.getMessage());
        }
        return null;
    }
}
