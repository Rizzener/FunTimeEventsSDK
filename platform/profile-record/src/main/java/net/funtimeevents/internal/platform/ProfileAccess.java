package net.funtimeevents.internal.platform;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
final class ProfileAccess {
    static String name(GameProfile profile) { return profile.name(); }
    static UUID id(GameProfile profile) { return profile.id(); }
}
