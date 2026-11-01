"""Build the five arc 4 "Descending the Mountain" monsters (migration V113, ADR-139) from the mesh kit — one script,
one `--species` flag, each species DERIVED from a shipped lineage spec (palette and accessories only), the same way
`build_monster_arc_three` does:

  wasteland_marauder        <- wandering_bandit     (rogue cultivator: road-worn, NO red — red means Blood Sand)
  blood_sand_digger         <- blood_sand_scout     (dust-caked Blood Sand hand with a digging maul)
  blood_sand_beast_warden   <- blood_sand_enforcer  (iron-plated keeper of the captive demon-kin)
  frenzied_vein_beast       <- wasteland_beast      (further gone: greyer skin, brighter sick-green)
  ancient_stone_colossus    <- ruinlord (sample_boss) (Linh Trieu vein guardian, moss stone + jade glow)

Palette rules of the play line (ART-PIPELINE §2.2/§2.5):
  * RED means Blood Sand: only the digger and the warden carry it.
  * Sickly green means vein sickness (the wasteland lineage). The frenzied beast pushes it brighter.
  * Machines keep `cap: False` + `stone_visor` + `face_style: none`; people keep `face_style: frontal`; no `cloak`
    on anything with a face.

Run headless, once per species:

    blender -b --python-exit-code 1 assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_monster_arc_four.py \
      -- --species wasteland_marauder --out assets/raw/blender/monsters/wasteland-marauder.blend
"""

import copy
import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from build_monster_bandit import BANDIT_SPEC  # noqa: E402
from build_monster_blood_sand_enforcer import ENFORCER_SPEC  # noqa: E402
from build_monster_blood_sand_scout import SCOUT_SPEC  # noqa: E402
from build_monster_sample_boss import BOSS_SPEC  # noqa: E402
from build_monster_wasteland_beast import WASTELAND_SPEC  # noqa: E402
from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

BLOOD = "#6E201B"
DUST = "#8C7458"
IRON = "#3C3F44"
PLUM = "#4A3A5A"
CHARCOAL = "#2E2B30"
SICK_GREEN = "#B6E04A"
MOSS = "#5E7A3A"
STONE = "#7D7A70"
STONE_DEEP = "#5C5A52"
JADE_GLOW = "#7FE0C0"


def _wasteland_marauder():
    spec = copy.deepcopy(BANDIT_SPEC)
    spec["name"] = "marauder"
    spec["skin"] = "#D2A87E"
    spec["hair"] = {"style": "spiky_long", "color": "#1C1A1E"}
    # Charcoal robe with plum accents: a cultivator of no sect — dark, but NOT the Blood Sand reds.
    spec["cloth"] = {"torso": CHARCOAL, "pelvis": "#3A363C", "legs": "#2A272C"}
    spec["shoe"] = "#1A181B"
    spec["accessories"] = [
        {"kind": "scarf", "color": PLUM},
        {"kind": "sash", "color": PLUM},
        {"kind": "arm_wrap", "color": "#6A6270"},
    ]
    spec["weapon"] = "long_blade"
    spec["weapon_color"] = "#8E96A0"
    spec["weapon_grip"] = PLUM
    return spec


def _blood_sand_digger():
    spec = copy.deepcopy(SCOUT_SPEC)
    spec["name"] = "digger"
    # Dust over the scout's dried-blood torso: a labourer of the cult, not a fighter.
    spec["cloth"] = {"torso": DUST, "pelvis": "#5A4A3A", "legs": "#4A3D30"}
    spec["accessories"] = [
        {"kind": "scarf", "color": "#3A2E24"},
        {"kind": "sash", "color": BLOOD},
        {"kind": "arm_wrap", "color": "#5A4A3A"},
    ]
    spec["weapon"] = "stone_mace"
    spec["weapon_color"] = "#6E6A63"
    spec["weapon_grip"] = "#3A2E24"
    return spec


def _blood_sand_beast_warden():
    spec = copy.deepcopy(ENFORCER_SPEC)
    spec["name"] = "beast_warden"
    spec["hair"] = {"style": "topknot", "color": "#1C1A19"}
    spec["cloth"] = {"torso": "#4A1C1A", "pelvis": "#2A1614", "legs": "#221210"}
    spec["accessories"] = [
        {"kind": "chest_plate", "color": IRON, "bone": "spine"},
        {"kind": "shoulder_plate", "color": IRON, "bone": "spine"},
        {"kind": "robe_sash", "color": "#B33B3B"},
        {"kind": "arm_wrap", "color": IRON},
    ]
    spec["weapon"] = "twin_blades"
    spec["weapon_color"] = "#9AA2AA"
    spec["weapon_grip"] = BLOOD
    return spec


def _frenzied_vein_beast():
    spec = copy.deepcopy(WASTELAND_SPEC)
    spec["name"] = "frenzied"
    spec["skin"] = "#9C9A86"
    spec["eye_color"] = SICK_GREEN
    spec["hair"] = {"style": "spiky_long", "color": "#3A4228"}
    spec["cloth"] = {"torso": "#6A5A36", "pelvis": "#4E4228", "legs": "#3E3420"}
    spec["accessories"] = [
        {"kind": "sash", "color": SICK_GREEN},
        {"kind": "arm_wrap", "color": MOSS},
        {"kind": "thigh_guard", "color": MOSS},
    ]
    spec["weapon_color"] = "#6E6A5A"
    return spec


def _ancient_stone_colossus():
    spec = copy.deepcopy(BOSS_SPEC)
    spec["name"] = "colossus"
    spec["skin"] = STONE
    spec["hair"] = {"style": "carved_crest", "color": STONE_DEEP, "cap": False}
    spec["cloth"] = {"torso": STONE, "pelvis": STONE_DEEP, "legs": "#6A675E"}
    spec["shoe"] = "#4A4840"
    spec["accessories"] = [
        {"kind": "stone_visor", "color": STONE_DEEP, "accent": JADE_GLOW},
        {"kind": "cloak", "color": MOSS},
        {"kind": "shoulder_plate", "color": MOSS, "bone": "arm_r"},
        {"kind": "shoulder_plate", "color": MOSS, "bone": "arm_l"},
        {"kind": "chest_plate", "color": "#4E6A40", "bone": "spine"},
        {"kind": "thigh_guard", "color": "#4E6A40"},
    ]
    spec["weapon"] = "stone_mace"
    spec["weapon_color"] = STONE_DEEP
    spec["weapon_grip"] = MOSS
    # The vein's power circling its guardian: jade, not the ruinlord's cinnabar.
    spec["motion_detail"] = {"kind": "stone_shards", "color": JADE_GLOW}
    return spec


SPECIES = {
    "wasteland_marauder": _wasteland_marauder,
    "blood_sand_digger": _blood_sand_digger,
    "blood_sand_beast_warden": _blood_sand_beast_warden,
    "frenzied_vein_beast": _frenzied_vein_beast,
    "ancient_stone_colossus": _ancient_stone_colossus,
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


if __name__ == "__main__":
    import argparse

    raw = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    parser = argparse.ArgumentParser()
    parser.add_argument("--species", required=True, choices=sorted(SPECIES))
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1)
    args = parser.parse_args(raw)
    build(args.species, args.out, args.outline_px)
