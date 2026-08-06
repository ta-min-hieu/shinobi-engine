"""Turn an imported Hyper3D mesh into a finished Kage character, headless.

Everything from step C to step D of BLENDER-SESSION-GUIDE §1.5, which up to now
was retyped into the MCP session once per character. Step A/B (generate, import)
still need the MCP session because the import tool lives there; step E stays in
`export_character_lods.py`.

The joint heights this needs are NOT constants — a generated body has whatever
proportions the model felt like, which is why `kage_text3d_kit.measure()` exists
and prints a profile for a human to read. `auto_measure()` below reads the same
profile mechanically so thirteen more characters do not each need a pair of eyes,
and prints every number it derived so a wrong one is visible rather than silent.
Override any of them with --joint name=value when the print looks wrong.

    blender -b assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_text3d_character.py -- \
      --source Rodin_Phong --element phong --tier b1 \
      --weapon twin_blades --color '#3DBF7A' --accent '#CFF5E0' \
      --out assets/raw/blender/characters/phong-b1.blend
"""

import math
import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import kage_sprite_render as ksr  # noqa: E402
import kage_text3d_kit as t3d  # noqa: E402

# A leg gap narrower than this is the crotch closing up, not two legs. Kept low
# on purpose: stopping too early puts the hip below where the legs really join.
LEG_GAP_FLOOR = 0.008

# How much wider than the torso a slice has to be before it is counted as arm.
ARM_WIDTH_RATIO = 1.15

# Outline width in @1x pixels. Deliberately NOT the pilot's 3.0: the shell
# inflates along the normals, so a mesh with finer detail blurs shut at a width
# another mesh wears comfortably. 1.2 is what `char_thuy-b1` measured to; check
# it per character (ART-QUALITY-STANDARD §4 item 3).
DEFAULT_OUTLINE_PX = 1.2

JOINTS = ("arm_y", "leg_y", "hip", "chest", "shoulder", "elbow", "wrist", "knee")


def _bands(obj, count=40):
    """(z, half-width across Y, widest Y gap, population) per horizontal slice."""
    points = [obj.matrix_world @ v.co for v in obj.data.vertices]
    lo = min(p.z for p in points)
    hi = max(p.z for p in points)
    step = (hi - lo) / count
    rows = []
    for index in range(count):
        low = lo + step * index
        slab = [p for p in points if low <= p.z < low + step]
        if not slab:
            rows.append((low, 0.0, 0.0, 0))
            continue
        ys = sorted(p.y for p in slab)
        gap = max((b - a for a, b in zip(ys, ys[1:])), default=0.0)
        rows.append((low, max(abs(ys[0]), abs(ys[-1])), gap, len(slab)))
    return rows


