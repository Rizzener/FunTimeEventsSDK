package net.funtimeevents.tracker.dungeon;

import net.funtimeevents.model.ChestInfo;
import net.funtimeevents.model.DungeonPayload;
import net.funtimeevents.model.PlayerGearInfo;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.tracker.Tracker;
import net.funtimeevents.tracker.server.ServerContext;
import net.funtimeevents.util.FteLogger;
import net.funtimeevents.util.PlayerNameUtil;
import net.funtimeevents.util.TextDisplayUtil;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DungeonTracker implements Tracker {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+):(\\d\\d)");

    private static final Zone WARDEN_ZONE = new Zone("Warden", -2100, -1900, -2100, -1900, -60, 0);
    private static final Zone COPPER_ZONE = new Zone("Copper", 1900, 2100, 1900, 2100, -60, 5);

    private final PayloadSender sender;

    private final Map<UUID, String> donateCache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, String> eldest) {
                    return size() > 128;
                }
            }
    );
    private volatile long donateCacheResetAt = 0L;

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
        FteLogger.info(FteLogger.TRACK, "DungeonTracker started");
    }

    @Override
    public void stop() {
        FteLogger.info(FteLogger.TRACK, "DungeonTracker stopped");
    }

    @Override
    public void tick() {
        ServerContext ctx = ServerContext.getInstance();
        if (!ctx.isOnFuntime()) {
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

        int serverId = ctx.getServerId();
        String serverType = ctx.getServerType();

        processZone(world, serverId, serverType, playerZone);
    }

    private void processZone(World world, int serverId, String serverType, Zone zone) {
        List<PlayerGearInfo> players = scanPlayers(world, zone);
        List<ChestInfo> chests = scanChests(world, zone);

        if (!players.isEmpty() || !chests.isEmpty()) {
            FteLogger.info(FteLogger.TRACK, "Dungeon " + zone.name + ": " + players.size() + " players, " + chests.size() + " chests");

            for (PlayerGearInfo p : players) {
                FteLogger.debug(FteLogger.TRACK, "  Player: " + p.playerName() + " donate=" + p.donate()
                        + " helm=" + p.helmet() + " chest=" + p.chestplate()
                        + " legs=" + p.leggings() + " boots=" + p.boots()
                        + " invis=" + p.isInvisible());
            }
            for (ChestInfo c : chests) {
                FteLogger.debug(FteLogger.TRACK, "  Chest: [" + c.x() + ", " + c.y() + ", " + c.z() + "] time_left=" + c.timeLeft() + "s");
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
        long now = System.currentTimeMillis();
        if (now - donateCacheResetAt > 30_000L) {
            donateCache.clear();
            donateCacheResetAt = now;
        }

        List<PlayerGearInfo> result = new ArrayList<>();
        for (PlayerEntity player : world.getPlayers()) {
            BlockPos pos = player.getBlockPos();
            if (!zone.contains(pos.getX(), pos.getY(), pos.getZ())) {
                continue;
            }

            String name = PlayerNameUtil.getProfileName(player.getGameProfile());
            String donate = donateCache.computeIfAbsent(
                    player.getUuid(),
                    id -> PlayerNameUtil.extractDonate(player)
            );
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
                String text = TextDisplayUtil.getDisplayText(e);
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
