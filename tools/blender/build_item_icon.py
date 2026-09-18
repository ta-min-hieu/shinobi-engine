"""Build the `icon_<item_code>` assets — one per real `item_template` row seeded
in the DB: V4/V5/V9 (kunai, wooden_sword, enhance_protection_charm), the 15 rows
V21 adds (`V21__seed_starter_equipment.sql`), `ancient_jade_shard` (V30),
`minor_healing_pill` (V36), the six `outer_sect_*` pieces of V45 and the
six `foundation_*` pieces of V48. ART-PIPELINE.md §8.3:
"item_template.code = 'kunai' <-> art_asset.asset_key = 'icon_kunai'"
(soft-link convention).

Icons are static (128x128 master @2x per ART-PIPELINE.md §4.1) — a single
still render, no action/keyframes needed at all, unlike every other asset
category built so far.

Run headless, one process builds+renders every item:

    blender -b -P tools/blender/build_item_icon.py -- --all \
      --out-blend assets/raw/blender/props --out-render assets/raw/render-out

`--item` also takes a comma-separated list, which is how the V21 batch was
built without re-saving (and so churning through Git LFS) the three .blend
files that already shipped:

    blender -b -P tools/blender/build_item_icon.py -- \
      --item novice_vest,genin_vest --out-blend ... --out-render ...

Two rules the V21 batch had to learn — keep them when adding more items:

  * A visible lean is a rotation around **Y**, not Z. The camera sits on -Y
    looking towards +Y, so +X is screen-right and +Z is screen-up; rotating
    around Z only foreshortens a shape sideways, it never tilts it on screen.
    Use `_tilt(degrees)`.
  * MEDIUM tier ships these at 64x64 (half the master, GraphicsTier), so each
    icon is a handful of chunky blocks in high-contrast colours. Anything
    thinner than ~6 master pixels is gone by the time the client draws it.
"""

import math
import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_sprite_render import apply_toon, setup_scene  # noqa: E402


# --- Palette -----------------------------------------------------------------
#
# V21 seeds two visible equipment tiers and the icons have to read the gap at a
# glance: "Tập Sự" is cloth, straw and wood in warm dull tones, "Hạ Nhẫn" is
# navy cloth with steel plating and gold trim. Same silhouette per slot, very
# different materials — that contrast is the whole reason both tiers exist.

NOVICE_CLOTH = "#B8A282"
NOVICE_TRIM = "#6E5B3E"
NOVICE_STRAW = "#D6B36A"
NOVICE_WOOD = "#8A6A46"

GENIN_CLOTH = "#39465A"
GENIN_DARK = "#1E242E"
STEEL = "#98A5B6"
STEEL_BRIGHT = "#DCE3EE"
GOLD = "#C9A227"
GEM_BLUE = "#2D7FE8"

IRON = "#8A8F99"
IRON_DARK = "#63696F"
RUST = "#8A5A3C"
CHAKRA = "#3FC8DC"
CHAKRA_CORE = "#A8F2FA"

# Tua kiếm — the tassel that hangs off a jian pommel. It is the single strongest
# "Chinese straight sword, not a katana" cue at 64x64, so it gets a colour of
# its own rather than borrowing one of the cloth tones.
TASSEL_RED = "#B0302E"

# Ngọc cổ — celadon jade, warm and desaturated, nothing like `chakra_shard`'s
# cyan. Three values because a single flat green plate has no readable form at
# 64x64; the gold below is the bore of the bi disc it broke off.
# ADR-046 D5 — đan dược. Đan sa (cinnabar) đỏ cam ấm: KHÔNG màu nào trong bộ icon đang ở vùng
# này (sắt xám, chakra xanh lơ, ngọc xanh lục, vải bậc Tập Sự nâu nhạt), nên ba viên tròn đỏ không
# bao giờ tráo chỗ với món khác ở 64x64.
PILL = "#D24B33"
PILL_SHEEN = "#F7C9B4"
PILL_DARK = "#8E2E1F"

JADE = "#5FA986"
JADE_PALE = "#A8D9BE"
JADE_DARK = "#3F7A5E"

# V48 — bậc "Trúc Cơ". Bạch ngọc gần trắng + huyền thiết gần đen: hai đầu của trục SẮC ĐỘ, vì bậc
# này phải đảo bố cục sáng-tối của bậc Ngoại Môn chứ không được nhích màu (xem khối ghi chú V48 bên
# dưới). `ONYX` ngả tím ấm để không lẫn với `GENIN_DARK` (#1E242E) vốn ngả lam — hai màu gần-đen
# nhưng nằm trên hai khối chính khác hẳn nhau (chàm sẫm vs trắng ngà) nên không bao giờ tráo chỗ.
BONE = "#E8DFC9"
ONYX = "#322C3C"


def _clear_scene():
    for obj in list(bpy.data.objects):
        if obj.type not in ("CAMERA", "LIGHT"):
            bpy.data.objects.remove(obj, do_unlink=True)
    for block_collection in (bpy.data.meshes, bpy.data.materials):
        for block in list(block_collection):
            if block.users == 0:
                block_collection.remove(block)


def _tilt(degrees):
    """Screen-plane lean. See the module docstring: it is the Y axis, not Z."""
    return (0.0, math.radians(degrees), 0.0)


def _link(name, verts, faces, loc, rot):
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    obj.location = loc
    obj.rotation_euler = rot
    return obj


