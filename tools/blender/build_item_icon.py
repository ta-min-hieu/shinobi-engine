"""Build the `icon_<item_code>` assets — one per real `item_template` row seeded
in the DB: V4/V5/V9 (kunai, wooden_sword, enhance_protection_charm) plus the 15
rows V21 adds (`V21__seed_starter_equipment.sql`). ART-PIPELINE.md §8.3:
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
