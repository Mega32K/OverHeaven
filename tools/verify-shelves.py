"""Validate the shelf backport's resource graph without starting Minecraft."""
from pathlib import Path
import argparse
import json
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
NS = "overheaven_foliage_compat"
WOODS = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped")
checks = 0


def check(condition, message):
    global checks
    if not condition:
        raise AssertionError(message)
    checks += 1


def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise AssertionError("Duplicate JSON key: " + key)
        result[key] = value
    return result


def read(relative):
    data = (ROOT / relative).read_bytes()
    check(not data.startswith(b"\xef\xbb\xbf"), "UTF-8 BOM: " + relative)
    return json.loads(data.decode("utf-8"), object_pairs_hook=unique_pairs)


def asset(kind, identifier):
    namespace, name = identifier.split(":", 1)
    return f"assets/{namespace}/{kind}/{name}.json"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference-jar", type=Path, help="Optional exact 1.21.9 client JAR for asset comparison")
    parser.add_argument("--classes", type=Path, help="Optional compiled main classes for Java version and client boundary checks")
    args = parser.parse_args()
    reference = zipfile.ZipFile(args.reference_jar) if args.reference_jar else None
    try:
        languages = {name: read(f"assets/{NS}/lang/{name}.json") for name in ("en_us", "zh_cn")}
        expected = {f"{NS}:{wood}_shelf" for wood in WOODS}
        for kind in ("blocks", "items"):
            tag = read(f"data/{NS}/tags/{kind}/shelves.json")
            check(tag["replace"] is False and set(tag["values"]) == expected, kind + " shelf tag")
        axe = read("data/minecraft/tags/blocks/mineable/axe.json")
        check(axe["replace"] is False and expected <= set(axe["values"]), "axe tool tag")
        for template in ("body", "unpowered", "inventory"):
            path = f"assets/{NS}/models/block/template_shelf_{template}.json"
            model = read(path)
            check("on_shelf" not in model.get("display", {}), "1.21 display context removed")
            if reference:
                original = json.loads(reference.read(f"assets/minecraft/models/block/template_shelf_{template}.json"))
                original.get("display", {}).pop("on_shelf", None)
                check(model == original, "template geometry/UV/display preserved: " + template)
        for wood in WOODS:
            name = wood + "_shelf"
            identifier = f"{NS}:{name}"
            blockstate = read(f"assets/{NS}/blockstates/{name}.json")
            parts = blockstate["multipart"]
            check(len(parts) == 8, "four body + four unpowered layers: " + name)
            for face, rotation in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                matches = [p for p in parts if p["when"] == {"facing": face}]
                check(len(matches) == 2, "orientation coverage: " + name + "/" + face)
                check({p["apply"]["model"] for p in matches} == {f"{NS}:block/{name}", f"{NS}:block/{name}_unpowered"}, "only unpowered models")
                for p in matches:
                    check(p["apply"].get("y", 0) == rotation, "model rotation")
                    check((ROOT / asset("models", p["apply"]["model"])).is_file(), "model exists")
            for suffix in ("", "_unpowered", "_inventory"):
                model = read(f"assets/{NS}/models/block/{name}{suffix}.json")
                check((ROOT / asset("models", model["parent"])).is_file(), "model parent exists")
                check(model["textures"]["all"] == f"{NS}:block/{name}", "variant texture")
            item = read(f"assets/{NS}/models/item/{name}.json")
            check(item == {"parent": f"{NS}:block/{name}_inventory"}, "legacy item model")
            texture = (ROOT / f"assets/{NS}/textures/block/{name}.png").read_bytes()
            check(texture[:8] == b"\x89PNG\r\n\x1a\n" and all(n > 0 for n in struct.unpack(">II", texture[16:24])), "valid PNG header")
            if reference:
                check(texture == reference.read(f"assets/minecraft/textures/block/{name}.png"), "exact reference texture")
            ingredient = f"minecraft:stripped_{wood}_" + ("block" if wood == "bamboo" else "stem" if wood in ("crimson", "warped") else "log")
            recipe = read(f"data/{NS}/recipes/{name}.json")
            check(recipe["pattern"] == ["###", "   ", "###"] and recipe["key"] == {"#": {"item": ingredient}}, "recipe ingredients")
            check(recipe["result"] == {"item": identifier, "count": 6}, "six shelves and 1.20.1 result schema")
            loot = read(f"data/{NS}/loot_tables/blocks/{name}.json")
            check(loot["pools"][0]["entries"] == [{"type": "minecraft:item", "name": identifier}], "single block drop")
            advancement = read(f"data/{NS}/advancements/recipes/decorations/{name}.json")
            check(advancement["rewards"]["recipes"] == [identifier], "recipe book unlock")
            for language, entries in languages.items():
                value = entries.get(f"block.{NS}.{name}", "")
                check(bool(value) and "?" not in value, "translation: " + language + "/" + name)
        if args.classes:
            common = args.classes / "com/mega/overheaven/foliagecompat/shelf"
            for name in ("Shelves", "ShelfBlock", "ShelfBlockEntity", "ShelfInventory", "ShelfInteraction"):
                bytecode = (common / (name + ".class")).read_bytes()
                check(bytecode[:4] == b"\xca\xfe\xba\xbe" and struct.unpack(">H", bytecode[6:8])[0] == 61, "Java 17 bytecode: " + name)
                check(not any(s in bytecode for s in (b"net/minecraft/client", b"com/mojang/blaze3d", b"shelf/client", b"IItemHandler", b"WorldlyContainer")), "common code/client and automation boundary: " + name)
            subscriber = (common / "client/ShelfClient.class").read_bytes()
            check(b"CLIENT" in subscriber and b"Mod$EventBusSubscriber" in subscriber, "client-only renderer registration")
        print(f"Shelf resource checks passed: {checks}; variants: {len(WOODS)}")
    finally:
        if reference:
            reference.close()


if __name__ == "__main__":
    main()
