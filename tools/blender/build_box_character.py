"""Build a rigged blocky-chibi character (7-bone armature, box-part mesh, 7
standard actions) from `kage_character_base.blend`.

Extracted from the char_hoa-pilot session — this is the recipe that came out
of that trial-and-error, not a new design. Every offset/angle/keyframe here
matches what was verified to render cleanly for char_hoa-pilot
(assets/raw/ASSET-LOG.md), so future elements stay visually consistent.

Run headless (never in an MCP session — this does a full file rebuild):

    blender -b assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_box_character.py \
      -- --element thuy --base-hex "#2D7FE8" --accent-hex "#7FD4FF" \
         --out assets/raw/blender/characters/thuy-pilot.blend

See docs/14-art/BLENDER-SESSION-GUIDE.md §4 for the two real bugs this
encodes the fix for: (1) every action keyframes every bone at every frame,
because fit_camera()'s cross-action sweep leaves un-keyframed channels at
whatever the last-swept action left them at; (2) outline defaults to 0.8px@1x
for this box-part-rig style, since the standard 2.5px floods it.
"""

import math
import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_sprite_render import make_toon_material, setup_scene  # noqa: E402

BONES = ["pelvis", "spine", "head", "arm_l", "arm_r", "leg_l", "leg_r"]


def _add_box(verts, faces, half_x, half_y, z_lo, z_hi, off_x=0.0, off_y=0.0):
    base = len(verts)
    for z in (z_lo, z_hi):
        for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            verts.append((off_x + sx * half_x, off_y + sy * half_y, z))
    face_start = len(faces)
    faces.extend([
        (base + 0, base + 1, base + 2, base + 3),
        (base + 7, base + 6, base + 5, base + 4),
        (base + 0, base + 4, base + 5, base + 1),
        (base + 1, base + 5, base + 6, base + 2),
        (base + 2, base + 6, base + 7, base + 3),
        (base + 3, base + 7, base + 4, base + 0),
    ])
    return list(range(base, base + 8)), list(range(face_start, len(faces)))


