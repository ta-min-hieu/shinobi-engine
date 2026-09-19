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

REVISED 2026-09-19, the first time this species was ever put through
`runWorldPreview`. It shipped in `3a8d98a` graded on nothing but its own colour
list, and at the size a player sees (~50px) it read as one pale lump: the
`carved_crest` cap became a brim over the whole face, the anime eyes landed on
the silhouette edges, and skin / torso / crest were all the same VALUE. Three
things fixed it, all of them already written down in
`build_monster_trial_construct` before this file was authored — `cap: False`,
`face_style: "none"`, and a `stone_visor` with AMBER slots. The lesson is not
about jade: a species is not graded until it has been through the client's own
renderer next to its own lineage.

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

# The sect's ceremonial jade, pale at the chest and darkening down the body.
JADE_PALE = "#A8D9BE"
JADE_MID = "#5FA986"
JADE_DEEP = "#3F7A5E"
# The dark end of the same hue, and the load-bearing colour of this spec: every
# piece that has to READ (crest, visor frame, blade, boots) is cut from it. The
# first version had nothing this dark above the waist and the model came back
# from `runWorldPreview` as one pale lump — see the 2026-09-19 entry in
# ASSET-LOG.
JADE_IRON = "#2E5847"
# Gold, where every other species on the play line carries dried red or (V48)
# sickly green. ⚠️ Do NOT swap this for red: red means Blood Sand here.
GOLD = "#C9A227"
# Copied verbatim from `build_monster_trial_construct`, which copied it from
# `build_monster_archive_warden`, which copied it from the ogre. Lit slots in
# this exact amber are the Linh Triều family tie — do not "improve" it.
AMBER = "#E8A33C"

WARDEN_SPEC = {
    "name": "foundation",
    # Carved stone, not flesh — paler and flatter than any living species so the
    # material reads before the shape does.
    "skin": "#C8CBC0",
    # ⚠️ `cap: False` is NOT a style choice, and this file paid for learning it
    # twice. `add_hair` cuts the dome for a LEVEL camera while the client's looks
    # down (ADR-031), so on a bare skull the cap renders as a brim over the whole
    # face — exactly the note `build_monster_trial_construct` already carried
    # ("learned on the elder, paid for again on the warden"). The first version
    # of this spec ignored it and the render showed a pale lid with no face under
    # it. Dark crest over pale skull is also the VALUE contrast the species was
    # missing.
    "hair": {"style": "carved_crest", "color": JADE_IRON, "cap": False},
    # A machine has no anime face, and `add_face` cannot give it one that works:
    # the kit authors eye/brow/mouth for a PROFILE view and the client draws
    # closer to head-on, which slides both eyes onto the silhouette edges. The
    # `stone_visor` below is the face. (This is the same defect recorded for
    # every haired kit character; on a bare skull it is merely more obvious.)
    "face_style": "none",
    # Pale jade body with darker jade at the legs — the sect's ceremonial colours,
    # unused by any monster on the play line.
    "cloth": {"torso": JADE_PALE, "pelvis": JADE_MID, "legs": JADE_DEEP},
    "shoe": JADE_IRON,
    "accessories": [
        # The face. Jade frame instead of the other three machines' iron, amber
        # slots because that is the family tie. It is the only warm colour above
        # the waist, so the head is the first thing the eye lands on — which is
        # what was missing when this species had no readable face at all.
        {"kind": "stone_visor", "color": JADE_IRON, "accent": AMBER},
        {"kind": "sash", "color": GOLD},
        # Dark on a pale arm. The first version used JADE_DEEP and it had no
        # edge at 50px — the same "pale rope on a pale arm is nothing" finding
        # that sent `trial_construct`'s arm bands to iron.
        {"kind": "arm_wrap", "color": JADE_IRON},
    ],
    # ⚠️ KEPT, and it renders NOTHING — knowingly. `_weapon_long_blade` places the
    # blade at x = -0.145, i.e. across the BACK (its own docstring says so), and
    # the client's camera is near head-on (ADR-031), so the body occludes it in
    # every frame of `runWorldPreview`: idle, run, attack, die. This is NOT a
    # colour problem — the first version of this spec painted the blade JADE_PALE
    # and I assumed that was the cause; darkening it to JADE_IRON changed nothing,
    # and `wandering_bandit` (shipped 2026-08) and `blood_sand_ringleader` carry
    # the same invisible blade. It is the `_acc_cloak` finding again, on a weapon.
    # Left in place because it is an identity choice (a ceremonial guard holds a
    # sword) and because the LOW tier has ~530 triangles of headroom; whoever
    # moves the blade to the front of the body in `kage_mesh_kit` gets three
    # species back for free. See ASSET-LOG 2026-09-19.
    "weapon": "long_blade",
    "weapon_color": JADE_IRON,
    "weapon_grip": GOLD,
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
