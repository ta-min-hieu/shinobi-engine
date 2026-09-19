"""Replace the weapon on an ALREADY BUILT character .blend, in place.

Why this exists rather than a rebuild: the five playable factions are Text->3D
characters, so their bodies came back from a Rodin job and `build_text3d_character`
needs that generated mesh in the scene to run at all. The Rodin key has been out
of funds since 2026-09-03, so those fifteen `.blend` files ARE the masters now -
the body cannot be regenerated, only edited.

The weapon, by contrast, is not part of the generated body: `attach_weapon` builds
it from `kage_mesh_kit.build_weapon` as separate objects rigidly bound to a bone
(ADR-031's "swap the mesh on the bone, do not re-bake the character"). So a weapon
fix in the kit can be pushed onto a finished character by deleting the old parts
and calling the same function again - which is exactly what this script does.

First use: 2026-09-19, moving `long_blade` into the client camera's view. It had
been mounted across the BACK at x = -0.145 and rendered ZERO pixels in game; see
ASSET-LOG 2026-09-19 (2).

Run headless, one character at a time (it rewrites the file it opens):

    blender -b assets/raw/blender/characters/loi-b1.blend \
      -P tools/blender/retrofit_weapon.py -- \
      --weapon long_blade --color "#8C4DE8" --accent "#FFE84D"

Then re-export the LODs exactly as the original build did (ART-PIPELINE §6.5).
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import kage_text3d_kit as t3d  # noqa: E402
from build_text3d_character import DEFAULT_OUTLINE_PX  # noqa: E402

# Every part `build_weapon` emits is named "<prefix>_<Shape>...", and the shapes
# are the only objects in these files that are neither the body, the rig, the
# scene guides nor the kept Rodin import.
WEAPON_SHAPES = ("LongBlade", "Kunai", "Gourd", "Sabre", "Mace")


def _weapon_parts():
    return [obj for obj in bpy.data.objects
            if obj.type == "MESH" and any(shape in obj.name for shape in WEAPON_SHAPES)]


def _rig():
    rigs = [obj for obj in bpy.data.objects if obj.type == "ARMATURE"]
    if len(rigs) != 1:
        raise SystemExit("expected exactly one armature, found {0}".format(
            [r.name for r in rigs]))
    return rigs[0]


def _body(rig, weapon_names):
    """The LARGEST deformed mesh that is not a weapon part.

    `Rodin_*` (the kept raw import) has no armature modifier and the scene guides
    are plain meshes, so "bound to the rig" already excludes those. It does NOT
    exclude a B3 tier's motion detail: `loi_Spark` is rigged too, and a first
    version of this script that demanded exactly one rigged body refused to touch
    `loi-b3.blend` at all. Size settles it without a name list to drift - a Rodin
    body is ~90,000 triangles against a spark's few dozen - and `seat_weapon`
    wants that same mesh anyway, since it measures the body's surface.
    """
    bodies = [obj for obj in bpy.data.objects
              if obj.type == "MESH" and obj.name not in weapon_names
              and any(m.type == "ARMATURE" and m.object is rig for m in obj.modifiers)]
    if not bodies:
        raise SystemExit("no rigged body found")
    bodies.sort(key=lambda obj: len(obj.data.vertices), reverse=True)
    if len(bodies) > 1:
        print("[retrofit] rigged meshes: " + ", ".join(
            "{0}({1}v)".format(b.name, len(b.data.vertices)) for b in bodies))
    return bodies[0]


def retrofit(kind, color, accent, outline_px):
    old = _weapon_parts()
    if not old:
        raise SystemExit("no weapon parts found; nothing to retrofit")
    prefix = old[0].name.split("_")[0]
    old_names = {obj.name for obj in old}
    rig = _rig()
    body = _body(rig, old_names)
    print("[retrofit] prefix={0} rig={1} body={2}".format(prefix, rig.name, body.name))
    print("[retrofit] removing: " + ", ".join(sorted(old_names)))

    meshes = [obj.data for obj in old]
    for obj in old:
        bpy.data.objects.remove(obj, do_unlink=True)
    for mesh in meshes:
        if mesh.users == 0:
            bpy.data.meshes.remove(mesh)

    parts = t3d.attach_weapon(rig, prefix, kind, offset=(0.0, 0.0, 0.0),
                              outline_px_at_1x=outline_px, color=color, accent=accent,
                              body=body)
    print("[retrofit] rebuilt: " + ", ".join(sorted(p.name for p in parts)))
    bpy.ops.wm.save_mainfile()
    print("[retrofit] saved {0}".format(bpy.data.filepath))


def _cli(argv):
    import argparse

    parser = argparse.ArgumentParser(prog="retrofit_weapon")
    parser.add_argument("--weapon", required=True,
                        help="kunai|gourd|twin_blades|long_blade|stone_mace")
    parser.add_argument("--color", required=True, help="weapon hex")
    parser.add_argument("--accent", required=True, help="grip/accent hex")
    parser.add_argument("--outline-px", type=float, default=DEFAULT_OUTLINE_PX)
    return parser.parse_args(argv)


if __name__ == "__main__":
    raw = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    parsed = _cli(raw)
    retrofit(parsed.weapon, parsed.color, parsed.accent, parsed.outline_px)
