"""Turn a generated mesh into a finished Kage monster, headless.

The monster twin of `build_text3d_character.py`, and it deliberately reuses that
script's `auto_measure` rather than owning a second copy: a monster is rigged by
the same eleven-bone skeleton, animated by the same seven actions and shaded by
the same toon path, so the only thing that differs is what surrounds the build —
no element palette, no weapon, no B1/B2/B3 tier, and the import happens here
instead of in an MCP session.

That import is the real reason this file exists. Steps A/B of
BLENDER-SESSION-GUIDE §1.5 (generate, import) went through the blender-mcp
session for the fifteen characters, which meant a GUI Blender had to be running.
A monster arrives from the same Rodin job as a plain `.glb` on disk, so `--glb`
imports it here and the whole pipeline runs headless from one command.

    blender -b assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_text3d_monster.py -- \
      --glb <abs path to the Rodin .glb> --code stone_ogre \
      --out assets/raw/blender/monsters/stone-ogre.blend

The mesh and rig come out named after the monster code (`StoneOgre` /
`stoneogre_Rig`), which is what `export_character_lods.py --object/--rig` then
takes, with `--asset-key mob_stone-ogre` (`MonsterModelKey` client side).
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import build_text3d_character as b3d  # noqa: E402
import kage_sprite_render as ksr  # noqa: E402
import kage_text3d_kit as t3d  # noqa: E402

# Same default as the character builder, and checked the same way: the shell
# inflates along the normals, so a mesh with finer detail blurs shut at a width
# another wears comfortably. Render and look before trusting it.
DEFAULT_OUTLINE_PX = 1.2


def import_glb(path):
    """Import the generated .glb and return its body mesh.

    Rodin returns one mesh named `model`; taking the heaviest new mesh rather
    than that name keeps this working if a job ever returns the body split in
    two, and fails loudly if it returns none.
    """
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=os.path.abspath(path))
    fresh = [obj for obj in bpy.data.objects
             if obj not in before and obj.type == "MESH"]
    if not fresh:
        raise SystemExit("no mesh imported from " + path)
    body = max(fresh, key=lambda obj: len(obj.data.vertices))
    body.name = "Rodin_Import"
    print("[mob] imported {0}: {1} verts".format(path, len(body.data.vertices)))
    return body


def mesh_name(code):
    """`stone_ogre` -> `StoneOgre`, the object name the LOD exporter is given."""
    return "".join(part.capitalize() for part in code.split("_"))


def build(source, code, outline_px=DEFAULT_OUTLINE_PX, overrides=None):
    name = mesh_name(code)
    obj = t3d.clean(t3d.normalise(source.name, name))
    joints = b3d.auto_measure(obj)
    joints.update(overrides or {})

    rig = t3d.rig(obj, code.replace("_", ""), **{key: joints[key] for key in b3d.JOINTS})
    t3d.author_scaled_actions(rig)
    t3d.toon_textured(obj, shades=3, rim=0.35)
    t3d.add_far_shade(obj)
    for action in bpy.data.actions:
        action.use_fake_user = True

    ksr.setup_scene(px=256)
    # The shell converts @1x pixels through the camera, so it can only be sized
    # once the camera is framed — same ordering constraint as the character path.
    ksr.fit_camera(ksr.renderable_meshes(bpy.context.scene), armature=rig,
                   actions=[action.name for action in bpy.data.actions])
    t3d.add_inverted_hull_outline(obj, width_px_at_1x=outline_px)
    print("[mob] built {0}: mesh={1} rig={2}".format(code, obj.name, rig.name))
    return obj, rig


def _cli(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_text3d_monster")
    parser.add_argument("--glb", required=True,
                        help="generated .glb to import; step A/B need no MCP session")
    parser.add_argument("--code", required=True,
                        help="monster_code as the server knows it, e.g. stone_ogre")
    parser.add_argument("--outline-px", type=float, default=DEFAULT_OUTLINE_PX)
    parser.add_argument("--joint", action="append", default=[], metavar="NAME=VALUE",
                        help="override one auto-measured joint, repeatable")
    parser.add_argument("--out", required=True, help="path to save the .blend to")
    return parser.parse_args(argv[argv.index("--") + 1:])


def _main():
    args = _cli(sys.argv)
    if args is None:
        return
    overrides = {}
    for item in args.joint:
        key, _, value = item.partition("=")
        if key not in b3d.JOINTS:
            raise SystemExit("unknown joint '{0}'; have {1}".format(
                key, ", ".join(b3d.JOINTS)))
        overrides[key] = float(value)

    source = import_glb(args.glb)
    build(source, args.code, outline_px=args.outline_px, overrides=overrides)

    # pack_all for the same reason as the character path: the texture arrives
    # from the cloud job and lives nowhere on disk once the temp file is gone.
    bpy.ops.file.pack_all()
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(args.out))
    print("[mob] saved -> {0}".format(bpy.data.filepath))


if __name__ == "__main__":
    _main()
