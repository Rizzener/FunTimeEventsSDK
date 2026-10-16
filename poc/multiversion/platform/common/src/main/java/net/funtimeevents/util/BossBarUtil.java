package net.funtimeevents.util;

import net.funtimeevents.poc.platform.mixin.BossHealthOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import java.util.Map;

public final class BossBarUtil {
    private BossBarUtil() {}
    public static Map<?, ?> getBossBars() {
        return ((BossHealthOverlayAccessor) net.funtimeevents.poc.platform.VersionAccess.bossOverlay()).fte$getEvents();
    }
    public static Component getBossBarName(Object bar) {
        return bar instanceof BossEvent event ? event.getName() : null;
    }
}
