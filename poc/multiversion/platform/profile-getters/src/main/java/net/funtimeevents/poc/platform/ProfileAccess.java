package net.funtimeevents.poc.platform;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
final class ProfileAccess {
    static String name(GameProfile profile) { return profile.getName(); }
    static UUID id(GameProfile profile) { return profile.getId(); }
}
