"""Turn a Hyper3D/Hunyuan text-to-3D character mesh into a Kage-ready rigged asset.

The procedural `kage_mesh_kit.py` builds a character out of primitives and owns
every material. A generated mesh is the opposite: one welded lump with a painted
diffuse map that already carries the face, the outlines and most of the shading.
Everything in this file exists because that difference breaks a step of the
normal pipeline:

  * `kage_sprite_render.apply_toon()` clears the material slots and assigns one
    flat colour — on a generated mesh that erases the face. `toon_textured()`
    keeps the map and quantises only the lighting.
  * Bone-heat weighting fails outright on the raw import (every vertex group
    comes back empty) because glTF splits vertices along UV seams. `clean()`
    welds them back; UVs survive because they live on loops, not vertices.
  * The generator has its own idea of scale, origin and facing.
    `normalise()` puts the feet on z=0, the height at 1.0 and the face on +X.
  * A single texture cannot use the kit's FAR_SHADE trick, so `add_far_shade()`
    moves the far-side limb polygons to a dimmed copy of the material.

Joint heights are NOT constants here. A generated body has whatever proportions
the model felt like; `measure()` reports where its joints actually are and the
caller overrides `kage_mesh_kit`'s module globals before calling `build_rig()`.

    import kage_text3d_kit as t3d
    obj = t3d.clean(t3d.normalise('Rodin_Import', 'Hoa'))
    t3d.measure(obj)                      # read the joint heights off this
    rig = t3d.rig(obj, 'hoa', arm_y=0.22, leg_y=0.09, hip=0.22, chest=0.47,
                  shoulder=0.41, elbow=0.31, wrist=0.20, knee=0.09)
    t3d.toon_textured(obj)
    t3d.add_far_shade(obj)
"""

import math
import os
import sys

import bpy
from mathutils import Matrix, Vector

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import kage_mesh_kit as kit  # noqa: E402
import kage_sprite_render as ksr  # noqa: E402

# Generated characters come out facing -Y; the render pipeline wants +X.
IMPORT_YAW_DEG = 90.0

# Weld radius for the UV-seam duplicates. Large enough to catch them, small
# enough to leave real detail alone on a mesh normalised to height 1.0.
WELD_DISTANCE = 0.0002

# How much darker the far-side limbs render. Same value as kage_mesh_kit's
# FAR_SHADE, for the same reason: a profile view has no other depth cue.
FAR_SHADE = 0.72

FAR_GROUPS = frozenset({"arm_l", "forearm_l", "leg_l", "shin_l"})

# How far a seated weapon must clear the body's own width, as a fraction of it,
# so its silhouette reads separately from the character's (Q7).
STICK_OUT = 0.30

# Ceiling on that sideways push, as a fraction of body half-width, so the weapon
# stays carried rather than floating alongside.
STICK_OUT_CAP = 0.70


def _local_bounds(mesh):
    lo = Vector((math.inf,) * 3)
    hi = Vector((-math.inf,) * 3)
    for vertex in mesh.vertices:
        for axis in range(3):
            lo[axis] = min(lo[axis], vertex.co[axis])
            hi[axis] = max(hi[axis], vertex.co[axis])
    return lo, hi


def normalise(source_name, out_name, yaw_deg=IMPORT_YAW_DEG):
    """Copy the import, then face it +X, scale it to height 1.0, feet on z=0.

    The transforms are applied to the mesh data, not the object. Setting
    `obj.rotation_euler` and calling `transform_apply` looks equivalent and is
    not: inside an MCP session the operator silently leaves the rotation
    unbaked, and the whole rig then gets built against the wrong axis — the
    arms end up on X while the bones sit on Y, and auto-weighting hands one
    arm bone 6000 vertices and the other 250.
    """
    if out_name in bpy.data.objects:
        bpy.data.objects.remove(bpy.data.objects[out_name], do_unlink=True)
    source = bpy.data.objects[source_name]
    obj = source.copy()
    obj.data = source.data.copy()
    # Keep the raw import as the only fallback — the same prompt never returns
    # the same mesh twice — but take it out of the render. It still sits at the
    # generator's scale and origin, and `fit_camera` frames whatever renders.
    # Hide it only after the copy exists, or the character inherits the flags.
    source.hide_render = True
    source.hide_viewport = True
    obj.hide_render = False
    obj.hide_viewport = False
    obj.name = out_name
    obj.data.name = out_name + "_Mesh"
    obj.matrix_world = Matrix.Identity(4)
    bpy.context.scene.collection.objects.link(obj)
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)

    mesh = obj.data
    mesh.transform(Matrix.Rotation(math.radians(yaw_deg), 4, "Z"))
    lo, hi = _local_bounds(mesh)
    scale = 1.0 / (hi.z - lo.z)
    mesh.transform(Matrix.Scale(scale, 4))
    lo, hi = lo * scale, hi * scale
    mesh.transform(Matrix.Translation(
        (-(lo.x + hi.x) / 2.0, -(lo.y + hi.y) / 2.0, -lo.z)))
    mesh.update()
    return obj


