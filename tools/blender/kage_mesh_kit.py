"""Kage chibi character mesh kit — smooth-body replacement for the box rig.

Why this exists: the first-generation characters (build_box_character.py) were
axis-aligned boxes. They proved the render pipeline works but failed the visual
bar in docs/14-art/ART-QUALITY-STANDARD.md (benchmark: Ninja School Online).
This module builds the same kind of character out of revolved smooth surfaces
plus a face, hair and real weapon silhouettes, so the result reads as an anime
chibi ninja instead of a stack of cubes.

Everything here is procedural geometry via bpy.data (from_pydata) — no operator
that depends on an active object, so the same code runs headless and inside an
MCP session (BLENDER-SESSION-GUIDE.md §4).

Orientation contract (do not change without re-rendering every asset):
  * The character stands on world z=0 and is 1.0 unit tall.
  * It faces +X. The camera sits on -Y looking towards +Y, so +X is screen
    right, -X is behind the character, and the body's left/right axis is Y.
  * -Y is the camera-facing side. Limbs on -Y are the NEAR pair (suffix _r),
    limbs on +Y are the FAR pair (suffix _l) and are rendered darker.
  * Because the view is a true profile, single-sided gear must be mounted on
    the near side or it is invisible.

Public API (see ART-QUALITY-STANDARD.md §5):
    build_rig(name)                     -> 11-bone armature
    build_chibi_body(rig, spec)         -> body/limb parts
    add_face(rig, spec)                 -> eye, brow, mouth
    add_hair(rig, style, color)         -> hair cap + spikes
    add_accessory(rig, kind, ...)       -> scarf/sash/plate/guard/wrap/headband
    build_weapon(rig, kind, ...)        -> kunai/gourd/twin_blades/long_blade/mace
    add_motion_detail(rig, kind, ...)   -> B3 driver-animated accents
    author_actions(rig)                 -> the 7 standard actions
    assemble_character(spec)            -> everything above from one dict
"""

import math
import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_sprite_render import make_toon_material  # noqa: E402

# --- Skeleton ----------------------------------------------------------------

# 11 bones: the box rig's 7 plus forearm/shin, which is what makes elbows and
# knees bend (ART-QUALITY-STANDARD.md Q11/Q12). Joint positions of the shared
# bones are unchanged from build_box_character.py so the baseline and pivot
# stay identical across generations.
BONES = [
    "pelvis", "spine", "head",
    "arm_l", "forearm_l", "arm_r", "forearm_r",
    "leg_l", "shin_l", "leg_r", "shin_r",
]

# Half-distance between the two arms / the two legs along the depth axis.
# ARM_Y must stay clear of the torso's half-depth (0.114 at the chest, see
# build_chibi_body). Tuck the arms inside that and the torso surface renders in
# front of them: in a profile view the arms then vanish completely, which is
# not obvious from any single still because the silhouette still looks correct.
ARM_Y = 0.125
LEG_Y = 0.058

# Joint heights.
Z_HIP = 0.25
Z_CHEST = 0.60
Z_SHOULDER = 0.56
Z_ELBOW = 0.43
Z_WRIST = 0.30
Z_KNEE = 0.13

# Head ellipsoid — a chibi head is ~0.4 of the body height (2.5 heads tall).
HEAD_CENTER = (0.0, 0.0, 0.80)
HEAD_RX, HEAD_RY, HEAD_RZ = 0.19, 0.17, 0.20

# Far-side limbs are multiplied by this before shading, the classic 2D trick
# for depth in a flat profile view.
FAR_SHADE = 0.70

_ROT_INDEX_CACHE = {}


# --- Colour ------------------------------------------------------------------


def shade_hex(hex_color, factor):
    """Scale an sRGB hex colour by `factor` (0.7 = darker, 1.2 = lighter)."""
    cleaned = hex_color.lstrip("#")
    channels = [int(cleaned[i:i + 2], 16) for i in (0, 2, 4)]
    return "#{0:02X}{1:02X}{2:02X}".format(
        *[max(0, min(255, int(round(c * factor)))) for c in channels])


# --- Primitive geometry ------------------------------------------------------


def _revolve(profile, segments=20):
    """Lathe a profile around the Z axis.

    `profile` is a bottom-to-top list of (z, radius_x, radius_y). A radius of 0
    collapses that ring to a single pole vertex, which is how the round caps of
    heads, hands and joint balls are made. Winding is outward-facing.
    """
    verts = []
    rings = []
    for z, rx, ry in profile:
        if rx <= 1e-6 or ry <= 1e-6:
            rings.append([len(verts)])
            verts.append((0.0, 0.0, z))
            continue
        ring = []
        for step in range(segments):
            angle = 2.0 * math.pi * step / segments
            ring.append(len(verts))
            verts.append((rx * math.cos(angle), ry * math.sin(angle), z))
        rings.append(ring)

    faces = []
    for low, high in zip(rings, rings[1:]):
        if len(low) == 1 and len(high) == 1:
            continue
        if len(low) == 1:
            for j in range(segments):
                faces.append((low[0], high[(j + 1) % segments], high[j]))
        elif len(high) == 1:
            for j in range(segments):
                faces.append((low[j], low[(j + 1) % segments], high[0]))
        else:
            for j in range(segments):
                k = (j + 1) % segments
                faces.append((low[j], low[k], high[k], high[j]))

    if len(rings[0]) > 1:
        faces.append(tuple(reversed(rings[0])))
    if len(rings[-1]) > 1:
        faces.append(tuple(rings[-1]))
    return verts, faces


def _ellipsoid(rx, ry, rz, rings=12, segments=20):
    profile = []
    for i in range(rings + 1):
        t = math.pi * i / rings
        profile.append((-rz * math.cos(t), rx * math.sin(t), ry * math.sin(t)))
    return _revolve(profile, segments)


def _dome(rx, ry, rz, z_min_frac=0.0, rings=10, segments=24):
    """The cap above a horizontal slice of an ellipsoid.

    This is how hair gets a hairline: a full ellipsoid pushed backwards still
    wraps around the front of the face, but a dome cut at the brow leaves the
    face clear by construction.
    """
    t_start = math.acos(-max(-1.0, min(1.0, z_min_frac)))
    profile = []
    for i in range(rings + 1):
        t = t_start + (math.pi - t_start) * i / rings
        profile.append((-rz * math.cos(t), rx * math.sin(t), ry * math.sin(t)))
    return _revolve(profile, segments)


def _taper(z_lo, z_hi, r_lo, r_hi, segments=16, cap=True):
    """A limb segment: a cone frustum, optionally rounded at both ends."""
    profile = []
    if cap:
        profile.append((z_lo - r_lo * 0.55, 0.0, 0.0))
    profile.append((z_lo, r_lo, r_lo))
    profile.append((z_hi, r_hi, r_hi))
    if cap:
        profile.append((z_hi + r_hi * 0.55, 0.0, 0.0))
    return _revolve(profile, segments)


def _box(half_x, half_y, z_lo, z_hi):
    verts = []
    for z in (z_lo, z_hi):
        for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            verts.append((sx * half_x, sy * half_y, z))
    faces = [
        (0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1),
        (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0),
    ]
    return verts, faces


def _merge(pieces):
    """Concatenate several (verts, faces) into one, offsetting each face index.

    A bundle of identical slats or a row of engraved grooves is ONE material and
    ONE rigid part; splitting it into a mesh object each would multiply the part
    count (and so the Freestyle outline passes) for nothing. `export_character_lods`
    also treats every object as a decimation candidate, and a six-triangle sliver
    is exactly what its `MIN_PART_TRIANGLES` floor exists to protect.
    """
    verts, faces = [], []
    for piece_verts, piece_faces in pieces:
        offset = len(verts)
        verts.extend(piece_verts)
        faces.extend(tuple(index + offset for index in face) for face in piece_faces)
    return verts, faces


def _blade(length, width, thickness, curve_deg=0.0, taper=0.25, sections=8):
    """A sword/knife blade swept along an arc, pointing +Z from the origin.

    curve_deg=0 gives a straight blade (Lôi's long blade), a positive value
    bends it into a sabre (Phong's twin blades). `taper` is the fraction of the
    width left at the tip, so the silhouette actually reads as a point.
    """
    verts, faces = [], []
    radius = None if abs(curve_deg) < 1e-3 else length / math.radians(curve_deg)
    for i in range(sections + 1):
        t = i / float(sections)
        half_w = width * (1.0 - (1.0 - taper) * t) * 0.5
        half_t = thickness * (1.0 - 0.5 * t) * 0.5
        if radius is None:
            cx, cz, angle = 0.0, length * t, 0.0
        else:
            angle = math.radians(curve_deg) * t
            cx = radius * (1.0 - math.cos(angle))
            cz = radius * math.sin(angle)
        # Cross-section rotated to stay perpendicular to the sweep.
        for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
            ox = sx * half_w * math.cos(angle)
            oz = -sx * half_w * math.sin(angle)
            verts.append((cx + ox, sy * half_t, cz + oz))
    for i in range(sections):
        a, b = i * 4, (i + 1) * 4
        for j in range(4):
            k = (j + 1) % 4
            faces.append((a + j, a + k, b + k, b + j))
    faces.append((3, 2, 1, 0))
    faces.append((sections * 4 + 0, sections * 4 + 1, sections * 4 + 2, sections * 4 + 3))
    return verts, faces


