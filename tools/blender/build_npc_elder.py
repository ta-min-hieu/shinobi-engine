"""Build the `town_elder` NPC (WorldFacade.SAMPLE_NPC_CODE) — a static,
non-combat dialogue NPC (code comment: "khong combat (khac spawnTrainingDummy)").
Only needs `idle` (ART-PIPELINE.md §4.2 standard set is a ceiling, not a floor —
`render_all_actions()` only renders actions actually present in the .blend,
same approach as `build_monster_dummy.py`'s idle/hit/die subset).

Run headless (never in an MCP session — full file rebuild):

    blender -b assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_npc_elder.py \
      -- --out assets/raw/blender/characters/town-elder.blend

2-bone rig: `pelvis` (fixed, the floor-length robe hem) and `spine` (torso +
arms + head + beard, gently sways for an idle breathing loop) — same
fixed/moving split as the training dummy's `base`/`post`, applied to a
person-shaped silhouette instead of a post.
"""

import math
import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_sprite_render import make_toon_material, setup_scene  # noqa: E402

ROBE_HEX = "#5B6B7F"
TRIM_HEX = "#D8D2C4"
CANE_HEX = "#5C4326"


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
    # Robe: one unified box, floor to shoulder — no separate torso piece (a
    # coincident seam between two same-color boxes doesn't read as a
    # silhouette line anyway) and no separate legs (an elder in a robe
    # doesn't need a walk cycle). Front face at y = -0.13.
    idx_robe, _ = _add_box(verts, faces, 0.19, 0.13, 0.0, 0.60)
    idx_head, _ = _add_box(verts, faces, 0.16, 0.15, 0.60, 0.92)
    # Beard: front face at y = -0.085 - 0.05 = -0.135, just past the head's
    # front (-0.15 is the head's own front... wait computed below) — pushed
    # forward of the head so it isn't depth-occluded, same bug class as the
    # original B3 accent-occlusion lesson (see ASSET-LOG.md char_*-b3 entry).
    idx_beard, faces_trim = _add_box(verts, faces, 0.09, 0.05, 0.58, 0.70, off_y=-0.20)
    # Arms: pushed out past the robe's x=+-0.19 edge so most of each arm sits
    # in open space (not occluded by the wider robe box), front face matched
    # to the robe's own front plane (y=-0.13) rather than recessed behind it
    # — the exact occlusion mistake fixed once already for char_*-pilot's B3
    # accents applies here too (ASSET-LOG.md, "contrast comes from silhouette
    # separation, not depth-ordering").
    idx_arm_l, _ = _add_box(verts, faces, 0.045, 0.045, 0.34, 0.58, off_x=-0.24, off_y=-0.085)
    idx_arm_r, _ = _add_box(verts, faces, 0.045, 0.045, 0.34, 0.58, off_x=0.24, off_y=-0.085)
    # Cane: held by the right hand, resting on the ground — static prop, own
    # accent color, parented to spine (moves with the idle sway like the arm).
    idx_cane, _ = _add_box(verts, faces, 0.035, 0.035, 0.0, 0.62, off_x=0.26, off_y=-0.085)

    mesh = bpy.data.meshes.new("Elder_Body")
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    body = bpy.data.objects.new("Elder_Body", mesh)
    bpy.context.scene.collection.objects.link(body)

    arm_data = bpy.data.armatures.new("Elder_RigData")
    rig = bpy.data.objects.new("Elder_Rig", arm_data)
    bpy.context.scene.collection.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode="EDIT")
    eb = arm_data.edit_bones
    b_pelvis = eb.new("pelvis")
    b_pelvis.head, b_pelvis.tail = (0, 0, 0.0), (0, 0, 0.20)
    b_spine = eb.new("spine")
    b_spine.head, b_spine.tail, b_spine.parent = (0, 0, 0.42), (0, 0, 0.94), b_pelvis
    bpy.ops.object.mode_set(mode="OBJECT")
    for pb in rig.pose.bones:
        pb.rotation_mode = "XYZ"

    idx_by_bone = {
        "pelvis": list(idx_robe),
        "spine": idx_head + idx_beard + idx_arm_l + idx_arm_r + idx_cane,
    }
    for vg_name, indices in idx_by_bone.items():
        vg = body.vertex_groups.new(name=vg_name)
        vg.add(indices, 1.0, "REPLACE")

    modifier = body.modifiers.new("Armature", type="ARMATURE")
    modifier.object = rig
    body.parent = rig

    faces_accent = list(faces_trim)
    return body, rig, mesh, faces_accent, idx_cane, faces


def _apply_materials(body, mesh, faces_accent, cane_face_range):
    mat_robe = make_toon_material("toon_elder_robe", ROBE_HEX, shades=3)
    mat_trim = make_toon_material("toon_elder_trim", TRIM_HEX, shades=2)
    mat_cane = make_toon_material("toon_elder_cane", CANE_HEX, shades=2)
    body.data.materials.append(mat_robe)
    body.data.materials.append(mat_trim)
    body.data.materials.append(mat_cane)
    for face_index in faces_accent:
        mesh.polygons[face_index].material_index = 1
    for face_index in cane_face_range:
        mesh.polygons[face_index].material_index = 2


def _kf(rig, frame, spine_deg):
    pb = rig.pose.bones["spine"]
    euler = list(pb.rotation_euler)
    euler[2] = math.radians(spine_deg)  # bend axis, matches build_box_character.py convention
    pb.rotation_euler = euler
    pb.keyframe_insert(data_path="rotation_euler", index=2, frame=frame)
    rig.pose.bones["pelvis"].keyframe_insert(data_path="rotation_euler", index=2, frame=frame)


def _author_actions(rig):
    if rig.animation_data is None:
        rig.animation_data_create()
    action = bpy.data.actions.new("idle")
    rig.animation_data.action = action
    _kf(rig, 1, 0.0)
    _kf(rig, 9, -2.5)
    _kf(rig, 16, 0.0)

    rig.pose.bones["spine"].rotation_euler = (0, 0, 0)
    rig.location = (0, 0, 0)
    for act in bpy.data.actions:
        act.use_fake_user = True


def build(out_path, outline_px_at_1x=0.8):
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    scene = bpy.context.scene
    lineset = scene.view_layers[0].freestyle_settings.linesets[0]
    lineset.select_crease = True
    lineset.select_silhouette = True
    lineset.select_border = True

    body, rig, mesh, faces_accent, idx_cane, faces = _build_body_and_rig()
    # Cane occupies the last box added (6 faces) — see idx_cane in _build_body_and_rig.
    cane_face_range = range(len(faces) - 6, len(faces))
    _apply_materials(body, mesh, faces_accent, cane_face_range)
    _author_actions(rig)

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=out_path)
    print("[kage] npc elder saved: " + out_path)
    print("[kage] actions: " + str([a.name for a in bpy.data.actions]))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_npc_elder")
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=0.8)
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    args = _cli_args(sys.argv)
    if args is not None:
        build(args.out, args.outline_px)