def _make_active(obj):
    """Make `obj` the sole selected, active object, in OBJECT mode.

    Order matters and is the whole point of this helper: `mode_set` polls the
    active object, so calling it before setting one raises "Context missing
    active object" the moment a file is opened headless with nothing active.
    Inside an MCP session something is always active, which is why the operator
    order here read as correct for as long as every caller was interactive.
    """
    bpy.context.view_layer.objects.active = obj
    if obj.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)


def clean(obj, distance=WELD_DISTANCE):
    """Weld the glTF seam duplicates so bone-heat weighting can solve at all."""
    _make_active(obj)
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.mesh.remove_doubles(threshold=distance)
    bpy.ops.mesh.normals_make_consistent(inside=False)
    bpy.ops.object.mode_set(mode="OBJECT")
    return obj


def measure(obj, bands=8):
    """Print a left-right density profile per height band, to place the joints.

    Limbs show up as lobes either side of the centre line; the torso as one
    solid block. Read the arm/leg separation off the lobes and the joint
    heights off where the lobes start and stop.
    """
    mesh = obj.data
    lo, hi = _local_bounds(mesh)
    columns = 32
    print("[t3d] {0}: depth(X)={1:.3f} width(Y)={2:.3f}".format(
        obj.name, hi.x - lo.x, hi.y - lo.y))
    rows = 20
    for row in range(rows - 1, -1, -1):
        z_lo, z_hi = row / rows, (row + 1) / rows
        counts = [0] * columns
        for vertex in mesh.vertices:
            if z_lo <= vertex.co.z < z_hi:
                index = int((vertex.co.y - lo.y) / (hi.y - lo.y) * columns)
                if 0 <= index < columns:
                    counts[index] += 1
        peak = max(counts) or 1
        bar = "".join("#" if c > peak * 0.5 else ("+" if c > peak * 0.15 else
                      ("." if c else " ")) for c in counts)
        print("  z {0:.2f} |{1}|".format(z_lo, bar))
    return lo, hi


def _segment_distance(point, head, tail):
    along = tail - head
    length_squared = along.length_squared
    if length_squared == 0.0:
        return (point - head).length
    t = max(0.0, min(1.0, (point - head).dot(along) / length_squared))
    return (point - (head + along * t)).length


def weight_by_proximity(obj, armature, power=2.5, influences=3, smooth=4):
    """Assign skin weights from distance to the bone segments.

    Blender's bone-heat solver refuses to run on most generated meshes: it needs
    a watertight manifold, and three bad vertices out of forty thousand are
    enough to make it hand back nothing at all. Welding fixes some imports and
    not others, and there is no way to know in advance which — so this is the
    fallback that always produces a result.

    Inverse distance to the closest `influences` bone segments, raised to
    `power`. Distance alone gives a hard boundary wherever two bones are
    equidistant — on a spiky hairstyle that boundary runs through the middle of
    the locks and tears them apart the first time the head turns — so the raw
    weights get `smooth` passes of neighbour averaging before use.
    """
    for bone_name in kit.BONES:
        if obj.vertex_groups.get(bone_name) is None:
            obj.vertex_groups.new(name=bone_name)
    groups = {name: obj.vertex_groups[name] for name in kit.BONES}
    segments = [(name,
                 armature.data.bones[name].head_local,
                 armature.data.bones[name].tail_local) for name in kit.BONES]

    for vertex in obj.data.vertices:
        distances = sorted(
            ((_segment_distance(vertex.co, head, tail), name)
             for name, head, tail in segments))[:influences]
        raw = [(name, 1.0 / max(distance, 1e-4) ** power)
               for distance, name in distances]
        total = sum(weight for _, weight in raw)
        for name, weight in raw:
            groups[name].add([vertex.index], weight / total, "REPLACE")

    if smooth:
        bpy.ops.object.mode_set(mode="OBJECT")
        bpy.ops.object.select_all(action="DESELECT")
        obj.select_set(True)
        bpy.context.view_layer.objects.active = obj
        bpy.ops.object.mode_set(mode="WEIGHT_PAINT")
        bpy.ops.object.vertex_group_smooth(
            group_select_mode="ALL", factor=0.5, repeat=smooth)
        bpy.ops.object.mode_set(mode="OBJECT")
    return obj


