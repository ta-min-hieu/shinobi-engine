"""Build the four arc 3 "Inner Sect" monsters (migration V109, ADR-130) from the
mesh kit — one script, one `--species` flag, because each species is DERIVED
from a shipped lineage spec and changes only its palette and a few accessories:

  inner_trial_construct    <- foundation_warden   (Linh Trieu machine, sect-built)
  blood_sand_infiltrator   <- blood_sand_acolyte  (Blood Sand acolyte in disciple robes)
  bach_kinh_chi            <- blood_sand_enforcer (a human disciple body; NOT Blood Sand)
  blood_sand_altar_master  <- blood_sand_ringleader (one rank up, altar colours)

Deriving instead of re-authoring keeps every lesson already paid for in those
files (`cap: False` + `stone_visor` on machines, `face_style: frontal` on
people, no `cloak` — it hides the face). ⚠️ Palette rules of the play line,
copied from the lineage files:
  * RED means Blood Sand. The trial construct and Bach Kinh Chi carry none.
  * Gold/jade belong to the sect's ceremony. Purple is new here and means
    Tu Phu (the "purple mansion" realm this arc is named after).
  * Nothing kunai-shaped: the theme pivot (ADR-038) retired ninja props, so the
    acolyte lineage's kunai is swapped for `twin_blades`.

These are FIRST PASSES graded only by their colour lists; none has been through
`runWorldPreview` yet (see the 2026-09-19 lesson in build_monster_foundation_warden).

Run headless, once per species:

    blender -b --python-exit-code 1 assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_monster_arc_three.py \
      -- --species inner_trial_construct --out assets/raw/blender/monsters/inner-trial-construct.blend
"""

import copy
import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from build_monster_blood_sand_acolyte import ACOLYTE_SPEC  # noqa: E402
from build_monster_blood_sand_enforcer import ENFORCER_SPEC  # noqa: E402
from build_monster_blood_sand_ringleader import RINGLEADER_SPEC  # noqa: E402
from build_monster_foundation_warden import WARDEN_SPEC  # noqa: E402
from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

# Tu Phu purple, pale at the chest and darkening down — the realm colour of arc 3.
PURPLE_PALE = "#C9B8E6"
PURPLE_MID = "#8E78C8"
PURPLE_DEEP = "#5A4789"
PURPLE_IRON = "#3A2E5C"
SILVER = "#D9DEE6"
AMBER = "#E8A33C"
# Sect disciple robe (the senior sister's palette) — what the infiltrator wears.
ROBE_WHITE = "#DCE5DC"
ROBE_TEAL = "#3F5A57"
BLOOD = "#6E201B"
BLOOD_BRIGHT = "#B33B3B"
ALTAR_GOLD = "#C9A227"


def _inner_trial_construct():
    spec = copy.deepcopy(WARDEN_SPEC)
    spec["name"] = "inner_trial"
    spec["hair"]["color"] = PURPLE_IRON
    spec["cloth"] = {"torso": PURPLE_PALE, "pelvis": PURPLE_MID, "legs": PURPLE_DEEP}
    spec["shoe"] = PURPLE_IRON
    spec["accessories"] = [
        {"kind": "stone_visor", "color": PURPLE_IRON, "accent": AMBER},
        {"kind": "sash", "color": SILVER},
        {"kind": "arm_wrap", "color": PURPLE_IRON},
    ]
    spec["weapon_color"] = PURPLE_IRON
    spec["weapon_grip"] = SILVER
    return spec


def _blood_sand_infiltrator():
    spec = copy.deepcopy(ACOLYTE_SPEC)
    spec["name"] = "infiltrator"
    spec["cloth"] = {"torso": ROBE_WHITE, "pelvis": ROBE_TEAL, "legs": ROBE_TEAL}
    spec["shoe"] = "#1F1A18"
    # Disciple robe on the outside, one red sash showing who they really serve.
    spec["accessories"] = [
        {"kind": "robe_sash", "color": BLOOD},
        {"kind": "arm_wrap", "color": "#2A2422"},
    ]
    spec["weapon"] = "twin_blades"
    spec["weapon_color"] = "#9AA2AA"
    spec["weapon_grip"] = BLOOD
    return spec


def _bach_kinh_chi():
    spec = copy.deepcopy(ENFORCER_SPEC)
    spec["name"] = "bach_kinh_chi"
    spec["skin"] = "#EED3B4"
    spec["eye_color"] = "#2B3A55"
    spec["hair"] = {"style": "spiky_long", "color": "#15171C"}
    # White and sky blue: the proudest disciple of a generation, nothing of Blood Sand.
    spec["cloth"] = {"torso": "#F2F4F7", "pelvis": "#6F9CC8", "legs": "#4A6F96"}
    spec["shoe"] = "#2B3A55"
    spec["accessories"] = [
        {"kind": "robe_sash", "color": "#6F9CC8"},
        {"kind": "shoulder_plate", "color": SILVER, "bone": "spine"},
    ]
    spec["weapon"] = "long_blade"
    spec["weapon_color"] = SILVER
    spec["weapon_grip"] = "#2B3A55"
    return spec


def _blood_sand_altar_master():
    spec = copy.deepcopy(RINGLEADER_SPEC)
    spec["name"] = "altar_master"
    spec["cloth"] = {"torso": "#3A1416", "pelvis": "#241012", "legs": "#1A0C0D"}
    spec["accessories"] = [
        {"kind": "shoulder_plate", "color": ALTAR_GOLD, "bone": "spine"},
        {"kind": "sash", "color": BLOOD_BRIGHT},
        # VÒNG B #1 (2026-11-01, runWorldPreview cạnh đối chứng): gấu áo VÀNG đọc ra một cái ĐẾ vàng dưới chân —
        # trả về màu máu như dòng ringleader; vàng chỉ còn ở giáp vai và chuôi kiếm.
        {"kind": "robe_hem", "color": BLOOD},
        {"kind": "arm_wrap", "color": "#1A0C0D"},
    ]
    spec["weapon_grip"] = ALTAR_GOLD
    spec["motion_detail"] = {"kind": "embers", "color": BLOOD_BRIGHT}
    return spec


SPECIES = {
    "inner_trial_construct": _inner_trial_construct,
    "blood_sand_infiltrator": _blood_sand_infiltrator,
    "bach_kinh_chi": _bach_kinh_chi,
    "blood_sand_altar_master": _blood_sand_altar_master,
}


def _clear_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    for collection in (bpy.data.meshes, bpy.data.armatures, bpy.data.actions, bpy.data.materials):
        for block in list(collection):
            if block.users == 0:
                collection.remove(block)


def build(species, out_path, outline_px_at_1x=1.1):
    _clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    spec = SPECIES[species]()
    rig = assemble_character(spec)
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[mob] {0} saved: {1}".format(species, out_path))
    print("[mob] parts={0} bones={1} torso={2}_Torso rig={2}_Rig".format(
        len(meshes), len(rig.pose.bones), spec["name"]))


def _cli_args(argv):
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--species", required=True, choices=sorted(SPECIES))
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1)
    return parser.parse_args(argv)


if __name__ == "__main__":
    raw = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    parsed = _cli_args(raw)
    build(parsed.species, parsed.out, parsed.outline_px)