def _transform(verts, translate=(0.0, 0.0, 0.0), rotate_y_deg=0.0, scale=(1.0, 1.0, 1.0)):
    """Scale, then rotate in the screen plane (about Y), then translate."""
    angle = math.radians(rotate_y_deg)
    cos_a, sin_a = math.cos(angle), math.sin(angle)
    out = []
    for x, y, z in verts:
        x, y, z = x * scale[0], y * scale[1], z * scale[2]
        rx = x * cos_a + z * sin_a
        rz = -x * sin_a + z * cos_a
        out.append((rx + translate[0], y + translate[1], rz + translate[2]))
    return out


def head_surface_y(x, z, offset=0.006):
    """Depth of the head's near surface at (x, z), for sticking a face on it.

    Returns a negative Y (the camera side) pushed `offset` further out, so the
    eye/brow/mouth float just proud of the skull instead of z-fighting it.
    """
    nx = (x - HEAD_CENTER[0]) / HEAD_RX
    nz = (z - HEAD_CENTER[2]) / HEAD_RZ
    remainder = 1.0 - nx * nx - nz * nz
    if remainder <= 0.0:
        raise ValueError("point ({0}, {1}) is off the head".format(x, z))
    return -(HEAD_RY * math.sqrt(remainder) + offset)


# --- Object plumbing ---------------------------------------------------------


def _new_part(rig, name, verts, faces, color_hex, bone, shades=3, smooth=True):
    """Create one mesh part, toon-shade it and rigidly bind it to `bone`."""
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    for polygon in mesh.polygons:
        polygon.use_smooth = smooth

    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    obj.data.materials.append(make_toon_material("toon_" + name, color_hex, shades=shades))

    if bone is not None:
        group = obj.vertex_groups.new(name=bone)
        group.add(list(range(len(verts))), 1.0, "REPLACE")
        modifier = obj.modifiers.new("Armature", type="ARMATURE")
        modifier.object = rig
    obj.parent = rig
    return obj


# Freestyle filters by collection, not by object flag. (obj.lineart.usage looks
# like the right switch but belongs to the Grease Pencil Line Art modifier and
# has no effect on a Freestyle render — it silently does nothing.)
NO_OUTLINE_COLLECTION = "kage_no_outline"


def _exclude_from_outline(obj):
    """Keep Freestyle off a part.

    Used for the smallest face features: at sprite scale an ink line as wide as
    the pupil itself turns the eye into a mud blob.
    """
    collection = bpy.data.collections.get(NO_OUTLINE_COLLECTION)
    if collection is None:
        collection = bpy.data.collections.new(NO_OUTLINE_COLLECTION)
        bpy.context.scene.collection.children.link(collection)
    if obj.name not in collection.objects:
        collection.objects.link(obj)


def apply_outline_exclusions(scene=None):
    """Point the Freestyle lineset at everything *except* the excluded parts."""
    scene = scene or bpy.context.scene
    collection = bpy.data.collections.get(NO_OUTLINE_COLLECTION)
    if collection is None:
        return
    lineset = scene.view_layers[0].freestyle_settings.linesets[0]
    lineset.select_by_collection = True
    lineset.collection = collection
    lineset.collection_negation = "EXCLUSIVE"


# --- Rig ---------------------------------------------------------------------


def build_rig(name="kage"):
    """The 11-bone chibi skeleton. Shared joints keep the box rig's positions."""
    arm_data = bpy.data.armatures.new(name + "_RigData")
    rig = bpy.data.objects.new(name + "_Rig", arm_data)
    bpy.context.scene.collection.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode="EDIT")

    edit_bones = arm_data.edit_bones
    pelvis = edit_bones.new("pelvis")
    pelvis.head, pelvis.tail = (0, 0, Z_HIP), (0, 0, Z_HIP + 0.07)
    spine = edit_bones.new("spine")
    spine.head, spine.tail, spine.parent = (0, 0, Z_HIP), (0, 0, Z_CHEST), pelvis
    head = edit_bones.new("head")
    head.head, head.tail, head.parent = (0, 0, Z_CHEST), (0, 0, 1.00), spine

    for side, sign in (("l", 1.0), ("r", -1.0)):
        upper = edit_bones.new("arm_" + side)
        upper.head = (0, sign * ARM_Y, Z_SHOULDER)
        upper.tail = (0, sign * ARM_Y, Z_ELBOW)
        upper.parent = spine
        fore = edit_bones.new("forearm_" + side)
        fore.head = (0, sign * ARM_Y, Z_ELBOW)
        fore.tail = (0, sign * ARM_Y, Z_WRIST)
        fore.parent = upper

        thigh = edit_bones.new("leg_" + side)
        thigh.head = (0, sign * LEG_Y, Z_HIP)
        thigh.tail = (0, sign * LEG_Y, Z_KNEE)
        thigh.parent = pelvis
        shin = edit_bones.new("shin_" + side)
        shin.head = (0, sign * LEG_Y, Z_KNEE)
        shin.tail = (0, sign * LEG_Y, 0.0)
        shin.parent = thigh

    bpy.ops.object.mode_set(mode="OBJECT")
    for pose_bone in rig.pose.bones:
        pose_bone.rotation_mode = "XYZ"
    _ROT_INDEX_CACHE.clear()
    return rig


def _swing_channel(rig, bone_name):
    """Which euler channel swings this bone in the screen plane, and its sign.

    Bones that point straight down get their roll resolved by Blender, and the
    resulting local axes differ per bone, so the channel is measured from the
    rest matrix instead of assumed. The sign is normalised so that a positive
    angle always swings the bone's TIP forward (+X, the way the character
    faces) — every pose table in this file relies on that.

    The sign cannot be read off the axis alone: turning a bone about the same
    world axis moves an upward bone (spine, head) and a downward one (arm, leg)
    in opposite directions. What decides it is the displacement of the tip,
    axis × bone_direction, so that is what gets measured.
    """
    key = (rig.name, bone_name)
    if key not in _ROT_INDEX_CACHE:
        bone = rig.pose.bones[bone_name].bone
        matrix = bone.matrix_local.to_3x3()
        axes = [matrix.col[i] for i in range(3)]
        index = max(range(3), key=lambda i: abs(axes[i].y))
        direction = bone.tail_local - bone.head_local
        sign = 1.0 if axes[index].cross(direction).x > 0 else -1.0
        _ROT_INDEX_CACHE[key] = (index, sign)
    return _ROT_INDEX_CACHE[key]


def pose_frame(rig, frame, pose, root_z=0.0, root_scale_z=1.0):
    """Key every bone at `frame`, even the ones this pose leaves at rest.

    Leaving a channel un-keyed is the "gãy gập" bug from BLENDER-SESSION-GUIDE
    §4: fit_camera() sweeps every action, and an un-keyed channel keeps
    whatever value the previously swept action left behind.
    """
    for bone in BONES:
        pose_bone = rig.pose.bones[bone]
        index, sign = _swing_channel(rig, bone)
        euler = [0.0, 0.0, 0.0]
        euler[index] = sign * math.radians(pose.get(bone, 0.0))
        pose_bone.rotation_euler = euler
        for axis in range(3):
            pose_bone.keyframe_insert(data_path="rotation_euler", index=axis, frame=frame)
    rig.location.z = root_z
    rig.keyframe_insert(data_path="location", index=2, frame=frame)
    rig.scale = (1.0, 1.0, root_scale_z)
    rig.keyframe_insert(data_path="scale", index=2, frame=frame)


# --- Body --------------------------------------------------------------------


