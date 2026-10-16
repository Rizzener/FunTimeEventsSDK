package net.funtimeevents.poc.platform;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.level.Level;
import java.util.UUID;

public final class VersionAccess {
    private VersionAccess() {}
    public static String profileName(GameProfile profile) { return ProfileAccess.name(profile); }
    public static UUID profileId(GameProfile profile) { return ProfileAccess.id(profile); }
    public static String dimension(Level level) { return DimensionAccess.id(level); }
    public static net.minecraft.client.gui.components.BossHealthOverlay bossOverlay() {
        return BossOverlayAccess.get();
    }
}