def _build_body_and_rig(name_prefix, bulk=1.0, props=None, gear=None):
    """bulk scales torso/pelvis/leg thickness (ART-PIPELINE §2.2 wants Thổ
    visibly "nặng nhất"). props is a list of extra weapon-silhouette boxes,
    each (half_x, half_y, z_lo, z_hi, off_x, off_y) — always hip-mounted
    (pelvis bone), matching the "Vũ khí" line which doesn't change per tier.
    gear is B2/B3 costume pieces (armor/scarf/thigh guards): each tuple adds
    a `bone` field so a piece can ride a different bone (e.g. thigh guards on
    leg_l/leg_r instead of pelvis)."""
    verts, faces = [], []
    idx_pelvis, faces_pelvis = _add_box(verts, faces, 0.13 * bulk, 0.10 * bulk, 0.20, 0.25)
    idx_torso, _ = _add_box(verts, faces, 0.14 * bulk, 0.10 * bulk, 0.25, 0.58)
    idx_head, _ = _add_box(verts, faces, 0.19, 0.17, 0.58, 0.98)
    idx_arm_l, _ = _add_box(verts, faces, 0.05, 0.05, 0.30, 0.56, off_x=-0.19, off_y=-0.05)
    idx_arm_r, _ = _add_box(verts, faces, 0.05, 0.05, 0.30, 0.56, off_x=0.19, off_y=-0.05)
    idx_leg_l, _ = _add_box(verts, faces, 0.055 * bulk, 0.06 * bulk, 0.0, 0.24, off_x=-0.07 * bulk, off_y=-0.04)
    idx_leg_r, _ = _add_box(verts, faces, 0.055 * bulk, 0.06 * bulk, 0.0, 0.24, off_x=0.07 * bulk, off_y=-0.04)

    idx_by_bone = {
        "pelvis": list(idx_pelvis), "spine": [], "head": [],
        "arm_l": [], "arm_r": [], "leg_l": [], "leg_r": [],
    }
    faces_accent = list(faces_pelvis)
    for half_x, half_y, z_lo, z_hi, off_x, off_y in (props or []):
        idx, prop_faces = _add_box(verts, faces, half_x, half_y, z_lo, z_hi, off_x=off_x, off_y=off_y)
        idx_by_bone["pelvis"].extend(idx)
        faces_accent.extend(prop_faces)
    for half_x, half_y, z_lo, z_hi, off_x, off_y, bone in (gear or []):
        idx, gear_faces = _add_box(verts, faces, half_x, half_y, z_lo, z_hi, off_x=off_x, off_y=off_y)
        idx_by_bone[bone].extend(idx)
        faces_accent.extend(gear_faces)

    mesh = bpy.data.meshes.new(name_prefix + "_Body")
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    body = bpy.data.objects.new(name_prefix + "_Body", mesh)
    bpy.context.scene.collection.objects.link(body)

    arm_data = bpy.data.armatures.new(name_prefix + "_RigData")
    rig = bpy.data.objects.new(name_prefix + "_Rig", arm_data)
    bpy.context.scene.collection.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode="EDIT")
    eb = arm_data.edit_bones
    b_pelvis = eb.new("pelvis")
    b_pelvis.head, b_pelvis.tail = (0, 0, 0.25), (0, 0, 0.32)
    b_spine = eb.new("spine")
    b_spine.head, b_spine.tail, b_spine.parent = (0, 0, 0.25), (0, 0, 0.60), b_pelvis
    b_head = eb.new("head")
    b_head.head, b_head.tail, b_head.parent = (0, 0, 0.60), (0, 0, 1.00), b_spine
    b_arm_l = eb.new("arm_l")
    b_arm_l.head, b_arm_l.tail, b_arm_l.parent = (-0.14, 0, 0.56), (-0.14, 0, 0.30), b_spine
    b_arm_r = eb.new("arm_r")
    b_arm_r.head, b_arm_r.tail, b_arm_r.parent = (0.14, 0, 0.56), (0.14, 0, 0.30), b_spine
    b_leg_l = eb.new("leg_l")
    b_leg_l.head, b_leg_l.tail, b_leg_l.parent = (-0.07, 0, 0.25), (-0.07, 0, 0.0), b_pelvis
    b_leg_r = eb.new("leg_r")
    b_leg_r.head, b_leg_r.tail, b_leg_r.parent = (0.07, 0, 0.25), (0.07, 0, 0.0), b_pelvis
    bpy.ops.object.mode_set(mode="OBJECT")
    for pb in rig.pose.bones:
        pb.rotation_mode = "XYZ"

    idx_by_bone["spine"].extend(idx_torso)
    idx_by_bone["head"].extend(idx_head)
    idx_by_bone["arm_l"].extend(idx_arm_l)
    idx_by_bone["arm_r"].extend(idx_arm_r)
    idx_by_bone["leg_l"].extend(idx_leg_l)
    idx_by_bone["leg_r"].extend(idx_leg_r)
    for vg_name, indices in idx_by_bone.items():
        vg = body.vertex_groups.new(name=vg_name)
        vg.add(indices, 1.0, "REPLACE")

    modifier = body.modifiers.new("Armature", type="ARMATURE")
    modifier.object = rig
    body.parent = rig

    return body, rig, mesh, faces_accent


def _apply_materials(body, mesh, faces_accent, element, base_hex, accent_hex):
    mat_main = make_toon_material("toon_" + element + "_body", base_hex, shades=3)
    mat_accent = make_toon_material("toon_" + element + "_accent", accent_hex, shades=3)
    body.data.materials.append(mat_main)
    body.data.materials.append(mat_accent)
    for face_index in faces_accent:
        mesh.polygons[face_index].material_index = 1


def _full_pose_kf(rig, frame, pose, obj_z=0.0):
    for bone in BONES:
        pb = rig.pose.bones[bone]
        degrees = pose.get(bone, 0.0)
        euler = list(pb.rotation_euler)
        euler[2] = math.radians(degrees)
        pb.rotation_euler = euler
        pb.keyframe_insert(data_path="rotation_euler", index=2, frame=frame)
    rig.location.z = obj_z
    rig.keyframe_insert(data_path="location", index=2, frame=frame)


