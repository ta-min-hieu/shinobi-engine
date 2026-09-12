"""Build the `blood_sand_scout` monster (migration V40, `monster_template`) from
the mesh kit — "Thám Tử Huyết Sa", the Blood Sand scout guarding the abandoned
cave at x=66.

Why it exists at all, rather than borrowing a model: the draft of V40 gave this
species `mob_wandering-bandit` and wrote the reuse down as a debt, citing V23 §3
and V30 §1. That precedent has since been replaced by a harder rule —
`PostgresMonsterCatalogAdapterIntegrationTest.shouldNotLetAnyTwoSpeciesShareAModelKey`
forbids two species sharing a `model_key`, and its own javadoc says what to do
when it goes red: build the art, then ship the migration. So this script is the
first half of that, written before the migration was allowed to land.

Lore it has to read as (STORY.md §6 arc 2, §5 "Hộ pháp/đàn chủ Huyết Sa"): a
person, not a Linh Triều machine — a cultivator of the Blood Sand sect posted to
watch a cave where disciples have been going missing. This is the first time the
name Blood Sand appears anywhere the player can see it, so the silhouette is
doing narrative work: it must read as "someone who does not belong to the sect"
at a glance.

Three decisions, each falling out of a rule already paid for:

  * **Invert the value pattern against `wandering_bandit`, do not shift the
    hue.** Both are person-shaped chibis of the same kit, so at the client's
    ~64px they collapse into each other unless light and dark swap places —
    the lesson from `archive_warden` vs `stone_ogre` (ASSET-LOG 2026-09-06) and
    again from the two sect NPCs. The bandit is mid-brown leather over
    road-dust cloth with a small red accent and an exposed face. This one is the
    opposite read: near-black robe, a face covered by a mask, and the red worn
    as a LARGE chest field rather than a sliver.
  * **Red is the faction, so it cannot be a two-pixel accent.** `wandering_bandit`
    needed the same accent twice (scarf plus sash) just to survive at chibi
    scale. Here the red has to be the thing you name the enemy by, so it goes on
    the torso itself with the darker robe around it.
  * **Colours stay outside the five element palettes** (ART-PIPELINE §2.2), same
    as the bandit: the player must never read an enemy as one of their own kit.
    Blood Sand red is deliberately a dried, brownish crimson rather than the
    clean scarlet of the Hoa faction.

Run headless (never in an MCP session — full file rebuild):

    blender -b assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_monster_blood_sand_scout.py \
      -- --out assets/raw/blender/monsters/blood-sand-scout.blend

Then the three LODs (ART-PIPELINE §6.5 — `--flat-materials` is mandatory for a
kit-built mesh, `--extras all` because naming the parts by hand is how
`bandit_SashTail` went missing):

    blender -b assets/raw/blender/monsters/blood-sand-scout.blend \
      -P tools/blender/export_character_lods.py -- \
      --object scout_Torso --rig scout_Rig --extras all --flat-materials \
      --asset-key mob_blood-sand-scout --out <ABSOLUTE>/assets/exported/models
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

# Same shape as `build_monster_bandit.BANDIT_SPEC`, and kept here for the same
# reason: `build_kage_character.CHARACTERS` IS the five playable factions, and a
# monster dropped into that table would be picked up by anything iterating it.
SCOUT_SPEC = {
    "name": "scout",
    # Paler than the bandit's #E0B183 and barely visible anyway — the mask
    # covers most of it. The point is that what little skin shows reads cold.
    "skin": "#C9A98E",
    # The one bright thing on the model. A masked face with dark eyes is a
    # featureless blob at 64px; amber eyes over a dark mask is a face.
    "eye_color": "#D8A23A",
    "hair": {"style": "topknot", "color": "#14100E"},
    # Near-black robe with the dried-blood field on the torso. This is the
    # inversion: the bandit is mid-tone cloth with a dark accent, this is a dark
    # body with ONE large warm field.
    "cloth": {"torso": "#8E2A24", "pelvis": "#1A1614", "legs": "#221C1A"},
    "shoe": "#14100E",
    # Mask first: it is the single feature that says "not of the sect" before
    # any colour registers. Sash second so the dried red repeats below the
    # chest field and the silhouette stays readable when the torso is turned.
    "accessories": [
        {"kind": "scarf", "color": "#1A1614"},
        {"kind": "sash", "color": "#6E201B"},
        {"kind": "arm_wrap", "color": "#1A1614"},
    ],
    # Twin short blades against the bandit's single long one. At this size the
    # weapon is half the silhouette, so two person-shaped enemies carrying the
    # same shape would undo everything the colours just did. (The kit builds
    # kunai / gourd / twin_blades / long_blade / stone_mace — a first draft of
    # this file asked for a "curved_blade" that does not exist.)
    "weapon": "twin_blades",
    "weapon_color": "#9AA2AA",
    "weapon_grip": "#3A1714",
}


def _clear_scene():
    """Drop everything the base file carries in, guides included.

    Same reason as `build_monster_bandit._clear_scene`: the scale guides are
    hide_render so they never reach a render, but `fit_camera` measures bounding
    boxes rather than visibility and would frame the shot around them.
    """
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)


def build(out_path, outline_px_at_1x=1.1):
    _clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    rig = assemble_character(SCOUT_SPEC)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[mob] blood_sand_scout saved: {0}".format(out_path))
    print("[mob] parts={0} bones={1}".format(len(meshes), len(rig.pose.bones)))
    print("[mob] mesh objects: " + ", ".join(sorted(obj.name for obj in meshes)))
    print("[mob] actions: " + ", ".join(sorted(action.name for action in bpy.data.actions)))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_monster_blood_sand_scout")
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1,
                        help="outline width @1x px, saved into the .blend; 1.1 is the "
                             "measured kit-character width (build_kage_character)")
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    _args = _cli_args(sys.argv)
    if _args is not None:
        build(_args.out, _args.outline_px)
