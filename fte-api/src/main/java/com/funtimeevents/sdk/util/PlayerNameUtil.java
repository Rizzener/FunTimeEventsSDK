package com.funtimeevents.sdk.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

public final class PlayerNameUtil {

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
            String profileName = entry.getProfile().getName();
            if (displayName.length() > profileName.length() && displayName.contains(profileName)) {
                return displayName.substring(0, displayName.indexOf(profileName)).trim();
            }
            return displayName;
        }
        return "";
    }
}
