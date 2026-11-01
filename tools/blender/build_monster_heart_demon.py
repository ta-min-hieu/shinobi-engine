"""Build the four inner demons (`heart_demon_1..4`, migration V118, ADR-146) from the mesh kit — one script, one
`--tier` flag. STORY §2: the inner demon is the cultivator's OWN obsession taking form during a breakthrough, so the
body is a human cultivator (derived from `wandering_bandit`) turned into a shadow: near-black skin and robe, and a
glowing accent in the colour of the realm being broken into.

  heart_demon_1  Purple Mansion trial   violet glow
  heart_demon_2  Golden Core trial      gold glow
  heart_demon_3  Nascent Soul trial     ice-cyan glow
  heart_demon_4  Spirit Transformation  white glow

Value pattern (ASSET-LOG "bố cục sáng-tối thắng màu ở 50px"): `wasteland_marauder` is a dark body with MID plum
accents; this one is darker still and carries a BRIGHT crown (hair) plus bright eyes, sash and wisps — light on top of
dark — so the two separate before hue is read. Inner demons only ever appear alone in the tribulation arena anyway.
People keep `face_style: frontal` and no `cloak` (it eats the face).

Run headless, once per tier:

    blender -b --python-exit-code 1 assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_monster_heart_demon.py \
      -- --tier 1 --out assets/raw/blender/monsters/heart-demon-1.blend
"""

import copy
import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from build_monster_bandit import BANDIT_SPEC  # noqa: E402
from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

SHADOW_SKIN = "#3B3546"
SHADOW_TORSO = "#1E1B24"
SHADOW_PELVIS = "#17151C"
SHADOW_LEGS = "#141218"
SHADOW_SHOE = "#0E0D11"

# Realm glow per tier: (bright accent, softer wrap tone).
GLOW = {
    1: ("#B98CFF", "#5E4A86"),
    2: ("#F2C84B", "#7E6630"),
    3: ("#86E0FF", "#3E6A80"),
    4: ("#F4F0FF", "#8A869A"),
}


def heart_demon_spec(tier):
    glow, soft = GLOW[tier]
    spec = copy.deepcopy(BANDIT_SPEC)
    spec["name"] = "heartdemon"
    spec["skin"] = SHADOW_SKIN
    spec["eye_color"] = glow
    # The bright crown is the read at 50px: a shadow whose hair burns with the realm's light.
    spec["hair"] = {"style": "spiky_long", "color": glow}
    spec["cloth"] = {"torso": SHADOW_TORSO, "pelvis": SHADOW_PELVIS, "legs": SHADOW_LEGS}
    spec["shoe"] = SHADOW_SHOE
    spec["accessories"] = [
        {"kind": "scarf", "color": glow},
        {"kind": "sash", "color": glow},
        {"kind": "arm_wrap", "color": soft},
    ]
    spec["weapon"] = "long_blade"
    spec["weapon_color"] = soft
    spec["weapon_grip"] = glow
    spec["motion_detail"] = {"kind": "ribbons", "color": glow}
    return spec


def _clear_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    for collection in (bpy.data.meshes, bpy.data.armatures, bpy.data.actions, bpy.data.materials):
        for block in list(collection):
            if block.users == 0:
                collection.remove(block)


def build(tier, out_path, outline_px_at_1x=1.1):
    _clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    spec = heart_demon_spec(tier)
    rig = assemble_character(spec)
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[mob] heart_demon_{0} saved: {1}".format(tier, out_path))
    print("[mob] parts={0} bones={1} torso={2}_Torso rig={2}_Rig".format(
        len(meshes), len(rig.pose.bones), spec["name"]))


if __name__ == "__main__":
    import argparse

    raw = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    parser = argparse.ArgumentParser()
    parser.add_argument("--tier", required=True, type=int, choices=sorted(GLOW))
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1)
    args = parser.parse_args(raw)
    build(args.tier, args.out, args.outline_px)