def build_chibi_body(rig, spec):
    """Head, torso, hips and both limb pairs as smooth revolved surfaces.

    `spec` keys: skin, cloth {torso, pelvis, legs}, shoe. Cloth colours cover
    the limbs too (sleeves and trousers), with the far pair auto-darkened.
    """
    skin = spec["skin"]
    cloth = spec["cloth"]
    shoe = spec.get("shoe", shade_hex(cloth["legs"], 0.55))
    name = spec["name"]
    parts = []

    verts, faces = _ellipsoid(HEAD_RX, HEAD_RY, HEAD_RZ, rings=14, segments=24)
    parts.append(_new_part(rig, name + "_Head", _transform(verts, HEAD_CENTER),
                           faces, skin, "head"))

    parts.append(_new_part(rig, name + "_Neck",
                           *_taper(0.575, 0.645, 0.052, 0.048, cap=False),
                           color_hex=shade_hex(skin, 0.88), bone="spine"))

    # Torso: a soft barrel, widest at the chest, pinched at the neck base.
    torso = [
        (0.235, 0.000, 0.000),
        (0.250, 0.112, 0.093),
        (0.330, 0.126, 0.106),
        (0.450, 0.120, 0.102),
        (0.545, 0.130, 0.114),
        (0.600, 0.098, 0.086),
        (0.625, 0.060, 0.052),
    ]
    parts.append(_new_part(rig, name + "_Torso", *_revolve(torso, 22),
                           color_hex=cloth["torso"], bone="spine"))

    hips = [
        (0.160, 0.060, 0.052),
        (0.185, 0.112, 0.096),
        (0.230, 0.128, 0.108),
        (0.270, 0.118, 0.098),
        (0.290, 0.070, 0.060),
    ]
    parts.append(_new_part(rig, name + "_Hips", *_revolve(hips, 22),
                           color_hex=cloth["pelvis"], bone="pelvis"))

    for side, sign in (("l", 1.0), ("r", -1.0)):
        far = side == "l"
        tone = (lambda c: shade_hex(c, FAR_SHADE)) if far else (lambda c: c)
        y = sign * ARM_Y

        parts.append(_new_part(
            rig, "{0}_Shoulder_{1}".format(name, side),
            *_shift(_ellipsoid(0.055, 0.052, 0.050, rings=8, segments=16), (0, y, Z_SHOULDER)),
            color_hex=tone(cloth["torso"]), bone="arm_" + side))
        parts.append(_new_part(
            rig, "{0}_UpperArm_{1}".format(name, side),
            *_shift(_taper(Z_ELBOW, Z_SHOULDER, 0.038, 0.045, cap=False), (0, y, 0)),
            color_hex=tone(cloth["torso"]), bone="arm_" + side))
        parts.append(_new_part(
            rig, "{0}_Elbow_{1}".format(name, side),
            *_shift(_ellipsoid(0.041, 0.039, 0.039, rings=8, segments=16), (0, y, Z_ELBOW)),
            color_hex=tone(skin), bone="forearm_" + side))
        parts.append(_new_part(
            rig, "{0}_Forearm_{1}".format(name, side),
            *_shift(_taper(Z_WRIST, Z_ELBOW, 0.034, 0.040, cap=False), (0, y, 0)),
            color_hex=tone(skin), bone="forearm_" + side))
        parts.append(_new_part(
            rig, "{0}_Hand_{1}".format(name, side),
            *_shift(_ellipsoid(0.048, 0.042, 0.050, rings=8, segments=16), (0.004, y, Z_WRIST - 0.022)),
            color_hex=tone(skin), bone="forearm_" + side))

        leg_y = sign * LEG_Y
        parts.append(_new_part(
            rig, "{0}_Thigh_{1}".format(name, side),
            *_shift(_taper(Z_KNEE, Z_HIP, 0.050, 0.062, cap=False), (0, leg_y, 0)),
            color_hex=tone(cloth["legs"]), bone="leg_" + side))
        parts.append(_new_part(
            rig, "{0}_Knee_{1}".format(name, side),
            *_shift(_ellipsoid(0.050, 0.046, 0.045, rings=8, segments=16), (0, leg_y, Z_KNEE)),
            color_hex=tone(cloth["legs"]), bone="shin_" + side))
        parts.append(_new_part(
            rig, "{0}_Shin_{1}".format(name, side),
            *_shift(_taper(0.035, Z_KNEE, 0.040, 0.048, cap=False), (0, leg_y, 0)),
            color_hex=tone(cloth["legs"]), bone="shin_" + side))
        # Feet run forward along +X so the profile silhouette reads as standing.
        foot_verts, foot_faces = _ellipsoid(0.075, 0.043, 0.038, rings=8, segments=16)
        parts.append(_new_part(
            rig, "{0}_Foot_{1}".format(name, side),
            _transform(foot_verts, (0.028, leg_y, 0.036)), foot_faces,
            color_hex=tone(shoe), bone="shin_" + side))

    return parts


def _shift(geometry, translate):
    """(verts, faces) -> (translated verts, faces); keeps call sites readable."""
    verts, faces = geometry
    return _transform(verts, translate), faces


# --- Face --------------------------------------------------------------------


def add_face(rig, spec, style="default"):
    """Eye, brow and mouth stuck on the head. Parented to the head bone and kept
    out of Freestyle so the ink line does not swallow the pupil.

    `style="frontal"` mirrors the eye and brow onto the far cheek as well. The
    single-sided default is correct for the profile view these assets were first
    judged in, and WRONG in the game: the client camera is a 3/4 view (ADR-031),
    both cheeks are on screen, and a one-eyed face reads as a face with
    something missing. Verified by rendering through the client itself
    (`runWorldPreview`, 2026-09-03), not in Blender — the Blender preview looks
    fine at yaw 0 and hides the problem completely.

    Left single-sided by default rather than flipped for everyone, because
    `wandering_bandit` shipped and was accepted with the one-eyed face and
    changing it is not this task's business. It would benefit from `frontal`
    too.
    """
    name = spec["name"]
    hair_color = spec["hair"]["color"]
    eye_color = spec.get("eye_color", "#2B2B33")
    if style == "none":
        # A construct has no anime face to author; it gets a `stone_visor`
        # instead. Leaving these four parts on under a visor would put a pair of
        # white sclera specks on the far edges of the skull — the shape they take
        # when a profile-authored face is seen head-on.
        return []
    wide = 1.15 if style == "wide" else 1.0
    frontal = style == "frontal"
    parts = []

    # Sit the features below the hairline add_hair() cuts at z=0.856, or the
    # hair dome renders straight over the top of them.
    eye_x, eye_z = 0.092, 0.782
    sclera_verts, sclera_faces = _ellipsoid(0.040 * wide, 0.020, 0.055 * wide, rings=10, segments=18)
    parts.append(_new_part(
        rig, name + "_EyeWhite",
        _transform(sclera_verts, (eye_x, head_surface_y(eye_x, eye_z, 0.004), eye_z)),
        sclera_faces, "#FFFFFF", "head", shades=2))

    pupil_x, pupil_z = 0.105, 0.776
    pupil_verts, pupil_faces = _ellipsoid(0.021 * wide, 0.018, 0.034 * wide, rings=8, segments=16)
    parts.append(_new_part(
        rig, name + "_Pupil",
        _transform(pupil_verts, (pupil_x, head_surface_y(pupil_x, pupil_z, 0.014), pupil_z)),
        pupil_faces, eye_color, "head", shades=2))

    brow_x, brow_z = 0.086, 0.838
    brow_verts, brow_faces = _ellipsoid(0.049, 0.016, 0.011, rings=6, segments=14)
    parts.append(_new_part(
        rig, name + "_Brow",
        _transform(brow_verts, (brow_x, head_surface_y(brow_x, brow_z, 0.004), brow_z),
                   rotate_y_deg=-12.0),
        brow_faces, hair_color, "head", shades=2))

    mouth_x, mouth_z = 0.139, 0.706
    mouth_verts, mouth_faces = _ellipsoid(0.026, 0.012, 0.009, rings=6, segments=12)
    parts.append(_new_part(
        rig, name + "_Mouth",
        _transform(mouth_verts, (mouth_x, head_surface_y(mouth_x, mouth_z, 0.003), mouth_z)),
        mouth_faces, shade_hex(spec["skin"], 0.45), "head", shades=2))

    if frontal:
        far = len(parts)
        far_sclera = _transform(sclera_verts,
                                (eye_x, -head_surface_y(eye_x, eye_z, 0.004), eye_z))
        parts.append(_new_part(rig, name + "_EyeWhiteFar", far_sclera, sclera_faces,
                               "#FFFFFF", "head", shades=2))
        far_pupil = _transform(pupil_verts,
                               (pupil_x, -head_surface_y(pupil_x, pupil_z, 0.014), pupil_z))
        parts.append(_new_part(rig, name + "_PupilFar", far_pupil, pupil_faces,
                               eye_color, "head", shades=2))
        far_brow = _transform(brow_verts,
                              (brow_x, -head_surface_y(brow_x, brow_z, 0.004), brow_z),
                              rotate_y_deg=-12.0)
        parts.append(_new_part(rig, name + "_BrowFar", far_brow, brow_faces,
                               hair_color, "head", shades=2))
        _exclude_from_outline(parts[far + 1])

    # The sclera and the brow keep their ink line — an outlined eye and a solid
    # brow are the anime read. Only the pupil and mouth, both already dark and
    # only a few pixels across, would be swallowed by their own outline.
    _exclude_from_outline(parts[1])
    _exclude_from_outline(parts[3])
    return parts


# --- Hair --------------------------------------------------------------------

