"""Build the `training_dummy` monster (WorldFacade.TRAINING_DUMMY_CODE) —
a wooden training post hopping about on its stump.

5 actions, not the 7 of a character: `idle`, `run`, `attack`, `hit`, `die`.
The first version of this script shipped only 3 (idle/hit/die) on the premise
that "a training target never moves or attacks". **That premise was wrong about
this project's own server**: `WorldFacade.spawnTrainingDummy` gives the entity
an `AiStateComponent` (patrol/chase, AI.md §3) and an `AttackCooldownComponent`,
so the dummy really does chase the player and really does hit back. Rendering it
frozen in `idle` while it closes distance is a lie the 3D branch cannot tell
(2026-08-09).

`jump` and `skill_cast` stay absent on purpose — no server code path makes a
monster jump or cast, and the client tolerates a missing clip by falling back to
`idle` (see `MonsterAnimationClips`), so authoring dead animation would be waste.

Run headless (never in an MCP session — full file rebuild):

    blender -b assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_monster_dummy.py \
      -- --out assets/raw/blender/monsters/training-dummy.blend

2-bone rig: `base` (fixed, never posed) and `post` (parent of the whole
wooden body+strawhead+crossbeam mesh) so every action is one bone's rotation
plus the rig's own height — no need for the 7-bone character armature.
"""

import math
import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_sprite_render import make_toon_material, setup_scene  # noqa: E402

WOOD_HEX = "#8B5A2B"
STRAW_HEX = "#D9BF77"
TARGET_HEX = "#B33B3B"


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


def _build_body_and_rig():
    verts, faces = [], []
    idx_stump, _ = _add_box(verts, faces, 0.12, 0.12, 0.0, 0.08)  # ground stump, never moves
    idx_post, _ = _add_box(verts, faces, 0.08, 0.08, 0.08, 0.75)  # wooden post
    idx_arm_l, _ = _add_box(verts, faces, 0.16, 0.045, 0.50, 0.58, off_x=-0.16)  # crossbeam
    idx_arm_r, _ = _add_box(verts, faces, 0.16, 0.045, 0.50, 0.58, off_x=0.16)
    idx_head, faces_target = _add_box(verts, faces, 0.14, 0.10, 0.75, 0.98)  # straw target head

    mesh = bpy.data.meshes.new("Dummy_Body")
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    body = bpy.data.objects.new("Dummy_Body", mesh)
    bpy.context.scene.collection.objects.link(body)

    arm_data = bpy.data.armatures.new("Dummy_RigData")
    rig = bpy.data.objects.new("Dummy_Rig", arm_data)
    bpy.context.scene.collection.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode="EDIT")
    eb = arm_data.edit_bones
    b_base = eb.new("base")
    b_base.head, b_base.tail = (0, 0, 0.0), (0, 0, 0.08)
    b_post = eb.new("post")
    b_post.head, b_post.tail, b_post.parent = (0, 0, 0.08), (0, 0, 0.75), b_base
    bpy.ops.object.mode_set(mode="OBJECT")
    for pb in rig.pose.bones:
        pb.rotation_mode = "XYZ"

    idx_by_bone = {"base": list(idx_stump), "post": idx_post + idx_arm_l + idx_arm_r + idx_head}
    for vg_name, indices in idx_by_bone.items():
        vg = body.vertex_groups.new(name=vg_name)
        vg.add(indices, 1.0, "REPLACE")

    modifier = body.modifiers.new("Armature", type="ARMATURE")
    modifier.object = rig
    body.parent = rig

    return body, rig, mesh, faces_target


def _apply_materials(body, mesh, faces_target):
    mat_wood = make_toon_material("toon_dummy_wood", WOOD_HEX, shades=3)
    mat_target = make_toon_material("toon_dummy_target", TARGET_HEX, shades=2)
    body.data.materials.append(mat_wood)
    body.data.materials.append(mat_target)
    for face_index in faces_target:
        mesh.polygons[face_index].material_index = 1


def _kf(rig, frame, post_deg, base_z=0.0):
    pb = rig.pose.bones["post"]
    euler = list(pb.rotation_euler)
    # Bone points along world Z (head->tail = local Y), same as the character
    # rig's spine/pelvis bones in build_box_character.py — index 2 (local Z)
    # is what bends/leans it in the camera-visible X-Z plane there, not
    # index 1 (that twists around the post's own long axis instead).
    euler[2] = math.radians(post_deg)
    pb.rotation_euler = euler
    pb.keyframe_insert(data_path="rotation_euler", index=2, frame=frame)
    rig.location.z = base_z
    rig.keyframe_insert(data_path="location", index=2, frame=frame)


def _author_actions(rig):
    if rig.animation_data is None:
        rig.animation_data_create()

    def new_action(name):
        action = bpy.data.actions.new(name)
        rig.animation_data.action = action
        return action

    new_action("idle")  # barely-there creak, stationary object
    _kf(rig, 1, 0.0)
    _kf(rig, 9, 2.0)
    _kf(rig, 16, 0.0)

    # A post with no legs closes distance by HOPPING on its stump: the whole rig
    # leaves the ground (base_z) while the body leans into the direction of
    # travel. Two hops per loop so the cycle reads as scurrying at the 12-frame
    # length the other looping clip (idle) sets the expectation for.
    new_action("run")
    _kf(rig, 1, 0.0, base_z=0.0)
    _kf(rig, 4, 7.0, base_z=0.09)
    _kf(rig, 7, 0.0, base_z=0.0)
    _kf(rig, 10, 7.0, base_z=0.09)
    _kf(rig, 13, 0.0, base_z=0.0)

    # Lunge FORWARD then settle — positive degrees, the opposite sign of `hit`
    # and `die`, which both lean away (mesh faces +X, ASSET-LOG "Quy ước bắt
    # buộc"). Peak at frame 3 so the swing lands early and the recovery reads as
    # the slow part, the same shape as the character rigs' `attack`.
    new_action("attack")
    _kf(rig, 1, 0.0)
    _kf(rig, 3, 24.0, base_z=0.04)
    _kf(rig, 6, 6.0)
    _kf(rig, 10, 0.0)

    new_action("hit")  # recoil from impact
    _kf(rig, 1, 0.0)
    _kf(rig, 3, -12.0)
    _kf(rig, 6, 0.0)

    new_action("die")  # topple and stay down (partial lean, not full flat —
    # a 90-degree topple blows out fit_camera's shared frame size for every
    # other action too, since it sweeps all actions for one ortho_scale)
    _kf(rig, 1, 0.0)
    _kf(rig, 8, -35.0, base_z=-0.03)
    _kf(rig, 14, -50.0, base_z=-0.05)

    rig.pose.bones["post"].rotation_euler = (0, 0, 0)
    rig.location = (0, 0, 0)
    for action in bpy.data.actions:
        action.use_fake_user = True


def build(out_path, outline_px_at_1x=0.8):
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    scene = bpy.context.scene
    lineset = scene.view_layers[0].freestyle_settings.linesets[0]
    lineset.select_crease = True
    lineset.select_silhouette = True
    lineset.select_border = True

    body, rig, mesh, faces_target = _build_body_and_rig()
    _apply_materials(body, mesh, faces_target)
    _author_actions(rig)

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=out_path)
    print("[kage] monster dummy saved: " + out_path)
    print("[kage] actions: " + str([a.name for a in bpy.data.actions]))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_monster_dummy")
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=0.8)
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    args = _cli_args(sys.argv)
    if args is not None:
        build(args.out, args.outline_px)
