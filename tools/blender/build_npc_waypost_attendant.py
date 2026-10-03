"""Build the `waypost_attendant` NPC — the disciple on relay-post duty (V49, ADR-050 D6).

The project's THIRD NPC, and the first one that is not a person. One
`npc_template` stands at all SIX way posts along the trunk (x = 83, 107, 127,
141, 168, 195), so this model is seen six times in one play session — more often
than either named NPC. Until it exists `NpcPresenter` draws a box for each of
them, which V49's own comment records as art debt rather than a blocker.

Lore row (STORY.md §6/§7, and `waypost_attendant.js`): an outer-sect disciple
holding a small ward — the sect's mountain-protecting formation divided down to
cover one seat — and handing out rations to the disciples working the edge of
the Wasteland. Not a fighter, not an elder: someone doing a duty shift.

Run headless (never in an MCP session — full file rebuild):

    blender -b --python-exit-code 1 assets/raw/blender/kage_character_base.blend \
      -P tools/blender/build_npc_waypost_attendant.py \
      -- --out assets/raw/blender/characters/waypost-attendant.blend

Then the three LODs (`--flat-materials` is mandatory for a kit-built mesh,
`--extras all` because naming forty parts by hand loses pieces):

    blender -b --python-exit-code 1 assets/raw/blender/characters/waypost-attendant.blend \
      -P tools/blender/export_character_lods.py -- \
      --object waypost_Torso --rig waypost_Rig --extras all --flat-materials \
      --asset-key char_waypost-attendant --out <ABSOLUTE>/assets/exported/models
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_mesh_kit import assemble_character, pose_frame  # noqa: E402
from kage_sprite_render import setup_scene  # noqa: E402

# =============================================================================
# THE THIRD NPC HAS TO TAKE THE MIDDLE, BECAUSE BOTH ENDS ARE GONE
# =============================================================================
# The two sect NPCs already sit at the two ends of the value axis, and that was
# deliberate (ASSET-LOG 2026-09-09):
#
#   Chu Hoành  : DARK slate robe · WHITE hair · pale beard over the chest
#   Tô Vân Nhi : LIGHT celadon robe · DARK hair · dark sash across the waist
#
# A third figure in the same sect robe can only be MID, and `mob_blood-sand-
# acolyte` already paid for that lesson: when two neighbours own the two ends,
# the third takes the centre and buys its separation somewhere else entirely.
#
# Here "somewhere else" is the SILHOUETTE, and the cheapest strong move is an
# omission: **no `long_robe`**. Both existing NPCs are ankle-length columns with
# no legs in the outline. This one wears a working tunic over trousers, so the
# outline breaks at the knee — a difference that survives being ringed in black
# at 50px, which a hue change would not.
#
# ⚠️ HAIR IS A REUSE, AND IT IS FORCED. All six kit styles are taken:
#   topknot      bandit · scout · Tô Vân Nhi
#   topknot_bun  enforcer · vein_raider · Chu Hoành
#   spiky_long   raider_chieftain · wasteland_beast
#   spiky_short  blood_sand_acolyte
#   bang         blood_sand_ringleader
#   carved_crest archive_warden · trial_construct · foundation_warden
# `build_monster_wasteland_beast` hit this same wall first and set the precedent:
# when the axis is exhausted, REUSE a shape and buy the separation on a free
# axis — do not invent a seventh entry so that one asset can feel new. `topknot`
# is the right reuse rather than the convenient one: it is what a disciplined
# sect disciple wears, and the figure it might be confused with (Tô Vân Nhi)
# differs from it at every other level — value, hem line, and the head-top mass,
# because this one adds a headband she does not have.
WAYPOST_SPEC = {
    "name": "waypost",
    "skin": "#E2BE9A",
    "eye_color": "#2B2620",
    # `frontal` mirrors eye + brow onto the far cheek. Lesson 5 of the elder
    # rebuild: the kit builds ONE eye ("the other is hidden behind the skull"),
    # true for a pure profile and wrong for the game's 3/4 camera. Any NPC the
    # player walks up to and TALKS to needs this on, and this one is talked to
    # six times over.
    "face_style": "frontal",
    "hair": {"style": "topknot", "color": "#4A4034"},
    # The MID mass. Warmer and browner than Tô Vân Nhi's celadon under-layers so
    # the two do not share a tone even where they share a family, and several
    # steps lighter than Chu Hoành's slate. Trousers step down twice so the
    # figure carries three values on its own and does not need the robe to do it.
    "cloth": {"torso": "#7E9488", "pelvis": "#5C6E64", "legs": "#52635A"},
    "shoe": "#39443E",
    "accessories": [
        # ⚠️ FOUR parts of accessory, matching Tô Vân Nhi's four, and that is the
        # whole budget. ASSET-LOG 2026-09-12: a kit character lands at
        # 4.976–4.988 tris of a 5.000 LOW ceiling, and `export_character_lods`
        # decimates DOWN to the budget rather than failing — so overspending
        # here does not break the build, it quietly mushes the LOW tier. If the
        # measured LOW comes back visibly worse than the two shipped NPCs, the
        # fix is to drop the sash, not to raise the ceiling.
        #
        # ⚠️ VÒNG B #1 (2026-09-18) — đổi `headband` sang `scarf`, và LÝ DO BAN ĐẦU TÔI NÊU LÀ SAI.
        # Tôi chấm bản đeo khăn trán là hỏng vì thấy một dải nhạt nằm ngang đường mắt, rồi kết luận
        # vành khăn che mặt. Nhìn lại ở 5x CẠNH bản render của sư tỷ thì dải nhạt đó là hai khối
        # tròng trắng `EyeWhite`/`EyeWhiteFar` của chính kit, nhìn nghiêng ở thái dương — trưởng lão
        # và sư tỷ đều có y hệt. Đó là giới hạn mặt của `kage_mesh_kit` dưới camera chúc xuống, đã
        # ghi sẵn trong docstring `build_npc_senior_sister`, KHÔNG do phụ kiện nào gây ra.
        #
        # Vẫn giữ `scarf`, vì hai lý do đứng độc lập với chẩn đoán sai kia: nó hạ 5 part phụ kiện
        # xuống 4 (đúng bao của sư tỷ; biên LOD LOW nới từ 4 lên 10 tam giác), và nó đặt điểm nhấn
        # ở cổ áo z 0.585-0.640 — thấp hơn cằm, nên không bao giờ dự phần vào họ lỗi che mặt mà
        # `cloak` đã gây ba lần. Đánh đổi có thật: đỉnh đầu nay không còn gì riêng, nên khác biệt
        # với sư tỷ dồn vào màu tóc (nâu ấm #4A4034 so với gần đen #2E2A2C) và vào gấu áo hở chân.
        {"kind": "scarf", "color": "#E4E0D2"},
        # Deep indigo at the waist: the third value, and the line that stops the
        # mid tunic reading as one undivided block from collar to knee. Indigo
        # rather than the sect green for the same reason Chu Hoành's sash is
        # jade — one warm/cool break per figure, placed where the outline bends.
        {"kind": "sash", "color": "#2F4A5C"},
    ],
    # ⚠️ NO `cloak`. It has eaten a character's face THREE times
    # (kage_cloak_hides_the_face, 2026-09-07): a dark mass built at head height
    # that closes over the head under the client's downward camera. On an NPC
    # the player stands still and talks to, that is the worst place to spend it.
    #
    # NO weapon either, matching both existing NPCs — and here it is also true:
    # all five kit weapon shapes are taken, and an attendant who hands out
    # rations carrying a mace would read as a guard. The ward he keeps is a
    # formation, not an object, so there is nothing to put in his hands.
    "weapon": None,
}


def _clear_scene():
    """Drop everything the base file carries in, guides included.

    Same reason as `build_npc_senior_sister._clear_scene`: the scale guides are
    hide_render so they never reach a render, but `fit_camera` measures bounding
    boxes rather than visibility and would frame the shot around them.
    """
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)


def _author_standing_watch_idle(rig):
    """Re-key `idle`: upright, feet apart, arms hanging clear of the body.

    The third distinct posture, and the arms are where it is bought. Chu Hoành
    folds his forearms hard so the hands vanish into opposite sleeves; Tô Vân Nhi
    clasps hers loosely in front. Both put the hands ON the torso, so both
    figures read as one unbroken column of cloth through the middle. This one
    lets the arms hang away from the body with only a shallow bend, which opens
    a gap of background on each side of the waist — at 50px that gap is a
    silhouette difference, not a pose detail.

    ⚠️ The CHIN is not taste. Lesson 4 of the elder rebuild, paid for in a
    rebuilt model and confirmed again on Tô Vân Nhi: at yaw 0 in Blender the face
    is fine, and through the client's real renderer (camera pitched DOWN,
    ADR-031) the hair dome closes over it. `head` negative = chin up. −30 is the
    value `runWorldPreview` accepted for a near-upright spine, and that is the
    only instrument that counts here — Blender stills have misled this project
    three separate times.

    ⚠️ Still not fixed, recorded honestly and NOT specific to this character: at
    the client's camera the eyes do not read, so the face comes out as skin plus
    a mouth. `town_elder` (2026-09-03) and `char_senior-sister` (2026-09-09) both
    have it. It is a limit of `kage_mesh_kit`'s eye geometry under a downward
    camera, and the concrete argument for the paid Rodin key still waiting on the
    project owner.
    """
    rig.animation_data.action = bpy.data.actions["idle"]
    # Feet wider than either sect NPC: someone posted somewhere, not someone
    # receiving a visitor.
    stance = {"leg_r": 6, "leg_l": -6, "shin_r": -3, "shin_l": 2}
    breath = (
        (1, 2, -30, 7, 10, -15, -18, 0.0, 1.0),
        (5, 3, -31, 6, 9, -16, -19, 0.008, 1.004),
        (9, 4, -32, 5, 8, -17, -20, 0.012, 1.007),
        (13, 3, -31, 6, 9, -16, -19, 0.006, 1.003),
        (17, 2, -30, 7, 10, -15, -18, 0.0, 1.0),
    )
    for frame, spine, head, arm_l, arm_r, fore_l, fore_r, root_z, root_scale in breath:
        pose_frame(rig, frame,
                   dict(stance, spine=spine, head=head, arm_l=arm_l, arm_r=arm_r,
                        forearm_l=fore_l, forearm_r=fore_r),
                   root_z=root_z, root_scale_z=root_scale)


def build(out_path, outline_px_at_1x=1.1):
    _clear_scene()
    setup_scene(px=256, outline_px_at_1x=outline_px_at_1x)
    rig = assemble_character(WAYPOST_SPEC)
    _author_standing_watch_idle(rig)

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.abspath(out_path))
    meshes = [obj for obj in bpy.data.objects if obj.type == "MESH"]
    print("[npc] waypost_attendant saved: {0}".format(out_path))
    print("[npc] parts={0} bones={1}".format(len(meshes), len(rig.pose.bones)))
    print("[npc] mesh objects: " + ", ".join(sorted(obj.name for obj in meshes)))
    print("[npc] actions: " + ", ".join(sorted(action.name for action in bpy.data.actions)))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_npc_waypost_attendant")
    parser.add_argument("--out", required=True, help="output .blend path")
    parser.add_argument("--outline-px", type=float, default=1.1,
                        help="outline width @1x px, saved into the .blend; 1.1 is the "
                             "measured kit-character width (build_kage_character)")
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    _args = _cli_args(sys.argv)
    if _args is not None:
        build(_args.out, _args.outline_px)
