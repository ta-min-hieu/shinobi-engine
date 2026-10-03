"""Build the `blood_sand_acolyte` monster (migration V47, `monster_template`) from
the mesh kit — "Tế Đồ Huyết Sa", the rank-and-file cultist working the dead
spirit line east of the raider camp.

STORY row it answers (§4.3): *"Tà tu, tu bằng cách hút tu vi kẻ khác"*, crossed
with §6 arc 2's loose end — the steward burned his own cultivation and the trail
went cold, so the sect never learned what he was digging for. These are the ones
who came back for it. Nothing here touches the crack in the Heaven Gate or Sa Y
Nhân: that is arc 3 and arc 6 respectively (§8 rule 2).

THE VALUE AXIS IS ALREADY CROWDED, so this one takes the last free point on it.
The two Blood Sand models that exist sit at opposite ends:

  * `blood_sand_scout`   — near-black body (#1A1614 / #221C1A) carrying ONE large
    dried-blood field on the torso (#8E2A24). Dark body, big warm field.
  * `blood_sand_enforcer` — a pale ash robe (#B9B4A6) with the dried red worn as
    a narrow sash. Light body, narrow accent.

A third "dark body + red" or "light robe + red" would be the `archive_warden`
vs `stone_ogre` mistake again (ASSET-LOG 2026-09-06) at ~64px. So this one is
MID-tone cloth with the pale worn as a narrow scarf — neither end of the axis,
and the red stays exactly where both siblings keep it (the sash) so the faction
still reads instantly.

`spiky_short` hair: every person in the roster is a topknot (bandit, scout),
a topknot_bun (enforcer, vein_raider) or the chieftain's spiky_long. Short
spikes are the only silhouette left that is not one of those.

`kunai` is the one weapon shape the kit builds that NO species has taken
(bandit long_blade; scout twin_blades; enforcer, vein_raider and archive_warden
all stone_mace; chieftain gourd). At this size the weapon is half the outline,
so reusing a shape would undo the value work — the same argument
`blood_sand_scout` used to pick twin_blades.

Deliberately NO proper name: STORY §8 rule 3 makes a new proper name an owner
decision in the same PR, and two are already queued ("Lý Tam", "Hình Vân Sơn").
A species CATEGORY needs only a §7 row, which this PR adds.

Run headless (never in an MCP session — full file rebuild):

    blender -b --python-exit-code 1 assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_monster_blood_sand_acolyte.py \
      -- --out assets/raw/blender/monsters/blood-sand-acolyte.blend

Then the three LODs (ART-PIPELINE §6.5 — `--flat-materials` is mandatory for a
kit-built mesh, `--extras all` because naming parts by hand is how
`bandit_SashTail` went missing):

    blender -b --python-exit-code 1 assets/raw/blender/monsters/blood-sand-acolyte.blend \
      -P tools/blender/export_character_lods.py -- \
      --object acolyte_Torso --rig acolyte_Rig --extras all --flat-materials \
      --asset-key mob_blood-sand-acolyte --out <ABSOLUTE>/assets/exported/models
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

ACOLYTE_SPEC = {
    "name": "acolyte",
    # Colder and greyer than the bandit's #E0B183 — these people live in a dead
    # spirit line where nothing grows.
    "skin": "#BFA68F",
    "eye_color": "#C0554A",
    # Two-eyed game-camera face + chin up in idle — see `build_monster_bandit`
    # (ASSET-LOG 2026-10-01, second pass).
    "face_style": "frontal",
    "idle_chin_lift": 28,
    "hair": {"style": "spiky_short", "color": "#171312"},
    # MID-tone throughout: the point of this model. Not the scout's near-black,
    # not the enforcer's pale ash.
    "cloth": {"torso": "#4A3F3A", "pelvis": "#332C29", "legs": "#2A2422"},
    "shoe": "#1F1A18",
    "accessories": [
        # The pale is NARROW here — a scarf, not a robe. That is the whole
        # inversion against the enforcer, who wears the pale as the whole body.
        {"kind": "scarf", "color": "#C9BFAE"},
        # Dried blood in exactly the place both siblings keep it.
        {"kind": "sash", "color": "#6E201B"},
        {"kind": "arm_wrap", "color": "#2A2422"},
    ],
    "weapon": "kunai",
    "weapon_color": "#9AA2AA",
    "weapon_grip": "#3A1714",
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
    rig = assemble_character(ACOLYTE_SPEC)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[mob] blood_sand_acolyte saved: {0}".format(out_path))
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