def _author_actions(rig):
    if rig.animation_data is None:
        rig.animation_data_create()

    def new_action(action_name):
        action = bpy.data.actions.new(action_name)
        rig.animation_data.action = action
        return action

    new_action("idle")
    _full_pose_kf(rig, 1, {"arm_l": 5, "arm_r": -5}, obj_z=0.0)
    _full_pose_kf(rig, 9, {"arm_l": -3, "arm_r": 3}, obj_z=0.015)
    _full_pose_kf(rig, 16, {"arm_l": 5, "arm_r": -5}, obj_z=0.0)

    new_action("run")
    _full_pose_kf(rig, 1, {"leg_l": 35, "leg_r": -35, "arm_l": -30, "arm_r": 30})
    _full_pose_kf(rig, 7, {"leg_l": -35, "leg_r": 35, "arm_l": 30, "arm_r": -30})
    _full_pose_kf(rig, 12, {"leg_l": 35, "leg_r": -35, "arm_l": -30, "arm_r": 30})

    new_action("attack")
    _full_pose_kf(rig, 1, {"arm_r": 60})
    _full_pose_kf(rig, 4, {"arm_r": -70})
    _full_pose_kf(rig, 10, {"arm_r": -70})

    new_action("skill_cast")
    _full_pose_kf(rig, 1, {})
    _full_pose_kf(rig, 6, {"arm_l": 80, "arm_r": -80})
    _full_pose_kf(rig, 12, {"arm_l": 90, "arm_r": -90})

    new_action("hit")
    _full_pose_kf(rig, 1, {})
    _full_pose_kf(rig, 3, {"spine": -20})
    _full_pose_kf(rig, 6, {})

    new_action("jump")
    _full_pose_kf(rig, 1, {}, obj_z=0.0)
    _full_pose_kf(rig, 5, {"leg_l": 40, "leg_r": 40}, obj_z=0.22)
    _full_pose_kf(rig, 10, {}, obj_z=0.0)

    new_action("die")
    _full_pose_kf(rig, 1, {}, obj_z=0.0)
    _full_pose_kf(rig, 8, {"spine": -55, "pelvis": -30}, obj_z=-0.05)
    _full_pose_kf(rig, 14, {"spine": -65, "pelvis": -40}, obj_z=-0.09)

    for pb in rig.pose.bones:
        pb.rotation_euler = (0, 0, 0)
    rig.location = (0, 0, 0)
    for action in bpy.data.actions:
        action.use_fake_user = True


# One weapon-silhouette prop box per element, matching the "Vũ khí" line in
# ART-PIPELINE.md §2.2 for that element. Each tuple is
# (half_x, half_y, z_lo, z_hi, off_x, off_y); all hip-mounted (parented to
# pelvis, offset_y=-0.08/-0.10 to sit level with the torso's front plane like
# the arms do, so it isn't depth-occluded — see BLENDER-SESSION-GUIDE §4).
# bulk scales torso/pelvis/leg thickness; only Thổ uses >1.0 ("nặng nhất").
ELEMENT_SPECS = {
    "hoa": {  # kunai lưỡi rộng ở hông
        "bulk": 1.0,
        "props": [(0.10, 0.02, 0.22, 0.42, 0.20, -0.08)],
    },
    "thuy": {  # bình hồ lô đeo hông (không phải kiếm, hệ hỗ trợ)
        "bulk": 1.0,
        "props": [(0.06, 0.06, 0.30, 0.44, -0.20, -0.06)],
    },
    "phong": {  # song đao ngắn cong, 2 bên hông
        "bulk": 1.0,
        "props": [
            (0.025, 0.07, 0.20, 0.34, 0.20, -0.08),
            (0.025, 0.07, 0.20, 0.34, -0.20, -0.08),
        ],
    },
    "loi": {  # trường đao thẳng lưỡi mảnh — dài hơn hẳn các hệ khác
        "bulk": 1.0,
        "props": [(0.02, 0.10, 0.12, 0.50, 0.20, -0.08)],
    },
    "tho": {  # chuỳ đá lớn + thân hình nặng nhất
        "bulk": 1.22,
        "props": [(0.09, 0.09, 0.16, 0.40, 0.24, -0.10)],
    },
}