def _box_obj(name, half_x, half_y, half_z, loc=(0.0, 0.0, 0.0), rot=(0.0, 0.0, 0.0)):
    verts = [(sx * half_x, sy * half_y, sz * half_z)
             for sz in (-1, 1) for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
    faces = [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]
    return _link(name, verts, faces, loc, rot)


def _gem_obj(name, half_x, half_y, half_z, loc=(0.0, 0.0, 0.0), rot=(0.0, 0.0, 0.0)):
    """Octahedron — the one non-box primitive, for crystals and pendants.

    Boxes cannot read as "gem" at 64x64: a diamond silhouette can, and eight
    flat faces stay inside the faceted low-poly look of the other icons.
    """
    verts = [(half_x, 0.0, 0.0), (0.0, half_y, 0.0), (-half_x, 0.0, 0.0), (0.0, -half_y, 0.0),
             (0.0, 0.0, half_z), (0.0, 0.0, -half_z)]
    faces = [(0, 1, 4), (1, 2, 4), (2, 3, 4), (3, 0, 4),
             (1, 0, 5), (2, 1, 5), (3, 2, 5), (0, 3, 5)]
    return _link(name, verts, faces, loc, rot)


def _frustum_obj(name, half_x_bottom, half_x_top, half_y, half_z,
                 loc=(0.0, 0.0, 0.0), rot=(0.0, 0.0, 0.0)):
    """Box whose top face is narrower than its bottom one.

    Needed because Freestyle draws a crease at every slab boundary: a dome
    approximated by stacking boxes of decreasing width renders as a visible
    staircase, not a curve. One tapered solid has one outline, and that outline
    is what makes a shape read as headwear at 64x64.
    """
    verts = [(-half_x_bottom, -half_y, -half_z), (half_x_bottom, -half_y, -half_z),
             (half_x_bottom, half_y, -half_z), (-half_x_bottom, half_y, -half_z),
             (-half_x_top, -half_y, half_z), (half_x_top, -half_y, half_z),
             (half_x_top, half_y, half_z), (-half_x_top, half_y, half_z)]
    faces = [(3, 2, 1, 0), (4, 5, 6, 7), (0, 1, 5, 4),
             (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
    return _link(name, verts, faces, loc, rot)


def _prism_obj(name, profile_xz, half_y, loc=(0.0, 0.0, 0.0), rot=(0.0, 0.0, 0.0)):
    """Extrude an arbitrary 2D outline (screen plane) into one solid.

    The escape hatch from the staircase trap: Freestyle draws its ink line per
    OBJECT, so a shape assembled from several overlapping boxes renders with the
    seams of every box (`icon_genin_headband`, ASSET-LOG 2026-09-03). Anything
    whose silhouette is not a box, a frustum, an octahedron or a torus has to be
    ONE mesh, and this is how one gets built.

    `profile_xz` is a list of (x, z) in screen coordinates, ordered
    counter-clockwise as the camera sees it (right, then up). That winding is
    what makes the front cap face the camera: with the camera on -Y, a
    screen-CCW polygon has normal -Y.
    """
    count = len(profile_xz)
    verts = [(x, -half_y, z) for x, z in profile_xz]
    verts += [(x, half_y, z) for x, z in profile_xz]
    faces = [tuple(range(count)), tuple(reversed(range(count, 2 * count)))]
    for i in range(count):
        j = (i + 1) % count
        # (front_i, back_i, back_j, front_j) — the other winding points the side
        # walls inwards, which the toon shader renders as an unlit black band.
        faces.append((i, count + i, count + j, j))
    return _link(name, verts, faces, loc, rot)


def _arc_band(radius_outer, radius_inner, degrees_from, degrees_to, segments=14):
    """Outline of a slice cut out of a flat ring — a bi-disc fragment.

    Outer edge swept counter-clockwise, inner edge swept back, closed by the two
    straight radial cuts. Those two straight edges against two curved ones are
    the whole read: they are what a break in a made object looks like, and a
    plain trapezoid has none of it.
    """
    outer = []
    inner = []
    for index in range(segments + 1):
        t = index / float(segments)
        angle = math.radians(degrees_from + (degrees_to - degrees_from) * t)
        cos_a, sin_a = math.cos(angle), math.sin(angle)
        outer.append((radius_outer * cos_a, radius_outer * sin_a))
        inner.append((radius_inner * cos_a, radius_inner * sin_a))
    return outer + list(reversed(inner))


def _disc(radius, segments=18):
    """Outline of a circle, screen-CCW — dùng với {@code _prism_obj} thành một viên tròn dẹt.

    Không có primitive hình cầu nào trong bộ này, và cũng không cần: ở 64x64 một khối trụ nhìn
    thẳng mặt ĐỌC RA hình tròn y hệt hình cầu, mà lại là MỘT mesh nên Freestyle chỉ vẽ đúng một
    đường viền (xem javadoc `_prism_obj` về bẫy đường nối).
    """
    return [(radius * math.cos(2.0 * math.pi * i / segments),
             radius * math.sin(2.0 * math.pi * i / segments)) for i in range(segments)]


def _ring_obj(name, major_r, minor_r, loc=(0.0, 0.0, 0.0), major_seg=16, minor_seg=6):
    """Low-poly torus lying in the XZ plane, i.e. facing the camera head-on."""
    verts = []
    for i in range(major_seg):
        theta = 2.0 * math.pi * i / major_seg
        cos_t, sin_t = math.cos(theta), math.sin(theta)
        for j in range(minor_seg):
            phi = 2.0 * math.pi * j / minor_seg
            radius = major_r + minor_r * math.cos(phi)
            verts.append((radius * cos_t, minor_r * math.sin(phi), radius * sin_t))
    faces = []
    for i in range(major_seg):
        next_i = (i + 1) % major_seg
        for j in range(minor_seg):
            next_j = (j + 1) % minor_seg
            faces.append((i * minor_seg + j,
                          next_i * minor_seg + j,
                          next_i * minor_seg + next_j,
                          i * minor_seg + next_j))
    return _link(name, verts, faces, loc, (0.0, 0.0, 0.0))


def _set_camera(ortho_scale):
    scene = bpy.context.scene
    camera = scene.camera
    camera.data.ortho_scale = ortho_scale
    camera.location = (0.0, -10.0, 0.0)


# --- Items already shipped (V4/V5/V9) ----------------------------------------


def build_kunai():
    blade = _box_obj("Blade", 0.065, 0.02, 0.34, loc=(0.0, -0.05, 0.14), rot=(0, 0, 0.20))
    apply_toon(blade, "#C9CDD6", shades=3)
    handle = _box_obj("Handle", 0.04, 0.035, 0.16, loc=(-0.03, -0.05, -0.20), rot=(0, 0, 0.20))
    apply_toon(handle, "#8B3A3A", shades=2)
    ring = _box_obj("Ring", 0.07, 0.02, 0.025, loc=(-0.10, -0.05, -0.36), rot=(0, 0, 0.20))
    apply_toon(ring, "#8A8F99", shades=2)
    return 0.85


def build_wooden_sword():
    blade = _box_obj("Blade", 0.055, 0.02, 0.34, loc=(0.0, -0.05, 0.10))
    apply_toon(blade, "#C9A66B", shades=3)
    guard = _box_obj("Guard", 0.16, 0.03, 0.03, loc=(0.0, -0.05, -0.26))
    apply_toon(guard, "#5C4326", shades=2)
    handle = _box_obj("Handle", 0.035, 0.035, 0.14, loc=(0.0, -0.05, -0.42))
    apply_toon(handle, "#3B2A1E", shades=2)
    pommel = _box_obj("Pommel", 0.05, 0.05, 0.04, loc=(0.0, -0.05, -0.58))
    apply_toon(pommel, "#5C4326", shades=2)
    return 1.0


def build_enhance_protection_charm():
    paper = _box_obj("Paper", 0.18, 0.015, 0.28, loc=(0.0, -0.05, 0.0))
    apply_toon(paper, "#F0E4C4", shades=3)
    seal = _box_obj("Seal", 0.09, 0.02, 0.09, loc=(0.0, -0.06, 0.02))
    apply_toon(seal, "#B33B3B", shades=2)
    stripe1 = _box_obj("Stripe1", 0.14, 0.022, 0.015, loc=(0.0, -0.065, -0.14))
    apply_toon(stripe1, "#8C2020", shades=2)
    stripe2 = _box_obj("Stripe2", 0.14, 0.022, 0.015, loc=(0.0, -0.065, -0.20))
    apply_toon(stripe2, "#8C2020", shades=2)
    loop = _box_obj("Loop", 0.05, 0.015, 0.02, loc=(0.0, -0.05, 0.30))
    apply_toon(loop, "#8C2020", shades=2)
    return 0.9


# --- V21 armour: one silhouette per equip_slot, two material tiers ------------


def _vest(cloth_hex, trim_hex, pad_hex, plate_hex, cloth_shades):
    """Shared ARMOR silhouette: torso + shoulder pads + belt + collar."""
    body = _box_obj("Body", 0.185, 0.055, 0.225, loc=(0.0, 0.0, 0.0))
    apply_toon(body, cloth_hex, shades=cloth_shades)
    for side, name in ((-1, "PadLeft"), (1, "PadRight")):
        pad = _box_obj(name, 0.072, 0.062, 0.078, loc=(side * 0.240, 0.0, 0.155))
        apply_toon(pad, pad_hex, shades=cloth_shades)
    for side, name in ((-1, "CollarLeft"), (1, "CollarRight")):
        collar = _box_obj(name, 0.026, 0.06, 0.105, loc=(side * 0.058, -0.012, 0.135),
                          rot=_tilt(side * -16))
        apply_toon(collar, trim_hex, shades=2)
    belt = _box_obj("Belt", 0.195, 0.062, 0.036, loc=(0.0, -0.006, -0.155))
    apply_toon(belt, trim_hex, shades=2)
    if plate_hex is not None:
        plate = _box_obj("ChestPlate", 0.112, 0.022, 0.125, loc=(0.0, -0.062, 0.005))
        apply_toon(plate, plate_hex, shades=3)
        buckle = _box_obj("Buckle", 0.048, 0.024, 0.042, loc=(0.0, -0.070, -0.155))
        apply_toon(buckle, GOLD, shades=2)
    return 0.74


def build_novice_vest():
    # Áo Vải Tập Sự — plain cloth, no plating at all.
    return _vest(NOVICE_CLOTH, NOVICE_TRIM, NOVICE_CLOTH, None, cloth_shades=2)


def build_genin_vest():
    # Áo Giáp Hạ Nhẫn — same cut, steel pauldrons + breastplate + gold buckle.
    return _vest(GENIN_CLOTH, GENIN_DARK, STEEL, STEEL, cloth_shades=3)


def _headband(band_hex, tail_hex, plate_hex):
    """Shared HELMET silhouette: a wide band with two tails trailing right."""
    band = _box_obj("Band", 0.300, 0.055, 0.052, loc=(-0.060, 0.0, 0.150))
    apply_toon(band, band_hex, shades=3 if plate_hex else 2)
    knot = _box_obj("Knot", 0.052, 0.062, 0.058, loc=(0.268, -0.010, 0.128))
    apply_toon(knot, tail_hex, shades=2)
    tail_long = _box_obj("TailLong", 0.030, 0.050, 0.160, loc=(0.262, -0.020, -0.055),
                         rot=_tilt(-14))
    apply_toon(tail_long, band_hex, shades=2)
    tail_short = _box_obj("TailShort", 0.026, 0.050, 0.120, loc=(0.330, -0.032, -0.010),
                          rot=_tilt(-32))
    apply_toon(tail_short, tail_hex, shades=2)
    if plate_hex is not None:
        plate = _box_obj("ForeheadPlate", 0.148, 0.030, 0.076, loc=(-0.070, -0.068, 0.150))
        apply_toon(plate, plate_hex, shades=3)
        for index, offset in enumerate((-0.196, 0.056)):
            rivet = _box_obj("Rivet%d" % index, 0.020, 0.020, 0.020,
                             loc=(offset, -0.100, 0.150))
            apply_toon(rivet, GOLD, shades=2)
    return 0.86


def build_novice_headband():
    # Băng Trán Tập Sự — cloth only, the plate is what the next tier buys.
    return _headband(NOVICE_CLOTH, NOVICE_TRIM, None)


def _outer_sect_cap(band_hex, cloth_hex, pin_hex):
    """"Mũ Ngoại Môn" silhouette: a dark brow band carrying a jade, a cloth
    crown above it, and a gold hairpin driven through the crown.

    Deliberately NOT `_headband`: that shape's whole point was the forehead
    plate, and ADR-038 removes it. A flat band with the plate taken off is just
    `novice_headband` in different colours, which at 64x64 is no icon at all.

    Two shape rules this had to learn, both from a first attempt that rendered
    as one navy blob wearing a gold stripe:

      * the crown must be LIGHTER than the band and the top brim WIDER than the
        crown. Same-tone stacked boxes merge into a single mass, and a brim that
        flares outwards is what makes the outline read "hat" instead of "box".
      * no hanging cloth tails at all. One tail on the right turned the icon
        into a pistol grip; two symmetric ones turned it into a pair of legs.
        Freestyle outlines every object separately, so a small detached shape
        never fuses into the mass it is meant to hang off.
    """
    band = _box_obj("Band", 0.268, 0.058, 0.052, loc=(0.0, 0.0, -0.176))
    apply_toon(band, band_hex, shades=2)
    jade = _gem_obj("Jade", 0.086, 0.050, 0.100, loc=(0.0, -0.080, -0.176))
    apply_toon(jade, GEM_BLUE, shades=3)

    crown = _frustum_obj("Crown", 0.208, 0.104, 0.070, 0.168, loc=(0.0, 0.0, 0.046))
    apply_toon(crown, cloth_hex, shades=3)

    pin = _box_obj("Pin", 0.238, 0.022, 0.022, loc=(0.0, -0.084, 0.062), rot=_tilt(-8))
    apply_toon(pin, pin_hex, shades=3)
    bead = _box_obj("PinBead", 0.044, 0.036, 0.044, loc=(-0.238, -0.086, 0.096))
    apply_toon(bead, pin_hex, shades=2)
    return 0.80


def build_genin_headband():
    # Mũ Ngoại Môn — cloth cap, jade brow stone, gold hairpin. No metal forehead
    # plate: that was the hitai-ate, the one unmistakably ninja object in the
    # whole icon set (ART-AUDIT-TU-TIEN §3, decision §5 #4). The jade is what
    # buys back the "this tier costs 1200 gold" signal the steel used to carry.
    return _outer_sect_cap(GENIN_DARK, GENIN_CLOTH, GOLD)


def _wraps(cloth_hex, wrap_hex, knuckle_hex):
    """Shared GLOVE silhouette: mitten + thumb + cuff, banded by wrap strips."""
    hand = _box_obj("Hand", 0.150, 0.060, 0.140, loc=(0.030, 0.0, 0.105))
    apply_toon(hand, cloth_hex, shades=3 if knuckle_hex else 2)
    thumb = _box_obj("Thumb", 0.055, 0.055, 0.085, loc=(-0.180, -0.012, 0.020), rot=_tilt(22))
    apply_toon(thumb, cloth_hex, shades=2)
    cuff = _box_obj("Cuff", 0.125, 0.065, 0.110, loc=(0.020, 0.0, -0.155))
    apply_toon(cuff, cloth_hex, shades=2)
    for index, (height, lean, width) in enumerate(
            ((0.170, -7.0, 0.165), (0.048, 6.0, 0.162), (-0.130, -5.0, 0.140))):
        strip = _box_obj("Wrap%d" % index, width, 0.070, 0.024,
                         loc=(0.025, -0.006, height), rot=_tilt(lean))
        apply_toon(strip, wrap_hex, shades=2)
    if knuckle_hex is not None:
        knuckle = _box_obj("Knuckle", 0.128, 0.074, 0.048, loc=(0.030, -0.020, 0.198))
        apply_toon(knuckle, knuckle_hex, shades=3)
        stud = _box_obj("Stud", 0.026, 0.026, 0.026, loc=(0.030, -0.098, 0.198))
        apply_toon(stud, GOLD, shades=2)
    return 0.66


def build_novice_wraps():
    # Băng Tay Tập Sự — bare cloth strips, nothing metal.
    return _wraps(NOVICE_CLOTH, NOVICE_TRIM, None)


def build_genin_wraps():
    # Găng Tay Hạ Nhẫn — same wrap, plus a steel knuckle guard and a gold stud.
    return _wraps(GENIN_CLOTH, GENIN_DARK, STEEL)


def build_novice_sandals():
    """Dép Rơm Tập Sự — a PAIR seen from above: two straw soles, X thong straps.

    Deliberately a different camera read from `genin_sandals` (side-on boot):
    two icons of the same slot that share a pose end up indistinguishable at
    64x64 no matter how the colours differ.
    """
    for side, suffix in ((-1, "Left"), (1, "Right")):
        sole = _box_obj("Sole" + suffix, 0.098, 0.030, 0.290,
                        loc=(side * 0.135, 0.030, -0.010), rot=_tilt(side * 4))
        apply_toon(sole, NOVICE_STRAW, shades=3)
        for index, lean in enumerate((24.0, -24.0)):
            strap = _box_obj("Strap%s%d" % (suffix, index), 0.092, 0.024, 0.026,
                             loc=(side * 0.135, -0.028, 0.130), rot=_tilt(lean))
            apply_toon(strap, NOVICE_TRIM, shades=2)
        thong = _box_obj("Thong" + suffix, 0.020, 0.028, 0.072,
                         loc=(side * 0.135, -0.028, 0.235))
        apply_toon(thong, NOVICE_TRIM, shades=2)
    return 0.74


def build_genin_sandals():
    """Giày Hạ Nhẫn — a shin-high boot in side profile: navy leather + steel."""
    shaft = _box_obj("Shaft", 0.085, 0.060, 0.185, loc=(-0.035, 0.0, 0.130))
    apply_toon(shaft, GENIN_CLOTH, shades=3)
    foot = _box_obj("Foot", 0.180, 0.060, 0.078, loc=(0.060, 0.0, -0.105))
    apply_toon(foot, GENIN_CLOTH, shades=3)
    sole = _box_obj("Sole", 0.200, 0.065, 0.034, loc=(0.055, 0.0, -0.180))
    apply_toon(sole, GENIN_DARK, shades=2)
    heel = _box_obj("Heel", 0.058, 0.062, 0.030, loc=(-0.110, 0.0, -0.225))
    apply_toon(heel, GENIN_DARK, shades=2)
    shin = _box_obj("ShinPlate", 0.044, 0.022, 0.150, loc=(-0.112, -0.066, 0.140))
    apply_toon(shin, STEEL, shades=3)
    cuff = _box_obj("Cuff", 0.100, 0.065, 0.036, loc=(-0.035, -0.004, 0.296))
    apply_toon(cuff, STEEL, shades=3)
    return 0.76


def build_novice_bracelet():
    """Vòng Tay Tập Sự — a closed ring of ten wooden beads, seen face-on.

    Closed ring vs. the open U of a necklace is the only silhouette cue that
    survives downscaling, so the two accessory slots never share one.
    """
    count = 10
    for index in range(count):
        angle = 2.0 * math.pi * index / count
        bead = _box_obj("Bead%02d" % index, 0.055, 0.045, 0.055,
                        loc=(0.265 * math.cos(angle), 0.0, 0.265 * math.sin(angle)),
                        rot=_tilt(math.degrees(angle)))
        apply_toon(bead, NOVICE_WOOD if index % 2 == 0 else NOVICE_STRAW, shades=2)
    return 0.78


def build_genin_bracelet():
    """Vòng Tay Hạ Nhẫn — one solid steel band, blue gem, two gold studs."""
    band = _ring_obj("Band", 0.255, 0.058)
    apply_toon(band, STEEL, shades=3)
    gem = _gem_obj("Gem", 0.080, 0.055, 0.100, loc=(0.0, -0.060, 0.262))
    apply_toon(gem, GEM_BLUE, shades=3)
    for index, angle_deg in enumerate((-45.0, 225.0)):
        angle = math.radians(angle_deg)
        stud = _box_obj("Stud%d" % index, 0.034, 0.034, 0.034,
                        loc=(0.255 * math.cos(angle), -0.055, 0.255 * math.sin(angle)),
                        rot=_tilt(angle_deg))
        apply_toon(stud, GOLD, shades=2)
    return 0.78


def _necklace_cord(link_builder):
    """Shared ACCESSORY_2 silhouette: an open U of links with a pendant below."""
    count = 11
    for index in range(count):
        x = -0.300 + 0.060 * index
        z = -0.070 + 3.1 * x * x
        link_builder(index, x, z)


def build_novice_necklace():
    """Dây Chuyền Tập Sự — leather cord of wooden beads, plain wooden pendant.

    Bead size and colour are load-bearing, not taste: the first pass used
    0.034-wide beads in the dark trim brown and every one of them came out a
    solid black lozenge — at 128px that bead is ~11px across and the 2px
    Freestyle outline meets in the middle. Anything meant to read as a filled
    shape has to stay well above ~14px on the master.
    """

    def cord_link(index, x, z):
        bead = _box_obj("Cord%02d" % index, 0.042, 0.034, 0.042, loc=(x, 0.0, z))
        apply_toon(bead, NOVICE_WOOD, shades=2)

    _necklace_cord(cord_link)
    pendant = _gem_obj("Pendant", 0.082, 0.050, 0.115, loc=(0.0, -0.040, -0.205))
    apply_toon(pendant, NOVICE_WOOD, shades=3)
    return 0.80


def build_genin_necklace():
    """Dây Chuyền Hạ Nhẫn — steel link chain, gold cap, blue gem pendant."""

    def chain_link(index, x, z):
        # A box turned 45 degrees, not an octahedron: eight facets on a 12px
        # shape means eight crease lines, and the link renders as solid ink.
        link = _box_obj("Link%02d" % index, 0.038, 0.032, 0.038, loc=(x, 0.0, z), rot=_tilt(45))
        apply_toon(link, STEEL, shades=2)

    _necklace_cord(chain_link)
    cap = _box_obj("Cap", 0.052, 0.040, 0.034, loc=(0.0, -0.040, -0.095))
    apply_toon(cap, GOLD, shades=2)
    pendant = _gem_obj("Pendant", 0.090, 0.055, 0.135, loc=(0.0, -0.045, -0.230))
    apply_toon(pendant, GEM_BLUE, shades=3)
    return 0.80


def build_steel_katana():
    """Trường Kiếm Thép — tier-2 WEAPON, a straight double-edged jian.

    The item CODE stays `steel_katana` (ADR-038 D5: business keys do not move),
    but nothing about the drawing may still say katana. Three things carried the
    old read and all three are gone (ART-AUDIT-TU-TIEN §5 #5):

      * the kink — blade leaned +9 deg, grip leaned -6 deg, so the two met at an
        angle and the eye filled in a curve. Both are dead straight now and share
        one axis.
      * the tsuba — a wide slab right under the blade. Replaced by a narrow
        crossguard bar with two quillons swept down towards the grip.
      * the ito wrap — three stacked bands down the grip. Two plain rings now.

    And one thing is added rather than removed, because taking cues away only
    gets you to "generic sword": the pommel tassel, which no katana has.
    """
    blade = _box_obj("Blade", 0.070, 0.026, 0.225, loc=(0.0, -0.050, 0.224))
    apply_toon(blade, STEEL_BRIGHT, shades=3)
    tip = _box_obj("Tip", 0.036, 0.024, 0.048, loc=(0.0, -0.050, 0.497))
    apply_toon(tip, STEEL_BRIGHT, shades=3)

    guard = _box_obj("Guard", 0.152, 0.048, 0.030, loc=(0.0, -0.050, -0.040))
    apply_toon(guard, GOLD, shades=3)
    for side in (-1, 1):
        quillon = _box_obj("Quillon%d" % side, 0.032, 0.044, 0.050,
                           loc=(side * 0.140, -0.050, -0.088), rot=_tilt(side * 26))
        apply_toon(quillon, GOLD, shades=2)

    handle = _box_obj("Handle", 0.044, 0.044, 0.120, loc=(0.0, -0.050, -0.200))
    apply_toon(handle, GENIN_DARK, shades=2)
    ring = _box_obj("Ring", 0.052, 0.048, 0.020, loc=(0.0, -0.050, -0.200))
    apply_toon(ring, GOLD, shades=2)
    pommel = _box_obj("Pommel", 0.058, 0.052, 0.032, loc=(0.0, -0.050, -0.372))
    apply_toon(pommel, GOLD, shades=2)

    knot = _box_obj("TasselKnot", 0.036, 0.038, 0.034, loc=(0.0, -0.058, -0.430))
    apply_toon(knot, TASSEL_RED, shades=2)
    fall = _box_obj("TasselFall", 0.034, 0.036, 0.082, loc=(0.056, -0.060, -0.490),
                    rot=_tilt(28))
    apply_toon(fall, TASSEL_RED, shades=3)
    return 1.18


def build_iron_scrap():
    """Sắt Vụn — a loose pile of five shards. A pile, not one object: it is the
    stackable junk drop (max_stack 99), and that has to be obvious in the bag."""
    shards = (
        ("Shard0", 0.200, 0.055, (-0.030, 0.000, -0.240), 6.0, IRON_DARK),
        ("Shard1", 0.155, 0.050, (0.110, -0.030, -0.100), -28.0, IRON),
        ("Shard2", 0.135, 0.046, (-0.135, -0.050, -0.020), 38.0, RUST),
        ("Shard3", 0.115, 0.042, (0.060, -0.070, 0.140), -14.0, IRON),
        ("Shard4", 0.088, 0.036, (-0.060, -0.090, 0.270), 52.0, IRON_DARK),
    )
    for name, half_x, half_z, loc, lean, color in shards:
        shard = _box_obj(name, half_x, 0.050, half_z, loc=loc, rot=_tilt(lean))
        apply_toon(shard, color, shades=3)
    return 0.74


def build_chakra_shard():
    """Mảnh Chakra — one tall cyan crystal with a bright core and two chips.
    The only cool-cyan icon in the set, so it never reads as `iron_scrap`."""
    crystal = _gem_obj("Crystal", 0.155, 0.085, 0.360, loc=(0.0, 0.0, 0.020), rot=_tilt(7))
    apply_toon(crystal, CHAKRA, shades=3)
    core = _gem_obj("Core", 0.068, 0.050, 0.170, loc=(0.0, -0.078, 0.020), rot=_tilt(7))
    apply_toon(core, CHAKRA_CORE, shades=2)
    chip_left = _gem_obj("ChipLeft", 0.062, 0.042, 0.118, loc=(-0.238, -0.040, -0.190),
                         rot=_tilt(-26))
    apply_toon(chip_left, CHAKRA, shades=3)
    chip_right = _gem_obj("ChipRight", 0.052, 0.040, 0.092, loc=(0.235, -0.040, -0.130),
                          rot=_tilt(21))
    apply_toon(chip_right, CHAKRA, shades=3)
    return 0.84


def build_ancient_jade_shard():
    """Mảnh Ngọc Cổ — a broken piece of a jade bi disc (V30, quest 3 reward).

    Three things it must NOT be confused with, which is what every shape choice
    below is for:

      * `chakra_shard` is the other "shard" in the bag and is a tall cyan
        octahedron. This one is a FLAT PLATE in warm green — different form,
        different temperature, so the two never trade places at 64x64.
      * `iron_scrap` is a loose pile. This is ONE object with one chip: it is
        `max_stack = 1`, a unique relic, and the bag should say so.
      * A generic gem. The gold arc of the bi disc's bore is the single cue that
        turns "green rock" into "piece of something old and made" — which is what
        the item is in the story (STORY.md §6, the arc-1 McGuffin).

    Shape: a real slice of the ring, cut by `_arc_band` — two curved edges and
    two straight radial breaks. The first attempt was a tapered plate with a
    whole gold torus beside it and read as a bell hanging off a keyring; the
    fragment has to BE part of a circle, not stand next to one.
    """
    span = (-62.0, 92.0)
    body = _prism_obj("Shard", _arc_band(0.440, 0.190, *span), 0.055,
                      loc=(-0.150, 0.000, -0.090), rot=_tilt(12))
    apply_toon(body, JADE, shades=3)

    # Gold on the bore edge only — the polished inside of a made disc, and the
    # one cue that stops this reading as a plain green rock. Sits proud of the
    # jade towards the camera so the cel ramp never merges the two.
    bore = _prism_obj("Bore", _arc_band(0.255, 0.190, span[0] + 3.0, span[1] - 3.0),
                      0.030, loc=(-0.150, -0.032, -0.090), rot=_tilt(12))
    apply_toon(bore, GOLD, shades=2)

    # Highlight on the top of the arc only, not a full concentric band: a band
    # would be a third ring and turn the icon into a target, while a short bright
    # wedge just reads as light catching the stone.
    sheen = _prism_obj("Sheen", _arc_band(0.400, 0.270, 34.0, 88.0), 0.026,
                       loc=(-0.150, -0.048, -0.090), rot=_tilt(12))
    apply_toon(sheen, JADE_PALE, shades=2)

    # The one loose chip, touching the lower break so it reads as "just came off
    # this", not as a second item in the slot (`max_stack = 1`).
    chip = _gem_obj("Chip", 0.088, 0.050, 0.110, loc=(0.243, -0.055, -0.400),
                    rot=_tilt(28))
    apply_toon(chip, JADE_DARK, shades=3)
    return 1.02


def build_minor_healing_pill():
    """Tiểu Hồi Nguyên Đan — ba viên đan tròn, một to hai nhỏ (V36, ADR-046 D5).

    Ba thứ quyết định hình dạng, tất cả đều là "đừng lẫn với món khác trong túi":

      * **TRÒN.** Mọi icon đang có đều góc cạnh: hộp, tháp cụt, bát diện, tấm dẹt.
        Một cụm hình tròn là bố cục sáng-tối khác hẳn, và đó là thứ đọc được ở 50px
        chứ không phải màu (bài học `mob_trial-construct` 2026-09-06).
      * **BA viên, không phải một.** `max_stack = 20` — món này xếp chồng, và ô túi
        phải nói ra điều đó, cùng lý do `iron_scrap` là một đống chứ không phải một
        mảnh. Nhưng khác `iron_scrap` ở chỗ ba viên ĐỀU NHAU và tròn: đống sắt vụn
        là phế liệu, đan dược là thứ được luyện ra.
      * **KHÔNG có bình/hồ lô.** Hồ lô là biểu tượng tu tiên mạnh hơn, nhưng tên món
        là "Đan" — vẽ cái bình là vẽ vật chứa chứ không phải vật phẩm, và người chơi
        sẽ hỏi vì sao uống một cái bình. Để dành hồ lô cho món "bình đan" của arc 2.

    Viên lớn nghiêng nhẹ và có vệt sáng lệch lên trái để không đọc ra "nút bấm phẳng".
    """
    main = _prism_obj("Pill", _disc(0.300), 0.140, loc=(0.000, 0.000, 0.105), rot=_tilt(-8))
    apply_toon(main, PILL, shades=3)

    # Vệt sáng NHỎ và LỆCH hẳn lên trái. Bản đầu để nó to gần bằng viên và gần tâm ⇒ đọc ra một
    # cái lỗ trên viên đan, không phải ánh sáng phản chiếu.
    sheen = _prism_obj("Sheen", _disc(0.082), 0.040, loc=(-0.108, -0.160, 0.205), rot=_tilt(-8))
    apply_toon(sheen, PILL_SHEEN, shades=2)

    # Hai viên sau phải TÁCH RỜI silhouette của viên chính, không chỉ "ở phía sau". Bản đầu đặt
    # chúng chạm vào viên to: Freestyle vẽ viền theo OBJECT nên ba đường viền dính lại thành MỘT
    # khối, và ở 64x64 cả cụm đọc ra một con bọ có hai chân. Khoảng cách tâm phải LỚN HƠN tổng hai
    # bán kính — đó là điều kiện để mắt còn thấy ba vật thể chứ không phải một.
    back_left = _prism_obj("PillLeft", _disc(0.170), 0.110, loc=(-0.372, 0.130, -0.300),
                           rot=_tilt(16))
    apply_toon(back_left, PILL_DARK, shades=3)
    back_right = _prism_obj("PillRight", _disc(0.190), 0.110, loc=(0.378, 0.100, -0.278),
                            rot=_tilt(-21))
    apply_toon(back_right, PILL_DARK, shades=3)
    return 1.22


# --- V45: bậc "Ngoại Môn" (required_level 15) --------------------------------
#
# Bậc THỨ BA của bộ icon, và nó phải đọc ra khác cả hai bậc cũ ở CHẤT LIỆU chứ không chỉ ở màu:
# "Tập Sự" là vải/rơm/gỗ tông ấm xỉn, "Hạ Nhẫn" là vải chàm + thép + vàng. Bậc này là NGỌC + VÀNG,
# không thép — kim loại đổi hẳn, nên ở 64x64 nó không bao giờ bị đọc nhầm thành bậc Hạ Nhẫn đổi màu.
# Ba màu ngọc JADE/JADE_PALE/JADE_DARK đã có sẵn (dùng cho `ancient_jade_shard`), không chế màu mới.


def build_outer_sect_robe():
    # Ngoại Môn Đạo Bào — cùng silhouette `_vest`, nhưng yếm ngọc thay yếm thép.
    return _vest(JADE, JADE_DARK, JADE_PALE, JADE_PALE, cloth_shades=3)


def build_outer_sect_cap():
    # Ngoại Môn Quan — dùng lại đúng `_outer_sect_cap` đã dựng cho `genin_headband` (ADR-038 Đợt 3),
    # đổi sang thân ngọc + trâm vàng. Hàm đó vốn sinh ra để thay băng trán ninja, nên tên nó khớp
    # bậc này còn đúng hơn chỗ nó đang được dùng.
    return _outer_sect_cap(JADE_DARK, JADE, GOLD)


def build_outer_sect_gloves():
    # Ngoại Môn Hộ Thủ — `_wraps` với khớp NGỌC (bậc Hạ Nhẫn là thép), đinh vàng do helper tự thêm.
    return _wraps(JADE, JADE_DARK, JADE_PALE)


def build_outer_sect_boots():
    """Ngoại Môn Vân Ngoa — ủng mây nhìn nghiêng, dựng bằng MỘT khối liền.

    ⚠️ Hai vòng B mới ra được hình này, và cả hai cái sai đều đã có sẵn lời cảnh báo trong file.

    **Vòng B #1** vẽ ủng nhìn CHÍNH DIỆN để lấy "tư thế thứ ba" khác `novice_sandals` (đôi, nhìn từ
    trên) và `genin_sandals` (một chiếc, nhìn nghiêng). Ra một cái THÁP đối xứng — đọc ra cái đèn.
    Thứ làm một chiếc giày đọc được LÀ bất đối xứng mũi-gót; nhìn chính diện thì bỏ đi đúng cái đó.

    **Vòng B #2** quay lại nhìn nghiêng nhưng ghép mũi hếch bằng HAI hộp nhỏ xoay. Ra một cái CO
    ỐNG NƯỚC: mũi tách hẳn thành một mảnh vàng rời, và ống với bàn chân cùng một tông `JADE` gặp
    nhau ở góc vuông nên đọc thành mối nối ống. `_outer_sect_cap` đã ghi sẵn luật này
    ("Freestyle outlines every object separately, so a small detached shape never fuses into the
    mass it is meant to hang off") — đọc rồi vẫn sập.

    Bản này sửa đúng hai nguyên nhân đó:
      * **MỘT khối liền** (`_prism_obj`) ôm trọn đường viền ủng — gót, ống, cổ, mu bàn chân và mũi
        HẾCH nằm trong cùng một profile, nên mũi cong là một phần của hình chứ không dính vào hình.
      * **Tách ống khỏi bàn chân bằng SẮC ĐỘ**, không bằng đường: ống sáng (`JADE_PALE`) trên bàn
        chân sẫm hơn, nên chỗ gập đọc ra cổ chân chứ không ra khớp nối.
    """
    # Profile (x, z) theo chiều NGƯỢC kim đồng hồ như camera nhìn — xem _prism_obj.
    # Đi: gót sau → lên lưng ống → cổ ống → xuống mặt trước ống → mu bàn chân → mũi hếch cong lên
    #     → xuống đầu mũi → dọc đế về gót.
    profile = [
        (-0.150, -0.150),   # gót, sát đế
        (-0.150,  0.150),   # lưng ống
        (-0.128,  0.318),   # vai cổ ống
        (-0.020,  0.318),   # mép cổ trước
        (-0.004,  0.120),   # mặt trước ống thu vào
        ( 0.060, -0.020),   # mu bàn chân
        ( 0.196,  0.004),   # mũi bắt đầu hếch
        ( 0.262,  0.092),   # đỉnh mũi cong (vân ngoa)
        ( 0.300,  0.052),   # mép ngoài mũi
        ( 0.236, -0.064),   # mũi đổ xuống
        ( 0.214, -0.150),   # đầu đế trước
    ]
    body = _prism_obj("Boot", profile, 0.058, loc=(-0.030, 0.0, 0.0))
    apply_toon(body, JADE, shades=3)

    # Ống sáng hơn bàn chân — đây là thứ làm chỗ gập đọc ra cổ chân (vòng B #2 thiếu đúng cái này).
    shaft = _box_obj("ShaftFace", 0.064, 0.030, 0.150, loc=(-0.106, -0.062, 0.170))
    apply_toon(shaft, JADE_PALE, shades=3)

    cuff = _box_obj("CuffBand", 0.084, 0.064, 0.030, loc=(-0.104, -0.006, 0.300))
    apply_toon(cuff, GOLD, shades=2)

    sole = _box_obj("Sole", 0.196, 0.062, 0.030, loc=(0.010, 0.0, -0.166))
    apply_toon(sole, JADE_DARK, shades=2)
    return 0.80


def build_outer_sect_talisman():
    """Ngoại Môn Hộ Thân Phù — thẻ ngọc TREO trên dây, buông thẳng xuống.

    ⚠️ KHÔNG được là một vòng tròn. Cả hai món ACCESSORY_1 đang có (`novice_bracelet`,
    `genin_bracelet`) đều là VÒNG KÍN nhìn chính diện, và chính `build_novice_bracelet` ghi rằng
    vòng-kín-đối-lại-chữ-U-của-dây-chuyền là tín hiệu DUY NHẤT sống sót khi thu nhỏ. Một vòng ngọc
    thứ ba sẽ đọc y hệt vòng thép thứ hai.

    Nên hình ở đây là trục DỌC: nút dây trên cùng, dải tua ngắn, rồi một thẻ ngọc chữ nhật khắc. Nó
    cũng không đụng chữ U của hai dây chuyền — chúng cong ngang, cái này buông thẳng.
    """
    knot = _box_obj("Knot", 0.062, 0.048, 0.052, loc=(0.0, 0.0, 0.330))
    apply_toon(knot, GOLD, shades=2)
    for index, side in enumerate((-1, 1)):
        cord = _box_obj("Cord%d" % index, 0.022, 0.030, 0.120,
                        loc=(side * 0.052, 0.0, 0.222), rot=_tilt(side * -12))
        apply_toon(cord, TASSEL_RED, shades=2)
    tablet = _box_obj("Tablet", 0.150, 0.048, 0.208, loc=(0.0, 0.0, -0.040))
    apply_toon(tablet, JADE, shades=3)
    bevel = _box_obj("Bevel", 0.116, 0.030, 0.170, loc=(0.0, -0.054, -0.040))
    apply_toon(bevel, JADE_PALE, shades=3)
    for index, height in enumerate((0.046, -0.040, -0.126)):
        glyph = _box_obj("Glyph%d" % index, 0.070, 0.020, 0.018, loc=(0.0, -0.086, height))
        apply_toon(glyph, JADE_DARK, shades=2)
    fall = _box_obj("TasselFall", 0.030, 0.034, 0.078, loc=(0.0, -0.010, -0.290))
    apply_toon(fall, TASSEL_RED, shades=3)
    return 0.92


def build_outer_sect_blade():
    """Ngoại Môn Kiếm — vẫn là kiếm thẳng (jian), nhưng KHÔNG được đọc ra `steel_katana` đổi màu.

    Cùng ô WEAPON nên cùng trục dọc; ba thứ tách nó ra: lưỡi HẸP hơn và dài hơn, hộ thủ là một đĩa
    NGỌC tròn thay thanh ngang + hai quillon, và tua kiếm dài gấp đôi (hai đoạn thay một).
    """
    # ⚠️ VÒNG B #1 (2026-09-13). Bản đầu để lưỡi HẸP hơn `steel_katana` (0.054 so với 0.070) và
    # ĐỒNG THỜI kéo ortho_scale ra xa hơn (1.24 so với 1.18). Hai thứ đó cộng lại làm viền Freestyle
    # 2.0px ăn gần hết bề ngang lưỡi ⇒ render ra một cái ỐNG ĐEN, không ra kiếm ngọc. Đây đúng bài
    # học `icon_genin_headband`: hình MẢNH không sống nổi qua viền ở cỡ icon.
    #
    # Bản này rộng hơn cả katana (0.078) và kéo gần lại (1.10). Và BỎ HẲN sống kiếm ở giữa: một
    # khối mảnh lồng trong khối mảnh chỉ thêm một đường viền đen thứ hai dọc thân, đúng thứ đang
    # làm lưỡi tối đi.
    blade = _box_obj("Blade", 0.078, 0.026, 0.258, loc=(0.0, -0.050, 0.232))
    apply_toon(blade, JADE_PALE, shades=3)
    tip = _box_obj("Tip", 0.042, 0.024, 0.054, loc=(0.0, -0.050, 0.534))
    apply_toon(tip, JADE_PALE, shades=3)

    disc = _gem_obj("GuardDisc", 0.128, 0.044, 0.070, loc=(0.0, -0.050, -0.046))
    apply_toon(disc, JADE, shades=3)
    hub = _box_obj("GuardHub", 0.052, 0.048, 0.030, loc=(0.0, -0.050, -0.046))
    apply_toon(hub, GOLD, shades=2)

    handle = _box_obj("Handle", 0.042, 0.042, 0.126, loc=(0.0, -0.050, -0.208))
    apply_toon(handle, JADE_DARK, shades=2)
    for index, height in enumerate((-0.150, -0.266)):
        ring = _box_obj("Ring%d" % index, 0.050, 0.046, 0.018, loc=(0.0, -0.050, height))
        apply_toon(ring, GOLD, shades=2)
    pommel = _gem_obj("Pommel", 0.058, 0.050, 0.040, loc=(0.0, -0.050, -0.352))
    apply_toon(pommel, GOLD, shades=2)

    knot = _box_obj("TasselKnot", 0.034, 0.036, 0.032, loc=(0.0, -0.058, -0.410))
    apply_toon(knot, TASSEL_RED, shades=2)
    upper = _box_obj("TasselUpper", 0.032, 0.034, 0.076, loc=(0.030, -0.060, -0.470),
                     rot=_tilt(16))
    apply_toon(upper, TASSEL_RED, shades=3)
    lower = _box_obj("TasselLower", 0.028, 0.032, 0.070, loc=(0.074, -0.062, -0.536),
                    rot=_tilt(30))
    apply_toon(lower, TASSEL_RED, shades=3)
    return 1.10


# --- V48: bậc "Trúc Cơ" (required_level 30) ----------------------------------
#
# Bậc THỨ TƯ, và là bậc nguy hiểm nhất từ trước tới nay: nó rơi vào ĐÚNG sáu ô của bậc Ngoại Môn
# với ĐÚNG sáu danh từ tiếng Việt (Kiếm · Đạo Bào · Quan · Hộ Thủ · Vân Ngoa · Hộ Thân Phù). Cùng
# một VẬT, cao hơn một bậc. Đường dễ nhất — tô lại bộ cũ bằng màu khác — là đúng cái sai mà
# ASSET-LOG đã ghi hai lần: ở 50–64px mắt đọc BỐ CỤC SÁNG-TỐI trước, màu sau, nên hai asset cùng
# dòng dõi phải ĐẢO bố cục, không nhích màu.
#
#     Ngoại Môn : khối chính TRUNG TÍNH (ngọc), chi tiết SÁNG hơn (JADE_PALE), viền tối
#     Trúc Cơ   : khối chính SÁNG GẦN TRẮNG (bạch ngọc), chi tiết TỐI (huyền thiết) + đỏ + vàng
#
# Chất liệu đổi hẳn lần thứ tư, đúng khuôn ba lần trước: vải/rơm/gỗ → chàm + THÉP SÁNG + vàng →
# ngọc + vàng, không thép → bạch ngọc + HUYỀN THIẾT TỐI. Kim loại quay lại nhưng ở đầu kia của trục
# sắc độ so với bậc Hạ Nhẫn, nên "có kim loại" không làm hai bậc lẫn nhau.
#
# ⚠️ Một ngoại lệ ĐƯỢC GHI NHẬN, không phải bỏ sót: `foundation_blade` KHÔNG đảo sắc độ ở lưỡi
# kiếm. Lưỡi tối là đúng cái đã làm `outer_sect_blade` vòng B #1 render ra "một cái ỐNG ĐEN". Luật
# hình mảnh không sống nổi qua viền Freestyle thắng luật đảo bố cục ở chỗ này, nên khác biệt của
# thanh kiếm bậc bốn nằm hết ở HỘ THỦ (lưỡi liềm thay đĩa) và ở chuôi/tua.


def build_foundation_robe():
    # Trúc Cơ Đạo Bào — cùng silhouette `_vest` như cả ba bậc trước, đảo sắc độ: thân trắng ngà,
    # vai + cổ + đai huyền thiết, yếm đỏ. Bậc Ngoại Môn là thân ngọc trung tính với yếm SÁNG hơn;
    # ở đây thân sáng nhất còn mọi chi tiết đều tối hơn nó. Yếm đỏ (không phải vàng) vì `_vest` tự
    # gắn khoá đai VÀNG ngay dưới yếm — hai khối vàng chạm nhau sẽ dính thành một hình lỗ khoá.
    return _vest(BONE, ONYX, ONYX, TASSEL_RED, cloth_shades=3)


def build_foundation_gloves():
    # Trúc Cơ Hộ Thủ — `_wraps` với bàn tay trắng ngà, dải quấn huyền thiết, tấm khớp ngón ĐỎ.
    # Không dùng vàng cho tấm khớp vì `_wraps` tự gắn chốt vàng lên chính tấm đó (cùng màu ⇒ chốt
    # biến mất), đúng lý do bậc Ngoại Môn để tấm `JADE_PALE` chứ không để vàng.
    return _wraps(BONE, ONYX, TASSEL_RED)


def build_foundation_cap():
    """Trúc Cơ Quan — mũ HAI TẦNG: vành trắng rộng, thân huyền thiết, chóp trắng nhỏ.

    KHÔNG tái dùng `_outer_sect_cap`. Lý do nằm nguyên văn trong docstring hàm đó, viết cho tình
    huống y hệt: bỏ tấm kim loại khỏi `_headband` chỉ ra `novice_headband` đổi màu, "ở 64x64 là
    không còn icon". Gọi `_outer_sect_cap` với ba màu mới thì ra `outer_sect_cap` đổi màu.

    ⚠️ **VÒNG B #1 (2026-09-18) — ra một cái THÙNG.** Bản đầu đảo luôn silhouette: chóp cụt bậc ba
    thu vào trên (hình A), nên bản này loe ra trên (hình V), thân tối, đai trắng nằm dưới. Ở 64px
    nó đọc ra thùng rác có que xuyên qua. Nguyên nhân KHÔNG phải màu — nó phá đúng luật đã ghi sẵn
    trong `_outer_sect_cap`: *"the top brim WIDER than the crown... a brim that flares outwards is
    what makes the outline read 'hat' instead of 'box'"*. Mũ loe lên trên thì phần rộng nhất nằm ở
    ĐỈNH, và một hình rộng ở đỉnh hẹp ở đáy là cái hộp, không phải cái mũ.

    ⇒ Bài học: luật đảo bố cục sáng-tối KHÔNG được phép đổi lấy một luật hình. Bản này giữ nguyên
    quan hệ vành-rộng-hơn-thân, và dồn toàn bộ khác biệt vào hai chỗ khác:

      * **Sắc độ đảo** — bậc ba là đai TỐI đỡ thân SÁNG; ở đây vành SÁNG đỡ thân TỐI. Luật thật sự
        cần giữ là hai khối xếp chồng phải chênh sắc độ (nếu không dính thành một cục); luật đó nói
        về độ CHÊNH, không nói chiều.
      * **Hai tầng thay một** — thêm tầng chóp trắng nhỏ trên đỉnh thân. Bậc ba chỉ có MỘT khối
        chóp cụt, nên đường viền ba bậc (rộng → hẹp → nhỏ) phân biệt được kể cả khi bỏ hết màu.

    Vẫn KHÔNG có đuôi khăn buông: Freestyle vẽ viền riêng từng object nên mảnh nhỏ không bao giờ
    hoà vào khối nó định bám — một đuôi ra báng súng, hai đuôi ra hai cái chân.
    """
    # Vành là khối RỘNG NHẤT của cả hình. Đây là điều kiện để đọc ra mũ, không phải tuỳ chọn.
    brim = _box_obj("Brim", 0.290, 0.066, 0.046, loc=(0.0, 0.0, -0.212))
    apply_toon(brim, BONE, shades=3)

    # Viên đá đỏ thay viên ngọc lam của bậc ba: `GEM_BLUE` đang là đá của cả `genin_bracelet` lẫn
    # `genin_necklace`, nên dùng lại nó ở đây là kéo bậc bốn về phía bậc hai.
    stone = _gem_obj("Stone", 0.072, 0.048, 0.086, loc=(0.0, -0.090, -0.212))
    apply_toon(stone, TASSEL_RED, shades=3)

    crown = _frustum_obj("Crown", 0.212, 0.150, 0.070, 0.140, loc=(0.0, 0.0, -0.030))
    apply_toon(crown, ONYX, shades=3)

    # Tầng chóp loe nhẹ so với đỉnh thân (0.166 > 0.150) — đủ để mắt bắt ra một BẬC, không đủ để
    # phần rộng nhất chuyển lên đỉnh và làm hình lộn ngược trở lại thành cái hộp.
    top = _frustum_obj("TopTier", 0.166, 0.132, 0.062, 0.088, loc=(0.0, 0.0, 0.196))
    apply_toon(top, BONE, shades=3)

    pin = _box_obj("Pin", 0.268, 0.022, 0.024, loc=(0.0, -0.086, 0.052), rot=_tilt(-8))
    apply_toon(pin, GOLD, shades=3)
    bead = _box_obj("PinBead", 0.044, 0.036, 0.046, loc=(-0.270, -0.088, 0.090))
    apply_toon(bead, GOLD, shades=2)
    return 0.92


def build_foundation_boots():
    """Trúc Cơ Vân Ngoa — vẫn MỘT khối liền nhìn nghiêng, ống cao hơn và vân mũi lớn hơn.

    Ba tư thế đã bị loại bằng render thật, ghi ở `build_outer_sect_boots`: nhìn chính diện ra cái
    ĐÈN, mũi hếch ghép rời ra cái CO ỐNG NƯỚC. Nên tư thế nghiêng-một-khối-liền là thứ DUY NHẤT đã
    chứng minh được, và bậc này không đi tìm tư thế thứ tư — nó đổi TỶ LỆ (ống 0.392 so với 0.318)
    và đảo sắc độ.

    Đảo so với bậc ba: ở đó thân ngọc trung tính, ống SÁNG hơn thân. Ở đây thân trắng ngà và ống
    TỐI hơn thân. Luật thật sự cần giữ là "tách ống khỏi bàn chân bằng SẮC ĐỘ, không bằng đường" —
    luật đó nói về độ CHÊNH, không nói chiều, nên đảo chiều vẫn thoả.
    """
    profile = [
        (-0.166, -0.160),   # gót, sát đế
        (-0.166,  0.190),   # lưng ống
        (-0.140,  0.392),   # vai cổ ống — cao hơn bậc ba 0.074
        (-0.018,  0.392),   # mép cổ trước
        (-0.002,  0.150),   # mặt trước ống thu vào
        ( 0.066, -0.026),   # mu bàn chân
        ( 0.198, -0.004),   # mũi bắt đầu hếch
        ( 0.286,  0.116),   # đỉnh vân mũi
        ( 0.336,  0.056),   # mép ngoài mũi
        ( 0.252, -0.074),   # mũi đổ xuống
        ( 0.228, -0.160),   # đầu đế trước
    ]
    body = _prism_obj("Boot", profile, 0.058, loc=(-0.030, 0.0, -0.030))
    apply_toon(body, BONE, shades=3)

    # ⚠️ VÒNG B #1 (2026-09-18): tấm ống cao tới 0.360 và nằm ở y=-0.092, tức TRƯỚC đai vàng
    # (mặt trước đai ở y=-0.072) ⇒ đai bị che, đỉnh ủng render ra một mảng tối phẳng và mất hẳn
    # điểm vàng. Hai số phải đi cùng nhau: tấm ống dừng DƯỚI đai, và đai đẩy ra TRƯỚC tấm ống.
    shaft = _box_obj("ShaftFace", 0.066, 0.030, 0.150, loc=(-0.112, -0.062, 0.176))
    apply_toon(shaft, ONYX, shades=3)

    cuff = _box_obj("CuffBand", 0.078, 0.066, 0.034, loc=(-0.082, -0.044, 0.356))
    apply_toon(cuff, GOLD, shades=2)

    sole = _box_obj("Sole", 0.210, 0.064, 0.032, loc=(0.014, 0.0, -0.206))
    apply_toon(sole, ONYX, shades=2)
    return 0.98


def build_foundation_talisman():
    """Trúc Cơ Hộ Thân Phù — thẻ HÌNH THOI tối, đai vàng ngang, lõi sáng.

    Ô ACCESSORY_1 có sẵn hai ràng buộc đã ghi và cả hai vẫn hiệu lực:
      * KHÔNG được là vòng kín — `novice_bracelet` và `genin_bracelet` đã chiếm hình đó, và
        `build_novice_bracelet` ghi rằng vòng-kín-đối-lại-chữ-U-của-dây-chuyền là tín hiệu DUY NHẤT
        sống sót khi thu nhỏ.
      * KHÔNG được là thẻ chữ nhật treo dây — `outer_sect_talisman` là hình đó.

    Nên hình ở đây vẫn trục dọc (không đụng chữ U ngang của hai necklace) nhưng thân là khối bát
    diện: viền THOI đối lại viền CHỮ NHẬT, phân biệt được ngay cả khi bỏ hết màu.

    Và đây là món đảo bố cục rõ nhất cả bộ: bậc ba là một vật SÁNG (thẻ ngọc + vát `JADE_PALE`) với
    vài nét tối; bậc bốn là một vật TỐI với vài nét sáng. KHÔNG dùng lại giấy bùa — `icon_
    enhance_protection_charm` đã là bùa giấy kem có ấn đỏ, thêm một lá bùa giấy nữa là hai icon
    tranh nhau cùng một hình.
    """
    cap = _box_obj("Cap", 0.058, 0.046, 0.048, loc=(0.0, 0.0, 0.348))
    apply_toon(cap, GOLD, shades=2)
    for index, side in enumerate((-1, 1)):
        cord = _box_obj("Cord%d" % index, 0.022, 0.030, 0.108,
                        loc=(side * 0.050, 0.0, 0.248), rot=_tilt(side * -12))
        apply_toon(cord, TASSEL_RED, shades=2)

    plaque = _gem_obj("Plaque", 0.196, 0.052, 0.252, loc=(0.0, 0.0, -0.036))
    apply_toon(plaque, ONYX, shades=3)

    # Đai vàng chạy ngang bụng thoi, lõi sáng đè lên giữa đai — hai cánh vàng còn thò ra hai bên là
    # thứ giữ cho lõi đọc ra "khảm vào" chứ không ra "dán lên".
    waist = _box_obj("Waist", 0.150, 0.030, 0.030, loc=(0.0, -0.060, -0.036))
    apply_toon(waist, GOLD, shades=2)
    # ⚠️ VÒNG B #1 (2026-09-18): lõi 0.064x0.084 biến mất hẳn ở 64px — bộ ba sắc độ (tối/vàng/
    # sáng) tụt còn hai và thẻ đọc ra một cái diều đen trơn. Phóng lên 0.088x0.116, vẫn nhỏ hơn
    # đai vàng 0.150 nên hai cánh vàng còn thò ra hai bên và lõi vẫn đọc ra "khảm vào".
    core = _gem_obj("Core", 0.088, 0.030, 0.116, loc=(0.0, -0.076, -0.036))
    apply_toon(core, BONE, shades=3)

    fall = _box_obj("TasselFall", 0.032, 0.034, 0.082, loc=(0.0, -0.010, -0.336))
    apply_toon(fall, TASSEL_RED, shades=3)
    return 1.00


def build_foundation_blade():
    """Trúc Cơ Kiếm — lưỡi THON một khối liền + hộ thủ LƯỠI LIỀM.

    Ba thanh kiếm đứng cùng ô WEAPON nên phải tách được bằng đúng hai thứ mắt bắt trước ở 64px:
    đường viền và chỗ rộng nhất.

        wooden_sword      : thanh gỗ, hộ thủ mảnh
        steel_katana      : lưỡi thẳng bản đều, thanh hộ thủ ngang + 2 quillon
        outer_sect_blade  : lưỡi bản đều, hộ thủ ĐĨA tròn ngọc
        foundation_blade  : lưỡi THON (rộng ở gốc, hẹp dần lên mũi), hộ thủ LƯỠI LIỀM chúc xuống

    ⚠️ Lưỡi vẫn SÁNG, cố ý không đảo. `outer_sect_blade` vòng B #1 đã trả giá đúng chỗ này: lưỡi
    hẹp + camera xa làm viền Freestyle ăn gần hết bề ngang và render ra một cái ỐNG ĐEN. Lưỡi tối
    là cùng một lỗi đi bằng cửa khác. Nên bản này còn rộng hơn bậc ba ở gốc (0.094 so với 0.078) và
    kéo camera GẦN hơn (1.14 so với 1.10); chỗ đảo bố cục dồn hết vào chuôi huyền thiết.

    Hộ thủ là MỘT khối `_prism_obj`, không phải hai cánh rời: hai mảnh rời sẽ tách khỏi lưỡi đúng
    như mũi ủng bậc ba từng tách ra thành mảnh vàng lơ lửng.
    """
    blade_profile = [
        (-0.094, -0.210),   # gốc lưỡi, bản rộng nhất
        (-0.090,  0.140),
        (-0.062,  0.286),   # vai lưỡi thu vào
        (-0.028,  0.362),   # mũi CÙN chứ không nhọn: một đỉnh nhọn bị viền 2px nuốt sạch
        ( 0.028,  0.362),
        ( 0.062,  0.286),
        ( 0.090,  0.140),
        ( 0.094, -0.210),
    ]
    blade = _prism_obj("Blade", blade_profile, 0.026, loc=(0.0, -0.050, 0.130))
    apply_toon(blade, BONE, shades=3)

    guard_profile = [
        (-0.232, -0.086),   # đầu cánh trái, chúc xuống
        (-0.152,  0.034),
        (-0.054,  0.062),
        ( 0.054,  0.062),
        ( 0.152,  0.034),
        ( 0.232, -0.086),
        ( 0.180, -0.120),   # cạnh dưới vòng ngược về
        ( 0.062, -0.030),
        (-0.062, -0.030),
        (-0.180, -0.120),
    ]
    guard = _prism_obj("Guard", guard_profile, 0.044, loc=(0.0, -0.050, -0.118))
    apply_toon(guard, GOLD, shades=3)

    grip = _box_obj("Grip", 0.046, 0.044, 0.136, loc=(0.0, -0.050, -0.296))
    apply_toon(grip, ONYX, shades=3)
    for index, height in enumerate((-0.232, -0.362)):
        ring = _box_obj("Ring%d" % index, 0.054, 0.048, 0.018, loc=(0.0, -0.050, height))
        apply_toon(ring, GOLD, shades=2)
    pommel = _gem_obj("Pommel", 0.062, 0.052, 0.044, loc=(0.0, -0.050, -0.448))
    apply_toon(pommel, GOLD, shades=2)

    # Tua kiếm GIỮ — ASSET-LOG 2026-09-03 ghi rằng bỏ bớt tín hiệu chỉ đưa về "kiếm chung chung",
    # phải THÊM một tín hiệu dương mới đọc ra kiếm Trung Hoa. Bỏ nó ở bậc bốn là đi lùi.
    knot = _box_obj("TasselKnot", 0.034, 0.036, 0.032, loc=(0.0, -0.058, -0.506))
    apply_toon(knot, TASSEL_RED, shades=2)
    upper = _box_obj("TasselUpper", 0.032, 0.034, 0.076, loc=(-0.030, -0.060, -0.566),
                     rot=_tilt(-16))
    apply_toon(upper, TASSEL_RED, shades=3)
    lower = _box_obj("TasselLower", 0.028, 0.032, 0.070, loc=(-0.074, -0.062, -0.632),
                     rot=_tilt(-30))
    apply_toon(lower, TASSEL_RED, shades=3)
    return 1.14


ITEMS = {
    "kunai": build_kunai,
    "wooden_sword": build_wooden_sword,
    "enhance_protection_charm": build_enhance_protection_charm,
    # V21 — bậc "Tập Sự"
    "novice_vest": build_novice_vest,
    "novice_headband": build_novice_headband,
    "novice_wraps": build_novice_wraps,
    "novice_sandals": build_novice_sandals,
    "novice_bracelet": build_novice_bracelet,
    "novice_necklace": build_novice_necklace,
    # V21 — bậc "Hạ Nhẫn"
    "steel_katana": build_steel_katana,
    "genin_vest": build_genin_vest,
    "genin_headband": build_genin_headband,
    "genin_wraps": build_genin_wraps,
    "genin_sandals": build_genin_sandals,
    "genin_bracelet": build_genin_bracelet,
    "genin_necklace": build_genin_necklace,
    # V21 — nguyên liệu (equip_slot NULL)
    "iron_scrap": build_iron_scrap,
    "chakra_shard": build_chakra_shard,
    # V30 — phần thưởng nhiệm vụ 3 (không bán, không rơi, max_stack 1)
    "ancient_jade_shard": build_ancient_jade_shard,
    # V36 — đan dược hồi máu (ADR-046 D5), món DÙNG ĐƯỢC đầu tiên của game
    "minor_healing_pill": build_minor_healing_pill,
    # V45 — bậc "Ngoại Môn" (required_level 15), phần thưởng quest 11. Ngọc + vàng, không thép.
    "outer_sect_blade": build_outer_sect_blade,
    "outer_sect_robe": build_outer_sect_robe,
    "outer_sect_cap": build_outer_sect_cap,
    "outer_sect_gloves": build_outer_sect_gloves,
    "outer_sect_boots": build_outer_sect_boots,
    "outer_sect_talisman": build_outer_sect_talisman,
    # V48 — bậc "Trúc Cơ" (required_level 30), phần thưởng quest 19/20. Bạch ngọc + huyền thiết.
    "foundation_blade": build_foundation_blade,
    "foundation_robe": build_foundation_robe,
    "foundation_cap": build_foundation_cap,
    "foundation_gloves": build_foundation_gloves,
    "foundation_boots": build_foundation_boots,
    "foundation_talisman": build_foundation_talisman,
}


def build_and_render(item_code, out_blend_dir, out_render_dir):
    _clear_scene()
    setup_scene(px=128, outline_px_at_1x=1.0)
    scene = bpy.context.scene
    lineset = scene.view_layers[0].freestyle_settings.linesets[0]
    lineset.select_crease = True
    lineset.select_silhouette = True
    lineset.select_border = True

    ortho_scale = ITEMS[item_code]()
    _set_camera(ortho_scale)

    os.makedirs(out_blend_dir, exist_ok=True)
    blend_path = os.path.join(out_blend_dir, item_code + ".blend")
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(blend_path))

    # os.path.abspath is NOT optional here. On Windows Blender resolves a
    # relative render filepath against the DRIVE ROOT, not against the process
    # cwd -- so passing a relative --out-render silently writes the PNGs to a
    # mirror tree at the root of C: and leaves the real output directory empty,
    # while still printing a "Saved:" line for every frame. Found the hard way
    # on 2026-08-30 while re-rendering the V21 batch from the committed sources.
    render_dir = os.path.abspath(os.path.join(out_render_dir, "icon_" + item_code))
    os.makedirs(render_dir, exist_ok=True)
    scene.render.filepath = os.path.join(render_dir, "icon")
    bpy.ops.render.render(write_still=True)
    print("[kage] icon_{0} done -> {1}".format(item_code, render_dir))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_item_icon")
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--item", help="item_code, or a comma-separated list of them")
    parser.add_argument("--out-blend", required=True)
    parser.add_argument("--out-render", required=True)
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    args = _cli_args(sys.argv)
    if args is not None:
        codes = list(ITEMS) if args.all else [c.strip() for c in args.item.split(",") if c.strip()]
        unknown = [code for code in codes if code not in ITEMS]
        if unknown:
            raise SystemExit("[kage] unknown item_code(s): " + ", ".join(unknown))
        for code in codes:
            build_and_render(code, args.out_blend, args.out_render)
