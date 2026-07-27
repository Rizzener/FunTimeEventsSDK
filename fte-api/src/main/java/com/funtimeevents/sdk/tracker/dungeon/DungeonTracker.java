package com.funtimeevents.sdk.tracker.dungeon;

import com.funtimeevents.sdk.model.ChestInfo;
import com.funtimeevents.sdk.model.DungeonPayload;
import com.funtimeevents.sdk.model.PlayerGearInfo;
import com.funtimeevents.sdk.spi.PayloadSender;
import com.funtimeevents.sdk.tracker.Tracker;
import com.funtimeevents.sdk.tracker.tabheader.TabHeaderTracker;
import com.funtimeevents.sdk.util.FteLogger;
import com.funtimeevents.sdk.util.PlayerNameUtil;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
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

    private static volatile java.lang.reflect.Method getTextMethod;

    private final PayloadSender sender;

    private record Zone(String name, int minX, int maxX, int minZ, int maxZ, int minY, int maxY) {
        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        boolean contains(int x, int y, int z) {
            return contains(x, z) && y >= minY && y <= maxY;
        }
    }

    public DungeonTracker(PayloadSender sender) {
        this.sender = sender;
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

        var player = client.player;
        if (player == null) {
            return;
        }

        BlockPos playerPos = player.getBlockPos();
        Zone playerZone = findZone(playerPos.getX(), playerPos.getZ());
        if (playerZone == null || !playerZone.contains(playerPos.getX(), playerPos.getY(), playerPos.getZ())) {
            return;
        }

        int serverId = header.getServerId();
        String serverType = header.getServerType();

        processZone(world, serverId, serverType, playerZone);
    }

    private void processZone(World world, int serverId, String serverType, Zone zone) {
        List<PlayerGearInfo> players = scanPlayers(world, zone);
        List<ChestInfo> chests = scanChests(world, zone);

        if (!players.isEmpty() || !chests.isEmpty()) {
            FteLogger.info("Dungeon " + zone.name + ": " + players.size() + " players, " + chests.size() + " chests");

            for (PlayerGearInfo p : players) {
                FteLogger.debug("  Player: " + p.playerName() + " donate=" + p.donate()
                        + " helm=" + p.helmet() + " chest=" + p.chestplate()
                        + " legs=" + p.leggings() + " boots=" + p.boots()
                        + " invis=" + p.isInvisible());
            }
            for (ChestInfo c : chests) {
                FteLogger.debug("  Chest: [" + c.x() + ", " + c.y() + ", " + c.z() + "] time_left=" + c.timeLeft() + "s");
            }

            DungeonPayload payload = new DungeonPayload(serverId, serverType, chests, players);
            if (zone == COPPER_ZONE) {
                sender.sendCopperDungeon(payload);
            } else {
                sender.sendWardenCity(payload);
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
            String donate = PlayerNameUtil.extractDonate(player);
            String helmet = itemName(player.getEquippedStack(EquipmentSlot.HEAD));
            String chestplate = itemName(player.getEquippedStack(EquipmentSlot.CHEST));
            String leggings = itemName(player.getEquippedStack(EquipmentSlot.LEGS));
            String boots = itemName(player.getEquippedStack(EquipmentSlot.FEET));
            boolean invisible = player.isInvisible();

            result.add(new PlayerGearInfo(name, donate, helmet, chestplate, leggings, boots, invisible));
        }
        return result;
    }

    private List<ChestInfo> scanChests(World world, Zone zone) {
        Map<BlockPos, Integer> hologramMap = new HashMap<>();

        if (world instanceof ClientWorld clientWorld) {
            for (Entity e : clientWorld.getEntities()) {
                String text = getDisplayText(e);
                if (text == null || text.isEmpty()) {
                    continue;
                }
                Matcher m = TIME_PATTERN.matcher(text);
                if (!m.matches()) {
                    continue;
                }
                int minutes = Integer.parseInt(m.group(1));
                int seconds = Integer.parseInt(m.group(2));
                int timeLeft = minutes * 60 + seconds;

                BlockPos entityPos = e.getBlockPos();
                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos blockPos = entityPos.down(dy);
                    var block = world.getBlockState(blockPos).getBlock();
                    if (block == Blocks.CHEST || block == Blocks.BARREL || block == Blocks.TRAPPED_CHEST) {
                        hologramMap.putIfAbsent(blockPos, timeLeft);
                    }
                }
            }
        }

        List<ChestInfo> result = new ArrayList<>();
        for (var entry : hologramMap.entrySet()) {
            BlockPos pos = entry.getKey();
            if (zone.contains(pos.getX(), pos.getY(), pos.getZ())) {
                result.add(new ChestInfo(pos.getX(), pos.getY(), pos.getZ(), entry.getValue()));
            }
        }
        return result;
    }

    private String getDisplayText(Entity entity) {
        String className = entity.getClass().getSimpleName().toLowerCase();
        if (className.contains("textdisplay") || className.contains("text_display")) {
            try {
                if (getTextMethod == null) {
                    getTextMethod = entity.getClass().getMethod("getText");
                }
                if (getTextMethod != null) {
                    var result = getTextMethod.invoke(entity);
                    if (result instanceof Text text) {
                        return text.getString().trim();
                    }
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

    private Zone findZone(int x, int z) {
        if (WARDEN_ZONE.contains(x, z)) return WARDEN_ZONE;
        if (COPPER_ZONE.contains(x, z)) return COPPER_ZONE;
        return null;
    }
}