# Each entry is a list of (length, base_radius, tilt_deg, anchor_x, anchor_z).
# Tilt is measured in the screen plane: negative sweeps the spike backwards
# (-X, behind the head), which is what makes an anime silhouette read at 64px.
HAIR_STYLES = {
    "spiky_short": [
        (0.105, 0.043, -18, 0.020, 0.960),
        (0.115, 0.045, -48, -0.070, 0.935),
        (0.100, 0.040, -76, -0.140, 0.870),
        (0.085, 0.036, -104, -0.170, 0.790),
        (0.078, 0.034, 22, 0.105, 0.905),
        (0.062, 0.030, 52, 0.150, 0.835),
    ],
    "spiky_long": [
        (0.150, 0.046, -24, 0.010, 0.965),
        (0.165, 0.048, -52, -0.080, 0.930),
        (0.145, 0.042, -82, -0.150, 0.860),
        (0.130, 0.038, -108, -0.175, 0.775),
        (0.120, 0.034, -128, -0.160, 0.700),
        (0.098, 0.036, 26, 0.108, 0.900),
        (0.080, 0.032, 58, 0.155, 0.828),
    ],
    "bang": [
        (0.070, 0.040, 34, 0.115, 0.900),
        (0.078, 0.042, 14, 0.060, 0.945),
        (0.066, 0.038, 62, 0.158, 0.828),
        (0.070, 0.036, -96, -0.160, 0.820),
    ],
    "topknot": [
        (0.090, 0.048, -4, -0.020, 0.980),
        (0.060, 0.030, 40, 0.130, 0.870),
        (0.058, 0.030, -92, -0.158, 0.828),
    ],
    # An elder's 髻: a short fat knob, not a spike. The spike generator lathes
    # a profile that ends at radius 0, so a LONG entry always comes to a point
    # — read as a horn on a white-haired head. Keeping length below the base
    # radius times about 1.4 keeps it round.
    "topknot_bun": [
        (0.072, 0.056, -2, -0.012, 0.982),
        (0.046, 0.026, 54, 0.142, 0.848),
        (0.044, 0.026, -96, -0.162, 0.832),
    ],
    # A carved stone crest, for constructs: a row of blunt knobs along the crown
    # instead of hair. Same trick as `topknot_bun` — every length stays under
    # about 1.4x its own base radius, because the spike generator lathes to a
    # point and a pointed knob on a stone head reads as a horn. Pair it with a
    # dark-stone hair colour: `add_hair`'s cap then becomes the carved helm that
    # gives the face a brow line, not a wig.
    "carved_crest": [
        (0.062, 0.050, -8, -0.010, 0.978),
        (0.058, 0.046, -46, -0.078, 0.938),
        (0.052, 0.042, -80, -0.146, 0.868),
        (0.048, 0.038, 30, 0.108, 0.900),
    ],
}


def add_hair(rig, spec, style="spiky_short", cap=True):
    """A hair cap over the skull plus swept spikes.

    Two pieces rather than one blob: a dome cut at the brow line, which gives
    the face a hairline instead of swallowing it, and a smaller mass over the
    nape, because hair hangs lower at the back than at the front. Cheaper and
    far more robust than booleaning a real haircut, and at sprite scale it
    reads the same.

    `cap=False` drops both masses and keeps only the swept shapes — for a head
    that has no hair on it at all, such as a carved construct. It is not a style
    preference: the dome is cut at the brow line for a camera looking level at
    the character, and the client's camera looks DOWN (ADR-031), so on a bald
    stone skull the dome's front edge becomes a brim that covers the whole face.
    That is the same failure the elder's hair caused (ASSET-LOG 2026-09-03,
    lesson 4), seen again through `runWorldPreview` and not in Blender.
    """
    if style not in HAIR_STYLES:
        raise KeyError("unknown hair style '{0}'; have {1}".format(
            style, sorted(HAIR_STYLES)))
    name = spec["name"]
    color = spec["hair"]["color"]
    parts = []

    if cap:
        cap_verts, cap_faces = _dome(0.197, 0.178, 0.207, z_min_frac=0.19, segments=24)
        parts.append(_new_part(rig, name + "_HairCap",
                               _transform(cap_verts, HEAD_CENTER),
                               cap_faces, color, "head"))

        nape_verts, nape_faces = _ellipsoid(0.126, 0.174, 0.148, rings=10, segments=20)
        parts.append(_new_part(rig, name + "_HairNape",
                               _transform(nape_verts, (-0.086, 0.0, 0.778)),
                               nape_faces, color, "head"))

    for index, (length, radius, tilt, anchor_x, anchor_z) in enumerate(HAIR_STYLES[style]):
        spike_verts, spike_faces = _revolve(
            [(0.0, radius, radius * 0.92), (length * 0.55, radius * 0.62, radius * 0.58),
             (length, 0.0, 0.0)], 12)
        parts.append(_new_part(
            rig, "{0}_HairSpike{1}".format(name, index),
            _transform(spike_verts, (anchor_x, 0.0, anchor_z), rotate_y_deg=tilt),
            spike_faces, color, "head"))
    return parts


# --- Accessories -------------------------------------------------------------


def add_accessory(rig, spec, kind, color, bone=None, accent=None):
    """One costume piece. See ART-QUALITY-STANDARD.md §5 for the vocabulary.

    `accent` is a second colour for the pieces that need one (a stele's engraved
    columns, a bundle's binding cord). It is passed on ONLY when the caller asked
    for it, so the thirteen builders whose art has already shipped and been
    accepted keep their four-argument signature and cannot be broken by this.
    """
    name = spec["name"]
    builder = _ACCESSORY_BUILDERS.get(kind)
    if builder is None:
        raise KeyError("unknown accessory '{0}'; have {1}".format(
            kind, sorted(_ACCESSORY_BUILDERS)))
    if accent is None:
        return builder(rig, name, color, bone)
    return builder(rig, name, color, bone, accent)


def _acc_headband(rig, name, color, bone):
    band = _revolve([(0.870, 0.196, 0.178), (0.906, 0.196, 0.178)], 24)
    parts = [_new_part(rig, name + "_Headband", *_shift(band, (0.0, 0.0, 0.0)),
                       color_hex=color, bone="head", shades=2)]
    # Trailing knot ends, so the headband is visible in silhouette too.
    for index, phase in enumerate((0.0, 1.7)):
        parts.append(_sway_strip(
            rig, "{0}_HeadbandTail{1}".format(name, index), color, "head",
            pivot=(-0.170, -0.030 + index * 0.055, 0.886),
            half_x=0.013, half_y=0.006, length=0.135,
            amplitude_deg=16.0, speed=0.34, phase=phase, base_tilt=-58.0))
    return parts


def _acc_scarf(rig, name, color, bone):
    collar = _revolve([(0.585, 0.086, 0.076), (0.640, 0.080, 0.070)], 20)
    parts = [_new_part(rig, name + "_ScarfCollar", *collar, color_hex=color,
                       bone="spine", shades=2)]
    parts.append(_sway_strip(rig, name + "_ScarfTail", color, "spine",
                             pivot=(-0.075, -0.045, 0.600),
                             half_x=0.020, half_y=0.010, length=0.300,
                             amplitude_deg=18.0, speed=0.26, phase=0.0,
                             base_tilt=-38.0))
    return parts


def _acc_sash(rig, name, color, bone):
    belt = _revolve([(0.238, 0.132, 0.112), (0.278, 0.134, 0.114)], 20)
    parts = [_new_part(rig, name + "_Sash", *belt, color_hex=color,
                       bone="pelvis", shades=2)]
    parts.append(_sway_strip(rig, name + "_SashTail", color, "pelvis",
                             pivot=(-0.105, -0.030, 0.262),
                             half_x=0.017, half_y=0.008, length=0.215,
                             amplitude_deg=13.0, speed=0.22, phase=1.1,
                             base_tilt=-14.0))
    return parts


def _acc_shoulder_plate(rig, name, color, bone):
    target = bone or "arm_r"
    y = -ARM_Y if target.endswith("_r") else ARM_Y
    verts, faces = _ellipsoid(0.078, 0.062, 0.052, rings=10, segments=18)
    return [_new_part(rig, name + "_Pauldron_" + target[-1],
                      _transform(verts, (0.0, y, Z_SHOULDER + 0.012), scale=(1.0, 1.0, 0.85)),
                      faces, color, target, shades=2)]


def _acc_chest_plate(rig, name, color, bone):
    verts, faces = _ellipsoid(0.104, 0.098, 0.130, rings=12, segments=20)
    return [_new_part(rig, name + "_ChestPlate",
                      _transform(verts, (0.022, 0.0, 0.435), scale=(1.0, 1.05, 1.0)),
                      faces, color, "spine", shades=2)]


def _acc_thigh_guard(rig, name, color, bone):
    parts = []
    for side, sign in (("l", 1.0), ("r", -1.0)):
        verts, faces = _ellipsoid(0.062, 0.055, 0.056, rings=8, segments=16)
        tone = shade_hex(color, FAR_SHADE) if side == "l" else color
        parts.append(_new_part(
            rig, "{0}_ThighGuard_{1}".format(name, side),
            _transform(verts, (0.008, sign * LEG_Y, 0.205)), faces,
            tone, "leg_" + side, shades=2))
    return parts


def _acc_arm_wrap(rig, name, color, bone):
    parts = []
    for side, sign in (("l", 1.0), ("r", -1.0)):
        wrap = _revolve([(0.318, 0.043, 0.041), (0.392, 0.041, 0.039)], 16)
        tone = shade_hex(color, FAR_SHADE) if side == "l" else color
        parts.append(_new_part(
            rig, "{0}_ArmWrap_{1}".format(name, side),
            *_shift(wrap, (0.0, sign * ARM_Y, 0.0)),
            color_hex=tone, bone="forearm_" + side, shades=2))
    return parts


