"""Build the `sample_boss` monster (migration V41, `monster_template`) from the
mesh kit — "Thủ Lĩnh Bí Cảnh", the only boss of the Bí Cảnh instance
(`DungeonInstanceRegistry.DUNGEON_BOSS_CODE`).

Why it exists: `model_key = mob_sample-boss` has been on the wire since V41, and
the key was already derived client-side long before that (`MonsterModelKey`,
"chưa vẽ tính đến 2026-08-09"). No `.glb` ever shipped under it, so the boss was
the LAST entity in the game still drawn as a red box — seen again in the live
ADR-047 check on 2026-09-29.

Lore it has to read as, WITHOUT writing any new lore: STORY.md defines a
Bí Cảnh as a sealed Linh Triều ruin "waking up" (the glossary row near the end
of STORY.md: "Di tích Linh Triều bị phong ấn"). The boss is therefore the same
Linh Triều machine-lineage as `stone_ogre`, `archive_warden`, `trial_construct`
and `foundation_warden` — and the i18n name stays deliberately neutral (see the
comment above `hud.monster.sample_boss`): this script gives it a BODY, not a
story. No proper name, no §7 row.

Four decisions, each falling out of a rule already paid for:

  * **Family by the head, not the body.** Every machine carries `carved_crest`
    + `stone_visor` + `face_style: none`; so does this one — that is the lineage
    mark. Everything below the head is different, which is the
    `archive_warden`-vs-`stone_ogre` rule: shared lineage yes, shared LAYOUT no.
  * **Bronze, not stone.** The four machines own grey granite (+ moss, amber)
    and pale jade. Ritual bronze — dark brown metal with a verdigris patina — is
    the one "ancient made thing" material left, and it is outside all five
    element palettes (ART-PIPELINE §2.2: Hỏa #E8552D/#FFB13D, Thủy
    #2D7FE8/#7FD4FF, Phong #3DBF7A/#CFF5E0, Lôi #8C4DE8/#FFE84D, Thổ
    #A6763D/#E0C089). The patina is a GREY-green, far from Phong's saturated
    #3DBF7A; the bronze is darker and redder than Thổ's #A6763D.
  * **The eye is cinnabar, not amber.** Amber is every machine that is merely
    doing its job. Red is the one that is master of its ruin — the single colour
    change that says "boss" at 50px (value pattern first, hue second — see
    ASSET-LOG on value-vs-hue).
  * **The cloak is the silhouette.** `MonsterPresenter.MODEL_HEIGHT_UNITS` is a
    constant 1.0, so a boss cannot be BIGGER; it has to be BROADER. No monster
    in the roster wears `cloak`, and it widens the figure behind the shoulders
    all the way to the knees. ⚠️ The cloak ate the face on the three B3 player
    bodies (ASSET-LOG 2026-09-07) — a machine has no face, but it does have a
    visor, so the cloak colour is kept a full value step away from the crest and
    the visor, and the render check looks for exactly that.

Weapon: `twin_blades`. Only the Huyết Sa scout carries them, a different lineage
and a near-black body, so there is no same-family collision; `stone_mace` is
already on three bodies and `long_blade` is `foundation_warden`'s.

Run headless (never in an MCP session — full file rebuild):

    blender -b --python-exit-code 1 assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_monster_sample_boss.py \
      -- --out assets/raw/blender/monsters/sample-boss.blend

Then the three LODs (ART-PIPELINE §6.5 — `--flat-materials` is mandatory for a
kit-built mesh, `--extras all` because naming the parts by hand is how
`bandit_SashTail` went missing):

    blender -b --python-exit-code 1 assets/raw/blender/monsters/sample-boss.blend \
      -P tools/blender/export_character_lods.py -- \
      --object ruinlord_Torso --rig ruinlord_Rig --extras all --flat-materials \
      --asset-key mob_sample-boss --out <ABSOLUTE>/assets/exported/models
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

# Ritual bronze in three values so the body does not flatten into one blob.
# ⚠️ Round 1 used #6E5335 and the whole figure read as ONE dark-brown lump at
# preview scale: chest plate gone, blades gone, patina only on the shoulders. The
# body has to be a MID value so the dark cloak can frame it — the `vein_raider`
# lesson again (accents survive by contrast and size, not by being there).
BRONZE = "#86653E"
BRONZE_DEEP = "#6E5233"
BRONZE_MID = "#7A5C38"
BRONZE_BASE = "#4A3722"
# Verdigris patina — the second large field. Grey-green, well away from Phong.
PATINA = "#6F9A8A"
PATINA_DARK = "#4E7064"
# The cloak: the darkest large field, so it frames the lit bronze front instead
# of merging with it.
CLOAK = "#3A2C20"
# Polished bronze for the blades — the only bright metal on the figure.
BLADE = "#C49A5A"
# The crest and visor: darkest metal, so the head stays one readable block. The
# cloak is dark too, but it hangs from the shoulders BEHIND a lit body and the
# patina pauldrons sit between the two — that band is what keeps them apart.
CREST = "#2E241A"
# The eye. Red where every other machine is amber — see module docstring.
CINNABAR = "#D2432C"

BOSS_SPEC = {
    # `ruinlord` and not `sample_boss`: the prefix goes into every datablock
    # name, and the exporter is handed `ruinlord_Torso` / `ruinlord_Rig` by hand.
    "name": "ruinlord",
    "skin": BRONZE,
    # The lineage mark, shared with every Linh Triều machine.
    "hair": {"style": "carved_crest", "color": CREST, "cap": False},
    "face_style": "none",
    "cloth": {"torso": BRONZE, "pelvis": BRONZE_DEEP, "legs": BRONZE_MID},
    "shoe": BRONZE_BASE,
    "accessories": [
        {"kind": "stone_visor", "color": CREST, "accent": CINNABAR},
        # Broadest silhouette piece in the file — see module docstring.
        {"kind": "cloak", "color": CLOAK},
        # Paired pauldrons in patina: the shoulder line is the widest point, and
        # the patina reads twice (shoulders + chest plate) — one accent colour
        # needs two placements, the lesson the bandit paid for with its scarf.
        {"kind": "shoulder_plate", "color": PATINA, "bone": "arm_r"},
        {"kind": "shoulder_plate", "color": PATINA, "bone": "arm_l"},
        {"kind": "chest_plate", "color": PATINA, "bone": "spine"},
        # ONE entry: `_acc_thigh_guard` builds both legs itself and ignores
        # `bone` (ASSET-LOG 2026-09-13 (2)).
        {"kind": "thigh_guard", "color": PATINA_DARK},
    ],
    "weapon": "twin_blades",
    "weapon_color": BLADE,
    "weapon_grip": CREST,
    # `stone_shards` orbit — the machine motion detail. Tinted cinnabar so it is
    # not `archive_warden`'s grey shards: the ruin's power circling its master.
    "motion_detail": {"kind": "stone_shards", "color": CINNABAR},
}


def _clear_scene():
    """Drop everything the base file carries in, guides included.

    Same reason as `build_monster_vein_raider._clear_scene`: the scale guides are
    hide_render, but `fit_camera` measures bounding boxes rather than visibility
    and would frame the shot around them.
    """
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)


def build(out_path, outline_px_at_1x=1.1):
    _clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    rig = assemble_character(BOSS_SPEC)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[mob] sample_boss saved: {0}".format(out_path))
    print("[mob] parts={0} bones={1}".format(len(meshes), len(rig.pose.bones)))
    print("[mob] mesh objects: " + ", ".join(sorted(obj.name for obj in meshes)))
    print("[mob] actions: " + ", ".join(sorted(action.name for action in bpy.data.actions)))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_monster_sample_boss")
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1,
                        help="outline width @1x px, saved into the .blend; 1.1 is the "
                             "measured kit-character width (build_kage_character)")
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    _args = _cli_args(sys.argv)
    if _args is not None:
        build(_args.out, _args.outline_px)