def rig(obj, name, arm_y, leg_y, hip, chest, shoulder, elbow, wrist, knee):
    """Build the kit's 11-bone skeleton at this mesh's own joint heights.

    `kage_mesh_kit` reads its joint constants as module globals at call time,
    so overriding them here is enough — the kit's own builders are unaffected
    because a generated character never calls them.
    """
    kit.ARM_Y, kit.LEG_Y = arm_y, leg_y
    kit.Z_HIP, kit.Z_CHEST, kit.Z_SHOULDER = hip, chest, shoulder
    kit.Z_ELBOW, kit.Z_WRIST, kit.Z_KNEE = elbow, wrist, knee

    rig_name = name + "_Rig"
    if rig_name in bpy.data.objects:
        bpy.data.objects.remove(bpy.data.objects[rig_name], do_unlink=True)
    obj.modifiers.clear()
    obj.vertex_groups.clear()
    obj.parent = None

    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.mode_set(mode="OBJECT")
    bpy.ops.object.select_all(action="DESELECT")
    armature = kit.build_rig(name)

    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    armature.select_set(True)
    bpy.context.view_layer.objects.active = armature
    bpy.ops.object.parent_set(type="ARMATURE_AUTO")

    orphans = sum(1 for v in obj.data.vertices
                  if not any(g.weight > 0.01 for g in v.groups))
    if orphans:
        # Bone heat gave up. It reports this as a warning and still parents the
        # mesh, so without this check the character silently renders in rest
        # pose for all 33 frames.
        print("[t3d] bone heat failed on {0} vertices — using proximity "
              "weights".format(orphans))
        obj.vertex_groups.clear()
        weight_by_proximity(obj, armature)
    return armature


# Per-bone gains applied to the kit's pose tables. The tables were authored
# against the procedural body, which is ~2.5 heads tall with limbs long enough
# that a 7-degree swing reads. A generated chibi is nearer 2 heads: the head is
# half the total height and the legs are a fifth of it, so the same numbers
# overshoot at the spine (the huge head swings far and the character folds over)
# and vanish at the limbs (a 7-degree swing on a 0.22-long leg moves the foot by
# nothing). Scaling per bone keeps the timing, weight and follow-through of the
# original tables — only the amplitude is re-fitted to the body.
CHIBI_GAINS = {
    "pelvis": 0.55, "spine": 0.45, "head": 0.5,
    "arm_l": 1.25, "arm_r": 1.25, "forearm_l": 1.15, "forearm_r": 1.15,
    "leg_l": 1.5, "leg_r": 1.5, "shin_l": 1.4, "shin_r": 1.4,
}


# Per-action overrides layered on CHIBI_GAINS. The global torso damping exists
# because a Text->3D body is half head, and swinging it on the kit's angles
# folds `attack` and `die` in half. `run` pays for that damping without getting
# anything back: its forward lean is a constant offset, not a swing, and the
# damping just stands the runner upright -- measured -6 degrees on char_thuy-b1
# where ART-QUALITY-STANDARD Q11 asks for 8-15. Raising it only here keeps the
# fix away from the two actions the damping was bought for.
ACTION_GAINS = {
    "run": {"pelvis": 1.02, "spine": 0.83},
}


def author_scaled_actions(rig, gains=None):
    """Author the kit's seven actions with per-bone amplitude re-fitted.

    Wraps `kage_mesh_kit.pose_frame` for the duration of the call rather than
    copying the pose tables: the tables are the animation, and a second copy of
    them would drift from the original the first time either is touched.
    """
    gains = CHIBI_GAINS if gains is None else gains
    original = kit.pose_frame

    def scaled(target, frame, pose, **kwargs):
        # `author_actions` assigns each action to the rig before posing it, so
        # the rig itself says which one is being written right now.
        action = getattr(rig.animation_data, "action", None)
        per_action = ACTION_GAINS.get(action.name, {}) if action else {}
        return original(target, frame,
                        {bone: angle * per_action.get(bone, gains.get(bone, 1.0))
                         for bone, angle in pose.items()},
                        **kwargs)

    kit.pose_frame = scaled
    try:
        kit.author_actions(rig)
    finally:
        kit.pose_frame = original
    return rig