def _acc_cloak(rig, name, color, bone):
    """A back cloak: a tapered skirt hanging off the shoulders, behind the body."""
    verts, faces = _revolve(
        [(0.560, 0.100, 0.092), (0.430, 0.128, 0.110), (0.270, 0.150, 0.124)], 20)
    trimmed = _transform(verts, (-0.052, 0.0, 0.0))
    return [_new_part(rig, name + "_Cloak", trimmed, faces, color, "spine", shades=3)]


def _acc_beard(rig, name, color, bone):
    """Chin beard, moustache and jaw mass — what makes a chibi read as OLD.

    Three pieces, and the jaw mass is the one that is easy to leave out and
    cannot be: a cone starting at the chin point alone hangs in space, because
    the head is an ellipsoid and its surface has already curved away by then.
    The mass bridges jawline to beard so the silhouette is continuous.

    Sized against the MOUTH, not against the head: the first pass used a jaw
    ellipsoid reaching z=0.740 and it covered `add_face`'s mouth at z=0.706, so
    the elder rendered with no face at all — the exact Q2 failure this whole
    rebuild exists to fix. Keep the jaw mass topping out below 0.700.

    All three ride the `head` bone: a beard that stayed behind when the head
    turned would be worse than no beard at all.
    """
    jaw_verts, jaw_faces = _ellipsoid(0.076, 0.076, 0.052, rings=10, segments=18)
    parts = [_new_part(rig, name + "_BeardJaw",
                       _transform(jaw_verts, (0.062, 0.0, 0.648)),
                       jaw_faces, color, "head", shades=2)]

    # Swept forward (-14 deg) rather than hanging plumb: a vertical beard stays
    # inside the head's own silhouette and reads as long side hair. The tip has
    # to clear the chest in +X before the shape says "beard".
    fall_verts, fall_faces = _taper(-0.190, 0.0, 0.020, 0.068, segments=16)
    parts.append(_new_part(rig, name + "_BeardFall",
                           _transform(fall_verts, (0.108, 0.0, 0.650), rotate_y_deg=-14.0),
                           fall_faces, color, "head", shades=3))

    mo_verts, mo_faces = _ellipsoid(0.044, 0.032, 0.017, rings=8, segments=16)
    parts.append(_new_part(rig, name + "_Moustache",
                           _transform(mo_verts, (0.136, 0.0, 0.734)),
                           mo_faces, color, "head", shades=2))
    return parts


def _acc_long_robe(rig, name, color, bone):
    """Floor-length robe: a flared skirt from the waist down, swallowing the legs.

    Hem stops at z=0.055 rather than 0.0 on purpose — the shoes stay visible, so
    the figure still has a contact point with the ground instead of appearing to
    float. Bound rigidly to `pelvis`, not to the legs: a stiff robe should swing
    with the hips as one piece, and skinning it to `leg_l`/`leg_r` would tear it
    down the middle the moment the walk cycle plays.
    """
    verts, faces = _revolve(
        [(0.055, 0.192, 0.170), (0.150, 0.182, 0.160),
         (0.262, 0.152, 0.132), (0.362, 0.132, 0.114)], 22)
    return [_new_part(rig, name + "_Robe", verts, faces, color, "pelvis", shades=3)]


def _acc_robe_sash(rig, name, color, bone):
    """Waist sash sized to go OUTSIDE `long_robe`, not inside it.

    `_acc_sash` sits at radius 0.132 around z=0.26, which is correct on a bare
    chibi and completely invisible under a robe whose skirt is 0.15-0.19 wide at
    that height — the elder's first build spent two mesh parts on a belt nobody
    could ever see. This one rides the robe's own waistline (its top ring is at
    z=0.362, radius 0.132) and clears it.

    Bound to `pelvis` like the robe rather than to `spine`: the two have to move
    as one piece, and it is the torso rising out of the sash at an angle that
    sells the stoop.
    """
    belt = _revolve([(0.344, 0.144, 0.126), (0.400, 0.146, 0.128)], 22)
    parts = [_new_part(rig, name + "_RobeSash", *belt, color_hex=color,
                       bone="pelvis", shades=2)]
    knot_verts, knot_faces = _ellipsoid(0.040, 0.030, 0.038, rings=8, segments=14)
    parts.append(_new_part(rig, name + "_RobeSashKnot",
                           _transform(knot_verts, (0.132, 0.0, 0.368)),
                           knot_faces, color, "pelvis", shades=2))
    return parts


def _acc_robe_hem(rig, name, color, bone):
    """Band around the bottom edge of `long_robe`.

    Exists because an accent at the waist does not survive: the wide sleeves
    hang from z=0.27 to z=0.46 and swallow the sash from every angle except
    dead-on profile, leaving about two pixels of colour at game size. The hem is
    the one band on a robed figure that nothing can occlude, and a contrasting
    hem is what a Daoist robe actually has.
    """
    band = _revolve([(0.052, 0.196, 0.174), (0.100, 0.194, 0.172)], 22)
    return [_new_part(rig, name + "_RobeHem", *band, color_hex=color,
                      bone="pelvis", shades=2)]


def _acc_wide_sleeves(rig, name, color, bone):
    """Wide hanging sleeves — the other half of the Daoist robe read.

    Flares the WRONG way round on purpose: radius is larger at the wrist than at
    the elbow, which is what a hanging sleeve does and what a bracer does not.
    """
    parts = []
    for side, sign in (("l", 1.0), ("r", -1.0)):
        sleeve = _taper(Z_WRIST - 0.030, Z_ELBOW + 0.030, 0.082, 0.056, cap=False)
        tone = shade_hex(color, FAR_SHADE) if side == "l" else color
        parts.append(_new_part(
            rig, "{0}_Sleeve_{1}".format(name, side),
            *_shift(sleeve, (0.0, sign * ARM_Y, 0.0)),
            color_hex=tone, bone="forearm_" + side, shades=2))
    return parts


def _acc_stone_visor(rig, name, color, bone, accent=None):
    """A machine's face: a recessed band round the skull with two lit slots.

    Pairs with `face_style="none"`. `add_face` builds an anime face for a PROFILE
    view — eye, brow and mouth all sit on one cheek at |y| near the head's own
    half-depth — and the client draws characters closer to head-on than that
    (`WorldModelRenderer.MODEL_YAW_DEGREES`), which pushes both eyes out onto the
    silhouette edges. On a haired head the fringe hides that; on a bare stone
    skull it leaves two white specks stuck to the ears.

    A band round the whole head has no such angle: it is a ring, so it presents
    the same width from every yaw the locked camera can produce. `accent` lights
    the two slots, and they are the only warm colour above the waist, which is
    what makes the head the first thing the eye lands on.
    """
    band = _revolve([(0.760, 0.198, 0.180), (0.824, 0.198, 0.180)], 24)
    parts = [_new_part(rig, name + "_Visor", *band, color_hex=color, bone="head",
                       shades=2, smooth=False)]

    lights = []
    for offset_y in (-0.058, 0.058):
        verts, faces = _box(0.022, 0.026, 0.776, 0.812)
        lights.append((_transform(verts, (0.182, offset_y, 0.0)), faces))
    parts.append(_new_part(rig, name + "_VisorLights", *_merge(lights),
                           color_hex=accent or shade_hex(color, 1.6),
                           bone="head", shades=2, smooth=False))
    # Same rule as the pupil in `add_face`: an ink line as wide as the lit slot
    # itself turns the glow into a black smudge.
    _exclude_from_outline(parts[1])
    return parts


def _acc_stone_tablet(rig, name, color, bone, accent=None):
    """A stele slab strapped flat across the chest, with engraved columns.

    Built as a BOX, not as the ellipsoid `chest_plate` uses: a stele is a cut
    stone and its silhouette has to have corners, otherwise it reads as one more
    piece of armour. Faceted (`smooth=False`) for the same reason.

    The slab is deliberately a lighter tone than whatever torso it sits on — two
    stone-grey blocks of the same value stack into one lump at 64px, the lesson
    `icon_genin_headband` paid for three times (ASSET-LOG 2026-09-03).

    `accent` colours the engraved columns; without one they fall back to a
    darkened slab tone, which is what a real chisel cut looks like.
    """
    slab_x, slab_half_x = 0.118, 0.024
    slab_verts, slab_faces = _box(slab_half_x, 0.078, 0.368, 0.556)
    parts = [_new_part(rig, name + "_Tablet",
                       _transform(slab_verts, (slab_x, 0.0, 0.0)), slab_faces,
                       color, "spine", shades=2, smooth=False)]

    # Vertical columns, the way the writing on a stele actually runs. Horizontal
    # bars at this size read as a ladder, which is a machine part, not a text.
    groove_verts, groove_faces = _box(0.006, 0.006, 0.390, 0.534)
    grooves = _merge([
        (_transform(groove_verts, (slab_x + slab_half_x, offset_y, 0.0)), groove_faces)
        for offset_y in (-0.045, -0.015, 0.015, 0.045)])
    parts.append(_new_part(rig, name + "_TabletGlyphs", *grooves,
                           color_hex=accent or shade_hex(color, 0.55),
                           bone="spine", shades=2, smooth=False))
    return parts


