"""Build the 3 `icon_<item_code>` assets — one per real `item_template` row
seeded in the DB (V4/V5/V9 migrations: kunai, wooden_sword,
enhance_protection_charm). ART-PIPELINE.md §8.3: "item_template.code = 'kunai'
<-> art_asset.asset_key = 'icon_kunai'" (soft-link convention).

Icons are static (128x128 master @2x per ART-PIPELINE.md §4.1) — a single
still render, no action/keyframes needed at all, unlike every other asset
category built so far.

Run headless, one process builds+renders all 3:

    blender -b -P tools/blender/build_item_icon.py -- --all \
      --out-blend assets/raw/blender/props --out-render assets/raw/render-out
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_sprite_render import apply_toon, setup_scene  # noqa: E402


def _clear_scene():
    for obj in list(bpy.data.objects):
        if obj.type not in ("CAMERA", "LIGHT"):
            bpy.data.objects.remove(obj, do_unlink=True)
    for block_collection in (bpy.data.meshes, bpy.data.materials):
        for block in list(block_collection):
            if block.users == 0:
                block_collection.remove(block)


def _box_obj(name, half_x, half_y, half_z, loc=(0.0, 0.0, 0.0), rot=(0.0, 0.0, 0.0)):
    verts = [(sx * half_x, sy * half_y, sz * half_z)
             for sz in (-1, 1) for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
    faces = [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    obj.location = loc
    obj.rotation_euler = rot
    return obj


def _set_camera(ortho_scale):
    scene = bpy.context.scene
    camera = scene.camera
    camera.data.ortho_scale = ortho_scale
    camera.location = (0.0, -10.0, 0.0)


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


ITEMS = {
    "kunai": build_kunai,
    "wooden_sword": build_wooden_sword,
    "enhance_protection_charm": build_enhance_protection_charm,
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
    bpy.ops.wm.save_as_mainfile(filepath=blend_path)

    render_dir = os.path.join(out_render_dir, "icon_" + item_code)
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
    parser.add_argument("--item", help="single item_code, e.g. kunai")
    parser.add_argument("--out-blend", required=True)
    parser.add_argument("--out-render", required=True)
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    args = _cli_args(sys.argv)
    if args is not None:
        codes = list(ITEMS) if args.all else [args.item]
        for code in codes:
            build_and_render(code, args.out_blend, args.out_render)