def _diffuse_image(material):
    for node in material.node_tree.nodes:
        if node.type == "TEX_IMAGE" and node.image is not None:
            if "diffuse" in node.image.name or "albedo" in node.image.name:
                return node.image
    raise RuntimeError("no diffuse texture on material: " + material.name)


# Cel ramp for a mesh whose texture is already shaded. `kage_sprite_render`'s
# SHADE_STEPS bottom out at 0.55, which is right when the ramp IS the shading:
# the material there gets one flat colour and the ramp carves the form out of
# it. A generated map arrives with its own painted shadows, so the same ramp
# darkens twice and the character goes muddy — the whole reason the raw import
# looks more vivid than the "toon" render of it. These steps keep the banding
# readable without eating the artwork underneath.
TEXTURED_SHADE_STEPS = (0.80, 0.90, 1.0)


def toon_textured(obj, shades=3, rim=0.0, steps=None):
    """Cel-shade a textured mesh without throwing the texture away.

    The kit's `apply_toon` bakes one flat colour per part, which is right for
    geometry it built itself and wrong here: the face, the flame pattern and
    the painted line art all live in the map. So the map stays, and only the
    lighting is quantised — the same stepped ramp, multiplied over the texture
    instead of replacing it.

    `rim` adds a flat Fresnel highlight on top. Generated hair is near-black,
    and black times any shade step is still black, so without a rim the whole
    hair mass reads as one silhouette blob.
    """
    image = _diffuse_image(obj.data.materials[0])
    material = bpy.data.materials.new("toon_tex_" + obj.name)
    material.use_nodes = True
    tree = material.node_tree
    tree.nodes.clear()

    output = tree.nodes.new("ShaderNodeOutputMaterial")
    output.location = (900, 0)
    emission = tree.nodes.new("ShaderNodeEmission")
    emission.location = (700, 0)
    multiply = tree.nodes.new("ShaderNodeMixRGB")
    multiply.location = (400, 0)
    multiply.blend_type = "MULTIPLY"
    multiply.inputs["Fac"].default_value = 1.0
    ramp = tree.nodes.new("ShaderNodeValToRGB")
    ramp.location = (150, -200)
    to_rgb = tree.nodes.new("ShaderNodeShaderToRGB")
    to_rgb.location = (-80, -200)
    diffuse = tree.nodes.new("ShaderNodeBsdfDiffuse")
    diffuse.location = (-300, -200)
    diffuse.inputs["Color"].default_value = (1.0, 1.0, 1.0, 1.0)
    texture = tree.nodes.new("ShaderNodeTexImage")
    texture.location = (-300, 250)
    texture.image = image

    color_ramp = ramp.color_ramp
    color_ramp.interpolation = "CONSTANT"
    while len(color_ramp.elements) > 1:
        color_ramp.elements.remove(color_ramp.elements[-1])
    steps = steps or TEXTURED_SHADE_STEPS
    if len(steps) != shades:
        raise ValueError("need one ramp step per shade band")
    color_ramp.elements[0].position = 0.0
    color_ramp.elements[0].color = (steps[0],) * 3 + (1.0,)
    for index in range(1, shades):
        element = color_ramp.elements.new(index / float(shades))
        element.color = (steps[index],) * 3 + (1.0,)

    tree.links.new(diffuse.outputs["BSDF"], to_rgb.inputs["Shader"])
    tree.links.new(to_rgb.outputs["Color"], ramp.inputs["Fac"])
    tree.links.new(texture.outputs["Color"], multiply.inputs["Color1"])
    tree.links.new(ramp.outputs["Color"], multiply.inputs["Color2"])

    shaded = multiply.outputs["Color"]
    if rim > 0.0:
        fresnel = tree.nodes.new("ShaderNodeFresnel")
        fresnel.location = (150, 320)
        fresnel.inputs["IOR"].default_value = 1.45
        band = tree.nodes.new("ShaderNodeValToRGB")
        band.location = (350, 320)
        band.color_ramp.interpolation = "CONSTANT"
        band.color_ramp.elements[0].position = 0.0
        band.color_ramp.elements[0].color = (0.0, 0.0, 0.0, 1.0)
        band.color_ramp.elements[1].position = 0.62
        band.color_ramp.elements[1].color = (rim, rim, rim, 1.0)
        add = tree.nodes.new("ShaderNodeMixRGB")
        add.location = (560, 160)
        add.blend_type = "ADD"
        add.inputs["Fac"].default_value = 1.0
        tree.links.new(fresnel.outputs["Fac"], band.inputs["Fac"])
        tree.links.new(shaded, add.inputs["Color1"])
        tree.links.new(band.outputs["Color"], add.inputs["Color2"])
        shaded = add.outputs["Color"]

    tree.links.new(shaded, emission.inputs["Color"])
    tree.links.new(emission.outputs["Emission"], output.inputs["Surface"])

    obj.data.materials.clear()
    obj.data.materials.append(material)
    return material