def _acc_bamboo_slips(rig, name, color, bone, accent=None):
    """A fan of bound bamboo slips (thẻ tre) rising off the back.

    The archive read, and it has to live in the SILHOUETTE to survive.

    ⚠️ **Spread along Y, not along X-Z.** Two builds were thrown away learning
    that. The first fanned the slips up and back off the spine in the screen
    plane, which is the shape a bundle of slips actually has and is invisible in
    this game: `WorldModelRenderer` draws characters close to head-on
    (`MODEL_YAW_DEGREES` 35 against a base correction of -45), so anything mounted
    behind the spine hides behind the body no matter how far back it is pushed.
    The second build made the fan half again as long and it disappeared exactly
    the same way. What clears a head-on body is WIDTH: the torso is 0.114 deep
    and the pauldrons reach |y| = 0.187, so the outer slats sit past 0.20 and are
    the only part of this piece the camera is guaranteed to see.

    Separate slats rather than one plank: Freestyle inks each disconnected shell
    inside the mesh, so the gaps come back as drawn lines and the shape says
    "bound strips" instead of "board". The two cords say it again, and stop the
    mat reading as a set of wings.

    `accent` is the binding cord — the only warm-on-warm break in the bundle, so
    it stays a real second colour rather than a shade of the slats.
    """
    back_x = -0.150
    slats = []
    for index in range(7):
        verts, faces = _box(0.026, 0.030, 0.345, 0.665)
        slats.append((_transform(verts, (back_x, -0.225 + index * 0.075, 0.0)), faces))
    parts = [_new_part(rig, name + "_BambooSlips", *_merge(slats), color_hex=color,
                       bone="spine", shades=2, smooth=False)]

    cords = []
    for cord_z in (0.400, 0.610):
        verts, faces = _box(0.014, 0.262, cord_z, cord_z + 0.036)
        cords.append((_transform(verts, (back_x - 0.030, 0.0, 0.0)), faces))
    parts.append(_new_part(rig, name + "_BambooCord", *_merge(cords),
                           color_hex=accent or shade_hex(color, 0.55),
                           bone="spine", shades=2, smooth=False))
    return parts


_ACCESSORY_BUILDERS = {
    "headband": _acc_headband,
    "stone_visor": _acc_stone_visor,
    "stone_tablet": _acc_stone_tablet,
    "bamboo_slips": _acc_bamboo_slips,
    "beard": _acc_beard,
    "long_robe": _acc_long_robe,
    "wide_sleeves": _acc_wide_sleeves,
    "robe_sash": _acc_robe_sash,
    "robe_hem": _acc_robe_hem,
    "scarf": _acc_scarf,
    "sash": _acc_sash,
    "shoulder_plate": _acc_shoulder_plate,
    "chest_plate": _acc_chest_plate,
    "thigh_guard": _acc_thigh_guard,
    "arm_wrap": _acc_arm_wrap,
    "cloak": _acc_cloak,
}


# --- Weapons -----------------------------------------------------------------


def build_weapon(rig, spec, kind, color=None, accent=None):
    """The element's signature weapon, mounted where ART-PIPELINE §2.2 says.

    Weapons keep flat shading and their Freestyle outline: hard edges are what
    makes a blade read as sharp next to the soft body.
    """
    builder = _WEAPON_BUILDERS.get(kind)
    if builder is None:
        raise KeyError("unknown weapon '{0}'; have {1}".format(
            kind, sorted(_WEAPON_BUILDERS)))
    steel = color or "#B9C2CC"
    return builder(rig, spec["name"], steel, accent or "#4A3524")


def _weapon_kunai(rig, name, steel, wood):
    """Broad-bladed kunai on the near hip, point down, plus its tail ring.

    Sized well past life-like: at @1x the whole character is ~83px, so a
    realistically small knife lands on three or four pixels and reads as a
    smudge. Silhouette legibility beats scale accuracy for every prop this
    small (ART-QUALITY-STANDARD.md Q7).
    """
    y = -ARM_Y - 0.040
    blade_verts, blade_faces = _blade(0.195, 0.082, 0.018, curve_deg=0.0, taper=0.08, sections=3)
    parts = [_new_part(rig, name + "_KunaiBlade",
                       _transform(blade_verts, (0.010, y, 0.315), rotate_y_deg=172.0),
                       blade_faces, steel, "pelvis", shades=2, smooth=False)]
    grip = _revolve([(0.0, 0.021, 0.019), (0.088, 0.021, 0.019)], 10)
    parts.append(_new_part(rig, name + "_KunaiGrip",
                           _transform(grip[0], (0.010, y, 0.315), rotate_y_deg=-8.0),
                           grip[1], wood, "pelvis", shades=2, smooth=False))
    ring_verts, ring_faces = _revolve(
        [(0.0, 0.036, 0.033), (0.016, 0.036, 0.033)], 12)
    parts.append(_new_part(rig, name + "_KunaiRing",
                           _transform(ring_verts, (0.000, y, 0.406), rotate_y_deg=-8.0),
                           ring_faces, steel, "pelvis", shades=2, smooth=False))
    return parts


def _weapon_gourd(rig, name, steel, wood):
    """Water gourd slung on the back — Thủy is a support element, not a blade."""
    y = -ARM_Y * 0.35
    parts = []
    for index, (radius, z) in enumerate(((0.058, 0.360), (0.042, 0.452))):
        verts, faces = _ellipsoid(radius, radius * 0.92, radius * 1.05, rings=10, segments=18)
        parts.append(_new_part(rig, "{0}_Gourd{1}".format(name, index),
                               _transform(verts, (-0.128, y, z)), faces,
                               steel, "spine", shades=3))
    cork = _revolve([(0.0, 0.020, 0.019), (0.030, 0.024, 0.022)], 10)
    parts.append(_new_part(rig, name + "_GourdCork",
                           _transform(cork[0], (-0.128, y, 0.492)), cork[1],
                           wood, "spine", shades=2, smooth=False))
    return parts


def _weapon_twin_blades(rig, name, steel, wood):
    """Two short sabres angled off the hips; the curve is what names them."""
    parts = []
    for index, (y, tilt) in enumerate(((-ARM_Y - 0.030, 150.0), (ARM_Y + 0.030, 156.0))):
        tone = steel if index == 0 else shade_hex(steel, FAR_SHADE)
        blade_verts, blade_faces = _blade(0.190, 0.040, 0.013, curve_deg=34.0,
                                          taper=0.12, sections=7)
        parts.append(_new_part(rig, "{0}_Sabre{1}".format(name, index),
                               _transform(blade_verts, (0.030, y, 0.318), rotate_y_deg=tilt),
                               blade_faces, tone, "pelvis", shades=2, smooth=False))
        grip = _revolve([(0.0, 0.015, 0.014), (0.062, 0.015, 0.014)], 10)
        parts.append(_new_part(rig, "{0}_SabreGrip{1}".format(name, index),
                               _transform(grip[0], (0.030, y, 0.318), rotate_y_deg=-24.0),
                               grip[1], wood, "pelvis", shades=2, smooth=False))
    return parts


def _weapon_long_blade(rig, name, steel, wood):
    """Long straight thin blade worn across the back — Lôi's armour-piercer."""
    y = -ARM_Y * 0.2
    blade_verts, blade_faces = _blade(0.520, 0.034, 0.011, curve_deg=0.0, taper=0.16, sections=3)
    parts = [_new_part(rig, name + "_LongBlade",
                       _transform(blade_verts, (-0.145, y, 0.235), rotate_y_deg=-24.0),
                       blade_faces, steel, "spine", shades=2, smooth=False)]
    grip = _revolve([(0.0, 0.016, 0.015), (0.105, 0.016, 0.015)], 10)
    parts.append(_new_part(rig, name + "_LongBladeGrip",
                           _transform(grip[0], (-0.145, y, 0.235), rotate_y_deg=156.0),
                           grip[1], wood, "spine", shades=2, smooth=False))
    guard, guard_faces = _box(0.006, 0.038, 0.0, 0.014)
    parts.append(_new_part(rig, name + "_LongBladeGuard",
                           _transform(guard, (-0.145, y, 0.235), rotate_y_deg=-24.0),
                           guard_faces, shade_hex(steel, 0.7), "spine", shades=2, smooth=False))
    return parts


def _weapon_stone_mace(rig, name, steel, wood):
    """Chunky stone mace: low segment count on purpose so it faceted-reads as rock."""
    y = -ARM_Y - 0.045
    head_verts, head_faces = _ellipsoid(0.070, 0.066, 0.072, rings=4, segments=7)
    parts = [_new_part(rig, name + "_MaceHead",
                       _transform(head_verts, (0.075, y, 0.320)), head_faces,
                       steel, "pelvis", shades=3, smooth=False)]
    shaft = _revolve([(0.0, 0.019, 0.018), (0.190, 0.022, 0.021)], 8)
    parts.append(_new_part(rig, name + "_MaceShaft",
                           _transform(shaft[0], (0.075, y, 0.320), rotate_y_deg=170.0),
                           shaft[1], wood, "pelvis", shades=2, smooth=False))
    return parts


_WEAPON_BUILDERS = {
    "kunai": _weapon_kunai,
    "gourd": _weapon_gourd,
    "twin_blades": _weapon_twin_blades,
    "long_blade": _weapon_long_blade,
    "stone_mace": _weapon_stone_mace,
}


