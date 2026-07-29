package net.funtimeevents.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.Method;

/**
 * Extracts donator prefix from TAB player display names.
 *
 * <p>Used by {@code TabTracker}, {@code MineTracker}, and {@code DungeonTracker}.
 * Two overloads — one takes a {@link PlayerEntity}, the other a {@link PlayerListEntry}.
 */
public final class PlayerNameUtil {

    private static volatile Method profileNameMethod;

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
        if (entry.getDisplayName() != null) {
            String displayName = entry.getDisplayName().getString();
            String profileName = getProfileName(entry.getProfile());
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
            return "?";
        }
    }
}
