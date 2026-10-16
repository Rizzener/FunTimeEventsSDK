"""Author deterministic wire inputs and independent expected payloads (no SDK imports)."""
from pathlib import Path
import json
import uuid

TYPES = ["tab", "ban", "copper", "warden", "hell", "mine", "coordinates"]


def npc(name, donate, pos, *, entity=True, invisible=False, gear=None):
    return {"name": name, "uuid": str(uuid.uuid5(uuid.NAMESPACE_DNS, "phase3." + name)), "donate": donate,
            "pos": pos, "entity": entity, "invisible": invisible, "gear": gear or ["none"] * 4}


def player(n):
    return {"player_name": n["name"], "donate": n["donate"], "active": "<ISO8601>"}


def body(server, **values): return {"server_id": server, "server_type": "anarchy", **values}


def case(name, server, dimension="minecraft:overworld", pos=None, host="fixture.funtime.su", wait=220):
    return {"name": name, "host": host, "server_id": server, "dimension": dimension, "pos": pos or [0, 10, 0],
            "wait_ticks": wait, "npcs": [], "chests": [], "decoys": 0, "messages": [],
            "expected": {t: [] for t in TYPES}}


def messages(s):
    valid = "||| [Мистический сундук] |||\nУровень лута: Эпический\nПоявился на координатах [-123 64 456]"
    s["messages"] = [
        {"id": "ban-root", "channel": "GAME", "text": "[♨] RootBan забанен [Подробнее]",
         "hover": "Причина: Читы\nДо: 2027-01-01 12:00\nСервер: Анархия-77", "placement": "root"},
        {"id": "ban-sibling", "channel": "GAME", "text": "[♨] SiblingBan забанен [Подробнее]",
         "hover": "Причина: Флуд\nНаказание: 7 дней", "placement": "sibling"},
        {"id": "ban-chat", "channel": "CHAT", "text": "[♨] ChatBan забанен [Подробнее]"},
        {"id": "event-valid", "channel": "GAME", "text": valid},
        {"id": "event-chat-ignored", "channel": "CHAT", "text": valid.replace("Мистический", "CHAT-only")},
        {"id": "event-no-level", "channel": "GAME", "text": "||| [Нет уровня] |||\nПоявился на координатах 1 2 3"},
        {"id": "event-overflow", "channel": "GAME", "text": "||| [Overflow] |||\nУровень лута: Редкий\nПоявился на координатах 999999999999 2 3"},
        {"id": "ordinary", "channel": "GAME", "text": "Обычное сообщение"},
    ]
    if "funtime" not in s["host"]: return
    s["expected"]["ban"].append(body(77, player_name="RootBan", reason="Читы", end="2027-01-01 12:00"))
    if s["server_id"] < 0: return
    s["expected"]["ban"] += [body(s["server_id"], player_name="SiblingBan", reason="Флуд", end="7 дней"),
                              body(s["server_id"], player_name="ChatBan", reason="", end="Навсегда")]
    s["expected"]["coordinates"] = [body(s["server_id"], event="Мистический сундук", level="Эпический", coordinates={"x": -123, "y": 64, "z": 456})]


def dungeon(name, server, x, z, kind, prefix, outside=False):
    s = case(name, server, pos=[x + (3 if outside else 0), -50, z])
    good = npc("Good", prefix, [x + 1, -50, z + 1], invisible=kind == "warden",
               gear=["diamond_helmet", "netherite_chestplate", "none", "leather_boots"])
    s["npcs"] = [good, npc("NoPrefix", "VIP", [x, -50, z]), npc("Outside", "⚡ELITE", [2101 if x > 0 else -1899, -50, z]),
                 npc("High", "⚡Y", [x, 6, z]), npc("TabOnly", "⚡TAB", [x, -50, z], entity=False)]
    s["chests"] = [{"type": t, "pos": [x + i, -53 + i, z], "timer": timer}
                    for i, (t, timer) in enumerate(zip(["chest", "barrel", "trapped_chest"], ["01:23", "2:05", "0:59"]))]
    s["chests"] += [{"type": "chest", "pos": [x-1, -51, z], "timer": "1:2"},
                    {"type": "chest", "pos": [2101 if x > 0 else -1899, -51, z], "timer": "01:00"}]
    s["decoys"] = 200
    s["extra_text_displays"] = 16
    if not outside:
        gear = {"player_name": "Good", "donate": prefix, "helmet": "diamond_helmet", "chestplate": "netherite_chestplate",
                "leggings": "none", "boots": "leather_boots", "is_invisible": kind == "warden"}
        s["expected"][kind] = [body(server, players=[gear], chests=[
            {"x": x+i, "y": -53+i, "z": z, "time_left": seconds} for i, seconds in enumerate([83, 125, 59])])]
    s["expected"]["tab"] = [body(server, players_list=[player(n) for n in s["npcs"] if n["donate"].startswith("⚡")])]
    messages(s)
    return s


def main():
    cases = [dungeon("copper", 401, 2098, 2000, "copper", "⚡VIP"),
             dungeon("outside-copper", 402, 2098, 2000, "copper", "⚡SECOND", outside=True),
             dungeon("warden", 403, -1998, -2000, "warden", "⚡MVP")]
    for outside in [False, True]:
        s = case("lobby-outside" if outside else "lobby", 405 if outside else 404,
                 "minecraft:lobby", [-35 if outside else -76, 10, 5])
        s["npcs"] = [npc("Near", "⚡NEAR", [-76, 10, 5]), npc("Boundary", "⚡BORDER", [-61, 200, 5]),
                     npc("Far", "⚡FAR", [-60, 10, 5]), npc("NoPrefix", "VIP", [-75, 10, 5]),
                     npc("TabOnly", "⚡TAB", [-75, 10, 5], entity=False)]
        eligible = [player(n) for n in s["npcs"] if n["donate"].startswith("⚡")]
        s["expected"]["tab"] = [body(s["server_id"], players_list=eligible)]
        if not outside: s["expected"]["mine"] = [body(404, players_around=[player(n) for n in s["npcs"][:2]])]
        cases.append(s)
    s=case("wrong-dimension", 406, pos=[-76, 10, 5]);s["boss"] = True
    s["npcs"] = [npc("Near", "⚡OTHER", [-76, 10, 5])]
    s["expected"]["tab"] = [body(406, players_list=[player(s["npcs"][0])])];cases.append(s)
    s=case("hell", 407, "minecraft:nether-event", wait=320);s["boss"] = True
    s["expected"]["hell"] = [body(407,mobs_count=17),body(407,mobs_count=9)];cases.append(s)
    s=dungeon("non-funtime", 408, 2098, 2000,"copper","⚡NEGATIVE");s["host"]="example.invalid"
    s["expected"]={t:[] for t in TYPES};cases.append(s)
    s=case("unresolved",-1);s["npcs"]=[npc("Good","⚡UNRESOLVED",[0,10,0])]
    s["expected"]["tab"]=[body(-1,players_list=[player(s["npcs"][0])])];messages(s);cases.append(s)
    for i in range(12): cases.append(case("rapid-"+str(i+1), 500+i, wait=4))
    for s in cases:s['fixture_revision']=3
    p=Path(__file__).parent/'data/phase3/tracker-sessions.json';p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(json.dumps(cases,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Wrote',len(cases),'sessions',p)


if __name__ == '__main__': main()
