"""Build the `foundation_warden` monster (migration V48, `monster_template`) from
the mesh kit — the sect's Foundation trial construct, Lv33, and the LAST fight of v1.

Same Linh Trieu machine lineage as `trial_construct` (the Lv10 awakening trial),
`archive_warden` and `stone_ogre` — but this one is NOT salvage. The sect BUILT
it for the Foundation examination, and that difference is the whole visual brief:
the other three are weathered grey stone, this one is ceremonial.

`carved_crest` is the last free hair shape in the kit, and it is the right one
here rather than merely the available one: on a construct it reads as a carved
stone crest, not hair. Every other shape in the kit reads as a person's hair and
would fight the material.

Weapon must be a REUSE (all 5 shapes are taken), so it takes `long_blade` and
separates itself by palette: PALE JADE with gold trim. No monster on the play
line uses jade or gold — they live in the item icons (`JADE`, `GOLD` in
build_item_icon.py). A trial piece dressed in the sect's own ceremonial colours
is exactly what a player should read a half-second before the fight starts.

⚠️ Do NOT give it a red accent. Every red on this play line means Blood Sand, and
this thing belongs to the sect.

Run headless (never in an MCP session — full file rebuild):

    blender -b assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_monster_foundation_warden.py \
      -- --out assets/raw/blender/monsters/foundation-warden.blend

Then the three LODs (ART-PIPELINE §6.5):

    blender -b assets/raw/blender/monsters/foundation-warden.blend \
      -P tools/blender/export_character_lods.py -- \
      --object foundation_Torso --rig foundation_Rig --extras all --flat-materials \
      --asset-key mob_foundation-warden --out <ABSOLUTE>/assets/exported/models
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

WARDEN_SPEC = {
    "name": "foundation",
    # Carved stone, not flesh — paler and flatter than any living species so the
    # material reads before the shape does.
    "skin": "#C8CBC0",
    # Spirit-lamp green-blue: it is lit, not alive. Distinct from the wasteland
    # species' sickly green (#8FBF5A) by being much colder.
    "eye_color": "#7FD6C6",
    "hair": {"style": "carved_crest", "color": "#8FA89B"},
    # Pale jade body with darker jade at the legs — the sect's ceremonial colours,
    # unused by any monster on the play line.
    "cloth": {"torso": "#A8D9BE", "pelvis": "#5FA986", "legs": "#3F7A5E"},
    "shoe": "#2E5847",
    "accessories": [
        # Gold, where every other species carries dried red or (V48) sickly green.
        {"kind": "sash", "color": "#C9A227"},
        {"kind": "arm_wrap", "color": "#3F7A5E"},
    ],
    "weapon": "long_blade",
    "weapon_color": "#A8D9BE",
    "weapon_grip": "#C9A227",
}


def _clear_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)
    for collection in (bpy.data.meshes, bpy.data.armatures, bpy.data.actions, bpy.data.materials):
        for block in list(collection):
            if block.users == 0:
                collection.remove(block)


def build(out_path, outline_px_at_1x=1.1):
    _clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    rig = assemble_character(WARDEN_SPEC)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[mob] foundation_warden saved: {0}".format(out_path))
    print("[mob] parts={0} bones={1}".format(len(meshes), len(rig.pose.bones)))
    print("[mob] mesh objects: " + ", ".join(sorted(obj.name for obj in meshes)))


def _cli_args(argv):
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1)
    return parser.parse_args(argv)


if __name__ == "__main__":
    raw = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    parsed = _cli_args(raw)
    build(parsed.out, parsed.outline_px)
