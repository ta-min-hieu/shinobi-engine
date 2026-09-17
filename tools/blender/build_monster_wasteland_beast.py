"""Build the `wasteland_beast` monster (migration V48, `monster_template`) from the
mesh kit — the feral cultivator at the edge of the Wasteland, Lv29.

⚠️ NAME: the DB code stays `wasteland_beast` (ADR-038 D5 — business keys never
change once a migration ships), but the display name is a PERSON, not an animal:
"Yêu Nhân Hoang Vực". The kit builds chibi HUMANOIDS only; dressing a quadruped
concept on a humanoid rig would read as a mistake at 64px. Lore fits either way —
STORY §6 puts the player at the Wasteland edge where the dead spirit line warps
whoever stays too long.

THE DIFFERENTIATION AXIS IS EXHAUSTED, and this file is where that stops being a
worry and starts being a constraint:

  * hair — 5 of the kit's 6 are taken (topknot: bandit/scout; topknot_bun:
    enforcer/vein_raider; spiky_long: chieftain; spiky_short: acolyte; bang:
    ringleader). Only `carved_crest` is free, and it goes to `foundation_warden`
    where a carved crest actually means something.
  * weapon — all 5 shapes are taken.

So this one REUSES `spiky_long` (the chieftain's silhouette) and `stone_mace`,
and buys its separation entirely with HUE. Every existing species sits in the
grey/brown/near-black family with dried red as the only accent; the Blood Sand
four own that space outright. This one is OCHRE DUST with a sickly green — a
family nothing else on the play line uses, so it reads as "not Blood Sand" before
the player can make out a single shape.

Run headless (never in an MCP session — full file rebuild):

    blender -b assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_monster_wasteland_beast.py \
      -- --out assets/raw/blender/monsters/wasteland-beast.blend

Then the three LODs (ART-PIPELINE §6.5 — `--flat-materials` is mandatory for a
kit-built mesh, `--extras all` because naming parts by hand loses pieces):

    blender -b assets/raw/blender/monsters/wasteland-beast.blend \
      -P tools/blender/export_character_lods.py -- \
      --object wasteland_Torso --rig wasteland_Rig --extras all --flat-materials \
      --asset-key mob_wasteland-beast --out <ABSOLUTE>/assets/exported/models
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

WASTELAND_SPEC = {
    "name": "wasteland",
    # Dust-bleached, several shades past the bandit's healthy #E0B183 — someone
    # who has been out there far too long.
    "skin": "#C7B48E",
    # The one loud note on the model, and the reason the species reads as WRONG
    # rather than merely poor: spirit-line sickness shows in the eyes first.
    "eye_color": "#8FBF5A",
    "hair": {"style": "spiky_long", "color": "#4A4230"},
    # OCHRE, not grey and not the Blood Sand browns — this is the whole
    # separation argument, see the module docstring.
    "cloth": {"torso": "#9A7A42", "pelvis": "#7A5E33", "legs": "#5E4A2A"},
    "shoe": "#3B3122",
    "accessories": [
        # Sickly green where every other species keeps dried red. Same PLACE on
        # the body, deliberately: the eye compares colour, not position.
        {"kind": "sash", "color": "#6E8F3A"},
        {"kind": "arm_wrap", "color": "#5E4A2A"},
    ],
    "weapon": "stone_mace",
    "weapon_color": "#8A8271",
    "weapon_grip": "#4A3D28",
}


def _clear_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    for collection in (bpy.data.meshes, bpy.data.armatures, bpy.data.actions, bpy.data.materials):
        for block in list(collection):
            if block.users == 0:
                collection.remove(block)


def build(out_path, outline_px_at_1x=1.1):
    _clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    rig = assemble_character(WASTELAND_SPEC)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[mob] wasteland_beast saved: {0}".format(out_path))
    print("[mob] parts={0} bones={1}".format(len(meshes), len(rig.pose.bones)))
    print("[mob] mesh objects: " + ", ".join(sorted(obj.name for obj in meshes)))


def _cli_args(argv):
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1)
    return parser.parse_args(argv)


if __name__ == "__main__":
    raw = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    parsed = _cli_args(raw)
    build(parsed.out, parsed.outline_px)