# B2 costume pieces ("giáp mảnh + khăn/đai màu hệ", ART-PIPELINE §2.2 §"Trang
# phục" B2 line for each element). Each tuple is
# (half_x, half_y, z_lo, z_hi, off_x, off_y, bone) — 7th field picks which
# bone the piece rides, since e.g. thigh guards need to move with the legs,
# not the torso. Rendered in the accent color, same as weapon props.
GEAR_SPECS_B2 = {
    "hoa": [(0.045, 0.05, 0.48, 0.58, -0.16, -0.06, "spine")],  # giáp vai trái đơn
    "thuy": [(0.11, 0.02, 0.38, 0.52, 0.0, -0.11, "spine")],  # giáp ngực mỏng
    "phong": [  # áo gile + đai lưng dài
        (0.145, 0.015, 0.30, 0.55, 0.0, -0.105, "spine"),
        (0.03, 0.015, 0.06, 0.22, -0.05, -0.10, "pelvis"),
    ],
    "loi": [  # giáp vai đôi góc cạnh
        (0.045, 0.05, 0.48, 0.58, -0.16, -0.06, "spine"),
        (0.045, 0.05, 0.48, 0.58, 0.16, -0.06, "spine"),
    ],
    "tho": [  # giáp ngực dày + tấm che đùi
        (0.16, 0.02, 0.30, 0.55, 0.0, -0.115, "spine"),
        (0.05, 0.02, 0.05, 0.15, -0.08, -0.09, "leg_l"),
        (0.05, 0.02, 0.05, 0.15, 0.08, -0.09, "leg_r"),
    ],
}

# B3 adds one continuously-moving decorative detail (ART-PIPELINE §2.2 B3
# line per element — NOT the same effect reused with a different color: each
# element gets its own motion style, matching what the doc actually
# describes). Implemented via drivers (not per-action keyframes) so it keeps
# moving no matter which action is playing — see BLENDER-SESSION-GUIDE §8.
#   hoa   "3 đốm lửa lơ lửng sau lưng"        -> 3 embers orbiting the back
#   thuy  "vòng nước xoay chậm quanh cổ tay"  -> 1 ring orbiting the wrist, slow
#   phong "2 dải lụa bay tự do"               -> 2 ribbons swaying off the shoulders
#   loi   "tia điện...2 frame lặp"            -> 1 spark blinking on a 2-frame cycle
#   tho   "mảnh đá lơ lửng quanh vai"         -> 2 chunky shards orbiting the shoulder, slow


def _add_scripted_driver(obj, path, index, expression):
    fcurve = obj.driver_add(path, index)
    drv = fcurve.driver
    drv.type = "SCRIPTED"
    var = drv.variables.new()
    var.name = "f"
    var.type = "SINGLE_PROP"
    var.targets[0].id_type = "SCENE"
    var.targets[0].id = bpy.context.scene
    var.targets[0].data_path = "frame_current"
    drv.expression = expression


def _new_prop_mesh(rig, name_prefix, suffix, element, accent_hex, verts, faces):
    mesh = bpy.data.meshes.new(name_prefix + suffix)
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name_prefix + suffix, mesh)
    bpy.context.scene.collection.objects.link(obj)
    mat = make_toon_material("toon_" + element + suffix.lower(), accent_hex, shades=2)
    obj.data.materials.append(mat)
    obj.parent = rig
    return obj


def _box_verts(size):
    return [(sx * size, sy * size, sz * size)
            for sz in (-1, 1) for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1))]


_BOX_FACES = [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]


def _add_orbit_prop(rig, name_prefix, element, accent_hex, radius, z_center, speed, size,
                     x_center=0.0, phase=0.0, suffix="_Orbit"):
    """Circles in the screen-visible X-Z plane. y=-0.22 keeps it in front of
    the head (front face at -0.17) at every angle, so it's never depth-
    occluded — same bug class as the original arm-vs-torso occlusion."""
    obj = _new_prop_mesh(rig, name_prefix, suffix, element, accent_hex, _box_verts(size), _BOX_FACES)
    obj.location = (x_center + radius, -0.22, z_center)
    _add_scripted_driver(obj, "location", 0,
                          "{0} + {1} * cos(f * {2} + {3})".format(x_center, radius, speed, phase))
    _add_scripted_driver(obj, "location", 2,
                          "{0} + {1} * sin(f * {2} + {3})".format(z_center, radius, speed, phase))
    return obj


def _add_flicker_prop(rig, name_prefix, element, accent_hex, position, size, period=2, min_scale=0.12):
    """Fixed position, scale toggles between full and near-zero every
    `period` frames — a blink/spark, not smooth motion (matches Lôi's "2
    frame lặp" description, which is explicitly not an orbit)."""
    obj = _new_prop_mesh(rig, name_prefix, "_Spark", element, accent_hex, _box_verts(size), _BOX_FACES)
    obj.location = position
    expr = "1.0 if (int(floor(f)) % {0}) == 0 else {1}".format(period, min_scale)
    for axis in range(3):
        _add_scripted_driver(obj, "scale", axis, expr)
    return obj


