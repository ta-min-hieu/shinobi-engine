"""Build the two lot 24 NPCs (ADR-131) from the mesh kit — `sect_blacksmith` (Forge Hall smith, static in the sect
yard) and `traveling_merchant` (placed by the server beside the waypost of an ongoing visit).

Both DERIVE from `build_npc_waypost_attendant`: same standing-watch idle, same four-part accessory budget (its
docstring: a kit character lands at ~4.98k of the 5k LOW ceiling, overspending quietly mushes the LOW tier), same
"no cloak" rule (it has eaten a face three times). Only palette, hair and accessories change:

  sect_blacksmith     soot-dark tunic + leather apron (chest_plate), rolled sleeves (arm_wrap), scarf; carries the
                      kit's `stone_mace` as a forge hammer — a smith is the one NPC whose tool IS a hammer.
  traveling_merchant  guild ochre robe with a gold sash and a `gourd` at the hip (the classic wandering-trader prop),
                      wide sleeves; no weapon-shaped object.

Run headless, once per NPC:

    blender -b --python-exit-code 1 assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_npc_lot24.py -- --npc sect_blacksmith --out assets/raw/blender/characters/sect-blacksmith.blend

Then the LODs (ART-PIPELINE §6.5):

    blender -b --python-exit-code 1 assets/raw/blender/characters/sect-blacksmith.blend \
      -P tools/blender/export_character_lods.py -- --object blacksmith_Torso --rig blacksmith_Rig \
      --extras all --flat-materials --asset-key char_sect-blacksmith --out <ABSOLUTE>/assets/exported/models
"""

import copy
import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import build_npc_waypost_attendant as waypost  # noqa: E402
from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402


def _blacksmith():
    spec = copy.deepcopy(waypost.WAYPOST_SPEC)
    spec["name"] = "blacksmith"
    spec["skin"] = "#C99A74"
    spec["hair"] = {"style": "topknot", "color": "#1E1A18"}
    spec["cloth"] = {"torso": "#4A4440", "pelvis": "#3A3532", "legs": "#2F2B29"}
    spec["shoe"] = "#211E1C"
    spec["accessories"] = [
        {"kind": "chest_plate", "color": "#7A5236"},
        {"kind": "arm_wrap", "color": "#B9A58A"},
        {"kind": "scarf", "color": "#C2462E"},
    ]
    spec["weapon"] = "stone_mace"
    spec["weapon_color"] = "#5E5A55"
    spec["weapon_grip"] = "#7A5236"
    return spec


def _merchant():
    spec = copy.deepcopy(waypost.WAYPOST_SPEC)
    spec["name"] = "merchant"
    spec["skin"] = "#E6C29E"
    spec["hair"] = {"style": "topknot", "color": "#3A2E26"}
    spec["cloth"] = {"torso": "#B07A2C", "pelvis": "#7E5420", "legs": "#5E3E18"}
    spec["shoe"] = "#3A2A18"
    spec["accessories"] = [
        {"kind": "robe_sash", "color": "#E2B33C"},
        {"kind": "wide_sleeves", "color": "#B07A2C"},
        {"kind": "scarf", "color": "#F0E6D0"},
    ]
    spec["weapon"] = "gourd"
    spec["weapon_color"] = "#C9A24C"
    spec["weapon_grip"] = "#7E5420"
    return spec


NPCS = {"sect_blacksmith": _blacksmith, "traveling_merchant": _merchant}


def build(npc, out_path, outline_px_at_1x=1.1):
    waypost._clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    spec = NPCS[npc]()
    rig = assemble_character(spec)
    waypost._author_standing_watch_idle(rig)
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[npc] {0} saved: {1} parts={2} torso={3}_Torso rig={3}_Rig".format(
        npc, out_path, len(meshes), spec["name"]))


if __name__ == "__main__":
    import argparse

    raw = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser()
    parser.add_argument("--npc", required=True, choices=sorted(NPCS))
    parser.add_argument("--out", required=True)
    parser.add_argument("--outline-px", type=float, default=1.1)
    args = parser.parse_args(raw)
    build(args.npc, args.out, args.outline_px)