def attach_weapon(rig, name, kind, offset=(0.0, 0.0, 0.0), outline_px_at_1x=None,
                  color=None, accent=None, body=None):
    """Mount the element's weapon as its own mesh, rigidly bound to a bone.

    A generated body arrives as one welded lump with the weapon nowhere in it,
    so the weapon comes from `kage_mesh_kit.build_weapon` — which already
    builds each part as a separate object with a single full-weight vertex
    group. That is exactly the shape visible equipment will need later
    (ADR-031): swap the mesh on the bone, do not re-bake the character.

    The kit's mount points are absolute coordinates tuned to its own body —
    including a sideways offset derived from ARM_Y, which on a wider generated
    body leaves the weapon hanging in mid-air beside the hip. `offset` moves
    the whole weapon onto this body; there is no formula for it, so render it
    and look.

    Weapon parts are separate objects, so the body's outline modifier does not
    reach them — pass `outline_px_at_1x` to give them their own, or a sharp
    black-outlined character ends up holding an unoutlined blade.

    `color`/`accent` reach `build_weapon` unchanged. They matter more here than
    on a procedural body: a generated costume comes back in whatever neutral the
    generator felt like, so on a Text->3D character the weapon is often the only
    surface carrying the element's palette, which Q10 is scored on
    (ART-QUALITY-STANDARD §5).
    """
    parts = kit.build_weapon(rig, {"name": name}, kind, color=color, accent=accent)
    if body is not None:
        seat_weapon(parts, body)
    for part in parts:
        if any(offset):
            for vertex in part.data.vertices:
                vertex.co.x += offset[0]
                vertex.co.y += offset[1]
                vertex.co.z += offset[2]
            part.data.update()
        if outline_px_at_1x:
            add_inverted_hull_outline(part, width_px_at_1x=outline_px_at_1x)
    return parts


def seat_weapon(parts, body, max_span=0.55, clearance=0.005):
    """Scale a kit weapon down to this body and push it back against its surface.

    `kage_mesh_kit` sizes and mounts weapons against the procedural torso, which
    is both narrower and differently proportioned than anything Rodin returns.
    Dropped onto a generated body unchanged they come out oversized and floating:
    char_loi-b1's long blade rendered as a slab taller than the character and
    detached behind it, char_tho-b1's mace as a pale lump over the chest.

    Two corrections, both measured rather than guessed:

    * anything spanning more than `max_span` of the body height is scaled about
      its own centre until it does. ART-QUALITY-STANDARD §4 item 7 wants weapons
      *over* true scale so they read at 83px, so this is a ceiling, not a fit.
    * the whole weapon is then shifted along X until its front face touches the
      body's surface within the height band it actually occupies, leaving
      `clearance` so the outline shells do not z-fight.

    Returns the (scale, shift) applied, so a caller can print or override it.
    """
    weapon_points = [part.matrix_world @ vertex.co
                     for part in parts for vertex in part.data.vertices]
    if not weapon_points:
        return 1.0, 0.0
    body_points = [body.matrix_world @ vertex.co for vertex in body.data.vertices]
    body_height = max(p.z for p in body_points) - min(p.z for p in body_points)

    span = max(max(p[axis] for p in weapon_points) - min(p[axis] for p in weapon_points)
               for axis in range(3))
    scale = min(1.0, max_span * body_height / span) if span else 1.0
    centre = Vector((sum(p.x for p in weapon_points) / len(weapon_points),
                     sum(p.y for p in weapon_points) / len(weapon_points),
                     sum(p.z for p in weapon_points) / len(weapon_points)))
    if scale < 1.0:
        for part in parts:
            part.data.transform(Matrix.Translation(-centre)
                                @ Matrix.Scale(scale, 4)
                                @ Matrix.Translation(centre))
            part.data.update()
        weapon_points = [part.matrix_world @ vertex.co
                         for part in parts for vertex in part.data.vertices]

    low_z = min(p.z for p in weapon_points)
    high_z = max(p.z for p in weapon_points)
    band = [p for p in body_points if low_z <= p.z <= high_z]
    if not band:
        return scale, 0.0
    # The weapon rides behind the character, which faces +X.
    shift = (min(p.x for p in band) - clearance) - min(p.x for p in weapon_points)
    # Seating alone hides it: pressed flat against the back, the body occludes
    # the whole weapon at the 3/4 preview angle and Q7 fails on all three of
    # phong/loi/tho. Thuy's gourd reads because it hangs clear of the silhouette,
    # so push the weapon sideways until it breaks the outline by `stick_out` of
    # the body's own width. A weapon that cannot be seen is not equipment.
    body_half_y = max(abs(p.y) for p in band)
    weapon_edge_y = max(abs(p.y) for p in weapon_points)
    side = 1.0 if sum(p.y for p in weapon_points) >= 0 else -1.0
    # Capped, or a weapon that started centred gets thrown clear of the body and
    # reads as floating rather than carried -- char_loi-b1's blade needed +0.365
    # to clear on its own and hung in mid-air at that distance. Sticking out is
    # for breaking the silhouette, not for leaving the character.
    shift_y = side * min(body_half_y * STICK_OUT_CAP,
                         max(0.0, body_half_y * (1.0 + STICK_OUT) - weapon_edge_y))
    for part in parts:
        part.data.transform(Matrix.Translation((shift, shift_y, 0.0)))
        part.data.update()
    print("[t3d] seat weapon: scale={0:.2f} shift_x={1:+.3f} shift_y={2:+.3f}".format(
        scale, shift, shift_y))
    return scale, shift


