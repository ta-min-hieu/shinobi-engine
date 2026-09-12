"""Build the `blood_sand_enforcer` monster (migration V41, `monster_template`)
from the mesh kit - "Chap su Hinh Van Son", the sect steward who is the climax
of arc 2.

Why it exists rather than borrowing `mob_blood-sand-scout`: the draft of V41
gave this species the scout's model key, and
`PostgresMonsterCatalogAdapterIntegrationTest.shouldNotLetAnyTwoSpeciesShareAModelKey`
went red for it - exactly as that test's javadoc says it will. The documented
fix is to build the art and then ship the migration, so this is the first half.

Lore it has to read as (STORY.md section 6, arc 2): a Blood Sand enforcer
WEARING THE SECT'S OWN UNIFORM. He is the steward who closed the missing-
disciple file in quest 7 by ruling that the disciples had run away, and quest 9
is where the player brings him the tally-slip and he turns.

That single fact inverts the usual monster brief. Every other hostile in the
game is built to read as "not one of us" at a glance - `blood_sand_scout` spends
its whole silhouette on exactly that. This one must read as SECT OFFICIALDOM, or
the twist is spoiled before the dialogue reaches it. The design problem is the
opposite one: hide him in the crowd, and plant one detail that is wrong on the
second look.

Four decisions, each against a rule already paid for:

  * He is measured against the two SECT NPCs, not against the scout. Those are
    who the player will compare him to. The elder is light hair over a dark
    blue-grey robe; the senior sister is dark hair over a near-white robe. This
    one is dark hair over a MID-tone ash robe - a third point on that axis
    rather than a repeat of either, so three sect figures stay separable at the
    client's ~64px (the value-pattern rule from `archive_warden` vs
    `stone_ogre`, ASSET-LOG 2026-09-06).
  * The faction red is a SLIVER here, deliberately the opposite of the scout.
    On the scout the dried red had to be a large chest field, because red IS the
    enemy's name and a two-pixel accent would not survive. Here the same
    #6E201B goes on the waist sash only, where it passes for ordinary robe trim.
    It is the tell, not the announcement - and it is the same hex as the scout's
    sash on purpose, so a player who has met the scout has genuinely been shown
    the answer.
  * `bamboo_slips`, not a weapon of office. He is a record-keeper: the slips at
    his waist are the same kind of tally the player spent quest 6 recovering,
    and they are what makes "the steward who closed the file" legible on the
    mesh. A `shoulder_plate` was the first choice and is wrong - armour reads as
    guard, which is the one thing his disguise cannot read as.
  * Three accessories, no more. ASSET-LOG 2026-09-12 measured the scout's LOW
    tier at 4.982 against a 5.000 ceiling - 18 triangles of headroom - and wrote
    down that one more accessory on any kit character goes over, and that the
    fix is to spend less rather than to raise the ceiling.

`stone_mace` is the weapon because the kit has no staff: the script calls his
disguised move "truong phap Hinh Duong" (staff technique) and the mace is the
only rod-shaped thing `_WEAPON_BUILDERS` makes (kunai / gourd / twin_blades /
long_blade / stone_mace). It also keeps him clear of the scout's twin blades and
the bandit's single long blade, which matters because at 64px the weapon is half
the silhouette.

Run headless (never in an MCP session - full file rebuild):

    blender -b assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_monster_blood_sand_enforcer.py \
      -- --out assets/raw/blender/monsters/blood-sand-enforcer.blend

Then the three LODs (ART-PIPELINE section 6.5 - `--flat-materials` is mandatory
for a kit-built mesh, `--extras all` because naming the parts by hand is how
`bandit_SashTail` went missing):

    blender -b assets/raw/blender/monsters/blood-sand-enforcer.blend \
      -P tools/blender/export_character_lods.py -- \
      --object enforcer_Torso --rig enforcer_Rig --extras all --flat-materials \
      --asset-key mob_blood-sand-enforcer --out <ABSOLUTE>/assets/exported/models
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

# Same shape as `build_monster_blood_sand_scout.SCOUT_SPEC`, and kept out of
# `build_kage_character.CHARACTERS` for the same reason: that table IS the five
# playable factions, and anything iterating it would pick a monster up.
ENFORCER_SPEC = {
    "name": "enforcer",
    # Ordinary, healthy, sect-normal. The scout's skin reads cold because the
    # mask leaves almost none of it showing; this face is fully visible.
    "skin": "#E3C29C",
    # NOT the scout's amber. Amber over a dark mask is that model's one bright
    # feature and the thing you remember it by - reusing it here would hand the
    # player the connection before the dialogue does.
    "eye_color": "#3A2A22",
    # The player walks up and TALKS to him, so `frontal` is mandatory: the kit
    # builds ONE eye ("the other is hidden behind the skull"), which is right
    # for a pure profile and wrong for the game's 3/4 camera - lesson 5 of the
    # elder rebuild, re-confirmed on the senior sister.
    "face_style": "frontal",
    # Dark hair, like the sister's and unlike the elder's pale one.
    # `topknot_bun` is the elder's OUTLINE and that is the point: a functionary
    # of the same sect wears the same knot. The two stay apart on VALUE (his
    # hair is near-black against the elder's #C6C3B8) rather than on shape.
    "hair": {"style": "topknot_bun", "color": "#1C1A19"},
    # Mid-tone under-layers under a mid-light robe. The elder is dark-on-dark,
    # the sister is mid-on-light; this is the third reading.
    "cloth": {"torso": "#6B7078", "pelvis": "#565B62", "legs": "#494D53"},
    "shoe": "#2A2D31",
    "accessories": [
        # The ash robe: sect uniform, mid-light so he sits between the elder and
        # the sister rather than beside either.
        {"kind": "long_robe", "color": "#B9B4A6"},
        # THE TELL. Same dried crimson as `blood_sand_scout`'s sash, worn small
        # enough to pass for robe trim. Everywhere else in this project an
        # accent colour gets TWO placements so it survives at chibi scale (the
        # bandit's scarf+sash, the elder's sash+hem). Here it gets ONE, on
        # purpose and against that rule: it is supposed to be easy to miss on
        # the first look and obvious on the second.
        {"kind": "robe_sash", "color": "#6E201B"},
        # The records. Same tallies the player recovered in quest 6 - this is
        # what makes "the steward who closed the file" readable on the mesh.
        {"kind": "bamboo_slips", "color": "#9A7B4A"},
    ],
    # No staff in the kit; the mace is the only rod-shaped weapon it builds, and
    # it keeps him clear of the scout's twin blades at a glance.
    "weapon": "stone_mace",
    "weapon_color": "#6E6A63",
    "weapon_grip": "#2E2724",
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
    rig = assemble_character(ENFORCER_SPEC)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[mob] blood_sand_enforcer saved: {0}".format(out_path))
    print("[mob] parts={0} bones={1}".format(len(meshes), len(rig.pose.bones)))
    print("[mob] mesh objects: " + ", ".join(sorted(obj.name for obj in meshes)))
    print("[mob] actions: " + ", ".join(sorted(action.name for action in bpy.data.actions)))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_monster_blood_sand_enforcer")
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1,
                        help="outline width @1x px, saved into the .blend; 1.1 is the "
                             "measured kit-character width (build_kage_character)")
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    _args = _cli_args(sys.argv)
    if _args is not None:
        build(_args.out, _args.outline_px)
