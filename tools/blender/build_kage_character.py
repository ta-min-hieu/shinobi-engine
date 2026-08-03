"""Build one Kage character .blend from the mesh kit.

This replaces build_box_character.py for every player/NPC-shaped asset. The box
builder stays in the tree only so the first-generation .blend files can still be
regenerated; nothing new should use it (ART-QUALITY-STANDARD.md §0).

    blender -b assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_kage_character.py \
      -- --element hoa --tier b1 --out assets/raw/blender/characters/hoa-b1.blend

Then render the sheet with the normal pipeline:

    blender -b <that file> -P tools/blender/kage_sprite_render.py \
      -- --all-actions --out assets/raw/render-out/char_hoa-b1

Costume descriptions come from ART-PIPELINE.md §2.3 (the "Trang phục" line of
each element). One deliberate adaptation: the doc describes single-sided pieces
by the character's own left/right, but the sprite is a true right-facing
profile, so a left-shoulder plate would sit behind the body and never be seen.
Single pieces are therefore mounted on the camera-facing side.
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_mesh_kit import assemble_character, shade_hex  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

# Element palette — the same hexes as ART-PIPELINE.md §2.2.
ELEMENT_COLORS = {
    "hoa": ("#E8552D", "#FFB13D"),
    "thuy": ("#2D7FE8", "#7FD4FF"),
    "phong": ("#3DBF7A", "#CFF5E0"),
    "loi": ("#8C4DE8", "#FFE84D"),
    "tho": ("#A6763D", "#E0C089"),
}

SKIN = "#F2C79A"

# Per-element base costume (B1) plus the pieces each tier adds. Cloth colours
# are deliberately NOT the element hex: a character dressed head to toe in the
# element colour loses the "3 separate colour blocks" read (Q4), so the element
# shows up in the accents, the hair keeps its own value, and the cloth sits in
# a muted family around it.
CHARACTERS = {
    "hoa": {
        "hair": {"style": "spiky_short", "color": "#4A2B1E"},
        "cloth": {"torso": "#C0703A", "pelvis": "#5E4028", "legs": "#8C5E3C"},
        "shoe": "#4A3526",
        "weapon": "kunai",
        "b1": [("arm_wrap", "#D9584A")],
        "b2": [("shoulder_plate", "#C9762F"), ("scarf", "#E8552D")],
        "b3": [("cloak", "#7A2E18")],
        "motion": {"kind": "embers", "color": "#FFB13D"},
    },
    "thuy": {
        "hair": {"style": "bang", "color": "#243447"},
        "cloth": {"torso": "#9FD3EC", "pelvis": "#4A6E8A", "legs": "#7FA9C4"},
        "shoe": "#E4E0D4",  # chân trần quấn băng
        "weapon": "gourd",
        "b1": [("arm_wrap", "#E4E0D4")],
        "b2": [("chest_plate", "#2D7FE8")],
        "b3": [("cloak", "#3E7FA8"), ("sash", "#7FD4FF")],
        "motion": {"kind": "water_ring", "color": "#7FD4FF"},
    },
    "phong": {
        "hair": {"style": "spiky_long", "color": "#2E3B2A"},
        "cloth": {"torso": "#8FCFA4", "pelvis": "#4C7057", "legs": "#6A9273"},
        "shoe": "#33443A",
        "weapon": "twin_blades",
        "b1": [("headband", "#3DBF7A")],
        "b2": [("chest_plate", "#3DBF7A"), ("sash", "#2F8C5A")],
        "b3": [("scarf", "#CFF5E0")],
        "motion": {"kind": "ribbons", "color": "#CFF5E0"},
    },
    "loi": {
        "hair": {"style": "spiky_short", "color": "#1E1B2B"},
        "cloth": {"torso": "#454263", "pelvis": "#302C48", "legs": "#3A3658"},
        "shoe": "#232038",
        "weapon": "long_blade",
        "b1": [("headband", "#FFE84D")],
        "b2": [("shoulder_plate", "#8C4DE8"), ("shoulder_plate", "#8C4DE8", "arm_l"),
               ("arm_wrap", "#4A3D6B")],
        "b3": [("cloak", "#3B2A5C")],
        "motion": {"kind": "spark", "color": "#FFE84D"},
    },
    "tho": {
        "hair": {"style": "topknot", "color": "#3B2A1C"},
        "cloth": {"torso": "#A6763D", "pelvis": "#6B4A28", "legs": "#8A6232"},
        "shoe": "#4A331E",
        "weapon": "stone_mace",
        "b1": [("shoulder_plate", "#7A5A32")],  # giáp da vai
        "b2": [("chest_plate", "#8A6232"), ("thigh_guard", "#6B4A28"),
               ("headband", "#E0C089")],
        "b3": [("cloak", "#6E5233")],
        "motion": {"kind": "stone_shards", "color": "#E0C089"},
    },
}

TIER_ORDER = ("b1", "b2", "b3")


def _accessories_for(entry, tier):
    """Accumulate tier pieces: B2 keeps B1's, B3 keeps both."""
    chosen = []
    for level in TIER_ORDER[:TIER_ORDER.index(tier) + 1]:
        for piece in entry.get(level, []):
            accessory = {"kind": piece[0], "color": piece[1]}
            if len(piece) > 2:
                accessory["bone"] = piece[2]
            chosen.append(accessory)
    return chosen


def build_spec(element, tier):
    if element not in CHARACTERS:
        raise KeyError("unknown element '{0}'; have {1}".format(
            element, sorted(CHARACTERS)))
    entry = CHARACTERS[element]
    base_hex, accent_hex = ELEMENT_COLORS[element]
    spec = {
        "name": "{0}_{1}".format(element, tier),
        "skin": SKIN,
        "hair": dict(entry["hair"]),
        "cloth": dict(entry["cloth"]),
        "shoe": entry["shoe"],
        "accessories": _accessories_for(entry, tier),
        "weapon": entry["weapon"],
        "weapon_color": entry.get("weapon_color", "#D6DCE4"),
        "weapon_grip": entry.get("weapon_grip", "#4A3524"),
    }
    if element == "tho":
        # A stone mace is rock, not steel.
        spec["weapon_color"] = shade_hex(base_hex, 0.72)
    if tier == "b3":
        spec["motion_detail"] = dict(entry["motion"])
    return spec


def _clear_scene():
    """Drop everything the base file carries in, guides included.

    The scale guides are hide_render, so they never reach a sprite, but
    fit_camera() measures bounding boxes rather than visibility and would frame
    the shot around them.
    """
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)


def build(element, tier, out_path, outline_px_at_1x=1.1):
    _clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    spec = build_spec(element, tier)
    rig = assemble_character(spec)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=out_path)
    meshes = [o for o in bpy.data.objects if o.type == "MESH"]
    print("[kage] character saved: {0}".format(out_path))
    print("[kage] element={0} tier={1} parts={2} bones={3}".format(
        element, tier, len(meshes), len(rig.pose.bones)))
    print("[kage] actions: " + ", ".join(sorted(a.name for a in bpy.data.actions)))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_kage_character")
    parser.add_argument("--element", required=True, choices=sorted(CHARACTERS))
    parser.add_argument("--tier", default="b1", choices=TIER_ORDER)
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1,
                        help="outline width @1x px, saved into the .blend so the render "
                             "step inherits it. 1.1 was measured, not assumed: a 256px "
                             "master frames the character ~166px tall, and the art "
                             "direction's 2.5 covers it in ink at that size.")
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    args = _cli_args(sys.argv)
    if args is not None:
        build(args.element, args.tier, args.out, args.outline_px)
