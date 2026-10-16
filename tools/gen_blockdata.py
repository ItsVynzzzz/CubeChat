#!/usr/bin/env python3
"""Ubah blocks.json (data generator resmi) menjadi assets/blockdata.txt.

Baris "N a-b" = block state tanpa collision (tembus), "F a-b" = fluida,
"L a-b" = bisa dipanjat (tangga, sulur; juga tembus).
Kalau registries.json ada di folder yang sama dengan blocks.json, dibuat juga
itemdata.txt (di folder output) berisi "<id> <nama>" untuk nama item di inventori.
Pakai: gen_blockdata.py <blocks.json> <output.txt>
"""
import json
import sys

AIR = {"air", "cave_air", "void_air"}
FLUID = {"water", "lava", "bubble_column", "kelp", "kelp_plant", "seagrass", "tall_seagrass"}

NONSOLID_EXACT = {
    "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "short_dry_grass",
    "tall_dry_grass", "vine", "weeping_vines", "weeping_vines_plant", "twisting_vines",
    "twisting_vines_plant", "cave_vines", "cave_vines_plant", "glow_lichen", "sculk_vein",
    "hanging_roots", "spore_blossom", "warped_roots", "crimson_roots", "nether_sprouts",
    "big_dripleaf_stem", "lever", "tripwire", "tripwire_hook", "redstone_wire", "fire",
    "soul_fire", "rail", "powered_rail", "detector_rail", "activator_rail", "ladder", "light",
    "structure_void", "nether_portal", "end_portal", "end_gateway", "cobweb", "sugar_cane",
    "nether_wart", "wheat", "carrots", "potatoes", "beetroots", "melon_stem", "pumpkin_stem",
    "attached_melon_stem", "attached_pumpkin_stem", "bamboo_sapling", "frogspawn", "lily_pad",
    "pink_petals", "wildflowers", "leaf_litter", "brown_mushroom", "red_mushroom",
    "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower",
    "lily_of_the_valley", "wither_rose", "sunflower", "lilac", "rose_bush", "peony",
    "torchflower", "pitcher_plant", "open_eyeblossom", "closed_eyeblossom",
}
NONSOLID_SUFFIX = (
    "_sign", "_button", "_pressure_plate", "_banner", "_sapling", "_carpet", "_coral",
    "_coral_fan", "_coral_wall_fan", "_fungus", "_tulip", "_torch", "_bush", "_crop",
)
OPENABLE_SUFFIX = ("_door", "_trapdoor", "_fence_gate")
CLIMBABLE = {
    "ladder", "vine", "weeping_vines", "weeping_vines_plant", "twisting_vines",
    "twisting_vines_plant", "cave_vines", "cave_vines_plant",
}


def classify(name, props):
    if name in FLUID:
        return "F"
    if name in AIR or name in NONSOLID_EXACT or name.endswith(NONSOLID_SUFFIX):
        return "N"
    if name.endswith(OPENABLE_SUFFIX) and props.get("open") == "true":
        return "N"
    if name == "snow" and props.get("layers") == "1":
        return "N"
    return None


def ranges(ids):
    ids = sorted(set(ids))
    out, start, prev = [], None, None
    for i in ids:
        if start is None:
            start = prev = i
        elif i == prev + 1:
            prev = i
        else:
            out.append((start, prev))
            start = prev = i
    if start is not None:
        out.append((start, prev))
    return out


def write_items(registries_path, out_path):
    """registries.json -> itemdata.txt ("<protocol_id> <nama>")."""
    with open(registries_path, encoding="utf-8") as f:
        reg = json.load(f)
    entries = reg.get("minecraft:item", {}).get("entries", {})
    rows = []
    for full_name, data in entries.items():
        pid = data.get("protocol_id")
        if pid is None:
            continue
        rows.append((int(pid), full_name.split(":", 1)[-1]))
    rows.sort()
    with open(out_path, "w", encoding="utf-8") as f:
        for pid, name in rows:
            f.write(f"{pid} {name}\n")
    return len(rows)


def main():
    import os
    src, dst = sys.argv[1], sys.argv[2]
    with open(src, encoding="utf-8") as f:
        blocks = json.load(f)

    groups = {"N": [], "F": [], "L": []}
    for full_name, data in blocks.items():
        name = full_name.split(":", 1)[-1]
        for st in data.get("states", []):
            if name in CLIMBABLE:
                groups["L"].append(st["id"])
                continue
            kind = classify(name, st.get("properties", {}))
            if kind:
                groups[kind].append(st["id"])

    os.makedirs(os.path.dirname(os.path.abspath(dst)), exist_ok=True)
    with open(dst, "w", encoding="utf-8") as f:
        for kind in ("N", "F", "L"):
            for a, b in ranges(groups[kind]):
                f.write(f"{kind} {a}-{b}\n")
    print(f"blockdata: {len(groups['N'])} nonsolid, {len(groups['F'])} fluid, "
          f"{len(groups['L'])} climbable states -> {dst}")

    reg_path = os.path.join(os.path.dirname(os.path.abspath(src)), "registries.json")
    if os.path.exists(reg_path):
        item_out = os.path.join(os.path.dirname(os.path.abspath(dst)), "itemdata.txt")
        n = write_items(reg_path, item_out)
        print(f"itemdata: {n} item -> {item_out}")
    else:
        print("registries.json tidak ditemukan: nama item akan tampil sebagai 'Item #id'")


if __name__ == "__main__":
    main()
