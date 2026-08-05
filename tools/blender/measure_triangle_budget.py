"""Measure what a character LOD actually costs, so the budget is not a guess.

ART-PIPELINE §4.4 leaves the per-tier triangle budget blank and says it has to
be measured (Constitution article 9). This is the harness that produces the
numbers: it decimates one accepted character to a series of targets, exports
each as a .glb through the real pipeline step, renders each so the quality loss
can be judged rather than assumed, and writes a table.

Two sweeps, because two different things drive the cost and mixing them gives a
number that cannot be acted on:

  * geometry — several triangle targets at one fixed texture size
  * texture  — one triangle target at each tier's texture size

Run:
    blender -b <char>.blend -P tools/blender/measure_triangle_budget.py -- \
        --object Hoa_D --rig hoa_d_Rig --out <abs dir>

What this does NOT measure is client frame time, which needs gdx-gltf on the
classpath and a 3D draw path in client-core; neither exists yet. Triangle count
and payload size are the parts that can be known today, and they are what the
budget table needs first.
"""

import json
import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import kage_sprite_render as ksr  # noqa: E402
import kage_text3d_kit as t3d  # noqa: E402

# Triangle targets to sweep. The top of the range is the raw generated mesh;
# ADR-031 guesses "5-10k per character" and this is what tests that guess.
GEOMETRY_TARGETS = (5000, 10000, 20000, 40000, None)

# Texture size per graphics tier (ART-PIPELINE §5).
TIER_TEXTURE_PX = {"low": 512, "med": 1024, "high": 2048}

# Fixed texture size for the geometry sweep, so only triangles vary.
GEOMETRY_SWEEP_TEXTURE_PX = 1024

# Triangle count held fixed for the texture sweep, so only the map varies.
TEXTURE_SWEEP_TRIANGLES = 10000


def _isolate_for_render(keep):
    """Hide every other object from the render.

    A working character .blend is a junk drawer — the pilot file carries five
    rejected generations of the same character plus an unrelated NPC — and all
    of it renders on top of the LOD under test unless it is hidden.
    """
    keep_names = {obj.name for obj in keep}
    for obj in bpy.context.scene.objects:
        if obj.type == "MESH":
            obj.hide_render = obj.name not in keep_names


def _retarget_texture(obj, texture_px):
    """Point the render materials at a resized copy of the diffuse map.

    `export_gltf` swaps in its own material, exports, and puts the originals
    back — so without this the preview renders through the untouched 2048 map
    no matter which tier was just written, and all three texture rows come out
    pixel-identical. That looked like "texture size does not matter"; it only
    meant the render never saw the resized map.
    """
    materials = []
    for index, material in enumerate(obj.data.materials):
        copied = material.copy()
        copied.name = "{0}_tex{1}".format(material.name, texture_px)
        for node in copied.node_tree.nodes:
            if node.type == "TEX_IMAGE" and node.image is not None:
                scaled = node.image.copy()
                scaled.name = "{0}_{1}".format(node.image.name, texture_px)
                scaled.scale(texture_px, texture_px)
                node.image = scaled
        obj.data.materials[index] = copied
        materials.append(copied)
    return materials


def _copy_of(obj, name):
    if name in bpy.data.objects:
        bpy.data.objects.remove(bpy.data.objects[name], do_unlink=True)
    duplicate = obj.copy()
    duplicate.data = obj.data.copy()
    duplicate.name = name
    bpy.context.scene.collection.objects.link(duplicate)
    return duplicate


def _one_level(source, armature, extras, label, target, texture_px, out_dir):
    duplicate = _copy_of(source, "{0}_{1}".format(source.name, label))
    before, after = (None, None)
    if target is None:
        duplicate.data.calc_loop_triangles()
        before = after = len(duplicate.data.loop_triangles)
    else:
        before, after = t3d.decimate(duplicate, target)

    extra_tris = 0
    for extra in extras:
        extra.data.calc_loop_triangles()
        extra_tris += len(extra.data.loop_triangles)

    glb = os.path.join(out_dir, "models", "{0}.glb".format(label))
    size = t3d.export_gltf(glb, duplicate, armature, extras=extras,
                           texture_px=texture_px)

    if texture_px is not None:
        _retarget_texture(duplicate, texture_px)
    _isolate_for_render([duplicate] + list(extras))
    ksr.fit_camera([duplicate] + list(extras), armature=armature)
    ksr.render_preview(os.path.join(out_dir, "renders", label + ".png"),
                       action="idle", armature=armature)

    bpy.data.objects.remove(duplicate, do_unlink=True)
    return {
        "label": label,
        "target_triangles": target,
        "body_triangles": after,
        "weapon_triangles": extra_tris,
        "total_triangles": after + extra_tris,
        "texture_px": texture_px,
        "glb_bytes": size,
        "glb_mb": round(size / (1024.0 * 1024.0), 2),
    }


def _args(argv):
    import argparse

    parser = argparse.ArgumentParser(prog="measure_triangle_budget")
    parser.add_argument("--object", required=True,
                        help="mesh to sweep, e.g. Hoa_D — NOT guessed, because a "
                             "pilot file holds several generations of it")
    parser.add_argument("--rig", required=True,
                        help="armature name; find_armature() picks the first in "
                             "the scene, which in the pilot file is the NPC's")
    parser.add_argument("--extras", default="",
                        help="comma-separated meshes exported alongside (weapon parts)")
    parser.add_argument("--out", required=True, help="absolute output directory")
    return parser.parse_args(argv[argv.index("--") + 1:])


def main():
    args = _args(sys.argv)
    scene = bpy.context.scene
    source = bpy.data.objects[args.object]
    armature = bpy.data.objects[args.rig]
    extras = [bpy.data.objects[n] for n in args.extras.split(",") if n]

    print("[budget] source={0} rig={1} extras={2}".format(
        source.name, armature.name, [e.name for e in extras]))
    for extra in extras:
        modifier = extra.modifiers.get("Armature")
        bound = modifier.object.name if modifier and modifier.object else None
        print("[budget]   extra {0} bound to {1}".format(extra.name, bound))
    print("[budget] scene res={0} freestyle={1}".format(
        scene.render.resolution_x, scene.render.use_freestyle))

    rows = []
    for target in GEOMETRY_TARGETS:
        label = "geom_{0}".format("orig" if target is None else target)
        rows.append(_one_level(source, armature, extras, label, target,
                               GEOMETRY_SWEEP_TEXTURE_PX, args.out))
    for tier, texture_px in sorted(TIER_TEXTURE_PX.items()):
        label = "tex_{0}_{1}".format(tier, texture_px)
        rows.append(_one_level(source, armature, extras, label,
                               TEXTURE_SWEEP_TRIANGLES, texture_px, args.out))

    os.makedirs(args.out, exist_ok=True)
    with open(os.path.join(args.out, "budget.json"), "w", encoding="utf-8") as handle:
        json.dump(rows, handle, indent=2, sort_keys=True)

    print("[budget] === RESULT ===")
    print("[budget] {0:18s} {1:>10s} {2:>8s} {3:>9s}".format(
        "level", "triangles", "tex", "glb MB"))
    for row in rows:
        print("[budget] {0:18s} {1:>10d} {2:>8d} {3:>9.2f}".format(
            row["label"], row["total_triangles"], row["texture_px"], row["glb_mb"]))


if __name__ == "__main__":
    main()
