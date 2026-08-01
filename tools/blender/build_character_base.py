"""Build assets/raw/blender/kage_character_base.blend from scratch.

Run headless — never from an MCP session, because resetting the file there
kills the blender-mcp addon along with the connection:

    blender -b --factory-startup -P tools/blender/build_character_base.py

Every character .blend starts as a copy of this file, so camera, lights,
Freestyle, render settings and the chibi scale guide are identical everywhere
(ART-PIPELINE.md §3.3).
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_sprite_render import make_toon_material, setup_scene  # noqa: E402

# Kage character scale: 1.0 Blender unit tall, feet at z=0, head 0.4 unit
# (2.5 heads). Locking this down is what keeps every sprite the same size.
CHARACTER_HEIGHT = 1.0
GUIDE_PARTS = (("head", 0.60, 1.00), ("torso", 0.25, 0.60), ("legs", 0.00, 0.25))

ELEMENT_PALETTE = (
    ("hoa", "#E8552D"),
    ("thuy", "#2D7FE8"),
    ("phong", "#3DBF7A"),
    ("loi", "#8C4DE8"),
    ("tho", "#A6763D"),
)


def _make_box(name, half_x, half_y, z_lo, z_hi):
    """Build a box without bpy.ops, so this works in any execution context."""
    verts = [(sx * half_x, sy * half_y, z)
             for z in (z_lo, z_hi)
             for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
    faces = [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1),
             (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    return bpy.data.objects.new(name, mesh)


def _clear_startup_scene():
    """Drop Blender's default Cube/Light and their material.

    --factory-startup loads the default scene, whose Cube is a renderable mesh
    that would appear in every sprite and whose point Light breaks the cel
    key/fill setup. The default Camera is kept: setup_scene() reconfigures
    whatever scene.camera already points at.
    """
    for name in ("Cube", "Light"):
        obj = bpy.data.objects.get(name)
        if obj is not None:
            bpy.data.objects.remove(obj, do_unlink=True)
    junk = bpy.data.materials.get("Material")
    if junk is not None:
        bpy.data.materials.remove(junk)


def build(out_path):
    _clear_startup_scene()
    scene = setup_scene(px=256)

    guide = bpy.data.collections.new("KageScaleGuide")
    scene.collection.children.link(guide)
    for name, z_lo, z_hi in GUIDE_PARTS:
        box = _make_box("guide_" + name, 0.22, 0.18, z_lo, z_hi)
        box.display_type = "WIRE"
        box.hide_render = True  # a guide must never reach a sprite
        guide.objects.link(box)

    for element, base_hex in ELEMENT_PALETTE:
        material = make_toon_material("toon_" + element, base_hex, shades=3)
        material.use_fake_user = True  # zero-user datablocks are dropped on save

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=out_path)
    print("[kage] base saved: " + out_path)
    print("[kage] materials: " + str([m.name for m in bpy.data.materials]))
    print("[kage] guide: " + str([o.name for o in guide.objects]))


if "--" in sys.argv:
    build(sys.argv[sys.argv.index("--") + 1])
else:
    repo = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    build(os.path.join(repo, "assets", "raw", "blender", "kage_character_base.blend"))
