"""Mechanical Yarn -> Mojmap port of the release algorithms; no writes by default.

The few lifecycle changes are explicit here, so parity checks cannot silently
accept hand-edited zones, patterns, player filters, or scan order.
"""
from pathlib import Path
import argparse
import hashlib
import json

ROOT = Path(__file__).resolve().parent
RELEASE = ROOT.parents[1] / "fte-api/src/main/java/net/funtimeevents"
CORE = ROOT / "core/src/main/java/net/funtimeevents"
COMMON = ROOT / "platform/common/src/main/java/net/funtimeevents"
COPIES = ["spi/PayloadSender.java", "tracker/Tracker.java", "scheduler/Scheduler.java"] + [
    "model/" + name + ".java" for name in ["DungeonPayload", "ChestInfo", "PlayerGearInfo", "TabPlayersPayload",
    "ObservedPlayer", "MinePlayersAroundPayload", "HellMapPayload", "EventCoordinatesPayload", "EventCoordinates"]]
TRACKERS = ["dungeon/DungeonTracker", "hell/HellMapTracker", "tab/TabTracker", "mine/MineTracker", "eventcoordinates/EventCoordinatesTracker"]


def port(relative):
    text = (RELEASE / relative).read_text(encoding="utf-8")
    replacements = {
        "net.funtimeevents.tracker.server.ServerContext": "net.funtimeevents.poc.slice.ServerContext",
        "net.funtimeevents.util.FteLogger": "net.funtimeevents.poc.trackers.TrackLog",
        "FteLogger": "TrackLog",
        "net.minecraft.client.MinecraftClient": "net.minecraft.client.Minecraft",
        "net.minecraft.client.world.ClientWorld": "net.minecraft.client.multiplayer.ClientLevel",
        "net.minecraft.client.network.PlayerListEntry": "net.minecraft.client.multiplayer.PlayerInfo",
        "net.minecraft.entity.player.PlayerEntity": "net.minecraft.world.entity.player.Player",
        "net.minecraft.entity.EquipmentSlot": "net.minecraft.world.entity.EquipmentSlot",
        "net.minecraft.entity.Entity": "net.minecraft.world.entity.Entity",
        "net.minecraft.block.Blocks": "net.minecraft.world.level.block.Blocks",
        "net.minecraft.item.": "net.minecraft.world.item.",
        "net.minecraft.registry.Registries": "net.minecraft.core.registries.BuiltInRegistries",
        "net.minecraft.util.math.BlockPos": "net.minecraft.core.BlockPos",
        "net.minecraft.world.World": "net.minecraft.world.level.Level",
        "net.minecraft.text.Text": "net.minecraft.network.chat.Component",
        "MinecraftClient": "Minecraft", "ClientWorld": "ClientLevel", "PlayerListEntry": "PlayerInfo",
        "PlayerEntity": "Player", "World world": "Level world", "Text name": "Component name",
        "client.world": "client.level", ".getNetworkHandler()": ".getConnection()",
        ".getPlayerListEntry(": ".getPlayerInfo(", ".getPlayerList()": ".getOnlinePlayers()",
        ".getDisplayName()": ".getTabListDisplayName()", ".getProfile()": ".getProfile()",
        ".getBlockPos()": ".blockPosition()", ".getUuid()": ".getUUID()",
        ".getPlayers()": ".players()", ".getEntities()": ".entitiesForRendering()",
        ".getEquippedStack(": ".getItemBySlot(", ".isOf(": ".is(", ".down(": ".below(",
        "Registries.ITEM.getId(": "BuiltInRegistries.ITEM.getKey(",
        "world.getRegistryKey().getValue().toString()": "net.funtimeevents.poc.platform.VersionAccess.dimension(world)",
        "ServerContext.getInstance()": "context",
    }
    for before, after in replacements.items():
        text = text.replace(before, after)
    if relative.startswith("tracker/") and "tab/" not in relative:
        text = text.replace("private final PayloadSender sender;", "private final PayloadSender sender;\n    private final ServerContext context;")
        name = Path(relative).stem
        text = text.replace(f"public {name}(PayloadSender sender)", f"public {name}(PayloadSender sender, ServerContext context)")
        text = text.replace("this.sender = sender;", "this.sender = sender;\n        this.context = context;")
    if "DungeonTracker" in relative:
        # Session invalidation is the only change to its cache lifecycle.
        text = text.replace('TrackLog.info(TrackLog.TRACK, "DungeonTracker stopped");',
                            'TrackLog.info(TrackLog.TRACK, "DungeonTracker stopped");\n        donateCache.clear();\n        donateCacheResetAt = 0L;')
        text = text.replace("for (Entity e : clientWorld.entitiesForRendering()) {", "for (Entity e : clientWorld.entitiesForRendering()) {\n                net.funtimeevents.poc.trackers.TrackerMetrics.entityVisited();")
        text = text.replace("var block = world.getBlockState(blockPos).getBlock();", "net.funtimeevents.poc.trackers.TrackerMetrics.blockRead();\n                    var block = world.getBlockState(blockPos).getBlock();")
    if "HellMapTracker" in relative:
        start = text.index("        ClientTickEvents.END_CLIENT_TICK.register")
        end = text.index("\n    }", start)
        text = text[:start] + text[end:]
        insertion = '''
    /** Called by the one platform listener, with the original 100-tick cadence. */
    public void clientTick() {
        if (!active || !context.isOnFuntime()) return;
        tickCounter++;
        if (tickCounter >= TICK_INTERVAL) {
            tickCounter = 0;
            long started = net.funtimeevents.poc.trackers.TrackerMetrics.begin();
            doScan();
            net.funtimeevents.poc.trackers.TrackerMetrics.end("hell", started);
        }
    }
'''
        text = text.replace("    @Override\n    public void start()", insertion + "\n    @Override\n    public void start()", 1)
        text = text.replace("import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;\n", "")
    if "EventCoordinatesTracker" in relative:
        start = text.index("        ClientReceiveMessageEvents.GAME.register")
        end = text.index("\n    }", start)
        text = text[:start] + text[end:]
        text = text.replace("private void handleMessage(Text message)", "public void handleMessage(net.funtimeevents.poc.slice.IncomingMessage message)")
        text = text.replace("String quick = TextUtil.tryGetRawText(message);", "if (!active || !context.isOnFuntime() || !message.isGame()) return;\n        String quick = message.rawText();")
        text = text.replace("String text = message.getString();", "String text = message.plainText();")
        text = text.replace("import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;\n", "")
    return text


