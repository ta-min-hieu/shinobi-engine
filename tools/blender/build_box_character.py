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


def _build_body_and_rig(name_prefix):
    verts, faces = [], []
    idx_pelvis, faces_pelvis = _add_box(verts, faces, 0.13, 0.10, 0.20, 0.25)
    idx_torso, _ = _add_box(verts, faces, 0.14, 0.10, 0.25, 0.58)
    idx_head, _ = _add_box(verts, faces, 0.19, 0.17, 0.58, 0.98)
    idx_arm_l, _ = _add_box(verts, faces, 0.05, 0.05, 0.30, 0.56, off_x=-0.19, off_y=-0.05)
    idx_arm_r, _ = _add_box(verts, faces, 0.05, 0.05, 0.30, 0.56, off_x=0.19, off_y=-0.05)
    idx_leg_l, _ = _add_box(verts, faces, 0.055, 0.06, 0.0, 0.24, off_x=-0.07, off_y=-0.04)
    idx_leg_r, _ = _add_box(verts, faces, 0.055, 0.06, 0.0, 0.24, off_x=0.07, off_y=-0.04)

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

    vgroups = {
        "pelvis": idx_pelvis, "spine": idx_torso, "head": idx_head,
        "arm_l": idx_arm_l, "arm_r": idx_arm_r, "leg_l": idx_leg_l, "leg_r": idx_leg_r,
    }
    for vg_name, indices in vgroups.items():
        vg = body.vertex_groups.new(name=vg_name)
        vg.add(indices, 1.0, "REPLACE")

    modifier = body.modifiers.new("Armature", type="ARMATURE")
    modifier.object = rig
    body.parent = rig

    return body, rig, mesh, faces_pelvis


def _apply_materials(body, mesh, faces_pelvis, element, base_hex, accent_hex):
    mat_main = make_toon_material("toon_" + element + "_body", base_hex, shades=3)
    mat_accent = make_toon_material("toon_" + element + "_accent", accent_hex, shades=3)
    body.data.materials.append(mat_main)
    body.data.materials.append(mat_accent)
    for face_index in faces_pelvis:
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
    _full_pose_kf(rig, 1, {})
    _full_pose_kf(rig, 8, {"spine": -85})
    _full_pose_kf(rig, 14, {"spine": -90})

    for pb in rig.pose.bones:
        pb.rotation_euler = (0, 0, 0)
    rig.location = (0, 0, 0)
    for action in bpy.data.actions:
        action.use_fake_user = True


def build(element, base_hex, accent_hex, out_path, outline_px_at_1x=0.8):
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    scene = bpy.context.scene
    lineset = scene.view_layers[0].freestyle_settings.linesets[0]
    lineset.select_crease = True
    lineset.select_silhouette = True
    lineset.select_border = True

    body, rig, mesh, faces_pelvis = _build_body_and_rig(element)
    _apply_materials(body, mesh, faces_pelvis, element, base_hex, accent_hex)
    _author_actions(rig)

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=out_path)
    print("[kage] box character saved: " + out_path)
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
    parser.add_argument("--outline-px", type=float, default=0.8,
                         help="outline width @1x px; 0.8 matches char_hoa-pilot's box-rig style")
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    args = _cli_args(sys.argv)
    if args is not None:
        build(args.element, args.base_hex, args.accent_hex, args.out, args.outline_px)