# --- Driver-animated detail (B3) --------------------------------------------


def _scripted_driver(obj, path, index, expression):
    fcurve = obj.driver_add(path, index)
    driver = fcurve.driver
    driver.type = "SCRIPTED"
    variable = driver.variables.new()
    variable.name = "f"
    variable.type = "SINGLE_PROP"
    variable.targets[0].id_type = "SCENE"
    variable.targets[0].id = bpy.context.scene
    variable.targets[0].data_path = "frame_current"
    driver.expression = expression


def _sway_strip(rig, name, color, bone, pivot, half_x, half_y, length,
                amplitude_deg, speed, phase, base_tilt=0.0):
    """A hanging cloth strip that swings on a driver, whatever action plays.

    Drivers rather than per-action keyframes: cloth has to keep moving in every
    action, and re-keying it seven times is exactly how the "forgot a bone"
    bug got in last time.
    """
    verts, faces = _box(half_x, half_y, -length, 0.0)
    obj = _new_part(rig, name, _transform(verts, (0.0, 0.0, 0.0)), faces,
                    color, bone, shades=2, smooth=False)
    obj.location = pivot
    # A bone-bound part still honours its object transform, and the object
    # rotation is what the driver animates.
    _scripted_driver(obj, "rotation_euler", 1,
                     "radians({0}) + radians({1}) * sin(f * {2} + {3})".format(
                         base_tilt, amplitude_deg, speed, phase))
    return obj


def add_motion_detail(rig, spec, kind, color):
    """The B3 tier's moving accent — one distinct mechanism per element."""
    name = spec["name"]
    if kind == "embers":
        for index in range(3):
            _orbit_prop(rig, "{0}_Ember{1}".format(name, index), color,
                        radius=0.115, center=(-0.055, 0.72), speed=0.16,
                        size=0.030, phase=index * 2.09)
    elif kind == "water_ring":
        _orbit_prop(rig, name + "_WristRing", color, radius=0.062,
                    center=(0.030, 0.315), speed=0.07, size=0.030, phase=0.0)
    elif kind == "ribbons":
        for index, phase in enumerate((0.0, 3.14159)):
            _sway_strip(rig, "{0}_Ribbon{1}".format(name, index), color, "spine",
                        pivot=(-0.100, -0.055 + index * 0.110, 0.545),
                        half_x=0.016, half_y=0.007, length=0.255,
                        amplitude_deg=26.0, speed=0.30, phase=phase, base_tilt=-24.0)
    elif kind == "spark":
        verts, faces = _ellipsoid(0.030, 0.026, 0.052, rings=4, segments=6)
        obj = _new_part(rig, name + "_Spark", verts, faces, color, "spine",
                        shades=2, smooth=False)
        obj.location = (0.135, -ARM_Y - 0.075, 0.545)
        for axis in range(3):
            _scripted_driver(obj, "scale", axis,
                             "1.0 if (int(floor(f)) % 2) == 0 else 0.08")
    elif kind == "stone_shards":
        for index in range(2):
            _orbit_prop(rig, "{0}_Shard{1}".format(name, index), color,
                        radius=0.150, center=(-0.030, 0.800), speed=0.05,
                        size=0.044, phase=index * 3.14159, faceted=True)
    else:
        raise KeyError("unknown motion detail '{0}'".format(kind))


def _orbit_prop(rig, name, color, radius, center, speed, size, phase, faceted=False):
    """A chunk circling in the screen plane, held in front of the body.

    y is pinned well in front of the torso so the orbit never dips behind the
    character and vanishes — the exact bug that made Lôi's spark invisible in
    the box generation.
    """
    rings, segments = (4, 6) if faceted else (8, 14)
    verts, faces = _ellipsoid(size, size * 0.9, size, rings=rings, segments=segments)
    obj = _new_part(rig, name, verts, faces, color, "spine", shades=2, smooth=not faceted)
    obj.location = (center[0] + radius, -0.235, center[1])
    _scripted_driver(obj, "location", 0,
                     "{0} + {1} * cos(f * {2} + {3})".format(center[0], radius, speed, phase))
    _scripted_driver(obj, "location", 2,
                     "{0} + {1} * sin(f * {2} + {3})".format(center[1], radius, speed, phase))
    return obj


# --- Actions -----------------------------------------------------------------

# Every rendered frame is an explicit keyframe: the ranges below are chosen so
# that kage_sprite_render._sample_frames() lands exactly on the keys, which
# means the sprite shows the pose that was authored, not an interpolation of
# two of them.
#   idle 4 frames looping over 1..17 -> 1, 5, 9, 13
#   run  6 frames looping over 1..13 -> 1, 3, 5, 7, 9, 11
#   attack 5 over 1..9  -> 1, 3, 5, 7, 9      (index 2 = frame 5 = impact)
#   skill_cast 6 over 1..11, hit 2 over 1..3, jump 4 over 1..7, die 6 over 1..11
#
# Angles are degrees, positive swings the bone's tip forward (+X). Spine and
# head are counter-posed so the character never looks like a rotating plank.


