package net.funtimeevents.poc.fixture;
import net.minecraft.world.level.Level;
public final class FixtureVersion {
    private FixtureVersion() {}
    public static String dimension(Level level) { return level.dimension().identifier().toString(); }
    public static net.minecraft.world.entity.Display.TextDisplay text(net.minecraft.server.level.ServerLevel world) {
        return new net.minecraft.world.entity.Display.TextDisplay(net.minecraft.world.entity.EntityType.TEXT_DISPLAY, world);
    }
    public static net.minecraft.world.entity.decoration.ArmorStand decoy(net.minecraft.server.level.ServerLevel world) {
        return new net.minecraft.world.entity.decoration.ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND, world);
    }
    public static net.minecraft.server.level.ServerBossEvent boss(net.minecraft.network.chat.Component text, net.minecraft.world.BossEvent.BossBarColor color) {
        return new net.minecraft.server.level.ServerBossEvent(java.util.UUID.randomUUID(), text, color, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
    }
}
