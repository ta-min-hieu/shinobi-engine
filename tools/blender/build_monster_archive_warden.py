"""Build the `archive_warden` monster (migration V30, `monster_template`) from
the mesh kit — "Thạch vệ Tàng Thư", the small stone guard of the ruined archive.

Why it exists: V30 shipped the species pointing at `mob_stone-ogre`, and wrote
that down as a visible debt (its own §2 comment, plus a test that asserts the
borrowed key so the debt lives in CI rather than in somebody's memory). In the
running game the two species were the same model. This script is the other half
of paying that back; the migration that flips `model_key` is V31.

Lore it has to read as (STORY.md §7, approved text): the same Linh Triều
machine-lineage as `stone_ogre`, built SMALL to guard a book vault, its master
dead a thousand years and still standing its post.

Three decisions that come straight out of that sentence:

  * **Family, not clone.** Shared with the ogre: grey granite body, amber eyes,
    moss growing on the shoulders. Not shared: everything about the build. The
    ogre is a Rodin mesh with a baked 2048 albedo; this is flat-coloured kit
    geometry, so the two never collapse into each other even at 64px.
  * **"Small" cannot be scale.** `MonsterPresenter.MODEL_HEIGHT_UNITS` is a
    constant 1.0 — every monster is drawn the same height, and a taller model
    only pushes the yellow target marker into its own skull (ASSET-LOG,
    2026-08-30). Small therefore has to read as PROPORTION: the kit chibi is
    big-headed, short-limbed and narrow, which is exactly how a scaled-down
    version of a heavy thing looks once it is normalised back to one height. The
    ogre is nearly twice as wide at the same height; standing them side by side
    is what sells the size difference.
  * **Archive, not just guard.** A bundle of bamboo slips fanned off the back
    and an engraved stele slab on the chest. Both are in the silhouette on
    purpose — a book motif painted flat on a grey body is gone the moment the
    camera moves.

Why the kit and not Text->3D (ART-QUALITY-STANDARD §0 prefers the generated
path): the Rodin key still answers `API_INSUFFICIENT_FUNDS` — the same wall
`wandering_bandit` hit on 2026-08-30 and `town_elder` hit again on 2026-09-03.
The kit is the offline deterministic fallback and every step after the mesh
(rig, actions, toon, outline, LOD export) is shared, so
`build_text3d_monster.py --code archive_warden` over a real `.glb` later swaps
the art without touching anything else.

Run headless (never in an MCP session — full file rebuild):

    blender -b assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_monster_archive_warden.py \
      -- --out assets/raw/blender/monsters/archive-warden.blend

Then the three LODs (ART-PIPELINE §6.5 — `--flat-materials` is mandatory for a
hand-built mesh, and `--extras all` because naming forty parts by hand is how
`bandit_SashTail` went missing):

    blender -b assets/raw/blender/monsters/archive-warden.blend \
      -P tools/blender/export_character_lods.py -- \
      --object warden_Torso --rig warden_Rig --extras all --flat-materials \
      --asset-key mob_archive-warden --out <ABSOLUTE>/assets/exported/models
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

# Granite in three values so the body does not flatten into one blob, plus the
# two colours that carry the read: warm bamboo tan against cold grey, and
# cinnabar where a seal would be. Deliberately outside the five element palettes
# (ART-PIPELINE §2.2) for the same reason as `wandering_bandit` — a player must
# never mistake an enemy for their own faction kit.
STONE_SKIN = "#9C9D96"
STONE_TORSO = "#8C8D86"
STONE_PELVIS = "#7C7D77"
STONE_LEGS = "#868780"
STONE_BASE = "#66675F"
STONE_CREST = "#6C6D66"
TABLET_PALE = "#B0B1A6"
MOSS = "#5E7A4A"
BAMBOO = "#C9A96A"
CINNABAR = "#B0402E"
AMBER = "#E8A33C"
SHARD_GREY = "#9AA0A6"

WARDEN_SPEC = {
    "name": "warden",
    "skin": STONE_SKIN,
    # Carved knobs along the crown and NO hair mass. `cap: False` is not a style
    # choice — the dome is cut for a level camera and the client's looks down, so
    # on a bald skull it renders as a brim over the whole face. Seen in
    # `runWorldPreview`, invisible in Blender, exactly like the elder's hair.
    "hair": {"style": "carved_crest", "color": STONE_CREST, "cap": False},
    # No anime face — this thing is a machine, and it wears `stone_visor` below.
    # `frontal` was tried first and rejected by `runWorldPreview`: the kit's face
    # is authored for a profile, so at the client's near-head-on yaw both eyes
    # slide out onto the edges of the skull and the head reads as a blank egg.
    "face_style": "none",
    "cloth": {"torso": STONE_TORSO, "pelvis": STONE_PELVIS, "legs": STONE_LEGS},
    "shoe": STONE_BASE,
    "accessories": [
        # The face. Amber in the slots is the one detail copied from the ogre on
        # purpose: it is the only warm light on either machine, and what says
        # "still running" on a body that is otherwise a rock.
        {"kind": "stone_visor", "color": STONE_CREST, "accent": AMBER},
        # Moss on the shoulders is the ogre's signature and the thousand years
        # in one shape. Two entries, not one: `shoulder_plate` builds a single
        # pauldron on the bone it is given.
        {"kind": "shoulder_plate", "color": MOSS, "bone": "arm_r"},
        {"kind": "shoulder_plate", "color": MOSS, "bone": "arm_l"},
        # Pale slab against the mid-grey torso — same-value blocks stacked on
        # each other are one lump at 64px (ASSET-LOG 2026-09-03, `icon_genin_headband`).
        {"kind": "stone_tablet", "color": TABLET_PALE, "accent": CINNABAR},
        {"kind": "bamboo_slips", "color": BAMBOO, "accent": CINNABAR},
    ],
    # A guard carries something. The stone mace is the kit's one faceted,
    # low-segment weapon, so it stays in the same material story as the body
    # instead of putting a steel blade on a rock.
    "weapon": "stone_mace",
    "weapon_color": "#8A8B85",
    "weapon_grip": "#6A5B45",
    # Two chunks orbiting the head, already in the kit as the Thổ B3 accent. On a
    # construct they stop being jewellery and become the thing that says the
    # mechanism never stopped — which is the whole point of the species.
    "motion_detail": {"kind": "stone_shards", "color": SHARD_GREY},
}


def _clear_scene():
    """Drop everything the base file carries in, guides included.

    Same reason as `build_monster_bandit._clear_scene`: the scale guides are
    hide_render, so they never reach a render, but `fit_camera` measures bounding
    boxes rather than visibility and would frame the shot around them.
    """
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)


def build(out_path, outline_px_at_1x=1.1):
    _clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    rig = assemble_character(WARDEN_SPEC)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[mob] archive warden saved: {0}".format(out_path))
    print("[mob] parts={0} bones={1}".format(len(meshes), len(rig.pose.bones)))
    print("[mob] mesh objects: " + ", ".join(sorted(obj.name for obj in meshes)))
    print("[mob] actions: " + ", ".join(sorted(action.name for action in bpy.data.actions)))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_monster_archive_warden")
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1,
                        help="outline width @1x px, saved into the .blend; 1.1 is the "
                             "measured kit-character width (build_kage_character)")
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    _args = _cli_args(sys.argv)
    if _args is not None:
        build(_args.out, _args.outline_px)
