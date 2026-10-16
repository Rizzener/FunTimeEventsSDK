package net.funtimeevents.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;

import com.mojang.authlib.GameProfile;
import net.funtimeevents.poc.platform.VersionAccess;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracts donator prefix from TAB player display names.
 *
 * <p>Used by {@code TabTracker}, {@code MineTracker}, and {@code DungeonTracker}.
 * Two overloads — one takes a {@link Player}, the other a {@link PlayerInfo}.
 */
public final class PlayerNameUtil {


    private static final Map<UUID, String> DONATE_CACHE = new ConcurrentHashMap<>();
    private static volatile long donateCacheResetAt = 0L;

    public static void clearSession() { DONATE_CACHE.clear(); donateCacheResetAt = 0L; }

    private PlayerNameUtil() {
    }

    public static String extractDonate(Player player) {
        var handler = Minecraft.getInstance().getConnection();
        if (handler != null) {
            var entry = handler.getPlayerInfo(player.getUUID());
            if (entry != null) {
                return extractDonate(entry);
            }
        }
        return "";
    }

    public static String extractDonate(net.minecraft.client.multiplayer.PlayerInfo entry) {
        UUID uuid = getProfileId(entry.getProfile());
        if (uuid == null) {
            return computeDonate(entry);
        }
        long now = System.currentTimeMillis();
        if (now - donateCacheResetAt > 30_000L) {
            DONATE_CACHE.clear();
            donateCacheResetAt = now;
        }
        return DONATE_CACHE.computeIfAbsent(uuid, id -> computeDonate(entry));
    }

    private static String computeDonate(net.minecraft.client.multiplayer.PlayerInfo entry) {
        if (entry.getTabListDisplayName() != null) {
            String displayName = entry.getTabListDisplayName().getString();
            String profileName = getProfileName(entry.getProfile());
            if (profileName == null || "?".equals(profileName)) return "";
            if (displayName.length() > profileName.length() && displayName.contains(profileName)) {
                return displayName.substring(0, displayName.indexOf(profileName)).trim();
            }
            return displayName;
        }
        return "";
    }

    public static String getProfileName(GameProfile profile) { return VersionAccess.profileName(profile); }

    public record PlayerInfo(String name, String donate) {}

    public static PlayerInfo resolvePlayer(Player player) {
        String name = getProfileName(player.getGameProfile());
        if (name == null) return null;
        String donate = extractDonate(player);
        if (!donate.startsWith("⚡")) return null;
        return new PlayerInfo(name, donate);
    }

    public static PlayerInfo resolvePlayer(net.minecraft.client.multiplayer.PlayerInfo entry) {
        String name = getProfileName(entry.getProfile());
        if (name == null) return null;
        String donate = extractDonate(entry);
        if (!donate.startsWith("⚡")) return null;
        return new PlayerInfo(name, donate);
    }

    public static UUID getProfileId(GameProfile profile) { return VersionAccess.profileId(profile); }
}
