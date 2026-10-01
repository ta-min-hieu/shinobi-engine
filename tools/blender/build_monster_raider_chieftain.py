"""Build the `raider_chieftain` monster (migration V45, `monster_template`) from
the mesh kit — "Đầu Lĩnh Đoạt Mạch", the chieftain of the band that took the
primary spirit vein. Stands at x≈93, the far end of the play line.

Lore row (STORY.md §7): same *"tán tu cường đạo — tu sĩ không tông môn, sống
bằng cướp ở rìa linh mạch"* family as `wandering_bandit` and `vein_raider`, one
rank further up. He is why the band holds a vein at all instead of robbing the
road, and he closes arc 1's Lv15-18 stretch (STORY.md §6).

⚠️ Same family as an existing species is a DELIBERATE choice, and it is the one
`archive_warden` made against `stone_ogre`: a shared lineage is fine as long as
the two read apart instantly. What is not fine is a shared lineage plus a shared
LAYOUT, which is the mistake that log entry records.

Separation here is bought with SILHOUETTE, not with hue, because hue is the
crowded axis now. The occupancy across every shipped monster and NPC:

    weapon        stone_mace x3 (warden, enforcer, vein_raider)
                  long_blade x1 (bandit) · twin_blades x1 (scout) · none x3
    hair          topknot x2 · topknot_bun x3 · carved_crest x2
    accessories   scarf/sash/arm_wrap/long_robe/robe_sash heavily used
    motion        stone_shards (warden) · spark (construct)

So this one takes the four things nothing else uses: **`gourd`**, **`spiky_long`**,
**`thigh_guard`** and **`ribbons`**. Every one of them changes the OUTLINE, which
is what survives being ringed in black at small sizes — the thing `vein_raider`
had to learn twice (ASSET-LOG 2026-09-13).

Three decisions, each falling out of a rule already paid for:

  * **The gourd is characterisation, not just a free slot.** A tán tu chieftain
    who drinks while his men work reads instantly as the one in charge, and the
    gourd hangs BESIDE the body rather than across it — unlike `stone_mace`,
    which the kit mounts inside the torso mass and which therefore only ever
    reads by contrast (the marginal Q7 on `vein_raider`).
  * **`spiky_long` against three topknots.** Every person in the roster so far
    has tied hair: disciplined, sect-shaped, or at least tidy. Loose long spikes
    are the silhouette of someone outside all of that, and they read at the top
    of the figure where the eye lands first.
  * **Colours stay outside the five element palettes** (ART-PIPELINE §2.2:
    Hỏa #E8552D/#FFB13D, Thủy #2D7FE8/#7FD4FF, Phong #3DBF7A/#CFF5E0,
    Lôi #8C4DE8/#FFE84D, Thổ #A6763D/#E0C089). Deep plum is chosen because it is
    the one dark that is neither the bandit's warm brown, the scout's near-black,
    nor `vein_raider`'s cold slate — and the ochre accent is warm, which no
    person in the roster currently carries as a large field.

⚠️ No new proper name (STORY.md §8 rule 3). A species CATEGORY needs only a §7
row; two proper names are already queued for the owner and this adds no third.

Run headless (never in an MCP session — full file rebuild; the addon refuses in
background mode anyway):

    blender -b assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_monster_raider_chieftain.py \
      -- --out assets/raw/blender/monsters/raider-chieftain.blend

Then the three LODs (ART-PIPELINE §6.5 — `--flat-materials` is mandatory for a
kit-built mesh, `--extras all` because naming the parts by hand is how
`bandit_SashTail` went missing):

    blender -b assets/raw/blender/monsters/raider-chieftain.blend \
      -P tools/blender/export_character_lods.py -- \
      --object chief_Torso --rig chief_Rig --extras all --flat-materials \
      --asset-key mob_raider-chieftain --out <ABSOLUTE>/assets/exported/models
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_mesh_kit import assemble_character  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

# Deep plum. Dark enough to outrank `vein_raider` at a glance, but a different
# dark from the scout's near-black #1A1614 and the bandit's warm #6B4A2E. Well
# clear of Lôi's #8C4DE8, which is a bright saturated violet; this is nearly grey.
PLUM = "#463A4E"
PLUM_DEEP = "#342B3A"
PLUM_MID = "#3D3444"
# The warm accent. No person in the roster carries a large warm field: the
# bandit's red is two slivers, the scout's red is dried and cold-leaning. Clear
# of Thổ's #A6763D by being more saturated and more orange, and clear of the
# machines' AMBER #E8A33C by being darker.
OCHRE = "#C08A3E"
# Boots, wraps, and the gourd cord.
DARK_LEATHER = "#241E22"

CHIEFTAIN_SPEC = {
    # `chief` and not `raider_chieftain`: the prefix goes into every datablock
    # name, and the exporter is handed `chief_Torso` / `chief_Rig` by hand.
    "name": "chief",
    # Ruddier than `vein_raider`'s #D2A57E — a man who drinks and stands in the
    # wind, next to one who does the work.
    "skin": "#D8A579",
    "eye_color": "#1E1820",
    # Two-eyed game-camera face + chin up in idle — see `build_monster_bandit`
    # (ASSET-LOG 2026-10-01, second pass).
    "face_style": "frontal",
    "idle_chin_lift": 28,
    # The single biggest silhouette separator in the file: every other person in
    # the roster ties their hair. See module docstring.
    "hair": {"style": "spiky_long", "color": "#1F1A22"},
    # Mid-dark plum body. NOT pale: `vein_raider` proved that a light body just
    # becomes an outlined blob once the figure gets small, and that the accents
    # have to be large to survive rather than the body being light.
    "cloth": {"torso": PLUM, "pelvis": PLUM_DEEP, "legs": PLUM_MID},
    "shoe": DARK_LEATHER,
    "accessories": [
        # Thigh guards — unused anywhere else in the roster, and they widen the
        # figure at the HIPS, which is where `vein_raider` is narrowest.
        #
        # ⚠️ ONE entry, not two. `_acc_thigh_guard` builds BOTH legs itself and
        # hardcodes `bone="leg_" + side`, so the `bone` argument is ignored —
        # the same trap as `_acc_arm_wrap` (ASSET-LOG 2026-09-13). The first
        # draft passed `leg_r` and `leg_l` separately and got FOUR guards:
        # `ThighGuard_l` + `ThighGuard_l.001` sitting on top of each other,
        # 49 parts instead of 47, with the doubled outline darkening the hips.
        #
        # The rule that generalises: a kit accessory that is inherently PAIRED
        # (arm_wrap, thigh_guard) ignores `bone` and shades the left with
        # FAR_SHADE by itself; an inherently SINGLE one honours it
        # (`shoulder_plate` does `target = bone or "arm_r"`).
        {"kind": "thigh_guard", "color": OCHRE},
        # The warm field, worn high so the ochre reads twice (hip and chest) —
        # one accent colour needs two placements, the lesson the bandit paid for
        # with its scarf.
        {"kind": "sash", "color": OCHRE},
        # A single pauldron in plum, so the shoulder line is asymmetric against
        # the symmetric thigh guards and the outline never reads as a rectangle.
        {"kind": "shoulder_plate", "color": PLUM_DEEP, "bone": "arm_r"},
        # Dark wraps keep the ochre sash from running into the hands.
        # (`_acc_arm_wrap` ignores `bone` and always builds both arms — noted in
        # ASSET-LOG 2026-09-13, so no bone is passed here.)
        {"kind": "arm_wrap", "color": DARK_LEATHER},
    ],
    # Hangs BESIDE the body instead of across it — see module docstring.
    "weapon": "gourd",
    "weapon_color": "#8A6A38",
    "weapon_grip": DARK_LEATHER,
    # `ribbons` is the only neutral motion detail left (embers and water_ring are
    # element-coded, spark and stone_shards belong to the machines). It keeps the
    # figure alive while idle, which is what a leader standing still needs.
    "motion_detail": {"kind": "ribbons", "color": OCHRE},
}


def _clear_scene():
    """Drop everything the base file carries in, guides included.

    Same reason as `build_monster_vein_raider._clear_scene`: the scale guides are
    hide_render so they never reach a render, but `fit_camera` measures bounding
    boxes rather than visibility and would frame the shot around them.
    """
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)


def build(out_path, outline_px_at_1x=1.1):
    _clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    rig = assemble_character(CHIEFTAIN_SPEC)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[mob] raider_chieftain saved: {0}".format(out_path))
    print("[mob] parts={0} bones={1}".format(len(meshes), len(rig.pose.bones)))
    print("[mob] mesh objects: " + ", ".join(sorted(obj.name for obj in meshes)))
    print("[mob] actions: " + ", ".join(sorted(action.name for action in bpy.data.actions)))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_monster_raider_chieftain")
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1,
                        help="outline width @1x px, saved into the .blend; 1.1 is the "
                             "measured kit-character width (build_kage_character)")
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    _args = _cli_args(sys.argv)
    if _args is not None:
        build(_args.out, _args.outline_px)
