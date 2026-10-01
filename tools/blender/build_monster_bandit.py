"""Build the `wandering_bandit` monster (ADR-037 `monster_template`) from the mesh kit.

Third monster/NPC asset after `build_monster_dummy.py` and `build_npc_elder.py`,
and the first one that is person-shaped, so it does NOT get its own hand-boxed
body: `kage_mesh_kit.assemble_character` already builds a chibi with a face,
hair, cloth blocks, a weapon and the eleven-bone rig the client's clips are
named for. A fourth copy of `_add_box` would buy nothing but drift.

Why the kit and not the Text->3D path the fifteen player characters use
(ART-QUALITY-STANDARD §0 says nothing new should use the older builders): the
generated path needs a Rodin job, and on 2026-08-30 the free-trial key answered
`API_INSUFFICIENT_FUNDS` — `stone_ogre` got its mesh minutes earlier, this one
did not. The kit is the offline, deterministic fallback, and every step after
the mesh (rig, actions, toon, outline, LOD export) is shared with the generated
path, so re-running `build_text3d_monster.py --code wandering_bandit` over a
real Rodin `.glb` later replaces the art without touching anything else.

Run headless (never in an MCP session — full file rebuild):

    blender -b assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_monster_bandit.py \
      -- --out assets/raw/blender/monsters/wandering-bandit.blend

Colours are deliberately outside the five element palettes (ART-PIPELINE §2.2):
a bandit is not of any faction, and a player must never read an enemy as one of
their own kit. Brown leather and road-dust cloth, with the one red accent that
carries the silhouette.
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

# One spec, same shape as `build_kage_character.CHARACTERS` entries. Kept here
# rather than added to that table because that table IS the five playable
# factions — a monster in it would be picked up by anything iterating it.
BANDIT_SPEC = {
    "name": "bandit",
    "skin": "#E0B183",
    "eye_color": "#241C16",
    # Two-eyed game-camera face + chin up in idle (ASSET-LOG 2026-10-01, second
    # pass). A monster turns to face the player, so half the time the client
    # draws it almost head-on — where the profile-authored `default` face is
    # skin plus a grey speck at the temple. The lift keeps the hair brim off the
    # eyes from the downward camera (16 was not enough, 28 is).
    "face_style": "frontal",
    "idle_chin_lift": 28,
    "hair": {"style": "topknot", "color": "#2B211A"},
    "cloth": {"torso": "#6B4A2E", "pelvis": "#3E342A", "legs": "#4C4238"},
    "shoe": "#2E2822",
    # Scarf first so the red sits highest in the read, sash second so it says
    # the same thing again at the waist: the first render had the scarf alone,
    # and under the chin of a two-head chibi it survived as a red sliver two
    # pixels tall. One accent colour needs two placements to read as an accent.
    # The wrap is the same road-dust family as the trousers and only breaks up
    # the forearm.
    "accessories": [
        {"kind": "scarf", "color": "#B33B3B"},
        {"kind": "sash", "color": "#B33B3B"},
        {"kind": "arm_wrap", "color": "#8C7A63"},
    ],
    # A stolen blade, not a ninja's tool: `wandering_bandit` hits for 22 where
    # the training post hits for 8 (monster_template, migration V23), and the
    # silhouette should say so before the damage numbers do.
    "weapon": "long_blade",
    "weapon_color": "#C8CED6",
    "weapon_grip": "#3A2A1C",
}


def _clear_scene():
    """Drop everything the base file carries in, guides included.

    Same reason as `build_kage_character._clear_scene`: the scale guides are
    hide_render, so they never reach a render, but `fit_camera` measures
    bounding boxes rather than visibility and would frame the shot around them.
    """
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)


def build(out_path, outline_px_at_1x=1.1):
    _clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    rig = assemble_character(BANDIT_SPEC)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[mob] bandit saved: {0}".format(out_path))
    print("[mob] parts={0} bones={1}".format(len(meshes), len(rig.pose.bones)))
    print("[mob] mesh objects: " + ", ".join(sorted(obj.name for obj in meshes)))
    print("[mob] actions: " + ", ".join(sorted(action.name for action in bpy.data.actions)))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_monster_bandit")
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1,
                        help="outline width @1x px, saved into the .blend; 1.1 is the "
                             "measured kit-character width (build_kage_character)")
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    _args = _cli_args(sys.argv)
    if _args is not None:
        build(_args.out, _args.outline_px)
