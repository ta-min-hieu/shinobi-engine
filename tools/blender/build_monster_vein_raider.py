"""Build the `vein_raider` monster (migration V45, `monster_template`) from the
mesh kit — "Tán Tu Đoạt Mạch", the organised rogue cultivator camping the primary
spirit vein at x=47.

Lore row it has to satisfy (STORY.md §7, the lore<->system table): *"Tán tu cường
đạo — tu sĩ không tông môn, sống bằng cướp ở rìa linh mạch"*, crossed with *"5
Linh Mạch — yêu thú tụ theo thuộc tính"*. This one is the same family as
`wandering_bandit` but a rank up: not a lone thief on the road, someone who has
taken a spirit vein and holds it. That is the whole reason the species exists —
arc 1's remaining stretch (Lv10-18, STORY.md §6) had no enemy above Lv8.

Why NOT another Linh Triều machine, which was the first idea: there are already
three (`stone_ogre`, `archive_warden`, `trial_construct`) and all three live in
the same grey stone / moss / amber space. A fourth would be the
`archive_warden` vs `stone_ogre` mistake again (ASSET-LOG 2026-09-06), at a size
where the player has ~64px to tell them apart.

Three decisions, each falling out of a rule already paid for:

  * **Invert the value pattern against `wandering_bandit`, do not shift the
    hue.** The bandit is mid-brown leather over road-dust cloth, dark at the
    hem, with a red scarf and sash. This one is the opposite read: a PALE
    bleached-hemp robe carrying the whole silhouette, with the dark worn as a
    narrow harness across it. Light body / dark accent versus the bandit's
    mid body / dark hem. Two brown person-shaped chibis would collapse into
    each other no matter how the browns differ.
  * **Change the weapon silhouette, not just the colours.** At chibi scale the
    weapon is half the outline, so a second `long_blade` would undo the value
    work on its own — the exact argument `blood_sand_scout` used to pick
    `twin_blades`. `stone_mace` is the heaviest shape the kit builds and says
    "hits harder than the bandit" before the damage numbers do. (The kit builds
    kunai / gourd / twin_blades / long_blade / stone_mace and nothing else.)
  * **Colours stay outside the five element palettes** (ART-PIPELINE §2.2:
    Hỏa #E8552D/#FFB13D, Thủy #2D7FE8/#7FD4FF, Phong #3DBF7A/#CFF5E0,
    Lôi #8C4DE8/#FFE84D, Thổ #A6763D/#E0C089) so the player never reads an enemy
    as their own kit. The accent here is a cold SLATE, chosen because both
    accents already in use on people are warm reds — the bandit's #B33B3B and
    Blood Sand's dried #6E201B — and a third red would be a third thing that
    looks like the first two.

⚠️ Deliberately NOT a new proper name. STORY.md §8 rule 3 makes a new proper
name an owner decision taken in the same PR, and two of those are already
waiting ("Lý Tam", "Hình Vân Sơn"). A species CATEGORY needs only a §7 row,
which §7/§8 say to add per-PR — so this ships without adding to the queue.

Run headless (never in an MCP session — full file rebuild):

    blender -b assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_monster_vein_raider.py \
      -- --out assets/raw/blender/monsters/vein-raider.blend

Then the three LODs (ART-PIPELINE §6.5 — `--flat-materials` is mandatory for a
kit-built mesh, `--extras all` because naming the parts by hand is how
`bandit_SashTail` went missing):

    blender -b assets/raw/blender/monsters/vein-raider.blend \
      -P tools/blender/export_character_lods.py -- \
      --object raider_Torso --rig raider_Rig --extras all --flat-materials \
      --asset-key mob_vein-raider --out <ABSOLUTE>/assets/exported/models
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

# ⚠️ TWO ROUNDS OF B WENT INTO THIS SPEC (2026-09-13). Both are recorded because
# the first correction was reasoned from a control nobody had actually looked at.
#
# Round B #1 — the first spec was PALE-BODIED. At 64px it collapsed into a dark
# blob with a small pale core, and that was written down as "pale bodies cannot
# survive the outline, mid-value can". **That conclusion was wrong.** Rendering
# the APPROVED `wandering_bandit` at the same 64px shows the same collapse: at
# @0.5x every kit chibi is a dark silhouette with a small warm core, because the
# Freestyle outline is a fixed pixel width and eats a fixed fraction of a 64px
# figure. Q10 at 64px therefore does not separate these species at all, and a
# FAIL declared against it is a FAIL against the whole roster.
#
# ⇒ Rule actually worth keeping: **never grade an asset against a remembered
# standard — render an already-approved asset through the identical command and
# put them side by side.** It costs one render and it is the difference between
# fixing a real defect and inventing one.
#
# Round B #2 — the real defect, visible at 256px where the grading happens: the
# pale accents (sash, scarf collar) were too SMALL to read, so the figure was one
# slate mass and drifted toward `blood_sand_scout`'s "dark body + one accent"
# shape. Fix: put the pale on the element that already projects out of the
# silhouette, so the accent and the silhouette-break are the same object.

# The body. A cold slate — the one mid-tone family no approved asset occupies:
# `wandering_bandit` is warm brown, `blood_sand_scout` near-black plus red, and
# all three machines are grey/moss/amber. Clear of Thủy's #2D7FE8 (that is a
# bright saturated blue; this is a desaturated grey-teal).
SLATE = "#4E5A62"
SLATE_DEEP = "#3A444B"
SLATE_MID = "#434E55"
# Bleached hemp, now an ACCENT rather than the body. Greyed well clear of Thổ's
# #E0C089 so it never reads as an earth-faction robe.
PALE_HEMP = "#C6C0AE"
# Boots and wraps. Dark, but small — it is no longer competing to be the body.
DARK_LEATHER = "#2B2620"

RAIDER_SPEC = {
    # `raider` and not `vein_raider`: the prefix goes into every datablock name,
    # and the exporter is handed `raider_Torso` / `raider_Rig` by hand.
    "name": "raider",
    # Weathered, a shade redder than the bandit's #E0B183 — someone who lives
    # outdoors at altitude rather than on a road.
    "skin": "#D2A57E",
    # Dark eyes: the face is uncovered here, which is itself a separator from
    # `blood_sand_scout`'s mask. A tán tu is not hiding, he has simply taken
    # something and expects to keep it.
    "eye_color": "#231B14",
    # Two-eyed game-camera face + chin up in idle — see `build_monster_bandit`
    # (ASSET-LOG 2026-10-01, second pass).
    "face_style": "frontal",
    "idle_chin_lift": 28,
    # `topknot_bun` — the third distinct head shape among the people in the
    # roster, and a tighter mass than a loose topknot, which is what a fighter
    # reads as. Tighter also means less dark area crowding the face, which is
    # half of why round B #1 was needed.
    "hair": {"style": "topknot_bun", "color": "#2A241E"},
    # Mid-value cold body, stepping down to the hem. This is the part that has
    # to survive the outline at 64px.
    "cloth": {"torso": SLATE, "pelvis": SLATE_DEEP, "legs": SLATE_MID},
    "shoe": DARK_LEATHER,
    "accessories": [
        # THE accent and the silhouette-break in ONE object: pale sleeves that
        # project sideways out of the body. Round B #1 had these dark and put the
        # pale on small parts instead — at 256px that pale did not read at all
        # and the figure collapsed into a single slate mass, drifting toward
        # `blood_sand_scout`'s "dark body + one accent" shape, which is the one
        # thing this species must not look like.
        # A `cloak` was rejected for this job: it merges with the hair mass into
        # one dark cap and buries the face (the B3 lesson, ASSET-LOG 2026-09-07).
        {"kind": "wide_sleeves", "color": PALE_HEMP},
        # Pale collar repeats the accent high, where the eye lands first — one
        # accent colour needs two placements, the lesson the bandit paid for with
        # its scarf. Now that the sleeves carry the accent, this reads as a
        # repeat instead of being the whole attempt.
        {"kind": "scarf", "color": PALE_HEMP},
        # Dark sash, and it is dark ON PURPOSE: its job is to SEPARATE the pale
        # sleeves from the pale collar so they do not merge into one field down
        # the front. The one place the figure steps darker rather than lighter.
        {"kind": "sash", "color": SLATE_DEEP},
        # Dark forearm wraps so the pale sleeves do not run straight into the
        # hands and end the arm as one undifferentiated pale block.
        {"kind": "arm_wrap", "color": DARK_LEATHER},
    ],
    # Kept, but now carried by CONTRAST rather than by shape: `long_blade` is
    # the bandit's and `twin_blades` the scout's, so the mace is what is left.
    # It sits across the waist INSIDE the body mass (that is how the kit mounts
    # it, not a colour choice), so the only way it reads at all is a pale head
    # against a dark body — which round B #1 had exactly backwards.
    "weapon": "stone_mace",
    "weapon_color": "#C3C7CB",
    "weapon_grip": "#2B2620",
}


def _clear_scene():
    """Drop everything the base file carries in, guides included.

    Same reason as `build_monster_blood_sand_scout._clear_scene`: the scale
    guides are hide_render so they never reach a render, but `fit_camera`
    measures bounding boxes rather than visibility and would frame the shot
    around them.
    """
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)


def build(out_path, outline_px_at_1x=1.1):
    _clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    rig = assemble_character(RAIDER_SPEC)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[mob] vein_raider saved: {0}".format(out_path))
    print("[mob] parts={0} bones={1}".format(len(meshes), len(rig.pose.bones)))
    print("[mob] mesh objects: " + ", ".join(sorted(obj.name for obj in meshes)))
    print("[mob] actions: " + ", ".join(sorted(action.name for action in bpy.data.actions)))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_monster_vein_raider")
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1,
                        help="outline width @1x px, saved into the .blend; 1.1 is the "
                             "measured kit-character width (build_kage_character)")
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    _args = _cli_args(sys.argv)
    if _args is not None:
        build(_args.out, _args.outline_px)
