"""Build the `blood_sand_ringleader` monster (migration V47, `monster_template`)
from the mesh kit — "Đàn Chủ Huyết Sa", the one leading the acolyte band working
the dead spirit line east of the raider camp.

STORY row it answers (§7, added this PR): the leader of the `blood_sand_acolyte`
band, one rank above them and NOT a hộ pháp — beating him scatters the band, it
does not dent the sect. That distinction matters: §6 arc 2's climax already spent
the hộ pháp (the disguised steward), and promising another one here would promise
a payoff arc 2 has no room left to deliver (§8 rule 2).

FOURTH PERSON IN THE BLOOD SAND PALETTE, so the value axis decides the design
again. The three that exist:

  * `blood_sand_scout`     dark body, ONE large dried-blood field
  * `blood_sand_enforcer`  pale ash robe, narrow red sash
  * `blood_sand_acolyte`   MID-tone cloth, narrow pale scarf, red sash

This one takes the remaining read: the DARKEST body of the four, with the pale
worn as hard plate at the shoulders — so the silhouette widens at the top where
the acolyte's stays narrow. Shoulders are where the eye lands first at ~64px,
which is the whole reason the plate goes there and not on the chest.

`bang` hair: topknot is the bandit and the scout, topknot_bun the enforcer and
`vein_raider`, spiky_long the raider chieftain, spiky_short the acolyte. Bang is
the last silhouette the kit has that nobody wears.

`long_blade` against the acolyte's `kunai`. The two of them appear TOGETHER in
the same fight, so their outlines have to differ from each other first and from
everyone else second — a band where leader and follower carry the same shape is
one enemy drawn twice. The bandit also carries a long_blade, but he stands at
x=21 in road-dust brown; these two are at x≈134 in near-black and red.

`embers` motion detail, in the dried red: the only one of the four that moves.
That is the cheapest way to say "this is the one to kill" without inventing a
bigger silhouette, and `embers` is free here because the three used elsewhere
are ribbons (chieftain), spark (trial_construct) and stone_shards.

⚠️ NO `cloak`. The kit's cloak has buried the face on three separate models
(ASSET-LOG, the "dark mass eats the face" bug) and a hooded cultist is exactly
the shape that invites it. The plate does the widening instead.

Deliberately NO proper name — §8 rule 3, same reasoning as the acolyte.

Run headless (never in an MCP session — full file rebuild):

    blender -b --python-exit-code 1 assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_monster_blood_sand_ringleader.py \
      -- --out assets/raw/blender/monsters/blood-sand-ringleader.blend

Then the three LODs (ART-PIPELINE §6.5 — `--flat-materials` mandatory for a
kit-built mesh, `--extras all` so no part goes missing):

    blender -b --python-exit-code 1 assets/raw/blender/monsters/blood-sand-ringleader.blend \
      -P tools/blender/export_character_lods.py -- \
      --object ringleader_Torso --rig ringleader_Rig --extras all --flat-materials \
      --asset-key mob_blood-sand-ringleader --out <ABSOLUTE>/assets/exported/models
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

RINGLEADER_SPEC = {
    "name": "ringleader",
    "skin": "#B79A83",
    # Red eyes over a near-black head: the acolyte's are the same family but
    # duller, so leader and follower still read as one faction up close.
    "eye_color": "#D4564A",
    # Two-eyed game-camera face + chin up in idle — see `build_monster_bandit`
    # (ASSET-LOG 2026-10-01, second pass). The `bang` fringe is the reason the
    # lift is 28, not 16: at 16 it still cut across both eye tops.
    "face_style": "frontal",
    "idle_chin_lift": 28,
    "hair": {"style": "bang", "color": "#141011"},
    # Darkest of the four. The acolyte is mid-tone; putting the leader BELOW him
    # on the value axis is what makes the pair read as two things, not two sizes
    # of one thing.
    "cloth": {"torso": "#241F22", "pelvis": "#1A1614", "legs": "#1A1614"},
    "shoe": "#120F10",
    "accessories": [
        # Pale as PLATE, at the shoulders — widens the top of the silhouette
        # where the acolyte's scarf keeps it narrow.
        {"kind": "shoulder_plate", "color": "#C9BFAE", "bone": "spine"},
        # Dried blood in the family place, plus a hem so the red repeats low and
        # the outline still reads when the torso turns.
        {"kind": "sash", "color": "#6E201B"},
        {"kind": "robe_hem", "color": "#6E201B"},
        {"kind": "arm_wrap", "color": "#1A1614"},
    ],
    "weapon": "long_blade",
    "weapon_color": "#8F98A1",
    "weapon_grip": "#3A1714",
    "motion_detail": {"kind": "embers", "color": "#B33B3B"},
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
    rig = assemble_character(RINGLEADER_SPEC)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[mob] blood_sand_ringleader saved: {0}".format(out_path))
    print("[mob] parts={0} bones={1}".format(len(meshes), len(rig.pose.bones)))
    print("[mob] mesh objects: " + ", ".join(sorted(obj.name for obj in meshes)))
    print("[mob] actions: " + ", ".join(sorted(action.name for action in bpy.data.actions)))


def _cli_args(argv):
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1)
    return parser.parse_args(argv)


if __name__ == "__main__":
    raw = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    args = _cli_args(raw)
    build(args.out, args.outline_px)
