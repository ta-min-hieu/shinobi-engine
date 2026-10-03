"""Build the `senior_sister` NPC — Tô Vân Nhi of Thanh Hư Tông (STORY.md §5).

The game's SECOND NPC. From Phase 6 until migration V37 the world held exactly
one (`town_elder`), so this is the first time the NPC branch of the art pipeline
has had to make two figures tell each other apart.

Why the mesh kit and not Rodin: same wall as `town_elder` on 2026-09-03 — the
free-trial key answers `API_INSUFFICIENT_FUNDS`, and a paid key is the one thing
still waiting on the project owner (docs/README "🔴 ĐANG CHỜ CHỦ DỰ ÁN" #12).
Nothing is spent, the kit is the deterministic offline path, and every step
after the mesh (rig, actions, toon, outline, LOD export) is shared with the
generated path — so `build_text3d_monster.py --code senior_sister` over a real
`.glb` later swaps the art without touching anything else.

Run headless (never in an MCP session — full file rebuild):

    blender -b --python-exit-code 1 assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_npc_senior_sister.py \
      -- --out assets/raw/blender/characters/senior-sister.blend
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_mesh_kit import assemble_character, pose_frame  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

# =============================================================================
# THE VALUE PATTERN IS INVERTED FROM THE ELDER'S, AND THAT IS THE WHOLE POINT
# =============================================================================
# Both NPCs stand in the same courtyard, both wear a Thanh Hư robe, and at the
# client's camera distance a player sees roughly a 50px figure. The lesson the
# project already paid for (ASSET-LOG 2026-09-06, `archive_warden` vs
# `stone_ogre`): at that size LIGHT/DARK LAYOUT is what separates two figures,
# and nudging the hue does nothing. So this is not "the elder's robe in another
# colour" — every value is swapped:
#
#   Chu Hoành  : DARK slate robe · WHITE hair · pale beard filling the chest
#   Tô Vân Nhi : LIGHT celadon robe · DARK hair · dark sash cutting the waist
#
# Read as a silhouette that is exactly the inverse, which is the only thing that
# survives being small.
#
# Deliberately outside the five element palettes (ART-PIPELINE §2.2) for the same
# reason the elder is: NPCs belong to no faction, and a player must not read a
# quest-giver as a Thủy or Phong character because they share a blue-green.
# Celadon (青瓷) sits between the two and belongs to neither.
SENIOR_SISTER_SPEC = {
    "name": "senior_sister",
    "skin": "#F0CDAA",
    "eye_color": "#2B2620",
    # `frontal` mirrors eye + brow onto the far cheek. Lesson 5 of the elder
    # rebuild: the kit builds ONE eye ("the other is hidden behind the skull"),
    # true for a pure profile and wrong for the game's 3/4 camera, where both
    # cheeks are visible and a one-eyed face reads as a face missing something.
    # Any NPC the player walks up to and TALKS to needs this on.
    "face_style": "frontal",
    # Dark hair against a light robe — the inversion above. `topknot` rather than
    # the elder's `topknot_bun`: a real spike instead of a knob, so the two heads
    # differ in OUTLINE and not only in colour.
    #
    # ⚠️ MEASURED, NOT PREFERRED. The first pass used `bang`, which is the obvious
    # choice for a young woman and which `runWorldPreview` rejected: the fringe
    # hangs over the brow, and at the client's downward camera it closed over the
    # whole eye line — the face came out as a bare patch of skin with a mouth.
    # `topknot` pulls the hair off the forehead and gives most of it back.
    "hair": {"style": "topknot", "color": "#2E2A2C"},
    # The under-layers stay MID-tone, not light. The robe over them is the light
    # mass; if the body under it were light too, the robe would stop being a
    # readable shape and the whole figure would flatten into one pale blob —
    # which is what happened to the elder's first pass in reverse (a white beard
    # on a light grey robe, ASSET-LOG 2026-09-03).
    "cloth": {"torso": "#8FA69C", "pelvis": "#6E8279", "legs": "#5E7168"},
    "shoe": "#3A4640",
    "accessories": [
        # The light mass. Ankle-length outer-sect robe.
        {"kind": "long_robe", "color": "#DCE5DC"},
        # ⚠️ TWO placements for the one accent colour, waist AND hem — the rule
        # learned on `wandering_bandit` and re-confirmed on the elder: a single
        # accent band survives as a two-pixel sliver once anything hangs over
        # it. The dark sash is also what stops the light robe from reading as
        # one undivided sheet from shoulder to ankle.
        {"kind": "robe_sash", "color": "#3F5A57"},
        {"kind": "robe_hem", "color": "#3F5A57"},
    ],
    # ⚠️ NO `cloak`, and that is a decision with a receipt. The cloak has now
    # eaten a character's face THREE times (kage_cloak_hides_the_face,
    # 2026-09-07): it is a dark mass built at head height, and at the client's
    # downward camera pitch it closes over the head. On the one NPC the player
    # stands still and talks to, that is the worst possible place to spend it.
    # `wide_sleeves` is also left out — it is the elder's silhouette, and a
    # three-years-in outer disciple who still carries water does not wear them.
    "weapon": None,
}


def _clear_scene():
    """Drop everything the base file carries in, guides included.

    Same reason as `build_npc_elder._clear_scene`: the scale guides are
    hide_render so they never reach a render, but `fit_camera` measures bounding
    boxes rather than visibility and would frame the shot around them.
    """
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)


def _author_upright_idle(rig):
    """Re-key `idle`: upright, hands loosely clasped, chin lifted.

    Two things are being solved here, and only one of them is taste.

    TASTE: she is not an old man. The elder's `idle` bends the spine +15 and
    folds the forearms hard so the hands vanish into opposite sleeves. Hers
    stays near vertical with a much shallower fold — hands loosely together in
    front, the posture of someone who has been standing around the practice
    yard, not someone holding themselves up.

    NOT TASTE: the CHIN. Lesson 4 of the elder rebuild, paid for in a rebuilt
    model — at yaw 0 in Blender the face is fine, and through the client's real
    renderer (camera pitched DOWN, ADR-031) the hair dome closes over it. The
    `head` is negative = chin up. It is keyed
    relative to a spine that is nearly upright. −30 is what `runWorldPreview`
    accepted, and that is the only instrument that counts (Blender stills have
    misled this project three separate times). The first pass tried −20 and the
    hair still covered the eye line; the chin lift and the `topknot` swap were
    BOTH needed, and neither was enough alone.

    ⚠️ WHAT IS STILL NOT FIXED, recorded honestly: at the client's camera her
    EYES are still not visible — the face reads as skin plus a mouth. That is not
    specific to her. `town_elder` (shipped 2026-09-03) has exactly the same face
    at exactly the same distance; it is a limit of `kage_mesh_kit`'s eye geometry
    under a downward camera, not of this character. The player models do not have
    it because they are Text→3D with a baked face texture — which makes this the
    most concrete argument yet for the paid Rodin key still waiting on the owner.
    """
    rig.animation_data.action = bpy.data.actions["idle"]
    stance = {"leg_r": 3, "leg_l": -3, "shin_r": -2, "shin_l": 1}
    breath = (
        (1, 3, -30, 17, 21, -30, -33, 0.0, 1.0),
        (5, 4, -31, 16, 20, -32, -35, 0.008, 1.004),
        (9, 5, -32, 15, 19, -33, -36, 0.012, 1.007),
        (13, 4, -31, 16, 20, -32, -35, 0.006, 1.003),
        (17, 3, -30, 17, 21, -30, -33, 0.0, 1.0),
    )
    for frame, spine, head, arm_l, arm_r, fore_l, fore_r, root_z, root_scale in breath:
        pose_frame(rig, frame,
                   dict(stance, spine=spine, head=head, arm_l=arm_l, arm_r=arm_r,
                        forearm_l=fore_l, forearm_r=fore_r),
                   root_z=root_z, root_scale_z=root_scale)


def build(out_path, outline_px_at_1x=1.1):
    _clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    rig = assemble_character(SENIOR_SISTER_SPEC)
    _author_upright_idle(rig)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[npc] senior_sister saved: {0}".format(out_path))
    print("[npc] parts={0} bones={1}".format(len(meshes), len(rig.pose.bones)))
    print("[npc] mesh objects: " + ", ".join(sorted(obj.name for obj in meshes)))
    print("[npc] actions: " + ", ".join(sorted(action.name for action in bpy.data.actions)))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_npc_senior_sister")
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1,
                        help="outline width @1x px, saved into the .blend; 1.1 is the "
                             "measured kit-character width (build_kage_character)")
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    _args = _cli_args(sys.argv)
    if _args is not None:
        build(_args.out, _args.outline_px)