def _add_sway_prop(rig, name_prefix, element, accent_hex, suffix, pivot, half_x, half_y, length,
                    amplitude_deg, speed, phase=0.0):
    """A thin strip hanging from `pivot`, swaying like a loose ribbon. Object-
    level rotation around Y = world Y here (no parent rotation), which is the
    axis that swings something visibly within our X-Z camera view."""
    verts = [(sx * half_x, sy * half_y, z) for z in (-length, 0.0)
             for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
    obj = _new_prop_mesh(rig, name_prefix, suffix, element, accent_hex, verts, _BOX_FACES)
    obj.location = pivot
    _add_scripted_driver(obj, "rotation_euler", 1,
                          "radians({0}) * sin(f * {1} + {2})".format(amplitude_deg, speed, phase))
    return obj


def _add_b3_detail(rig, element, accent_hex):
    if element == "hoa":
        for i in range(3):
            _add_orbit_prop(rig, "hoa", element, accent_hex, radius=0.11, z_center=0.72,
                             speed=0.16, size=0.032, phase=i * 2.09, suffix="_Ember{0}".format(i))
    elif element == "thuy":
        _add_orbit_prop(rig, "thuy", element, accent_hex, radius=0.06, z_center=0.42,
                         speed=0.06, size=0.032, x_center=0.19, suffix="_WristRing")
    elif element == "phong":
        _add_sway_prop(rig, "phong", element, accent_hex, "_RibbonL", pivot=(-0.15, -0.10, 0.55),
                        half_x=0.018, half_y=0.055, length=0.24, amplitude_deg=24, speed=0.30, phase=0.0)
        _add_sway_prop(rig, "phong", element, accent_hex, "_RibbonR", pivot=(0.15, -0.10, 0.55),
                        half_x=0.018, half_y=0.055, length=0.24, amplitude_deg=24, speed=0.30, phase=3.14159)
    elif element == "loi":
        # Two failed spots first: near the hip weapon (same accent color,
        # same location -> invisible), then touching the shoulder plate edge
        # (still same color, no negative space around it -> no contrast even
        # in front). This sits in open space past the shoulder's outer edge
        # (plate spans x=0.115-0.205) so there's always a visible gap around
        # it regardless of the plate's own shading.
        _add_flicker_prop(rig, "loi", element, accent_hex, position=(0.26, -0.08, 0.53),
                           size=0.05, period=2, min_scale=0.08)
    elif element == "tho":
        for i in range(2):
            _add_orbit_prop(rig, "tho", element, accent_hex, radius=0.15, z_center=0.80,
                             speed=0.05, size=0.048, phase=i * 3.14159, suffix="_Shard{0}".format(i))


def build(element, base_hex, accent_hex, out_path, tier="b1", outline_px_at_1x=0.8):
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    scene = bpy.context.scene
    lineset = scene.view_layers[0].freestyle_settings.linesets[0]
    lineset.select_crease = True
    lineset.select_silhouette = True
    lineset.select_border = True

    spec = ELEMENT_SPECS.get(element, {})
    gear = GEAR_SPECS_B2.get(element) if tier in ("b2", "b3") else None
    body, rig, mesh, faces_accent = _build_body_and_rig(
        element, bulk=spec.get("bulk", 1.0), props=spec.get("props"), gear=gear)
    _apply_materials(body, mesh, faces_accent, element, base_hex, accent_hex)
    _author_actions(rig)
    if tier == "b3":
        _add_b3_detail(rig, element, accent_hex)

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=out_path)
    print("[kage] box character saved: " + out_path)
    print("[kage] tier: " + tier)
    print("[kage] actions: " + str([a.name for a in bpy.data.actions]))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_box_character")
    parser.add_argument("--element", required=True, help="element key, e.g. thuy")
    parser.add_argument("--base-hex", required=True, help="main element color, ART-PIPELINE §2.2")
    parser.add_argument("--accent-hex", required=True, help="secondary element color")
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--tier", default="b1", choices=("b1", "b2", "b3"),
                         help="gear tier per ART-PIPELINE §2.2: b1 plain, b2 +armor/sash, "
                              "b3 +moving accent detail")
    parser.add_argument("--outline-px", type=float, default=0.8,
                         help="outline width @1x px; 0.8 matches char_hoa-pilot's box-rig style")
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    args = _cli_args(sys.argv)
    if args is not None:
        build(args.element, args.base_hex, args.accent_hex, args.out, args.tier, args.outline_px)
