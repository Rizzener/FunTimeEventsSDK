package net.funtimeevents.internal.platform;
import net.minecraft.world.level.Level;
final class DimensionAccess {
    static String id(Level level) { return level.dimension().location().toString(); }
}