def port_player_names():
    text = port('util/PlayerNameUtil.java')
    text = text.replace('    private static volatile Method profileNameMethod;\n    private static volatile Method profileIdMethod;\n', '')
    text = text.replace('import java.lang.reflect.Method;', 'import com.mojang.authlib.GameProfile;\nimport net.funtimeevents.poc.platform.VersionAccess;')
    for name, typ, body in [('getProfileName', 'String', 'VersionAccess.profileName(profile)'), ('getProfileId', 'UUID', 'VersionAccess.profileId(profile)')]:
        start = text.index('    public static ' + typ + ' ' + name + '(')
        brace = text.index('{', start)
        depth, end = 1, brace + 1
        while depth:
            depth += (text[end] == '{') - (text[end] == '}')
            end += 1
        text = text[:start] + f'    public static {typ} {name}(GameProfile profile) {{ return {body}; }}' + text[end:]
    text = text.replace('    private PlayerNameUtil()', '    public static void clearSession() { DONATE_CACHE.clear(); donateCacheResetAt = 0L; }\n\n    private PlayerNameUtil()')
    for method in ['extractDonate', 'computeDonate', 'resolvePlayer']:
        text = text.replace(method + '(PlayerInfo entry)', method + '(net.minecraft.client.multiplayer.PlayerInfo entry)')
    return text


def expected_files():
    for relative in COPIES:
        yield RELEASE / relative, CORE / relative, (RELEASE / relative).read_bytes()
    for tracker in TRACKERS:
        relative = "tracker/" + tracker + ".java"
        yield RELEASE / relative, COMMON / relative, port(relative).encode("utf-8")
    yield RELEASE / 'util/PlayerNameUtil.java', COMMON / 'util/PlayerNameUtil.java', port_player_names().encode('utf-8')


def matches(target, expected):
    if not target.exists(): return False
    # Payload/SPI/Scheduler copies are byte-identical. For transformed Java,
    # only checkout EOL differences are normalized; no code tokens are removed.
    actual = target.read_bytes() if target.is_relative_to(CORE) else target.read_text(encoding='utf-8').encode('utf-8')
    return actual == expected


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="Initial mechanical port only")
    args = parser.parse_args()
    checks = []
    for source, target, expected in expected_files():
        if args.write:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(expected)
        passed = matches(target, expected)
        checks.append({"source": str(source.relative_to(RELEASE)), "status": "PASS" if passed else "FAIL",
                       "release_sha256": hashlib.sha256(source.read_bytes()).hexdigest()})
        print(checks[-1]["status"], checks[-1]["source"])
    (ROOT / "results/phase3-source-parity.json").write_text(json.dumps(checks, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    raise SystemExit(any(row["status"] != "PASS" for row in checks))


if __name__ == "__main__": main()
