package com.funtimeevents.sdk.tracker.dungeon;

import com.funtimeevents.sdk.model.ChestInfo;
import com.funtimeevents.sdk.model.DungeonPayload;
import com.funtimeevents.sdk.model.PlayerGearInfo;
import com.funtimeevents.sdk.tracker.Tracker;
import com.funtimeevents.sdk.tracker.tabheader.TabHeaderTracker;
import com.funtimeevents.sdk.util.FteLogger;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DungeonTracker implements Tracker {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+):(\\d\\d)");

    private static final Zone WARDEN_ZONE = new Zone("Warden", -2100, -1900, -2100, -1900, -60, 0);
    private static final Zone COPPER_ZONE = new Zone("Copper", 1900, 2100, 1900, 2100, -60, 5);

    private record Zone(String name, int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        boolean contains(int x, int y, int z) {
            return contains(x, z) && y >= minY && y <= maxY;
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void tick() {
        TabHeaderTracker header = TabHeaderTracker.getInstance();
        if (!header.isOnFuntime()) {
            return;
        }

        var client = MinecraftClient.getInstance();
        World world = client.world;
        if (world == null) {
            return;
        }

        int serverId = header.getServerId();
        String serverType = header.getServerType();

        processZone(world, serverId, serverType, COPPER_ZONE);
        processZone(world, serverId, serverType, WARDEN_ZONE);
    }

    private void processZone(World world, int serverId, String serverType, Zone zone) {
        FteLogger.info("=== " + zone.name + " zone x[" + zone.minX + "," + zone.maxX + "] z[" + zone.minZ + "," + zone.maxZ + "] y[" + zone.minY + "," + zone.maxY + "] ===");

        List<PlayerGearInfo> players = scanPlayers(world, zone);
        List<ChestInfo> chests = scanChests(world, zone);

        FteLogger.info("Dungeon " + zone.name + ": " + players.size() + " players, " + chests.size() + " chests");

        for (PlayerGearInfo p : players) {
            FteLogger.info("  Player: " + p.playerName() + " donate=" + p.donate()
                    + " helm=" + p.helmet() + " chest=" + p.chestplate()
                    + " legs=" + p.leggings() + " boots=" + p.boots()
                    + " invis=" + p.isInvisible());
        }
        for (ChestInfo c : chests) {
            FteLogger.info("  Chest: [" + c.x() + ", " + c.y() + ", " + c.z() + "] time_left=" + c.timeLeft() + "s");
        }

        if (!players.isEmpty() || !chests.isEmpty()) {
            DungeonPayload payload = new DungeonPayload(serverId, serverType, chests, players);
            if (zone == COPPER_ZONE) {
                DungeonBroadcaster.publishCopper(payload);
            } else {
                DungeonBroadcaster.publishWarden(payload);
            }
        }
    }

    private List<PlayerGearInfo> scanPlayers(World world, Zone zone) {
        List<PlayerGearInfo> result = new ArrayList<>();
        for (PlayerEntity player : world.getPlayers()) {
            BlockPos pos = player.getBlockPos();
            if (!zone.contains(pos.getX(), pos.getY(), pos.getZ())) {
                continue;
            }

            String name = player.getGameProfile().getName();
            String donate = extractDonate(player);
            String helmet = itemName(player.getEquippedStack(EquipmentSlot.HEAD));
            String chestplate = itemName(player.getEquippedStack(EquipmentSlot.CHEST));
            String leggings = itemName(player.getEquippedStack(EquipmentSlot.LEGS));
            String boots = itemName(player.getEquippedStack(EquipmentSlot.FEET));
            boolean invisible = player.isInvisible();

            FteLogger.info("  Player scan: " + name + " pos=[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]"
                    + " helm=" + helmet + " chest=" + chestplate + " legs=" + leggings + " boots=" + boots
                    + " invis=" + invisible + " donate=" + donate);

            result.add(new PlayerGearInfo(name, donate, helmet, chestplate, leggings, boots, invisible));
        }
        return result;
    }

    private List<ChestInfo> scanChests(World world, Zone zone) {
        Map<BlockPos, Integer> hologramMap = new HashMap<>();

        List<Entity> entities = new ArrayList<>();
        if (world instanceof ClientWorld clientWorld) {
            for (Entity e : clientWorld.getEntities()) {
                entities.add(e);
            }
        }

        int totalEntities = entities.size();
        int timeMatches = 0;
        int foundChests = 0;

        for (Entity entity : entities) {
            String text = getDisplayText(entity);
            if (text == null || text.isEmpty()) {
                continue;
            }
            Matcher m = TIME_PATTERN.matcher(text);
            if (!m.matches()) {
                continue;
            }
            timeMatches++;
            int minutes = Integer.parseInt(m.group(1));
            int seconds = Integer.parseInt(m.group(2));
            int timeLeft = minutes * 60 + seconds;

            BlockPos entityPos = entity.getBlockPos();
            boolean inZone = zone.contains(entityPos.getX(), entityPos.getZ());
            String zoneMarker = inZone ? " [IN ZONE]" : " [OUTSIDE]";
            String entityType = entity.getType().getName().getString();
            FteLogger.info("  HOLO: '" + text + "' type=" + entityType + " at [" + entityPos.getX() + "," + entityPos.getY() + "," + entityPos.getZ() + "] " + timeLeft + "s" + zoneMarker);

            int chestsUnder = 0;
            for (int dy = 1; dy <= 3; dy++) {
                BlockPos blockPos = entityPos.down(dy);
                var block = world.getBlockState(blockPos).getBlock();
                if (block == Blocks.CHEST || block == Blocks.BARREL || block == Blocks.TRAPPED_CHEST) {
                    chestsUnder++;
                    foundChests++;
                    hologramMap.putIfAbsent(blockPos, timeLeft);
                    FteLogger.info("    CHEST found at [" + blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ() + "] time_left=" + timeLeft + "s");
                }
            }
            if (chestsUnder == 0) {
                FteLogger.info("    No chest under (blocks: dy=1:" + world.getBlockState(entityPos.down(1)).getBlock().getTranslationKey()
                        + " dy=2:" + world.getBlockState(entityPos.down(2)).getBlock().getTranslationKey()
                        + " dy=3:" + world.getBlockState(entityPos.down(3)).getBlock().getTranslationKey() + ")");
            }
        }

        int inZoneChests = 0;
        int outsideZoneChests = 0;
        List<ChestInfo> result = new ArrayList<>();
        for (var entry : hologramMap.entrySet()) {
            BlockPos pos = entry.getKey();
            if (zone.contains(pos.getX(), pos.getY(), pos.getZ())) {
                inZoneChests++;
                result.add(new ChestInfo(pos.getX(), pos.getY(), pos.getZ(), entry.getValue()));
            } else {
                outsideZoneChests++;
                FteLogger.info("  Chest [" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "] outside " + zone.name + " zone");
            }
        }

        FteLogger.info("  SCAN: " + totalEntities + " entities, " + timeMatches + " time_matches, " + foundChests + " chests_found, " + inZoneChests + " in zone");

        return result;
    }

    private String getDisplayText(Entity entity) {
        String className = entity.getClass().getSimpleName().toLowerCase();
        if (className.contains("textdisplay") || className.contains("text_display")) {
            try {
                var method = entity.getClass().getMethod("getText");
                var result = method.invoke(entity);
                if (result instanceof Text text) {
                    return text.getString().trim();
                }
            } catch (Exception ignored) {
            }
        }
        var name = entity.getCustomName();
        return name != null ? name.getString().trim() : null;
    }

    private String itemName(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.isOf(Items.AIR)) {
            return "none";
        }
        var id = Registries.ITEM.getId(stack.getItem());
        return id.getPath();
    }

    private String extractDonate(PlayerEntity player) {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler != null) {
            PlayerListEntry entry = handler.getPlayerListEntry(player.getUuid());
            if (entry != null && entry.getDisplayName() != null) {
                String displayName = entry.getDisplayName().getString();
                String profileName = player.getGameProfile().getName();
                if (displayName.length() > profileName.length() && displayName.contains(profileName)) {
                    return displayName.substring(0, displayName.indexOf(profileName)).trim();
                }
                return displayName;
            }
        }
        return "";
    }
}