# Where the kit's own body puts the shoulder (ART-QUALITY-STANDARD §3). The B3
# motion accents are placed against this, so it is the yardstick for moving them
# onto a body with different proportions.
KIT_SHOULDER_Z = 0.56


def attach_motion_detail(rig, name, kind, color, shoulder_z):
    """Add the B3 moving accent, repositioned for a generated body.

    `kage_mesh_kit.add_motion_detail` places each accent at absolute coordinates
    measured off the procedural body — embers behind the head at z=0.72, the
    water ring at the wrist at z=0.315, ribbons pivoting at z=0.545. A generated
    body puts those joints somewhere else entirely, so unmoved the ribbons sprout
    from the stomach and the wrist ring orbits empty air.

    The accents are positioned by drivers on `location`, so the mesh cannot just
    be translated the way a weapon can — overwriting the channel would delete the
    animation. `delta_location` adds on top of the driven value instead, which
    moves the whole orbit without touching what drives it.

    Returns the parts and the vertical scale applied.
    """
    before = set(bpy.data.objects)
    kit.add_motion_detail(rig, {"name": name}, kind, color)
    parts = [obj for obj in bpy.data.objects if obj not in before]

    scale = shoulder_z / KIT_SHOULDER_Z
    for part in parts:
        # Height is what differs most between the two body plans; the accent's
        # own size stays as authored so it still reads at 83px.
        part.delta_location = (0.0, 0.0, part.location.z * (scale - 1.0))
    print("[t3d] motion detail {0}: {1} part(s), z-scale={2:.2f}".format(
        kind, len(parts), scale))
    return parts


def add_inverted_hull_outline(obj, width_px_at_1x=1.2, scene=None):
    """Draw the ink line as a flipped, inflated shell instead of with Freestyle.

    Freestyle draws a contour wherever a surface turns away from the camera —
    which on a procedural body means the silhouette and nothing else, but on a
    generated head means every one of forty hair locks gets its own outline and
    the face fills up with black scribble. The shell has no such opinion: it
    inflates the mesh, flips it inside out and culls its front faces, so exactly
    one line survives, around the outside.

    Thickness is given in @1x sprite pixels and converted through the camera's
    ortho_scale, so the line stays the same weight on screen no matter how the
    character is framed.
    """
    scene = scene or bpy.context.scene
    camera = scene.camera
    if camera is None or camera.data.type != "ORTHO":
        raise RuntimeError("fit the orthographic camera before the outline")
    pixels_per_unit = scene.render.resolution_x / camera.data.ortho_scale
    thickness = width_px_at_1x * ksr.MASTER_SCALE / pixels_per_unit

    ink = bpy.data.materials.get("kage_ink")
    if ink is None:
        ink = bpy.data.materials.new("kage_ink")
        ink.use_nodes = True
        tree = ink.node_tree
        tree.nodes.clear()
        output = tree.nodes.new("ShaderNodeOutputMaterial")
        emission = tree.nodes.new("ShaderNodeEmission")
        emission.location = (-200, 0)
        emission.inputs["Color"].default_value = (0.0, 0.0, 0.0, 1.0)
        tree.links.new(emission.outputs["Emission"], output.inputs["Surface"])
    # Without this the shell's own front faces sit in front of the character.
    ink.use_backface_culling = True

    slot = obj.data.materials.find("kage_ink")
    if slot < 0:
        obj.data.materials.append(ink)
        slot = len(obj.data.materials) - 1

    modifier = obj.modifiers.get("KageOutline")
    if modifier is None:
        modifier = obj.modifiers.new("KageOutline", "SOLIDIFY")
        # Must run after the armature or the shell keeps the rest pose.
        while obj.modifiers[-1] != modifier:
            bpy.ops.object.modifier_move_down(modifier=modifier.name)
    modifier.thickness = thickness
    modifier.offset = 1.0
    modifier.use_flip_normals = True
    modifier.use_rim = False
    modifier.material_offset = slot
    modifier.material_offset_rim = slot
    scene.render.use_freestyle = False
    return thickness


