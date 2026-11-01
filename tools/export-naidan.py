"""Export the saved Naidan Blockbench assets for GeckoLib 4.7 / Forge 1.20.1.

First export the complete geometry as art/naidan/naidan-full.geo.json and the
animations as art/naidan/naidan.animation.json with the GeckoLib Blockbench plugin.
The editable project's animation keys are never changed here.
"""
import base64
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "art/naidan"
ASSETS = ROOT / "src/main/resources/assets/overheaven_foliage_compat"
NS = "overheaven_foliage_compat"


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def animation_channel(channel):
    if not isinstance(channel, dict):
        return channel
    if "vector" in channel:
        return channel["vector"]
    converted = {}
    for time, key in channel.items():
        if isinstance(key, dict) and "pre" in key and "post" in key:
            # 4.7 only reads pre from a Bedrock pre/post key. Two numerically equal
            # timestamps preserve an instantaneous jump without losing post.
            converted[time] = key["pre"]
            post_time = time + ("0" if "." in time else ".0")
            converted[post_time] = key["post"]
        else:
            converted[time] = key
    return converted


def main():
    project = json.loads((ART / "naidan.bbmodel").read_text(encoding="utf-8"))
    geometry = json.loads((ART / "naidan-full.geo.json").read_text(encoding="utf-8"))
    geometry["minecraft:geometry"][0]["description"]["identifier"] = "geometry.naidan"
    write(ASSETS / "geo/block/naidan.geo.json", geometry)
    texture = ASSETS / "textures/block/naidan.png"
    texture.parent.mkdir(parents=True, exist_ok=True)
    texture.write_bytes(base64.b64decode(project["textures"][0]["source"].split(",", 1)[1]))

    animations = json.loads((ART / "naidan.animation.json").read_text(encoding="utf-8"))
    animations.pop("geckolib_format_version", None)
    for animation in animations["animations"].values():
        for bone in animation["bones"].values():
            for channel, keys in list(bone.items()):
                bone[channel] = animation_channel(keys)
    animations["animations"]["idle"] = {
        "loop": True, "animation_length": 1,
        "bones": {
            "naidan_sway": {"rotation": [0, 0, 0]},
            "naidan_root": {"rotation": [0, 0, 0], "scale": [1, 1, 1]},
            "face": {"scale": [1, 1, 1]},
            "face_laugh": {"scale": [0, 0, 0]},
            "face_talk": {"scale": [0, 0, 0]},
        },
    }
    write(ASSETS / "animations/block/naidan.animation.json", animations)

    groups = {group["uuid"]: group["name"] for group in project["groups"]}
    elements = {cube["uuid"]: cube for cube in project["elements"]}
    visible = []

    def visit(nodes):
        for node in nodes:
            if isinstance(node, str):
                visible.append(elements[node])
            elif groups[node["uuid"]] not in {"face_laugh", "face_talk"}:
                visit(node["children"])

    visit(project["outliner"])
    item_elements = []
    for cube in visible:
        faces = {}
        for name, face in cube["faces"].items():
            if face.get("texture") is None:
                continue
            faces[name] = {"texture": "#0", "uv": [uv / 4 for uv in face["uv"]]}
            if face.get("rotation"):
                faces[name]["rotation"] = face["rotation"]
        item_elements.append({
            "from": [cube["from"][i] + (8 if i != 1 else 0) for i in range(3)],
            "to": [cube["to"][i] + (8 if i != 1 else 0) for i in range(3)],
            "faces": faces,
        })
    write(ASSETS / "models/item/naidan.json", {
        "gui_light": "front", "textures": {"0": NS + ":block/naidan", "particle": NS + ":block/naidan"},
        "elements": item_elements,
        "display": {
            "gui": {"rotation": [10, 165, 0], "scale": [0.65, 0.65, 0.65]},
            "ground": {"translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
            "fixed": {"rotation": [0, 180, 0], "scale": [0.6, 0.6, 0.6]},
            "thirdperson_righthand": {"rotation": [75, 180, 0], "translation": [0, 2.5, 0], "scale": [0.35, 0.35, 0.35]},
            "thirdperson_lefthand": {"rotation": [75, 180, 0], "translation": [0, 2.5, 0], "scale": [0.35, 0.35, 0.35]},
            "firstperson_righthand": {"rotation": [0, 160, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_lefthand": {"rotation": [0, 200, 0], "scale": [0.45, 0.45, 0.45]},
        },
    })
    write(ASSETS / "models/block/naidan.json", {"textures": {"particle": NS + ":block/naidan"}})
    write(ASSETS / "blockstates/naidan.json", {"variants": {"": {"model": NS + ":block/naidan"}}})
    write(ROOT / "src/main/resources/data" / NS / "loot_tables/blocks/naidan.json", {
        "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":naidan"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })


if __name__ == "__main__":
    main()
