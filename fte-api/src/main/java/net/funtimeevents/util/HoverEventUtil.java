package net.funtimeevents.util;

import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.Optional;

public final class HoverEventUtil {

    private static volatile Method GET_ACTION_METHOD;
    private static volatile Method GET_VALUE_METHOD;

    private HoverEventUtil() {
    }

    public static String extractHoverText(Text message) {
        return findHoverText(message).orElse("");
    }

    private static Optional<String> findHoverText(Text component) {
        var hoverEvent = component.getStyle().getHoverEvent();
        if (hoverEvent != null) {
            try {
                Method getAction = resolveGetAction(hoverEvent);
                if (getAction == null) return Optional.empty();
                Object action = getAction.invoke(hoverEvent);
                if (action != null && action.toString().contains("show_text")) {
                    Method getValue = resolveGetValue(hoverEvent, action.getClass());
                    if (getValue == null) return Optional.empty();
                    Object value = getValue.getParameterCount() > 0
                            ? getValue.invoke(hoverEvent, action)
                            : getValue.invoke(hoverEvent);
                    if (value instanceof Text hoverText) {
                        String raw = TextUtil.tryGetRawText(hoverText);
                        return Optional.ofNullable(raw != null ? raw : hoverText.getString());
                    }
                }
            } catch (Exception e) {
                FteLogger.debug(FteLogger.TRACK, "Hover text extraction failed: " + e.getMessage());
            }
        }
        for (Text sibling : component.getSiblings()) {
            var result = findHoverText(sibling);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    private static Method resolveGetAction(Object hoverEvent) {
        if (GET_ACTION_METHOD != null) return GET_ACTION_METHOD;
        try {
            GET_ACTION_METHOD = hoverEvent.getClass().getMethod("getAction");
        } catch (Exception e) {
            FteLogger.debug(FteLogger.TRACK, "getAction method not found: " + e.getMessage());
        }
        return GET_ACTION_METHOD;
    }

    private static Method resolveGetValue(Object hoverEvent, Class<?> actionClass) {
        if (GET_VALUE_METHOD != null) return GET_VALUE_METHOD;
        try {
            GET_VALUE_METHOD = hoverEvent.getClass().getMethod("getValue", actionClass);
        } catch (NoSuchMethodException e) {
            try {
                GET_VALUE_METHOD = hoverEvent.getClass().getMethod("value");
            } catch (NoSuchMethodException e2) {
                FteLogger.debug(FteLogger.TRACK, "getValue/value method not found");
            }
        }
        return GET_VALUE_METHOD;
    }
}