def decimate(obj, target_triangles):
    """Collapse the mesh towards a triangle budget, in place.

    A generated mesh arrives near 93k triangles because the generator has no
    idea what it is for. Twenty of those on screen is 1.9M triangles per frame
    (ADR-031), so the budget is a real constraint rather than tidiness.

    The modifier is moved to the top of the stack before being applied: left at
    the bottom it would sit after Armature and KageOutline, and Blender applies
    a modifier as if the ones above it were already applied — which bakes the
    rest pose and the outline shell into the mesh data.

    Collapse (not un-subdivide or planar) because it is the only mode that
    keeps vertex groups: the mesh is already skinned at this point, and losing
    the weights means re-rigging every LOD by hand.
    """
    mesh = obj.data
    mesh.calc_loop_triangles()
    before = len(mesh.loop_triangles)
    if before <= target_triangles:
        return before, before

    _make_active(obj)

    modifier = obj.modifiers.new("KageDecimate", "DECIMATE")
    modifier.decimate_type = "COLLAPSE"
    modifier.use_collapse_triangulate = True
    modifier.ratio = target_triangles / float(before)
    while obj.modifiers[0] != modifier:
        bpy.ops.object.modifier_move_up(modifier=modifier.name)
    bpy.ops.object.modifier_apply(modifier=modifier.name)

    mesh = obj.data
    mesh.calc_loop_triangles()
    after = len(mesh.loop_triangles)
    print("[t3d] decimate {0}: {1} -> {2} tris (asked {3})".format(
        obj.name, before, after, target_triangles))
    return before, after


def _export_material(image, name):
    """Plain base-colour material, because the runtime shader does the rest.

    `toon_textured()` builds an Emission graph and `add_far_shade()` adds a
    dimmed twin of it. Both are render-time tricks for a fixed camera: the cel
    banding is a stepped ramp the GLSL shader reproduces, and the far shade
    fakes depth for a profile view that no longer exists once the character can
    turn. Exporting them would bake a lighting decision into the asset, which is
    the thing ADR-031 set out to stop.

    glTF also cannot carry them. The exporter maps Principled BSDF onto the PBR
    material model; an Emission-only graph exports with no base colour texture
    at all, so the character would arrive in the client untextured.
    """
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    tree = material.node_tree
    tree.nodes.clear()
    output = tree.nodes.new("ShaderNodeOutputMaterial")
    output.location = (400, 0)
    principled = tree.nodes.new("ShaderNodeBsdfPrincipled")
    principled.location = (100, 0)
    principled.inputs["Metallic"].default_value = 0.0
    principled.inputs["Roughness"].default_value = 1.0
    texture = tree.nodes.new("ShaderNodeTexImage")
    texture.location = (-250, 0)
    texture.image = image
    tree.links.new(texture.outputs["Color"], principled.inputs["Base Color"])
    tree.links.new(principled.outputs["BSDF"], output.inputs["Surface"])
    return material


