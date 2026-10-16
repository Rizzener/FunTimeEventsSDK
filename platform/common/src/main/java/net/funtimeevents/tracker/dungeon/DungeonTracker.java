package net.funtimeevents.tracker.dungeon;

import net.funtimeevents.model.ChestInfo;
import net.funtimeevents.model.DungeonPayload;
import net.funtimeevents.model.PlayerGearInfo;
import net.funtimeevents.spi.PayloadSender;
import net.funtimeevents.tracker.Tracker;
import net.funtimeevents.internal.game.ServerContext;
import net.funtimeevents.internal.tracker.TrackLog;
import net.funtimeevents.util.PlayerNameUtil;
import net.funtimeevents.util.TextDisplayUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

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
    private final ServerContext context;

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

    public DungeonTracker(PayloadSender sender, ServerContext context) {
        this.sender = sender;
        this.context = context;
    }

    @Override
    public void start() {
        TrackLog.info(TrackLog.TRACK, "DungeonTracker started");
    }

    @Override
    public void stop() {
        TrackLog.info(TrackLog.TRACK, "DungeonTracker stopped");
        donateCache.clear();
        donateCacheResetAt = 0L;
    }

    @Override
    public void tick() {
        ServerContext ctx = context;
        if (!ctx.isOnFuntime()) {
            return;
        }

        var client = Minecraft.getInstance();
        Level world = client.level;
        if (world == null) {
            return;
        }

        var player = client.player;
        if (player == null) {
            return;
        }

        BlockPos playerPos = player.blockPosition();
        Zone playerZone = findZone(playerPos.getX(), playerPos.getZ());
        if (playerZone == null || !playerZone.contains(playerPos.getX(), playerPos.getY(), playerPos.getZ())) {
            return;
        }

        int serverId = ctx.getServerId();
        if (serverId < 0) {
            TrackLog.warn(TrackLog.TRACK, "DungeonTracker skipped: serverId not resolved");
            return;
        }
        String serverType = ctx.getServerType();

        processZone(world, serverId, serverType, playerZone);
    }

    private void processZone(Level world, int serverId, String serverType, Zone zone) {
        List<PlayerGearInfo> players = scanPlayers(world, zone);
        List<ChestInfo> chests = scanChests(world, zone);

        if (!players.isEmpty() || !chests.isEmpty()) {
            TrackLog.info(TrackLog.TRACK, "Dungeon " + zone.name + ": " + players.size() + " players, " + chests.size() + " chests");

            for (PlayerGearInfo p : players) {
                TrackLog.debug(TrackLog.TRACK, "  Player: " + p.playerName() + " donate=" + p.donate()
                        + " helm=" + p.helmet() + " chest=" + p.chestplate()
                        + " legs=" + p.leggings() + " boots=" + p.boots()
                        + " invis=" + p.isInvisible());
            }
            for (ChestInfo c : chests) {
                TrackLog.debug(TrackLog.TRACK, "  Chest: [" + c.x() + ", " + c.y() + ", " + c.z() + "] time_left=" + c.timeLeft() + "s");
            }

            DungeonPayload payload = new DungeonPayload(serverId, serverType, chests, players);
            if (zone == COPPER_ZONE) {
                sender.sendCopperDungeon(payload);
            } else {
                sender.sendWardenCity(payload);
            }
        }
    }

    private List<PlayerGearInfo> scanPlayers(Level world, Zone zone) {
        long now = System.currentTimeMillis();
        if (now - donateCacheResetAt > 30_000L) {
            donateCache.clear();
            donateCacheResetAt = now;
        }

        List<PlayerGearInfo> result = new ArrayList<>();
        for (Player player : world.players()) {
            BlockPos pos = player.blockPosition();
            if (!zone.contains(pos.getX(), pos.getY(), pos.getZ())) {
                continue;
            }

            var info = PlayerNameUtil.resolvePlayer(player);
            if (info == null) continue;
            String name = info.name();
            String donate = donateCache.computeIfAbsent(
                    player.getUUID(),
                    id -> info.donate()
            );
            String helmet = itemName(player.getItemBySlot(EquipmentSlot.HEAD));
            String chestplate = itemName(player.getItemBySlot(EquipmentSlot.CHEST));
            String leggings = itemName(player.getItemBySlot(EquipmentSlot.LEGS));
            String boots = itemName(player.getItemBySlot(EquipmentSlot.FEET));
            boolean invisible = player.isInvisible();

            result.add(new PlayerGearInfo(name, donate, helmet, chestplate, leggings, boots, invisible));
        }
        return result;
    }

    private List<ChestInfo> scanChests(Level world, Zone zone) {
        Map<BlockPos, Integer> hologramMap = new HashMap<>();

        if (world instanceof ClientLevel clientWorld) {
            for (Entity e : clientWorld.entitiesForRendering()) {
                net.funtimeevents.internal.tracker.TrackerMetrics.entityVisited();
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

                BlockPos entityPos = e.blockPosition();
                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos blockPos = entityPos.below(dy);
                    net.funtimeevents.internal.tracker.TrackerMetrics.blockRead();
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
        if (stack == null || stack.isEmpty() || stack.is(Items.AIR)) {
            return "none";
        }
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getPath();
    }

    private Zone findZone(int x, int z) {
        if (WARDEN_ZONE.contains(x, z)) return WARDEN_ZONE;
        if (COPPER_ZONE.contains(x, z)) return COPPER_ZONE;
        return null;
    }
}
