package net.funtimeevents.poc.platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
final class BossOverlayAccess {
    static BossHealthOverlay get() { return Minecraft.getInstance().gui.getBossOverlay(); }
}
