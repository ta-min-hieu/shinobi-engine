"""Build the `trial_construct` monster (migration V33, `monster_template`) from
the mesh kit — "Trắc Linh Thạch Nhân", the sect's spirit-gauge sparring partner.

Why it exists: V33 shipped the species pointing at `mob_archive-warden` and
wrote that down as a visible debt in its own §2 comment. Same debt, same shape,
same fix as V30→V31 — this script is the art half, and the migration that flips
`model_key` is V34. Two constructs standing 14m apart on the same play line and
looking identical is exactly what V24 and V31 were each written to stop.

Lore it has to read as (STORY.md §7, row added with V33): the same Linh Triều
machine-lineage as `stone_ogre` and `archive_warden`, but this one the sect
SALVAGED AND REPAIRED to serve as the sparring partner of the awakening
examination. It measures how long a disciple can hold their spiritual energy
together, so it strikes lightly (atk 15, the lowest of any species that fights
back) and endures (hp 130, the highest in arc 1).

Four decisions, each straight out of that sentence — and each one chosen to
survive 64px, because `MonsterPresenter.MODEL_HEIGHT_UNITS` is a constant 1.0 so
every monster is drawn at the same height and only PROPORTION, SILHOUETTE and
COLOUR can carry a difference (the lesson `build_monster_archive_warden` had to
learn first):

  * **Kept, not derelict — that is the whole read.** The warden is abandoned
    machinery: cold granite, moss on the shoulders, a thousand years of nobody.
    This one is maintained property: pale stone that somebody still oils, dark
    iron brackets bolted where the original cracked, hemp rope the sect wound
    round both arms. Same lineage, opposite story, and the difference is visible
    before you can read either name plate.
  * **Empty hands.** The warden carries a stone mace; this one carries nothing,
    and `weapon: None` removes a whole limb-extension from the silhouette — the
    single largest shape difference available in the kit. It is also literally
    true: the thing does not kill, it measures.
  * **The amber stays.** The visor slots keep `AMBER`, copied from the ogre and
    the warden on purpose. It is the one warm light on any of the three, the
    thing that says "still running", and it is what makes this a COUSIN rather
    than an unrelated monster. Family is carried by colour, difference by shape.
  * **`spark` instead of `stone_shards`.** The warden orbits two chunks of rubble
    — debris that never settled. A gauge should PULSE: `spark` is driver-blinked
    on/off every other frame, which reads as an instrument taking a reading. It
    is the only motion detail in the kit that is not continuous motion.

Palette check (ART-PIPELINE §2.2): pale limestone + dark iron + cream hemp
touches none of the five element kits — Hỏa orange, Thủy blue, Phong green, Lôi
purple, Thổ brown-tan. The warden's own tan (`BAMBOO #C9A96A`) sits closest to
Thổ of anything on either construct, which is one more reason this one avoids
tan entirely and goes cold.

Why the kit and not Text→3D (ART-QUALITY-STANDARD §0 prefers the generated
path): the Rodin key still answers `API_INSUFFICIENT_FUNDS` — the same wall
`wandering_bandit` (2026-08-30), `town_elder` (2026-09-03) and `archive_warden`
(2026-09-06) all hit. The kit is the offline deterministic fallback and every
step after the mesh is shared, so `build_text3d_monster.py --code trial_construct`
over a real `.glb` later swaps the art without touching anything else.

Run headless (never in an MCP session — full file rebuild):

    blender -b --python-exit-code 1 assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_monster_trial_construct.py \
      -- --out assets/raw/blender/monsters/trial-construct.blend

Then the three LODs (ART-PIPELINE §6.5 — `--flat-materials` is mandatory for a
hand-built mesh, and `--extras all` because naming forty parts by hand is how
`bandit_SashTail` went missing):

    blender -b --python-exit-code 1 assets/raw/blender/monsters/trial-construct.blend \
      -P tools/blender/export_character_lods.py -- \
      --object gauge_Torso --rig gauge_Rig --extras all --flat-materials \
      --asset-key mob_trial-construct --out <ABSOLUTE>/assets/exported/models
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

# Pale jade-stone, near white. The FIRST version of this file used a mid-grey
# limestone one step lighter than the warden's granite, and `runWorldPreview`
# threw it out: at the size a player actually sees (~50px), "slightly lighter
# grey" is not a difference, and the thing read as the warden with its
# belongings taken away. What reads at 50px is the VALUE PATTERN, so this
# version inverts the warden's instead of nudging it —
#
#     warden  = dark body + one pale patch (the stele)
#     gauge   = pale body + dark fittings (crest, visor, plate, bands)
#
# and that survives being shrunk, being greyscaled, and being seen for a second.
# It is also the more accurate lore: a 測靈 stone in this genre is pale jade,
# not granite, so the species finally looks like the thing its name calls it.
STONE_SKIN = "#CFCCBE"
STONE_TORSO = "#C4C1B2"
STONE_PELVIS = "#55595F"
STONE_LEGS = "#5E626A"
STONE_BASE = "#3A3D42"
# The sect's repairs — cold dark iron. On the first version this sat on mid-grey
# stone and disappeared into it; on pale jade every piece of it is legible.
IRON = "#4A4E55"
# Undyed hemp rope, kept from the first version for the waist knot where it
# still has a pale-on-dark job to do. It was ALSO on the arms there and was
# invisible: pale rope on a pale arm is nothing, so the arms went to iron.
HEMP = "#DCD4BE"
# Copied verbatim from `build_monster_archive_warden` and from the ogre before
# it. This is the family tie; do not "improve" it.
AMBER = "#E8A33C"

TRIAL_SPEC = {
    # `gauge` and not `trial`: the prefix goes into every datablock name and
    # `trial_*` reads like a test fixture in a file list.
    "name": "gauge",
    "skin": STONE_SKIN,
    # Same carved crest as the warden — they ARE the same lineage and the skull
    # should say so — but cast in IRON, so it reads as a cap the sect bolted
    # over a broken crown rather than more of the same rock. `cap: False` is not
    # a style choice: the dome is cut for a level camera and the client's looks
    # down (ADR-031), so on a bald skull it renders as a brim over the whole
    # face. Learned on the elder, paid for again on the warden.
    "hair": {"style": "carved_crest", "color": IRON, "cap": False},
    # No anime face — a machine, and it wears `stone_visor` below. `frontal` was
    # rejected on the warden for the same reason it would be rejected here: the
    # kit's face is authored for a profile and the client's near-head-on yaw
    # slides both eyes onto the edges of the skull.
    "face_style": "none",
    # Pale above, dark below — the split is the silhouette. It also reads as what
    # it is: the measuring stone sits on an iron stand the sect bolted it to.
    "cloth": {"torso": STONE_TORSO, "pelvis": STONE_PELVIS, "legs": STONE_LEGS},
    "shoe": STONE_BASE,
    "accessories": [
        # The face. Iron frame, amber slots — the family tie (see module docstring).
        {"kind": "stone_visor", "color": IRON, "accent": AMBER},
        # The gauge itself: an iron plate over the chest, DARK on pale jade —
        # the exact inverse of the warden's pale stele on dark granite. That
        # inversion is the one thing in this spec that has to survive a greyscale
        # thumbnail, which is the honest test of a silhouette.
        {"kind": "chest_plate", "color": IRON, "bone": "spine"},
        # Iron bands on both arms — the sect's own hands on the machine. HEMP was
        # tried here first and `runWorldPreview` showed nothing at all: pale rope
        # on a pale jade arm has no edge. Two entries because `arm_wrap` builds
        # on the bone it is given.
        {"kind": "arm_wrap", "color": IRON, "bone": "arm_r"},
        {"kind": "arm_wrap", "color": IRON, "bone": "arm_l"},
        # A rope round the waist, knotted by whoever last serviced it. Pale
        # against the darker pelvis for the same reason the warden's stele is
        # pale against its torso: same-value blocks stacked on each other are one
        # lump at 64px (ASSET-LOG 2026-09-03, `icon_genin_headband`).
        {"kind": "sash", "color": HEMP, "bone": "spine"},
        # ⚠️ A `cloak` was tried here on the third pass and REMOVED after
        # measuring it — kept as a note because the next person will reach for
        # the same thing. `_acc_cloak` hangs BEHIND the body, and the client's
        # camera (ADR-031) looks close to head-on, so on this chibi it was
        # occluded in every frame of `runWorldPreview`: idle, run, attack, die.
        # It cost 116 triangles (4986 → 5102) and rendered nothing at all.
        # Open question for someone else's pass: four B3 characters in
        # `build_kage_character.py` also wear one — worth checking whether they
        # are paying the same toll, on bodies wide enough that it might peek out
        # at the sides.
    ],
    # NOTHING. See the module docstring — this is the largest silhouette
    # difference the kit can give, and it is also the literal truth about the
    # species.
    "weapon": None,
    # The reading being taken. `spark` is the only motion detail in the kit that
    # blinks rather than travels (driver: on/off every other frame), which is
    # what an instrument does and what orbiting rubble does not.
    "motion_detail": {"kind": "spark", "color": AMBER},
}


def _clear_scene():
    """Drop everything the base file carries in, guides included.

    Same reason as `build_monster_archive_warden._clear_scene`: the scale guides
    are hide_render, so they never reach a render, but `fit_camera` measures
    bounding boxes rather than visibility and would frame the shot around them.
    """
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)


def build(out_path, outline_px_at_1x=1.1):
    _clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    rig = assemble_character(TRIAL_SPEC)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[mob] trial construct saved: {0}".format(out_path))
    print("[mob] parts={0} bones={1}".format(len(meshes), len(rig.pose.bones)))
    print("[mob] mesh objects: " + ", ".join(sorted(obj.name for obj in meshes)))
    print("[mob] actions: " + ", ".join(sorted(action.name for action in bpy.data.actions)))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_monster_trial_construct")
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1,
                        help="outline width @1x px, saved into the .blend; 1.1 is the "
                             "measured kit-character width (build_kage_character)")
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    _args = _cli_args(sys.argv)
    if _args is not None:
        build(_args.out, _args.outline_px)