def auto_measure(obj):
    """Derive the eight rig joint heights from the mesh's own silhouette.

    Reads the same left-right density profile a human reads off `measure()`:
    the legs are the slices that still split in two, the neck is the narrowest
    slice above them, and the arms are the slices wider than the torso between
    those. Every value is printed, because a rig built on a joint in the wrong
    place produces a character that animates subtly wrong rather than visibly
    broken — the expensive kind of failure.
    """
    rows = _bands(obj)
    top = max(row[0] for row in rows)

    leg_rows = []
    for z, half, gap, _ in rows:
        if z > top * 0.45:
            break
        if gap <= LEG_GAP_FLOOR and leg_rows:
            break
        leg_rows.append((z, half, gap))
    if not leg_rows:
        raise RuntimeError("no split legs found; is the mesh in a T/A pose?")

    hip = leg_rows[-1][0]
    knee = hip * 0.5
    ankle_z, ankle_half, ankle_gap = leg_rows[min(1, len(leg_rows) - 1)]
    leg_y = (ankle_half + ankle_gap * 0.5) * 0.5

    upper = [row for row in rows if hip < row[0] < top * 0.75 and row[3]]
    neck_z, neck_half = min(((z, half) for z, half, _, _ in upper),
                            key=lambda pair: pair[1])
    torso = [half for z, half, _, _ in upper if z < neck_z]
    torso_half = sorted(torso)[len(torso) // 2] if torso else neck_half

    arm_rows = [(z, half) for z, half, _, _ in upper
                if z < neck_z and half > torso_half * ARM_WIDTH_RATIO]
    if arm_rows:
        # The shoulder is NOT the widest arm slice. A generated character comes
        # back with its arms hanging, so the widest slices are the elbow and the
        # hand — reading the shoulder off them put it at 0.275 on `char_thuy-b1`
        # where it belongs at 0.400, and a rig hung off that bends the arm from
        # the middle of the ribcage. The joint sits just under the neck instead.
        shoulder = neck_z - (neck_z - hip) * 0.15
        wrist = min(z for z, _ in arm_rows)
        arm_y = max(half for _, half in arm_rows) * 0.85
    else:
        # Arms merged into the torso silhouette: fall back to proportion and say so.
        print("[t3d] WARNING: arms not separable from torso; arm joints are estimates")
        shoulder, wrist = neck_z - (neck_z - hip) * 0.15, hip + (neck_z - hip) * 0.05
        arm_y = torso_half * 1.05

    joints = {
        "hip": hip, "knee": knee, "leg_y": leg_y, "chest": neck_z,
        "shoulder": shoulder, "elbow": (shoulder + wrist) * 0.5,
        "wrist": wrist, "arm_y": arm_y,
    }
    print("[t3d] auto joints: " + "  ".join(
        "{0}={1:.3f}".format(name, joints[name]) for name in JOINTS))
    return joints


def purge_foreign(source, element):
    """Delete everything that belongs to a *different* character.

    Step A/B run inside one long-lived MCP session, so the obvious way to build
    the second character is to import it into whatever file is already open —
    and then it inherits the first character's rig, mesh and weapon. Caught for
    real: `phong-b1.blend` came out wearing Thuy's gourd, and carried Thuy's raw
    45 MB Rodin import inside it. Opening the base file first would also fix it,
    but that has to happen *before* the import, which is exactly the step easiest
    to forget once the mesh is already on screen.
    """
    keep = {source, element.capitalize(), element + "_Rig",
            "KageCam", "KageKey", "KageFill",
            "guide_head", "guide_torso", "guide_legs"}
    foreign = [obj for obj in bpy.context.scene.objects
               if obj.name not in keep and not obj.name.startswith(element + "_")]
    for obj in foreign:
        bpy.data.objects.remove(obj, do_unlink=True)
    if foreign:
        print("[t3d] purged {0} object(s) from another character: {1}".format(
            len(foreign), ", ".join(sorted(obj.name for obj in foreign))))


def build(source, element, tier, weapon=None, color=None, accent=None,
          outline_px=DEFAULT_OUTLINE_PX, weapon_offset=None, overrides=None):
    purge_foreign(source, element)
    name = element.capitalize()
    obj = t3d.clean(t3d.normalise(source, name))
    joints = auto_measure(obj)
    joints.update(overrides or {})

    rig = t3d.rig(obj, element, **{key: joints[key] for key in JOINTS})
    t3d.author_scaled_actions(rig)
    # `rim` lifts near-black hair off its own silhouette; without it a generated
    # head reads as one flat blob no matter how good the map underneath is.
    t3d.toon_textured(obj, shades=3, rim=0.35)
    t3d.add_far_shade(obj)
    for action in bpy.data.actions:
        action.use_fake_user = True

    ksr.setup_scene(px=256)
    if weapon:
        t3d.attach_weapon(rig, element, weapon, offset=weapon_offset or (0.0, 0.0, 0.0),
                          outline_px_at_1x=outline_px, color=color, accent=accent)
    # The shell converts @1x pixels through the camera, so it can only be sized
    # once the camera is framed.
    ksr.fit_camera(ksr.renderable_meshes(bpy.context.scene), armature=rig,
                   actions=[a.name for a in bpy.data.actions])
    t3d.add_inverted_hull_outline(obj, width_px_at_1x=outline_px)
    print("[t3d] built {0}-{1}: mesh={2} rig={3}".format(element, tier, obj.name, rig.name))
    return obj, rig


def _cli(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_text3d_character")
    parser.add_argument("--source", required=True,
                        help="name of the imported Rodin mesh in the scene")
    parser.add_argument("--element", required=True,
                        choices=("hoa", "thuy", "phong", "loi", "tho"))
    parser.add_argument("--tier", required=True, choices=("b1", "b2", "b3"))
    parser.add_argument("--weapon", default=None,
                        help="kunai|gourd|twin_blades|long_blade|stone_mace")
    parser.add_argument("--color", default=None, help="weapon hex, element palette")
    parser.add_argument("--accent", default=None, help="weapon accent hex")
    parser.add_argument("--weapon-offset", default=None,
                        help="x,y,z shift onto this body. Measure the real gap; the "
                             "kit's mount point is tuned to the procedural torso and "
                             "hangs in mid-air on a generated one")
    parser.add_argument("--outline-px", type=float, default=DEFAULT_OUTLINE_PX)
    parser.add_argument("--joint", action="append", default=[], metavar="NAME=VALUE",
                        help="override one auto-measured joint, repeatable")
    parser.add_argument("--out", required=True, help="path to save the .blend to")
    return parser.parse_args(argv[argv.index("--") + 1:])


def _main():
    args = _cli(sys.argv)
    if args is None:
        return
    overrides = {}
    for item in args.joint:
        key, _, value = item.partition("=")
        if key not in JOINTS:
            raise SystemExit("unknown joint '{0}'; have {1}".format(key, ", ".join(JOINTS)))
        overrides[key] = float(value)
    offset = None
    if args.weapon_offset:
        offset = tuple(float(part) for part in args.weapon_offset.split(","))

    build(args.source, args.element, args.tier, weapon=args.weapon, color=args.color,
          accent=args.accent, outline_px=args.outline_px, weapon_offset=offset,
          overrides=overrides)

    # pack_all is not optional: the texture arrives from the cloud job and lives
    # nowhere on disk, so an unpacked save loses the only copy of the artwork.
    bpy.ops.file.pack_all()
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(args.out))
    print("[t3d] saved -> {0}".format(bpy.data.filepath))


if __name__ == "__main__":
    _main()
