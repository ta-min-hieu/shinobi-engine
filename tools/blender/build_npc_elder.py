"""Build the `town_elder` NPC — elder Chu Hoành of Thanh Hư Tông (STORY.md §5).

Replaces a hand-boxed model that never met ART-QUALITY-STANDARD §2: no face
(Q2), no readable hair (Q3), one flat colour region (Q4) and a bare box body
(§3 forbids it). That was pre-existing debt, not something the theme pivot
created — but ADR-038 raised the stakes, because this NPC now leads the whole of
arc 1, is the first person the player ever meets, and his death is the climax of
arc 5. A grey box cannot carry that.

Why the mesh kit and not Rodin, which ART-AUDIT-TU-TIEN §5 #3 budgeted a credit
for: the free-trial key answered `API_INSUFFICIENT_FUNDS` on 2026-09-03, the
same wall `wandering_bandit` hit on 2026-08-30. Nothing was spent. The kit is
the deterministic offline path and every downstream step (rig, actions, toon,
outline, LOD export) is shared with the generated one, so re-running
`build_text3d_monster.py --code town_elder` over a real `.glb` later swaps the
art without touching anything else.

Run headless (never in an MCP session — full file rebuild):

    blender -b assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_npc_elder.py \
      -- --out assets/raw/blender/characters/town-elder.blend

The kit's eleven-bone rig replaces the old two-bone one. The NPC is still static
and the client still only ever plays `idle`, but `assemble_character` authors the
standard seven and there is no reason to strip six of them back out — an unused
action costs nothing at runtime and the day someone wants the elder to walk, it
is already there.
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_mesh_kit import assemble_character, pose_frame  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

# Colours are read straight off STORY.md §5: "đạo bào xám bạc", white hair in a
# topknot, long beard. Deliberately outside the five element palettes
# (ART-PIPELINE §2.2) — the elder belongs to no faction, and a player must not
# read the quest-giver as a Thủy character because they share a blue-grey.
#
# Five separate tonal regions, which is the specific thing the old box failed:
# warm skin, grey hair, near-white beard, slate robe, darker skirt, jade sash.
# Hair and beard are deliberately DIFFERENT whites: identical ones merged the
# skull and the chin into a single pale mass with a face somewhere inside it. The
# robe is DARK on purpose — a white beard needs something to be white against,
# and the first pass put a white beard on a light grey robe and lost it.
ELDER_SPEC = {
    "name": "elder",
    "skin": "#E8C7A2",
    "eye_color": "#3A3226",
    "face_style": "frontal",
    "hair": {"style": "topknot_bun", "color": "#C6C3B8"},
    "cloth": {"torso": "#4F5A6B", "pelvis": "#3E4756", "legs": "#37404D"},
    "shoe": "#262A31",
    "accessories": [
        {"kind": "long_robe", "color": "#3E4756"},
        {"kind": "wide_sleeves", "color": "#5B6779"},
        # Jade, and the only warm-cool break in the figure. The first pass had
        # a near-black sash against a light grey robe and the whole elder came
        # out one grey mass — the Q4 failure the old box model had. It gets TWO
        # placements, waist and hem, for the same reason the bandit's red does
        # (ASSET-LOG 2026-08-30): the sash alone survived as a two-pixel sliver
        # because the wide sleeves hang straight over it.
        {"kind": "robe_sash", "color": "#2F6B5A"},
        {"kind": "robe_hem", "color": "#2F6B5A"},
        # Beard last so it is built after the robe and sits in front of it in
        # the object order; at this scale the two overlap around the collar.
        {"kind": "beard", "color": "#F2F0E6"},
    ],
    "weapon": None,
}

# The broom he is sweeping with when arc 1 opens (STORY.md §6) is deliberately
# NOT part of this mesh. It belongs on the rig as a detachable prop, the way
# every player weapon already is, so a later scene can take it out of his hands
# without rebuilding the character.


def _clear_scene():
    """Drop everything the base file carries in, guides included.

    Same reason as `build_monster_bandit._clear_scene`: the scale guides are
    hide_render so they never reach a render, but `fit_camera` measures bounding
    boxes rather than visibility and would frame the shot around them.
    """
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)


def _author_stooped_idle(rig):
    """Re-key `idle` with an old man's posture, over the kit's default.

    The stoop is the one thing that has to be in the ANIMATION rather than the
    mesh: the kit authors every part in world space against an upright rig, so
    bending him at build time would mean re-deriving every vertex. Bending the
    spine bone in the pose costs nothing and the skinning follows.

    Only `idle` is re-authored, because it is the only action this NPC ever
    plays. Arms come forward and the forearms fold well past the kit's default
    so the hands meet in front — hands tucked into opposite sleeves, which is
    how an elder stands and is also what stops two bare forearms from reading as
    a fighting stance.
    """
    rig.animation_data.action = bpy.data.actions["idle"]
    stance = {"leg_r": 4, "leg_l": -4, "shin_r": -3, "shin_l": 2}
    # Head values are large negatives (chin UP) and that is measured, not taste.
    # `head` is relative to a spine already bent +15 forward, and the game camera
    # looks DOWN on the character (runWorldPreview, 2026-09-03): at the kit's
    # default head angle the hair dome hides the whole face from that pitch —
    # the same thing that happens to `wandering_bandit`, and tolerable on a
    # monster you only ever see across a fight, but not on the NPC the player
    # walks up to and talks to. Tilting the chin up turns the face into the
    # camera and swings the beard clear of it at the same time.
    breath = (
        (1, 15, -26, 22, 26, -40, -44, 0.0, 1.0),
        (5, 16, -27, 21, 25, -42, -46, 0.008, 1.004),
        (9, 17, -28, 20, 24, -43, -47, 0.012, 1.007),
        (13, 16, -27, 21, 25, -42, -46, 0.006, 1.003),
        (17, 15, -26, 22, 26, -40, -44, 0.0, 1.0),
    )
    for frame, spine, head, arm_l, arm_r, fore_l, fore_r, root_z, root_scale in breath:
        pose_frame(rig, frame,
                   dict(stance, spine=spine, head=head, arm_l=arm_l, arm_r=arm_r,
                        forearm_l=fore_l, forearm_r=fore_r),
                   root_z=root_z, root_scale_z=root_scale)


def build(out_path, outline_px_at_1x=1.1):
    _clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    rig = assemble_character(ELDER_SPEC)
    _author_stooped_idle(rig)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[npc] elder saved: {0}".format(out_path))
    print("[npc] parts={0} bones={1}".format(len(meshes), len(rig.pose.bones)))
    print("[npc] mesh objects: " + ", ".join(sorted(obj.name for obj in meshes)))
    print("[npc] actions: " + ", ".join(sorted(action.name for action in bpy.data.actions)))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_npc_elder")
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1,
                        help="outline width @1x px, saved into the .blend; 1.1 is the "
                             "measured kit-character width (build_kage_character)")
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    _args = _cli_args(sys.argv)
    if _args is not None:
        build(_args.out, _args.outline_px)