def author_actions(rig):
    """Author the seven standard actions with real weight and follow-through."""
    if rig.animation_data is None:
        rig.animation_data_create()

    def start(action_name):
        action = bpy.data.actions.new(action_name)
        rig.animation_data.action = action
        return action

    # Idle is the pose every other asset gets compared against, so it carries
    # the standing silhouette: arms swung a little forward instead of hanging
    # dead straight (a straight arm in profile is a tube down the middle of the
    # chest and reads as nothing), and a staggered stance so the two legs do
    # not stack into one shape.
    start("idle")
    idle_stance = {"leg_r": 7, "leg_l": -7, "shin_r": -5, "shin_l": 4}
    pose_frame(rig, 1, dict(idle_stance, arm_l=16, arm_r=20, forearm_l=-14,
                            forearm_r=-18, spine=2, head=-1), root_z=0.0)
    pose_frame(rig, 5, dict(idle_stance, arm_l=13, arm_r=17, forearm_l=-17,
                            forearm_r=-21, spine=3, head=-2),
               root_z=0.012, root_scale_z=1.006)
    pose_frame(rig, 9, dict(idle_stance, arm_l=10, arm_r=14, forearm_l=-19,
                            forearm_r=-23, spine=4, head=-3),
               root_z=0.018, root_scale_z=1.010)
    pose_frame(rig, 13, dict(idle_stance, arm_l=13, arm_r=17, forearm_l=-16,
                             forearm_r=-20, spine=3, head=-2),
               root_z=0.008, root_scale_z=1.004)
    pose_frame(rig, 17, dict(idle_stance, arm_l=16, arm_r=20, forearm_l=-14,
                             forearm_r=-18, spine=2, head=-1), root_z=0.0)

    # Run: a two-step cycle. Frames 3 and 9 are the airborne passes (both feet
    # off the ground), which is the read that separates running from walking.
    start("run")
    run_lean = {"spine": 13, "head": -11}
    pose_frame(rig, 1, dict(run_lean, leg_r=40, shin_r=-26, leg_l=-34, shin_l=-48,
                            arm_r=-38, forearm_r=-44, arm_l=36, forearm_l=-28), root_z=0.012)
    pose_frame(rig, 3, dict(run_lean, leg_r=14, shin_r=-10, leg_l=-6, shin_l=-72,
                            arm_r=-16, forearm_r=-52, arm_l=18, forearm_l=-34), root_z=0.042)
    pose_frame(rig, 5, dict(run_lean, leg_r=-22, shin_r=-12, leg_l=32, shin_l=-30,
                            arm_r=30, forearm_r=-30, arm_l=-34, forearm_l=-46), root_z=0.006)
    pose_frame(rig, 7, dict(run_lean, leg_r=-34, shin_r=-48, leg_l=40, shin_l=-26,
                            arm_r=36, forearm_r=-28, arm_l=-38, forearm_l=-44), root_z=0.012)
    pose_frame(rig, 9, dict(run_lean, leg_r=-6, shin_r=-72, leg_l=14, shin_l=-10,
                            arm_r=18, forearm_r=-34, arm_l=-16, forearm_l=-52), root_z=0.042)
    pose_frame(rig, 11, dict(run_lean, leg_r=32, shin_r=-30, leg_l=-22, shin_l=-12,
                             arm_r=-34, forearm_r=-46, arm_l=30, forearm_l=-30), root_z=0.006)
    pose_frame(rig, 13, dict(run_lean, leg_r=40, shin_r=-26, leg_l=-34, shin_l=-48,
                             arm_r=-38, forearm_r=-44, arm_l=36, forearm_l=-28), root_z=0.012)

    # Attack: wind up (1, 3), strike on the third rendered frame (5), then two
    # frames of follow-through. Frame 5 is the one the server's hitbox timing
    # is documented against (ART-PIPELINE §4.2).
    start("attack")
    pose_frame(rig, 1, {"arm_r": -44, "forearm_r": -54, "arm_l": 14, "forearm_l": -20,
                        "spine": -7, "head": 4, "leg_r": 8, "leg_l": -6}, root_z=0.0)
    pose_frame(rig, 3, {"arm_r": -74, "forearm_r": -86, "arm_l": 26, "forearm_l": -26,
                        "spine": -13, "head": 7, "leg_r": 12, "leg_l": -10}, root_z=0.004)
    pose_frame(rig, 5, {"arm_r": 78, "forearm_r": -4, "arm_l": -30, "forearm_l": -16,
                        "spine": 16, "head": 6, "leg_r": 22, "leg_l": -18}, root_z=0.010)
    pose_frame(rig, 7, {"arm_r": 56, "forearm_r": -24, "arm_l": -18, "forearm_l": -22,
                        "spine": 9, "head": 2, "leg_r": 16, "leg_l": -12}, root_z=0.002)
    pose_frame(rig, 9, {"arm_r": 16, "forearm_r": -30, "arm_l": -4, "forearm_l": -18,
                        "spine": 3, "head": 0, "leg_r": 6, "leg_l": -4}, root_z=0.0)

    # Skill cast: seal-forming hands, then both palms pushed forward. The last
    # frame is the hold pose for channelled skills.
    start("skill_cast")
    pose_frame(rig, 1, {"arm_l": 4, "arm_r": -4, "forearm_l": -10, "forearm_r": -8,
                        "spine": 2}, root_z=0.0)
    pose_frame(rig, 3, {"arm_l": -22, "arm_r": -26, "forearm_l": -78, "forearm_r": -84,
                        "spine": -8, "head": 5}, root_z=0.0)
    pose_frame(rig, 5, {"arm_l": -30, "arm_r": -34, "forearm_l": -96, "forearm_r": -102,
                        "spine": -12, "head": 8}, root_z=0.004)
    pose_frame(rig, 7, {"arm_l": 20, "arm_r": 26, "forearm_l": -52, "forearm_r": -46,
                        "spine": 6, "head": 2}, root_z=0.008)
    pose_frame(rig, 9, {"arm_l": 64, "arm_r": 70, "forearm_l": -12, "forearm_r": -8,
                        "spine": 12, "head": -2}, root_z=0.006)
    pose_frame(rig, 11, {"arm_l": 70, "arm_r": 76, "forearm_l": -6, "forearm_r": -4,
                         "spine": 13, "head": -3}, root_z=0.004)

    start("hit")
    pose_frame(rig, 1, {"spine": -20, "head": -13, "arm_l": -28, "arm_r": -25,
                        "forearm_l": -38, "forearm_r": -34, "leg_r": -12, "leg_l": 9,
                        "shin_r": -16}, root_z=0.006)
    pose_frame(rig, 3, {"spine": -12, "head": -8, "arm_l": -17, "arm_r": -15,
                        "forearm_l": -28, "forearm_r": -25, "leg_r": -7, "leg_l": 5,
                        "shin_r": -11}, root_z=0.0)

    # Jump: crouch, launch with a stretch, tuck at the apex, reach down to land.
    # The squash is scale rather than a lowered root, because scale pivots on
    # the object origin — which is the ground — so the feet stay planted.
    # The apex height also sets the sheet's ortho_scale for every other frame;
    # 0.20 keeps this generation framed like the one before it.
    start("jump")
    pose_frame(rig, 1, {"leg_l": 22, "leg_r": 24, "shin_l": -46, "shin_r": -48,
                        "spine": 14, "head": -10, "arm_l": -30, "arm_r": -34,
                        "forearm_l": -20, "forearm_r": -22},
               root_z=-0.006, root_scale_z=0.90)
    pose_frame(rig, 3, {"leg_l": -6, "leg_r": -4, "shin_l": -4, "shin_r": -2,
                        "spine": 4, "head": -2, "arm_l": 58, "arm_r": 62,
                        "forearm_l": -8, "forearm_r": -10},
               root_z=0.090, root_scale_z=1.070)
    pose_frame(rig, 5, {"leg_l": 44, "leg_r": 38, "shin_l": -74, "shin_r": -68,
                        "spine": 8, "head": -6, "arm_l": 34, "arm_r": 40,
                        "forearm_l": -34, "forearm_r": -38},
               root_z=0.200, root_scale_z=1.010)
    pose_frame(rig, 7, {"leg_l": 16, "leg_r": -12, "shin_l": -30, "shin_r": -14,
                        "spine": 10, "head": -8, "arm_l": -22, "arm_r": 26,
                        "forearm_l": -26, "forearm_r": -28},
               root_z=0.115, root_scale_z=1.030)

    # Die: stagger, knees buckle, collapse forward onto them, settle.
    #
    # Deliberately a crumple, not a body laid out flat. A 1.0-tall character
    # lying down is ~0.8 wide, and fit_camera sizes every frame of the sheet
    # around the widest pose, so a sprawled death pose shrinks the character in
    # all 33 frames to buy realism in six. Folding the legs under keeps the
    # silhouette compact and still reads unmistakably as death.
    #
    # Angles compound down the chain: the torso's real tilt is pelvis+spine,
    # the head's is pelvis+spine+head. Overshooting here is what drove the
    # first attempt's skull through the floor.
    start("die")
    pose_frame(rig, 1, {"spine": -12, "head": -8, "arm_l": -24, "arm_r": -20,
                        "forearm_l": -26, "forearm_r": -22}, root_z=0.0)
    pose_frame(rig, 3, {"leg_l": 38, "leg_r": 34, "shin_l": -68, "shin_r": -64,
                        "spine": -8, "head": -4, "arm_l": -22, "arm_r": -18,
                        "forearm_l": -30, "forearm_r": -26}, root_z=-0.055)
    pose_frame(rig, 5, {"pelvis": -6, "leg_l": 56, "leg_r": 52,
                        "shin_l": -88, "shin_r": -84, "spine": 10, "head": 10,
                        "arm_l": -12, "arm_r": -8, "forearm_l": -34, "forearm_r": -30},
               root_z=-0.062)
    pose_frame(rig, 7, {"pelvis": -4, "leg_l": 60, "leg_r": 56,
                        "shin_l": -94, "shin_r": -90, "spine": 26, "head": 20,
                        "arm_l": 12, "arm_r": 16, "forearm_l": -30, "forearm_r": -26},
               root_z=-0.080)
    pose_frame(rig, 9, {"pelvis": -3, "leg_l": 61, "leg_r": 57,
                        "shin_l": -96, "shin_r": -92, "spine": 29, "head": 23,
                        "arm_l": 14, "arm_r": 18, "forearm_l": -32, "forearm_r": -28},
               root_z=-0.074)
    pose_frame(rig, 11, {"pelvis": -2, "leg_l": 62, "leg_r": 58,
                         "shin_l": -98, "shin_r": -94, "spine": 34, "head": 27,
                         "arm_l": 16, "arm_r": 20, "forearm_l": -34, "forearm_r": -30},
                root_z=-0.086)

    for pose_bone in rig.pose.bones:
        pose_bone.rotation_euler = (0.0, 0.0, 0.0)
    rig.location = (0.0, 0.0, 0.0)
    rig.scale = (1.0, 1.0, 1.0)
    # Actions with zero users are dropped on save (BLENDER-SESSION-GUIDE §4).
    for action in bpy.data.actions:
        action.use_fake_user = True


# --- Assembly ----------------------------------------------------------------


def assemble_character(spec):
    """Build a whole character from one spec dict. See CHARACTER_SPEC_KEYS."""
    rig = build_rig(spec["name"])
    build_chibi_body(rig, spec)
    add_face(rig, spec, style=spec.get("face_style", "default"))
    add_hair(rig, spec, style=spec["hair"]["style"], cap=spec["hair"].get("cap", True))
    for accessory in spec.get("accessories", []):
        add_accessory(rig, spec, accessory["kind"], accessory["color"],
                      accessory.get("bone"), accessory.get("accent"))
    if spec.get("weapon"):
        build_weapon(rig, spec, spec["weapon"],
                     color=spec.get("weapon_color"),
                     accent=spec.get("weapon_grip"))
    if spec.get("motion_detail"):
        add_motion_detail(rig, spec, spec["motion_detail"]["kind"],
                          spec["motion_detail"]["color"])
    author_actions(rig)
    apply_outline_exclusions()
    return rig


CHARACTER_SPEC_KEYS = """
name            str, prefix for every datablock (e.g. 'hoa_b1')
skin            hex
eye_color       hex, optional (default near-black)
hair            {"style": one of HAIR_STYLES, "color": hex,
                 "cap": False to drop the hair masses — see add_hair}
face_style      'default' | 'wide' | 'frontal' (mirrors eye+brow) | 'none', optional
                'none' builds no face at all — for a construct wearing a stone_visor
cloth           {"torso": hex, "pelvis": hex, "legs": hex}
shoe            hex, optional (defaults to a darkened trouser colour)
accessories     [{"kind": one of _ACCESSORY_BUILDERS, "color": hex, "bone": optional,
                 "accent": hex, optional second colour — see add_accessory}]
weapon          one of _WEAPON_BUILDERS, or None
weapon_color    hex, optional   weapon_grip hex, optional
motion_detail   {"kind": embers|water_ring|ribbons|spark|stone_shards, "color": hex}
"""
