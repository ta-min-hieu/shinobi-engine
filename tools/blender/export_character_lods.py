"""Step E of the character pipeline: write the three shipping LODs.

BLENDER-SESSION-GUIDE §1.5 step E. Takes an accepted character .blend and
produces one .glb per graphics tier plus a `model-meta.json` sidecar, which is
to the 3D branch what `render-meta.json` is to the sprite branch — the file
`tools/asset-pipeline` reads to fill the `art_asset` row (ART-PIPELINE §8.6,
migration V20). `blend_file` in the sidecar is load-bearing: it is the only
record of which .blend a model really came from, and the upsert refuses to run
without it rather than guess the name from the asset key.

    blender -b <char>.blend -P tools/blender/export_character_lods.py -- \
        --object Hoa_D --rig hoa_d_Rig \
        --extras hoa_KunaiBlade,hoa_KunaiGrip,hoa_KunaiRing \
        --asset-key char_hoa-b1 --out <repo>/assets/exported/models

The budget in TIERS is measured, not guessed — `measure_triangle_budget.py`
produces the evidence and ART-PIPELINE §4.4 records it.

Output is .glb (one self-contained file) rather than .gltf + .bin + .png:
the patcher hashes and ships whole files (RESOURCE.md §9), and a three-file
asset is three chances for a partial download to leave a model that loads
without its texture.
"""

import json
import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import kage_text3d_kit as t3d  # noqa: E402

# tier -> (triangle budget for the body, texture edge in px). ART-PIPELINE §4.4.
TIERS = {
    "low": (5000, 512),
    "med": (10000, 1024),
    "high": (20000, 2048),
}


def _copy_of(obj, name):
    if name in bpy.data.objects:
        bpy.data.objects.remove(bpy.data.objects[name], do_unlink=True)
    duplicate = obj.copy()
    duplicate.data = obj.data.copy()
    duplicate.name = name
    bpy.context.scene.collection.objects.link(duplicate)
    return duplicate


def export_lods(source, armature, extras, asset_key, out_dir):
    """Write one .glb per tier; return the sidecar dict."""
    source.data.calc_loop_triangles()
    extra_triangles = 0
    for extra in extras:
        extra.data.calc_loop_triangles()
        extra_triangles += len(extra.data.loop_triangles)

    tri_counts, byte_counts = {}, {}
    for tier, (budget, texture_px) in sorted(TIERS.items()):
        working = _copy_of(source, "{0}_{1}".format(source.name, tier))
        _, after = t3d.decimate(working, budget)
        path = os.path.join(out_dir, tier, asset_key + ".glb")
        byte_counts[tier] = t3d.export_gltf(
            path, working, armature, extras=extras, texture_px=texture_px)
        tri_counts[tier] = after + extra_triangles
        bpy.data.objects.remove(working, do_unlink=True)

    meta = {
        "asset_key": asset_key,
        "asset_kind": "MODEL",
        "source_triangles": len(source.data.loop_triangles) + extra_triangles,
        "tri_counts": tri_counts,
        "texture_px": {tier: px for tier, (_, px) in TIERS.items()},
        "glb_bytes": byte_counts,
        "actions": sorted(a.name for a in bpy.data.actions),
        "bones": len(armature.data.bones),
        "blend_file": bpy.data.filepath,
    }
    os.makedirs(out_dir, exist_ok=True)
    meta_path = os.path.join(out_dir, asset_key + ".model-meta.json")
    with open(meta_path, "w", encoding="utf-8") as handle:
        json.dump(meta, handle, indent=2, sort_keys=True)
    print("[lod] sidecar -> " + meta_path)
    return meta


def _args(argv):
    import argparse

    parser = argparse.ArgumentParser(prog="export_character_lods")
    parser.add_argument("--object", required=True,
                        help="body mesh; never guessed, a pilot .blend holds "
                             "several generations of the same character")
    parser.add_argument("--rig", required=True, help="armature name")
    parser.add_argument("--extras", default="",
                        help="comma-separated rigid meshes bound to the same rig "
                             "(weapon parts) — exported, never decimated")
    parser.add_argument("--asset-key", required=True,
                        help="art_asset.asset_key, e.g. char_hoa-b1")
    parser.add_argument("--out", required=True,
                        help="absolute path to assets/exported/models")
    return parser.parse_args(argv[argv.index("--") + 1:])


def main():
    args = _args(sys.argv)
    meta = export_lods(
        bpy.data.objects[args.object],
        bpy.data.objects[args.rig],
        [bpy.data.objects[n] for n in args.extras.split(",") if n],
        args.asset_key,
        args.out)
    print("[lod] === {0} ===".format(meta["asset_key"]))
    print("[lod] source {0} tris".format(meta["source_triangles"]))
    for tier in sorted(TIERS):
        print("[lod] {0:5s} {1:7d} tris  tex {2:4d}  {3:6.2f} MB".format(
            tier, meta["tri_counts"][tier], meta["texture_px"][tier],
            meta["glb_bytes"][tier] / 1048576.0))


if __name__ == "__main__":
    main()
