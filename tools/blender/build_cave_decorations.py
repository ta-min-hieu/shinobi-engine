"""Build the four cave-dwelling decorations (ADR-129 D3, shown in 3D by lot 25) as static props.

The server shows a placed decoration as a dynamic NPC named `cave_<code>` inside the owner's cave World, so the client
loads `char_cave-<code>` exactly like any NPC model (`NpcModelKey`). That path always starts the `idle` clip, so a
prop still needs the kit rig and an `idle` action — a rig-less mesh would throw "unknown animation" on load. Parts bind
rigidly to `pelvis` (static) or `spine` (sways a couple of degrees in idle: pine foliage, lotus).

Scale: the kit character is 1.0 unit tall; props are sized against that (pine 0.95, lantern 0.86, screen 0.8 high,
pond 0.25 high). Orientation per `kage_mesh_kit._transform`: in the GAME the screen-horizontal axis is Y and X is
depth, so the folding screen spreads its panels along Y.

Palette from ART-PIPELINE §2 (sect, no red): stone greys, dark wood, jade #4FA58C/#7FE0C0, gold #D4AF5A, silk cream.

    blender -b --python-exit-code 1 --factory-startup -P tools/blender/build_cave_decorations.py -- \
      --decoration spirit_pine --out assets/raw/blender/props/cave-spirit-pine.blend

Then the LODs (ART-PIPELINE §6.5):

    blender -b --python-exit-code 1 assets/raw/blender/props/cave-spirit-pine.blend \
      -P tools/blender/export_character_lods.py -- --object spirit_pine_Main --rig spirit_pine_Rig \
      --extras all --flat-materials --asset-key char_cave-spirit-pine --out <ABSOLUTE>/assets/exported/models
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import kage_mesh_kit as kit  # noqa: E402

STONE = "#7A7F84"
STONE_LIGHT = "#959AA0"
WOOD = "#4A2E22"
JADE = "#4FA58C"
JADE_GLOW = "#7FE0C0"
GOLD = "#D4AF5A"
SILK = "#E8DFC8"


def _part(rig, name, geometry, color, bone="pelvis", smooth=True):
    verts, faces = geometry
    return kit._new_part(rig, name, verts, faces, color, bone, smooth=smooth)


def _moved(geometry, translate=(0.0, 0.0, 0.0), rotate_x_deg=0.0, scale=(1.0, 1.0, 1.0)):
    verts, faces = geometry
    return kit._transform(verts, translate=translate, rotate_x_deg=rotate_x_deg, scale=scale), faces


def _disc(radius, z_lo, z_hi, segments=16):
    return kit._revolve([(z_lo, radius, radius), (z_hi, radius, radius)], segments)


def _spirit_pine(rig, name):
    """Bonsai pine in a stone pot: a leaning trunk and three cloud-pad tiers — the classic xianxia courtyard pine."""
    main = _part(rig, name + "_Main", kit._revolve(
        [(0.0, 0.13, 0.13), (0.10, 0.17, 0.17), (0.13, 0.19, 0.19), (0.15, 0.17, 0.17)], segments=8), STONE,
        smooth=False)
    _part(rig, name + "_Soil", _disc(0.16, 0.14, 0.155, segments=8), "#3B2A1E", smooth=False)
    trunk = kit._merge([
        _moved(kit._taper(0.14, 0.46, 0.05, 0.036, segments=8), rotate_x_deg=14.0),
        _moved(kit._taper(0.40, 0.70, 0.036, 0.022, segments=8), translate=(0.0, -0.11, 0.02), rotate_x_deg=-16.0),
    ])
    _part(rig, name + "_Trunk", trunk, "#5A3E2B")
    # VÒNG B #1 (2026-11-01, runWorldPreview): ba tầng lá gần thẳng hàng che hết thân ⇒ đọc ra cây cắt tỉa hình tháp.
    # Giãn tầng sang HAI BÊN theo trục Y (trục ngang màn hình) để thân cong lộ ra giữa các tầng — dáng tùng bonsai.
    pads = kit._merge([
        _moved(kit._ellipsoid(0.14, 0.22, 0.055, rings=6, segments=12), translate=(0.0, -0.24, 0.46)),
        _moved(kit._ellipsoid(0.13, 0.19, 0.05, rings=6, segments=12), translate=(0.0, 0.20, 0.60)),
        _moved(kit._ellipsoid(0.11, 0.15, 0.05, rings=6, segments=12), translate=(0.0, -0.08, 0.80)),
    ])
    _part(rig, name + "_Foliage", pads, "#2F6B4F", bone="spine")
    _part(rig, name + "_Spirit", _moved(kit._ellipsoid(0.03, 0.03, 0.03, rings=6, segments=8),
                                        translate=(0.0, 0.16, 0.92)), JADE_GLOW, bone="spine")
    return main


def _jade_lantern(rig, name):
    """Stone lantern on a post with a glowing jade light chamber, a four-sided roof and a gold finial."""
    main = _part(rig, name + "_Main", kit._merge([
        kit._box(0.14, 0.14, 0.0, 0.06),
        kit._taper(0.06, 0.42, 0.05, 0.04, segments=6, cap=False),
        kit._box(0.13, 0.13, 0.42, 0.47),
    ]), STONE, smooth=False)
    _part(rig, name + "_Light", kit._box(0.085, 0.085, 0.47, 0.66), JADE_GLOW, smooth=False)
    posts = kit._merge([
        _moved(kit._box(0.018, 0.018, 0.47, 0.66), translate=(sx * 0.09, sy * 0.09, 0.0))
        for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1))
    ])
    _part(rig, name + "_Posts", posts, "#3A4A48", smooth=False)
    _part(rig, name + "_Roof", kit._revolve(
        [(0.66, 0.21, 0.21), (0.70, 0.18, 0.18), (0.80, 0.0, 0.0)], segments=4), "#2F3D3B", smooth=False)
    _part(rig, name + "_Finial", _moved(kit._ellipsoid(0.03, 0.03, 0.045, rings=6, segments=8),
                                        translate=(0.0, 0.0, 0.84)), GOLD)
    return main


def _cloud_screen(rig, name):
    """Three-panel folding screen spread along screen-horizontal Y, zig-zagging in depth, jade clouds on silk."""
    frames, silks, clouds = [], [], []
    for index, centre_y in enumerate((-0.30, 0.0, 0.30)):
        depth = 0.04 if index % 2 == 0 else -0.04
        bars = kit._merge([
            kit._box(0.016, 0.15, 0.04, 0.09),
            kit._box(0.016, 0.15, 0.73, 0.78),
            _moved(kit._box(0.016, 0.016, 0.0, 0.78), translate=(0.0, -0.135, 0.0)),
            _moved(kit._box(0.016, 0.016, 0.0, 0.78), translate=(0.0, 0.135, 0.0)),
        ])
        frames.append(_moved(bars, translate=(depth, centre_y, 0.0)))
        silks.append(_moved(kit._box(0.008, 0.12, 0.09, 0.73), translate=(depth, centre_y, 0.0)))
        for z, offset in ((0.30, -0.03), (0.52, 0.04)):
            for side in (-1, 1):
                clouds.append(_moved(kit._ellipsoid(0.006, 0.06, 0.035, rings=4, segments=10),
                                     translate=(depth + side * 0.011, centre_y + offset, z)))
    main = _part(rig, name + "_Main", kit._merge(frames), WOOD, smooth=False)
    _part(rig, name + "_Silk", kit._merge(silks), SILK, smooth=False)
    _part(rig, name + "_Clouds", kit._merge(clouds), JADE)
    return main


def _lotus_pond(rig, name):
    """Low round pond: stone rim, water, three lily pads and two pink lotus flowers on short stems."""
    main = _part(rig, name + "_Main", _disc(0.42, 0.0, 0.12, segments=16), STONE_LIGHT, smooth=False)
    _part(rig, name + "_Water", _disc(0.36, 0.12, 0.128, segments=16), "#3E7E9E", smooth=False)
    pads = kit._merge([
        _moved(kit._ellipsoid(0.09, 0.09, 0.008, rings=4, segments=10), translate=(x, y, 0.132))
        for x, y in ((-0.12, -0.15), (0.10, 0.12), (0.02, -0.02))
    ])
    _part(rig, name + "_Pads", pads, "#4F8F5F", bone="spine")
    flowers, hearts = [], []
    for x, y in ((-0.10, 0.10), (0.12, -0.12)):
        flowers.append(_moved(kit._revolve(
            [(0.0, 0.0, 0.0), (0.03, 0.05, 0.05), (0.07, 0.075, 0.075)], segments=6),
            translate=(x, y, 0.15)))
        hearts.append(_moved(kit._ellipsoid(0.02, 0.02, 0.02, rings=4, segments=8), translate=(x, y, 0.20)))
    _part(rig, name + "_Lotus", kit._merge(flowers), "#F2A7C3", bone="spine", smooth=False)
    _part(rig, name + "_Heart", kit._merge(hearts), GOLD, bone="spine")
    return main


DECORATIONS = {
    "spirit_pine": (_spirit_pine, 2.0),
    "jade_lantern": (_jade_lantern, 0.0),
    "cloud_screen": (_cloud_screen, 0.0),
    "lotus_pond": (_lotus_pond, 1.5),
}


def _author_idle(rig, sway_deg):
    """The one clip a prop needs. Every bone keyed (kit.pose_frame) so no channel inherits a stale value."""
    if rig.animation_data is None:
        rig.animation_data_create()
    action = bpy.data.actions.new("idle")
    rig.animation_data.action = action
    kit.pose_frame(rig, 1, {})
    kit.pose_frame(rig, 60, {"spine": sway_deg})
    kit.pose_frame(rig, 120, {})
    action.use_fake_user = True


def build(decoration, out_path):
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)
    builder, sway = DECORATIONS[decoration]
    rig = kit.build_rig(decoration)
    bpy.ops.object.mode_set(mode="OBJECT")
    main = builder(rig, decoration)
    _author_idle(rig, sway)
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    triangles = sum(sum(len(p.vertices) - 2 for p in obj.data.polygons) for obj in meshes)
    print("[deco] {0} saved: {1} parts={2} tris={3} main={4} rig={5}".format(
        decoration, out_path, len(meshes), triangles, main.name, rig.name))


if __name__ == "__main__":
    import argparse

    raw = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser()
    parser.add_argument("--decoration", required=True, choices=sorted(DECORATIONS))
    parser.add_argument("--out", required=True)
    args = parser.parse_args(raw)
    build(args.decoration, args.out)
