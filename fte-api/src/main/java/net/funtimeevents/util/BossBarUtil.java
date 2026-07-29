package net.funtimeevents.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public final class BossBarUtil {

    private static volatile Field bossBarsField;
    private static volatile Method getNameMethod;

    private BossBarUtil() {
    }

    public static Map<?, ?> getBossBars() {
        Field field = resolveBossBarsField();
        if (field == null) return null;
        try {
            var bossBarHud = MinecraftClient.getInstance().inGameHud.getBossBarHud();
            return (Map<?, ?>) field.get(bossBarHud);
        } catch (Exception e) {
            return null;
        }
    }

    public static Text getBossBarName(Object bar) {
        Method method = resolveGetNameMethod(bar);
        if (method == null) return null;
        try {
            return (Text) method.invoke(bar);
        } catch (Exception e) {
            return null;
        }
    }

    private static Field resolveBossBarsField() {
        if (bossBarsField != null) return bossBarsField;
        try {
            var bossBarHud = MinecraftClient.getInstance().inGameHud.getBossBarHud();
            var field = bossBarHud.getClass().getDeclaredField("bossBars");
            field.setAccessible(true);
            bossBarsField = field;
        } catch (Exception e) {
            FteLogger.error(FteLogger.TRACK, "bossBars field not found: " + e.getMessage());
        }
        return bossBarsField;
    }

    private static Method resolveGetNameMethod(Object bar) {
        if (getNameMethod != null) return getNameMethod;
        try {
            getNameMethod = bar.getClass().getMethod("getName");
        } catch (Exception e) {
            FteLogger.error(FteLogger.TRACK, "getName method not found: " + e.getMessage());
        }
        return getNameMethod;
    }
}
