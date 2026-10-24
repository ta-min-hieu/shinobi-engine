"""Build the `demon_king` world boss (migration V78, ADR-081) from the mesh kit —
"Yêu Vương", the scheduled world boss west of the sect.

It exists because `PostgresMonsterCatalogAdapterIntegrationTest` refuses two
species sharing one `model_key` — the first draft of V78 borrowed
`mob_sample-boss`, and the fence did its job.

No new lore: the i18n name is a title ("Yêu Vương"), not a person, and nothing
in STORY.md §1–§6 is touched.

The one rule it has to obey is the value-pattern rule from ASSET-LOG ("bố cục
sáng-tối thắng màu ở 50px"): `sample_boss` is a MID bronze body framed by a DARK
cloak. This one INVERTS that — a near-black body carrying BRIGHT bone plates,
framed by a crimson cloak — so the two bosses separate before hue is read.
Crimson stays darker and bluer than Hỏa's #E8552D; bone-white is outside every
element palette.

Run headless (never in an MCP session — full file rebuild):

    blender -b --python-exit-code 1 assets/raw/blender/kage_character_base.blend       -P tools/blender/build_monster_demon_king.py       -- --out assets/raw/blender/monsters/demon-king.blend

Then the three LODs:

    blender -b --python-exit-code 1 assets/raw/blender/monsters/demon-king.blend       -P tools/blender/export_character_lods.py --       --object demonking_Torso --rig demonking_Rig --extras all --flat-materials       --asset-key mob_demon-king --out <ABSOLUTE>/assets/exported/models
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

# Near-black body: the DARK field that the bright plates sit on (inverse of sample_boss).
BODY = "#2B2630"
BODY_DEEP = "#221E27"
BODY_BASE = "#18151C"
# Bone-white plates — the bright accent, placed twice (shoulders + chest) so it survives 50px.
BONE = "#D8D0C0"
BONE_DARK = "#A89F8E"
# Crimson cloak — the broad silhouette piece, darker/bluer than Hỏa.
CLOAK = "#5A1A22"
HAIR = "#7A1F2B"
EYE = "#E04040"

BOSS_SPEC = {
    "name": "demonking",
    "skin": "#8E7F86",
    "eye_color": EYE,
    "face_style": "frontal",
    "idle_chin_lift": 28,
    "hair": {"style": "spiky_long", "color": HAIR},
    "cloth": {"torso": BODY, "pelvis": BODY_DEEP, "legs": BODY_DEEP},
    "shoe": BODY_BASE,
    "accessories": [
        {"kind": "cloak", "color": CLOAK},
        {"kind": "shoulder_plate", "color": BONE, "bone": "arm_r"},
        {"kind": "shoulder_plate", "color": BONE, "bone": "arm_l"},
        {"kind": "chest_plate", "color": BONE, "bone": "spine"},
        {"kind": "thigh_guard", "color": BONE_DARK},
    ],
    "weapon": "twin_blades",
    "weapon_color": BONE,
    "weapon_grip": BODY_BASE,
    "motion_detail": {"kind": "stone_shards", "color": HAIR},
}


def _clear_scene():
    """Drop everything the base file carries in, guides included.

    Same reason as `build_monster_vein_raider._clear_scene`: the scale guides are
    hide_render, but `fit_camera` measures bounding boxes rather than visibility
    and would frame the shot around them.
    """
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)


def build(out_path, outline_px_at_1x=1.1):
    _clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    rig = assemble_character(BOSS_SPEC)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[mob] demon_king saved: {0}".format(out_path))
    print("[mob] parts={0} bones={1}".format(len(meshes), len(rig.pose.bones)))
    print("[mob] mesh objects: " + ", ".join(sorted(obj.name for obj in meshes)))
    print("[mob] actions: " + ", ".join(sorted(action.name for action in bpy.data.actions)))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_monster_demon_king")
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1,
                        help="outline width @1x px, saved into the .blend; 1.1 is the "
                             "measured kit-character width (build_kage_character)")
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    _args = _cli_args(sys.argv)
    if _args is not None:
        build(_args.out, _args.outline_px)
