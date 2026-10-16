package net.funtimeevents.poc.platform;
import net.minecraft.world.level.Level;
final class DimensionAccess {
    static String id(Level level) { return level.dimension().identifier().toString(); }
}
