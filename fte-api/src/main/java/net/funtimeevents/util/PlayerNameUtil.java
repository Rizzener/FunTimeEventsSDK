package net.funtimeevents.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracts donator prefix from TAB player display names.
 *
 * <p>Used by {@code TabTracker}, {@code MineTracker}, and {@code DungeonTracker}.
 * Two overloads — one takes a {@link PlayerEntity}, the other a {@link PlayerListEntry}.
 */
public final class PlayerNameUtil {

    private static volatile Method profileNameMethod;
    private static volatile Method profileIdMethod;

    private static final Map<UUID, String> DONATE_CACHE = new ConcurrentHashMap<>();
    private static volatile long donateCacheResetAt = 0L;

    private PlayerNameUtil() {
    }

    public static String extractDonate(PlayerEntity player) {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler != null) {
            var entry = handler.getPlayerListEntry(player.getUuid());
            if (entry != null) {
                return extractDonate(entry);
            }
        }
        return "";
    }

    public static String extractDonate(PlayerListEntry entry) {
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

    private static String computeDonate(PlayerListEntry entry) {
        if (entry.getDisplayName() != null) {
            String displayName = entry.getDisplayName().getString();
            String profileName = getProfileName(entry.getProfile());
            if (profileName == null || "?".equals(profileName)) return "";
            if (displayName.length() > profileName.length() && displayName.contains(profileName)) {
                return displayName.substring(0, displayName.indexOf(profileName)).trim();
            }
            return displayName;
        }
        return "";
    }

    public static String getProfileName(Object profile) {
        try {
            if (profileNameMethod == null) {
                try {
                    profileNameMethod = profile.getClass().getMethod("getName");
                } catch (NoSuchMethodException e) {
                    profileNameMethod = profile.getClass().getMethod("name");
                }
            }
            return (String) profileNameMethod.invoke(profile);
        } catch (Exception e) {
            FteLogger.warn(FteLogger.TRACK, "failed to get profile name: " + e.getMessage());
            return null;
        }
    }

    public record PlayerInfo(String name, String donate) {}

    public static PlayerInfo resolvePlayer(PlayerEntity player) {
        String name = getProfileName(player.getGameProfile());
        if (name == null) return null;
        String donate = extractDonate(player);
        if (!donate.startsWith("⚡")) return null;
        return new PlayerInfo(name, donate);
    }

    public static PlayerInfo resolvePlayer(PlayerListEntry entry) {
        String name = getProfileName(entry.getProfile());
        if (name == null) return null;
        String donate = extractDonate(entry);
        if (!donate.startsWith("⚡")) return null;
        return new PlayerInfo(name, donate);
    }

    public static UUID getProfileId(Object profile) {
        try {
            if (profileIdMethod == null) {
                try {
                    profileIdMethod = profile.getClass().getMethod("getId");
                } catch (NoSuchMethodException e) {
                    profileIdMethod = profile.getClass().getMethod("id");
                }
            }
            return (UUID) profileIdMethod.invoke(profile);
        } catch (Exception e) {
            FteLogger.warn(FteLogger.TRACK, "failed to get profile id: " + e.getMessage());
            return null;
        }
    }
}
