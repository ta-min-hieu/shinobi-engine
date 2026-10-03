"""Step E of the character pipeline: write the three shipping LODs.

BLENDER-SESSION-GUIDE §1.5 step E. Takes an accepted character .blend and
produces one .glb per graphics tier plus a `model-meta.json` sidecar, which is
to the 3D branch what `render-meta.json` is to the sprite branch — the file
`tools/asset-pipeline` reads to fill the `art_asset` row (ART-PIPELINE §8.6,
migration V20). `blend_file` in the sidecar is load-bearing: it is the only
record of which .blend a model really came from, and the upsert refuses to run
without it rather than guess the name from the asset key.

    blender -b --python-exit-code 1 <char>.blend -P tools/blender/export_character_lods.py -- \
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


# Smallest share of a tier budget the body may be squeezed to before the extras
# are decimated as well. A generated character is one big body plus three small
# weapon parts, and shrinking the body alone is right there. A mesh-kit
# character is the opposite shape — `wandering_bandit` is 40 parts and its torso
# is 3% of them — so body-only decimation cannot reach the low tier at all: with
# only the torso shrinking, the low .glb shipped ~5800 triangles against a 5000
# ceiling, which `TriangleBudgetValidator` fails on, correctly.
BODY_SHARE_FLOOR = 0.25

# Parts at or below this are left whole. A scarf tail is one box: twelve
# triangles. Decimating it "proportionally" to eight turns the box into a wedge,
# which is a visible shape change bought for four triangles out of five
# thousand. Measured on the first `wandering_bandit` low tier, which did exactly
# that to the sash tail, the scarf tail and the blade guard.
MIN_PART_TRIANGLES = 64

# Retries for a shared-ratio shrink that lands over its tier budget — see export_lods.
MAX_SHRINK_ATTEMPTS = 4


def _triangles(obj):
    obj.data.calc_loop_triangles()
    return len(obj.data.loop_triangles)


def _shrink_together(body, extras, tier, budget, extra_triangles):
    """Decimate body and every big-enough part by one shared ratio.

    Returns (body triangles, part copies) — the caller owns the copies, since
    the originals have to survive for the next tier.

    One shared ratio rather than "take it all out of the biggest part": every
    part is already at the poly count its own shape needs, so spending the whole
    cut on one of them is what turns a hand into a wedge. Parts under
    `MIN_PART_TRIANGLES` sit out and their triangles come off the pool first, so
    the ratio the rest pay is honest.
    """
    part_triangles = [_triangles(extra) for extra in extras]
    fixed = sum(count for count in part_triangles if count <= MIN_PART_TRIANGLES)
    pool = _triangles(body) + extra_triangles - fixed
    ratio = (budget - fixed) / float(pool) if pool > 0 else 1.0
    copies = []
    for extra, count in zip(extras, part_triangles):
        copy = _copy_of(extra, "{0}_{1}".format(extra.name, tier))
        if count > MIN_PART_TRIANGLES:
            t3d.decimate(copy, max(MIN_PART_TRIANGLES, int(count * ratio)))
        copies.append(copy)
    _, after = t3d.decimate(body, max(4, int(_triangles(body) * ratio)))
    return after, copies


def _copy_of(obj, name):
    if name in bpy.data.objects:
        bpy.data.objects.remove(bpy.data.objects[name], do_unlink=True)
    duplicate = obj.copy()
    duplicate.data = obj.data.copy()
    duplicate.name = name
    bpy.context.scene.collection.objects.link(duplicate)
    return duplicate


def export_lods(source, armature, extras, asset_key, out_dir, flat_materials=False):
    """Write one .glb per tier; return the sidecar dict.

    `flat_materials` switches to the procedural-asset export path
    (`kage_text3d_kit.export_gltf_flat`) — see that function for why a
    hand-built mesh cannot go through the textured one. Such models are already
    far below every tier budget, so the three tiers come out byte-identical;
    they are still written per tier because `GraphicsTier` resolves a directory,
    not a file, and a missing file is what disables the whole 3D branch client
    side.
    """
    source.data.calc_loop_triangles()
    extra_triangles = 0
    for extra in extras:
        extra.data.calc_loop_triangles()
        extra_triangles += len(extra.data.loop_triangles)

    tri_counts, byte_counts = {}, {}
    for tier, (budget, texture_px) in sorted(TIERS.items()):
        working = _copy_of(source, "{0}_{1}".format(source.name, tier))
        if extra_triangles <= budget * (1.0 - BODY_SHARE_FLOOR):
            # The budget in TIERS is the shipped TOTAL (ART-PIPELINE §4.4), extras included —
            # decimate the body to budget minus what the un-decimated extras already cost, so body
            # + extras lands back on budget instead of budget + extras.
            _, after = t3d.decimate(working, budget - extra_triangles)
            tier_extras = extras
        else:
            # Decimate does not land exactly on its target: summed over ~40 kit parts the low
            # tier can come out a few triangles OVER the ceiling (blood_sand_scout 5006 on the
            # 2026-10-01 face rebuild), which TriangleBudgetValidator fails on. Re-run from a
            # fresh copy with the overshoot taken off the target until it fits.
            target = budget
            for _attempt in range(MAX_SHRINK_ATTEMPTS):
                after, tier_extras = _shrink_together(working, extras, tier, target, extra_triangles)
                total = after + sum(_triangles(extra) for extra in tier_extras)
                if total <= budget:
                    break
                for copy in tier_extras:
                    bpy.data.objects.remove(copy, do_unlink=True)
                bpy.data.objects.remove(working, do_unlink=True)
                working = _copy_of(source, "{0}_{1}".format(source.name, tier))
                target -= total - budget
        path = os.path.join(out_dir, tier, asset_key + ".glb")
        if flat_materials:
            byte_counts[tier] = t3d.export_gltf_flat(path, working, armature, extras=tier_extras)
        else:
            byte_counts[tier] = t3d.export_gltf(
                path, working, armature, extras=tier_extras, texture_px=texture_px)
        tri_counts[tier] = after + sum(_triangles(extra) for extra in tier_extras)
        bpy.data.objects.remove(working, do_unlink=True)
        for extra in tier_extras:
            if extra not in extras:
                bpy.data.objects.remove(extra, do_unlink=True)

    meta = {
        "asset_key": asset_key,
        "asset_kind": "MODEL",
        "source_triangles": len(source.data.loop_triangles) + extra_triangles,
        "tri_counts": tri_counts,
        # No texture at all on the flat path — record 0, not the tier's texture
        # edge, so the sidecar keeps describing the file that was actually written.
        "texture_px": {tier: (0 if flat_materials else px) for tier, (_, px) in TIERS.items()},
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
                             "(weapon parts) — exported, never decimated. `all` takes "
                             "every other mesh on the rig, which is what a mesh-kit "
                             "asset needs: `wandering_bandit` is 40 parts, and the "
                             "hand-written list left `bandit_SashTail` out silently")
    parser.add_argument("--asset-key", required=True,
                        help="art_asset.asset_key, e.g. char_hoa-b1")
    parser.add_argument("--out", required=True,
                        help="absolute path to assets/exported/models")
    parser.add_argument("--flat-materials", action="store_true",
                        help="mesh was built by hand from flat-coloured parts "
                             "(monsters, NPCs) instead of generated with a baked "
                             "albedo — keep one material per part, export no texture")
    return parser.parse_args(argv[argv.index("--") + 1:])


def _extra_objects(spec, body, armature):
    """Resolve `--extras`: an explicit list, or every other mesh on this rig.

    A generated character has one body and three weapon parts, so naming them is
    fine. A mesh-kit character is forty separate parts, and naming those by hand
    is how `bandit_SashTail` got left out of the first `wandering_bandit` export
    — the .glb came out one mesh short and nothing failed, because a missing
    extra is indistinguishable from an extra that was never wanted.
    """
    if spec != "all":
        return [bpy.data.objects[name] for name in spec.split(",") if name]
    return [obj for obj in bpy.data.objects
            if obj.type == "MESH" and obj is not body and not obj.hide_render
            and (obj.parent is armature
                 or any(mod.type == "ARMATURE" and mod.object is armature
                        for mod in obj.modifiers))]


def main():
    args = _args(sys.argv)
    meta = export_lods(
        bpy.data.objects[args.object],
        bpy.data.objects[args.rig],
        _extra_objects(args.extras, bpy.data.objects[args.object],
                       bpy.data.objects[args.rig]),
        args.asset_key,
        args.out,
        flat_materials=args.flat_materials)
    print("[lod] === {0} ===".format(meta["asset_key"]))
    print("[lod] source {0} tris".format(meta["source_triangles"]))
    for tier in sorted(TIERS):
        print("[lod] {0:5s} {1:7d} tris  tex {2:4d}  {3:6.2f} MB".format(
            tier, meta["tri_counts"][tier], meta["texture_px"][tier],
            meta["glb_bytes"][tier] / 1048576.0))


if __name__ == "__main__":
    main()