def export_gltf(path, obj, armature, extras=(), texture_px=None):
    """Write one LOD as a self-contained .glb, ready for gdx-gltf.

    Everything this does is undone afterwards: the caller keeps working on the
    same scene for the next LOD.

    Three things are deliberately dropped on the way out.

      * The KageOutline SOLIDIFY modifier. glTF has no modifier concept, so the
        exporter evaluates it — the inverted hull becomes real geometry and the
        triangle count doubles, which would make the budget meaningless. The
        shell is one extra draw with front faces culled; the runtime redoes it
        for free (ADR-031).
      * The toon and far-shade materials — see `_export_material`.
      * Everything else in the .blend. A working character file accumulates the
        rejected generations, and `use_selection` is what stops a 93k reject
        from riding along inside the shipped model.
    """
    scene = bpy.context.scene
    exported = [obj] + list(extras)

    image = _diffuse_image(obj.data.materials[0])
    if texture_px is not None and tuple(image.size) != (texture_px, texture_px):
        scaled = image.copy()
        scaled.name = "{0}_{1}".format(image.name, texture_px)
        scaled.scale(texture_px, texture_px)
        image = scaled
    material = _export_material(image, "gltf_" + obj.name)

    saved_materials = {}
    saved_outlines = {}
    for mesh_obj in exported:
        saved_materials[mesh_obj] = list(mesh_obj.data.materials)
        mesh_obj.data.materials.clear()
        mesh_obj.data.materials.append(material)
        for polygon in mesh_obj.data.polygons:
            polygon.material_index = 0
        outline = mesh_obj.modifiers.get("KageOutline")
        if outline is not None:
            saved_outlines[mesh_obj] = {
                "thickness": outline.thickness,
                "material_offset": outline.material_offset,
            }
            mesh_obj.modifiers.remove(outline)

    bpy.ops.object.mode_set(mode="OBJECT")
    bpy.ops.object.select_all(action="DESELECT")
    for mesh_obj in exported:
        mesh_obj.select_set(True)
    armature.select_set(True)
    bpy.context.view_layer.objects.active = armature

    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    try:
        bpy.ops.export_scene.gltf(
            filepath=path,
            export_format="GLB",
            use_selection=True,
            export_apply=True,
            export_skins=True,
            export_animations=True,
            export_animation_mode="ACTIONS",
            export_yup=True,
        )
    finally:
        for mesh_obj in exported:
            mesh_obj.data.materials.clear()
            for slot in saved_materials[mesh_obj]:
                mesh_obj.data.materials.append(slot)
            saved = saved_outlines.get(mesh_obj)
            if saved is not None:
                restored = mesh_obj.modifiers.new("KageOutline", "SOLIDIFY")
                restored.thickness = saved["thickness"]
                restored.offset = 1.0
                restored.use_flip_normals = True
                restored.use_rim = False
                restored.material_offset = saved["material_offset"]
                restored.material_offset_rim = saved["material_offset"]

    size = os.path.getsize(path)
    print("[t3d] export {0}: {1:.2f} MB texture={2}".format(
        os.path.basename(path), size / (1024.0 * 1024.0), tuple(image.size)))
    return size


def add_far_shade(obj, factor=FAR_SHADE):
    """Dim the limbs on the far side of a profile view.

    The camera sits at -Y, so +Y is away from it, which is where the kit's
    `*_l` bones live. Whole polygons move to the dimmed slot — assigning by
    vertex would cut faces in half down the middle of an arm.
    """
    base = obj.data.materials[0]
    far = base.copy()
    far.name = "toon_tex_far_" + obj.name
    tree = far.node_tree
    emission = next(n for n in tree.nodes if n.type == "EMISSION")
    dim = tree.nodes.new("ShaderNodeMixRGB")
    dim.location = (750, 250)
    dim.blend_type = "MULTIPLY"
    dim.inputs["Fac"].default_value = 1.0
    dim.inputs["Color2"].default_value = (factor, factor, factor, 1.0)
    upstream = emission.inputs["Color"].links[0].from_socket
    tree.links.new(upstream, dim.inputs["Color1"])
    tree.links.new(dim.outputs["Color"], emission.inputs["Color"])

    if len(obj.data.materials) < 2:
        obj.data.materials.append(far)
    else:
        obj.data.materials[1] = far

    index_to_name = {g.index: g.name for g in obj.vertex_groups}
    dominant = []
    for vertex in obj.data.vertices:
        best, best_weight = None, 0.0
        for group in vertex.groups:
            if group.weight > best_weight:
                best, best_weight = index_to_name[group.group], group.weight
        dominant.append(best)

    # The dominant group alone is not enough: with blended weights a polygon on
    # the near arm can still name a far bone as its strongest influence, and
    # dimming it puts a shadow on the side of the character facing the camera.
    # Geometry decides — a far-side polygon is one that actually sits at +Y.
    moved = 0
    for polygon in obj.data.polygons:
        if not all(dominant[i] in FAR_GROUPS for i in polygon.vertices):
            continue
        if all(obj.data.vertices[i].co.y > 0.02 for i in polygon.vertices):
            polygon.material_index = 1
            moved += 1
    obj.data.update()
    return moved
