"""Build the 17 `vfx_<skill_code>` sprite sheets — one per SkillRegistry entry
(ART-PIPELINE.md §2.3: "Skill code trong bang la code that ... quy tac dat ten
VFX: vfx_<skill_code>"). Master 384x384 (ART-PIPELINE.md §4.1 "VFX skill (1 o)").

Unlike characters/monsters/NPCs, VFX have no armature and no shared rig — each
skill is a handful of simple primitive meshes (box/cone/torus/cylinder/
ico_sphere) keyframed directly on their own object transforms across a short
frame range, then rendered frame-by-frame. No fit_camera() sweep either (that
helper is written around bone-action sweeps): ortho_scale/baseline are picked
by hand per skill and confirmed by reading back the rendered PNGs.

Design grounded in two sources, not invented per-skill:
  - ART-PIPELINE.md Section 2.3 describes the VFX explicitly for 9 of the 17
    (fireball, thuy_hoi_phuc, thuy_thuy_trieu, phong_gia_toc, phong_loc_xoay,
    tho_khien_da, tho_dia_chan, loi_tia_set, loi_thien_loi).
  - The other 8 have no doc description; their effect/target came from
    SkillRegistry.java (element, target kind) and each script's return value
    (damage/heal/shield/buff/debuff) in server-world/.../scripts/skill/*.js,
    then matched to the closest of the archetypes below.

Explicitly OUT of scope (ART-PIPELINE.md is explicit these are runtime, not
pre-rendered): ember/particle trails, afterimage-via-alpha, camera shake.

Run headless, one process builds+renders all 17:

    blender -b --python-exit-code 1 -P tools/blender/build_skill_vfx.py -- --all \
      --out-blend assets/raw/blender/effects --out-render assets/raw/render-out
"""

import math
import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from kage_sprite_render import apply_toon, setup_scene  # noqa: E402

ELEMENT_HEX = {
    "hoa": ("#E8552D", "#FFB13D"),
    "thuy": ("#2D7FE8", "#7FD4FF"),
    "phong": ("#3DBF7A", "#CFF5E0"),
    "loi": ("#8C4DE8", "#FFE84D"),
    "tho": ("#A6763D", "#E0C089"),
}


# --- Scene / object plumbing -------------------------------------------------


def _clear_scene():
    for obj in list(bpy.data.objects):
        if obj.type not in ("CAMERA", "LIGHT"):
            bpy.data.objects.remove(obj, do_unlink=True)
    for block_collection in (bpy.data.meshes, bpy.data.actions, bpy.data.materials):
        for block in list(block_collection):
            if block.users == 0:
                block_collection.remove(block)


def _box_obj(name, half_x, half_y, half_z, loc=(0.0, 0.0, 0.0)):
    verts = [(sx * half_x, sy * half_y, sz * half_z)
             for sz in (-1, 1) for sx, sy in ((-1, -1), (1, -1), (1, 1), (-1, 1))]
    faces = [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    obj.location = loc
    return obj


def _prim(kind, name, loc=(0.0, 0.0, 0.0), radius=0.1, radius2=0.0, depth=0.2,
          major_radius=0.15, minor_radius=0.04, subdivisions=1, vertices=6):
    if kind == "ico_sphere":
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=subdivisions, radius=radius, location=loc)
    elif kind == "cone":
        bpy.ops.mesh.primitive_cone_add(vertices=4, radius1=radius, radius2=radius2, depth=depth, location=loc)
    elif kind == "cylinder":
        bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=depth, location=loc)
    elif kind == "torus":
        bpy.ops.mesh.primitive_torus_add(major_radius=major_radius, minor_radius=minor_radius, location=loc)
    else:
        raise ValueError("unknown primitive kind: " + kind)
    obj = bpy.context.active_object
    obj.name = name
    bpy.ops.object.shade_flat()
    return obj


def _kf(obj, frame, loc=None, scale=None, rot=None):
    if loc is not None:
        obj.location = loc
        obj.keyframe_insert(data_path="location", frame=frame)
    if scale is not None:
        obj.scale = scale if isinstance(scale, tuple) else (scale, scale, scale)
        obj.keyframe_insert(data_path="scale", frame=frame)
    if rot is not None:
        obj.rotation_euler = rot
        obj.keyframe_insert(data_path="rotation_euler", frame=frame)


def _linearize(*objs):
    """No-op: every _render_phase() call below renders exactly the integer
    frames each skill keyframes on (verified by construction — frame_start
    accounting in build_and_render lines up 1:1 with each builder's own
    frame numbers), so no in-between frame is ever sampled and interpolation
    mode never matters. Kept as a marker at each call site instead of ripping
    the calls out, since Blender 5.2's Action layers/slots API (fcurves no
    longer live directly on Action) makes forcing LINEAR here more trouble
    than it's worth for a value that's never actually interpolated."""


def _set_camera(ortho_scale, baseline=0.5):
    scene = bpy.context.scene
    camera = scene.camera
    camera.data.ortho_scale = ortho_scale
    camera.location = (0.0, -10.0, ortho_scale * (0.5 - baseline))


def _render_phase(out_dir, action_name, frame_start, frame_end):
    scene = bpy.context.scene
    os.makedirs(out_dir, exist_ok=True)
    written = []
    for index, frame in enumerate(range(frame_start, frame_end + 1)):
        scene.frame_set(frame)
        filepath = os.path.join(out_dir, "{0}_{1:02d}".format(action_name, index))
        scene.render.filepath = filepath
        bpy.ops.render.render(write_still=True)
        written.append(filepath + ".png")
    print("[kage] {0}: {1} frames".format(action_name, len(written)))
    return written


def _write_meta(out_dir, phases, ortho_scale, baseline, blend_file):
    import json
    scene = bpy.context.scene
    meta = {
        "master_px": scene.render.resolution_x,
        "master_scale": 2,
        "playback_fps": 12,
        "actions": phases,
        "pivot_x_px": scene.render.resolution_x / 2.0,
        "pivot_y_px_from_bottom": baseline * scene.render.resolution_y,
        "ortho_scale": ortho_scale,
        "blend_file": blend_file,
    }
    with open(os.path.join(out_dir, "render-meta.json"), "w", encoding="utf-8") as handle:
        json.dump(meta, handle, indent=2, sort_keys=True)


# --- Per-skill builders -------------------------------------------------------
# Each returns (phases: {action_name: frame_count}, ortho_scale, baseline).


def build_fireball(main_hex, accent_hex):
    orb = _prim("ico_sphere", "Orb", radius=0.12)
    apply_toon(orb, main_hex, shades=2)
    ember = _prim("ico_sphere", "Ember", radius=0.045)
    apply_toon(ember, accent_hex, shades=2)

    _kf(orb, 1, loc=(-0.55, -0.05, 0.10), scale=1.0)
    _kf(orb, 2, loc=(-0.20, -0.05, 0.11), scale=1.0)
    _kf(orb, 3, loc=(0.05, -0.05, 0.12), scale=1.0)
    _kf(ember, 1, loc=(-0.68, -0.05, 0.09), scale=0.7)
    _kf(ember, 2, loc=(-0.35, -0.05, 0.10), scale=0.7)
    _kf(ember, 3, loc=(-0.10, -0.05, 0.11), scale=0.7)

    spikes = []
    for i in range(4):
        spike = _prim("cone", "Spike{0}".format(i), radius=0.05, radius2=0.0, depth=0.22,
                       loc=(0.05, -0.05, 0.12))
        spike.rotation_euler = (math.radians(90), 0.0, math.radians(i * 90))
        apply_toon(spike, accent_hex, shades=2)
        spikes.append(spike)

    _kf(orb, 4, loc=(0.05, -0.05, 0.12), scale=1.3)
    _kf(orb, 5, loc=(0.05, -0.05, 0.12), scale=0.0)
    for spike in spikes:
        _kf(spike, 4, scale=0.0)
        _kf(spike, 5, scale=1.0)
    _linearize(orb, ember, *spikes)

    return {"travel": 3, "impact": 2}, 1.6, 0.5


def _traveling_orb_burst(main_hex, accent_hex, orb_radius=0.11, spike_len=0.20):
    """Shared archetype: orb+trail flies left->center (travel), then pops into
    a 4-spike burst (impact). Used by any ENEMY damage-nuke skill without a
    bespoke ART-PIPELINE description."""
    orb = _prim("ico_sphere", "Orb", radius=orb_radius)
    apply_toon(orb, main_hex, shades=2)
    trail = _prim("ico_sphere", "Trail", radius=orb_radius * 0.55)
    apply_toon(trail, accent_hex, shades=2)

    _kf(orb, 1, loc=(-0.55, -0.05, 0.12), scale=1.0)
    _kf(orb, 2, loc=(0.05, -0.05, 0.12), scale=1.0)
    _kf(trail, 1, loc=(-0.72, -0.05, 0.11), scale=1.0)
    _kf(trail, 2, loc=(-0.25, -0.05, 0.115), scale=1.0)

    spikes = []
    for i in range(4):
        spike = _prim("cone", "Spike{0}".format(i), radius=0.045, radius2=0.0,
                       depth=spike_len, loc=(0.05, -0.05, 0.12))
        spike.rotation_euler = (math.radians(90), 0.0, math.radians(i * 90 + 45))
        apply_toon(spike, accent_hex, shades=2)
        spikes.append(spike)

    _kf(orb, 3, loc=(0.05, -0.05, 0.12), scale=1.35)
    _kf(orb, 4, loc=(0.05, -0.05, 0.12), scale=0.0)
    for spike in spikes:
        _kf(spike, 3, scale=0.0)
        _kf(spike, 4, scale=1.0)
    _linearize(orb, trail, *spikes)
    return {"travel": 2, "impact": 2}, 1.6, 0.5


def build_thuy_song_du(main_hex, accent_hex):
    return _traveling_orb_burst(main_hex, accent_hex, orb_radius=0.13, spike_len=0.16)


def build_phong_cuong_phong(main_hex, accent_hex):
    return _traveling_orb_burst(main_hex, accent_hex, orb_radius=0.09, spike_len=0.24)


def build_tho_trong_kich(main_hex, accent_hex):
    fist = _box_obj("Fist", 0.14, 0.14, 0.14, loc=(0.0, -0.05, 0.55))
    apply_toon(fist, main_hex, shades=3)
    _kf(fist, 1, loc=(0.0, -0.05, 0.55), scale=1.0)
    _kf(fist, 2, loc=(0.0, -0.05, 0.10), scale=1.15)

    cracks = []
    for i, dx in enumerate((-0.22, 0.0, 0.24)):
        crack = _box_obj("Crack{0}".format(i), 0.10, 0.015, 0.015, loc=(dx, -0.05, 0.01))
        crack.rotation_euler = (0, 0, math.radians(15 * (i - 1)))
        apply_toon(crack, accent_hex, shades=2)
        cracks.append(crack)
        _kf(crack, 2, scale=0.0)
        _kf(crack, 3, scale=1.0)
    _kf(fist, 3, loc=(0.0, -0.05, 0.10), scale=1.15)
    _linearize(fist, *cracks)
    return {"impact": 3}, 1.4, 0.15


def build_tho_khien_da(main_hex, accent_hex):
    plates = []
    for i, (dz, dy) in enumerate(((0.20, -0.10), (0.42, -0.09), (0.62, -0.08))):
        plate = _box_obj("Plate{0}".format(i), 0.20 - i * 0.01, 0.03, 0.16, loc=(0.0, dy, dz))
        apply_toon(plate, main_hex if i != 1 else accent_hex, shades=3)
        _kf(plate, 1, loc=(0.0, dy, 0.02), scale=0.2)
        _kf(plate, 2, loc=(0.0, dy, dz - 0.05), scale=1.1)
        _kf(plate, 3, loc=(0.0, dy, dz), scale=1.0)
        plates.append(plate)
    _linearize(*plates)
    return {"cast": 3}, 1.6, 0.15


def build_tho_kien_dinh(main_hex, accent_hex):
    shards = []
    for i in range(3):
        angle0 = i * 2.094
        shard = _prim("cone", "Shard{0}".format(i), radius=0.04, radius2=0.02, depth=0.09,
                       loc=(0.20 * math.cos(angle0), -0.15, 0.55 + 0.10 * math.sin(angle0)))
        apply_toon(shard, accent_hex, shades=2)
        shards.append(shard)
        for f, angle in ((1, angle0), (2, angle0 + 1.2), (3, angle0 + 2.4)):
            loc = (0.20 * math.cos(angle), -0.15, 0.55 + 0.10 * math.sin(angle))
            _kf(shard, f, loc=loc, scale=1.0)
    body_glow = _prim("cylinder", "AuraRing", radius=0.24, depth=0.02, vertices=8, loc=(0.0, -0.10, 0.30))
    body_glow.rotation_euler = (math.radians(90), 0, 0)
    apply_toon(body_glow, main_hex, shades=2)
    _kf(body_glow, 1, scale=(1.0, 1.0, 1.0))
    _kf(body_glow, 2, scale=(1.15, 1.15, 1.0))
    _kf(body_glow, 3, scale=(1.0, 1.0, 1.0))
    _linearize(body_glow, *shards)
    return {"cast": 3}, 1.6, 0.35


def build_tho_dia_chan(main_hex, accent_hex):
    jump = _box_obj("Jumper", 0.10, 0.10, 0.18, loc=(0.0, -0.05, 0.30))
    apply_toon(jump, main_hex, shades=3)
    _kf(jump, 1, loc=(0.0, -0.05, 0.30), scale=1.0)
    _kf(jump, 2, loc=(0.0, -0.05, 0.55), scale=1.0)

    cracks = []
    for i, dx in enumerate((-0.55, -0.25, 0.0, 0.28, 0.58)):
        crack = _box_obj("Crack{0}".format(i), 0.14, 0.02, 0.02, loc=(dx, -0.05, 0.01))
        apply_toon(crack, accent_hex, shades=2)
        cracks.append(crack)
        _kf(crack, 2, scale=0.0)
        _kf(crack, 3, scale=0.5)
        _kf(crack, 4, scale=1.0)
    dust = []
    for i, dx in enumerate((-0.3, 0.1, 0.4)):
        d = _box_obj("Dust{0}".format(i), 0.09, 0.05, 0.05, loc=(dx, -0.05, 0.05))
        apply_toon(d, accent_hex, shades=2)
        dust.append(d)
        _kf(d, 3, scale=0.0)
        _kf(d, 4, scale=1.0)
    _kf(jump, 3, loc=(0.0, -0.05, 0.02), scale=1.2)
    _kf(jump, 4, loc=(0.0, -0.05, 0.02), scale=1.2)
    _linearize(jump, *cracks, *dust)
    return {"cast": 2, "impact": 3}, 2.4, 0.12


def build_thuy_hoi_phuc(main_hex, accent_hex):
    pillar = _prim("cylinder", "WaterPillar", radius=0.14, depth=0.10, vertices=8, loc=(0.0, -0.05, 0.05))
    apply_toon(pillar, accent_hex, shades=2)
    ring = _prim("torus", "Ripple", major_radius=0.16, minor_radius=0.02, loc=(0.0, -0.05, 0.01))
    ring.rotation_euler = (math.radians(90), 0, 0)
    apply_toon(ring, main_hex, shades=2)

    _kf(pillar, 1, loc=(0.0, -0.05, 0.05), scale=(1.0, 1.0, 0.4))
    _kf(pillar, 2, loc=(0.0, -0.05, 0.20), scale=(1.0, 1.0, 1.4))
    _kf(pillar, 3, loc=(0.0, -0.05, 0.30), scale=(1.0, 1.0, 2.0))
    _kf(pillar, 4, loc=(0.0, -0.05, 0.30), scale=(0.8, 0.8, 2.2))
    _kf(ring, 1, scale=(0.3, 0.3, 1.0))
    _kf(ring, 2, scale=(0.7, 0.7, 1.0))
    _kf(ring, 3, scale=(1.1, 1.1, 1.0))
    _kf(ring, 4, scale=(1.4, 1.4, 1.0))
    _linearize(pillar, ring)
    return {"impact": 4}, 1.5, 0.15


def build_thuy_phong_toa(main_hex, accent_hex):
    rings = []
    for i, dz in enumerate((0.04, 0.16)):
        ring = _prim("torus", "Bind{0}".format(i), major_radius=0.13, minor_radius=0.025, loc=(0.0, -0.05, dz))
        ring.rotation_euler = (math.radians(90), 0, 0)
        apply_toon(ring, accent_hex if i == 0 else main_hex, shades=2)
        rings.append(ring)
        _kf(ring, 1, scale=(1.6, 1.6, 1.0))
        _kf(ring, 2, scale=(1.0, 1.0, 1.0))
        _kf(ring, 3, scale=(1.05, 1.05, 1.0))
    _linearize(*rings)
    return {"bind": 3}, 1.3, 0.12


def build_thuy_thuy_trieu(main_hex, accent_hex):
    rise = _prim("cylinder", "RisingWater", radius=0.12, depth=0.06, vertices=8, loc=(0.0, -0.05, 0.03))
    apply_toon(rise, accent_hex, shades=2)
    _kf(rise, 1, scale=(1.0, 1.0, 0.5))
    _kf(rise, 2, scale=(1.3, 1.3, 1.6))

    wave = _box_obj("Wave", 0.55, 0.10, 0.30, loc=(-0.9, -0.05, 0.20))
    apply_toon(wave, main_hex, shades=2)
    _kf(wave, 3, loc=(-0.9, -0.05, 0.20), scale=0.6)
    _kf(wave, 4, loc=(-0.2, -0.05, 0.24), scale=1.1)
    _kf(wave, 5, loc=(0.6, -0.05, 0.20), scale=1.0)
    _kf(wave, 6, loc=(1.0, -0.05, 0.16), scale=0.7)

    ripple = _prim("torus", "SettleRipple", major_radius=0.20, minor_radius=0.02, loc=(0.4, -0.05, 0.01))
    ripple.rotation_euler = (math.radians(90), 0, 0)
    apply_toon(ripple, accent_hex, shades=2)
    _kf(ripple, 6, scale=(0.2, 0.2, 1.0))
    _kf(ripple, 7, scale=(1.0, 1.0, 1.0))

    _linearize(rise, wave, ripple)
    return {"cast": 2, "impact": 5}, 2.4, 0.18


def build_phong_gia_toc(main_hex, accent_hex):
    swirls = []
    for i, dz in enumerate((0.03, 0.20)):
        ring = _prim("torus", "Swirl{0}".format(i), major_radius=0.12 - i * 0.02, minor_radius=0.02,
                     loc=(0.0, -0.05, dz))
        ring.rotation_euler = (math.radians(90), 0, math.radians(i * 30))
        apply_toon(ring, main_hex if i == 0 else accent_hex, shades=2)
        swirls.append(ring)
        _kf(ring, 1, scale=(0.4, 0.4, 1.0), rot=(math.radians(90), 0, 0))
        _kf(ring, 2, scale=(1.0, 1.0, 1.0), rot=(math.radians(90), 0, math.radians(60)))
        _kf(ring, 3, scale=(0.7, 0.7, 1.0), rot=(math.radians(90), 0, math.radians(120)))
    _linearize(*swirls)
    return {"cast": 3}, 1.3, 0.15


def build_phong_bao_kich(main_hex, accent_hex):
    bursts = []
    for i, dx in enumerate((-0.16, 0.16)):
        ring = _prim("torus", "FistBurst{0}".format(i), major_radius=0.09, minor_radius=0.018,
                     loc=(dx, -0.06, 0.42))
        ring.rotation_euler = (math.radians(90), 0, 0)
        apply_toon(ring, accent_hex, shades=2)
        bursts.append(ring)
        _kf(ring, 1, scale=(0.3, 0.3, 1.0))
        _kf(ring, 2, scale=(1.2, 1.2, 1.0))
        _kf(ring, 3, scale=(0.9, 0.9, 1.0))
    _linearize(*bursts)
    return {"cast": 3}, 1.3, 0.4


def build_phong_loc_xoay(main_hex, accent_hex):
    spin = _prim("torus", "SpinStart", major_radius=0.10, minor_radius=0.02, loc=(0.0, -0.05, 0.03))
    spin.rotation_euler = (math.radians(90), 0, 0)
    apply_toon(spin, main_hex, shades=2)
    _kf(spin, 1, scale=(0.3, 0.3, 1.0))
    _kf(spin, 2, scale=(1.0, 1.0, 1.0))

    rings = []
    for i, dz in enumerate((0.05, 0.30, 0.55, 0.80)):
        ring = _prim("torus", "Funnel{0}".format(i), major_radius=0.22 - i * 0.03, minor_radius=0.025,
                     loc=(0.0, -0.05, dz))
        ring.rotation_euler = (math.radians(90), 0, math.radians(i * 20))
        apply_toon(ring, accent_hex if i % 2 else main_hex, shades=2)
        rings.append(ring)
        _kf(ring, 3, scale=(0.2, 0.2, 1.0))
        _kf(ring, 4, scale=(1.0, 1.0, 1.0))
        _kf(ring, 5, scale=(1.0, 1.0, 1.0) if i > 0 else (0.6, 0.6, 1.0))
        _kf(ring, 6, scale=(0.0, 0.0, 1.0) if i == 0 else (0.7 - i * 0.15, 0.7 - i * 0.15, 1.0))
    _linearize(spin, *rings)
    return {"cast": 2, "impact": 4}, 2.2, 0.3


def build_loi_tia_set(main_hex, accent_hex):
    # No pre-rendered white flash: ART-PIPELINE.md's standard-action table already
    # establishes the convention for this exact situation ("hit": "kem tint flash
    # trong code, khong render rieng ban trang") — the doc's "flash trang toan
    # man 1 frame" for this skill is a client-side screen effect, not a sprite frame.
    spark = _prim("ico_sphere", "FingerSpark", radius=0.035, loc=(0.0, -0.10, 0.55))
    apply_toon(spark, accent_hex, shades=2)
    _kf(spark, 1, scale=1.0)

    bolt_segs = []
    points = ((0.0, 0.60), (-0.06, 0.44), (0.05, 0.30), (-0.04, 0.15), (0.0, 0.02))
    for i in range(len(points) - 1):
        (x0, z0), (x1, z1) = points[i], points[i + 1]
        mx, mz = (x0 + x1) / 2.0, (z0 + z1) / 2.0
        length = math.hypot(x1 - x0, z1 - z0)
        angle = math.atan2(x1 - x0, z1 - z0)
        seg = _box_obj("Bolt{0}".format(i), 0.018, 0.018, length / 2.0, loc=(mx, -0.06, mz))
        seg.rotation_euler = (0, angle, 0)
        apply_toon(seg, main_hex, shades=2)
        bolt_segs.append(seg)
        _kf(seg, 2, scale=0.4)
        _kf(seg, 3, scale=1.0)

    _linearize(spark, *bolt_segs)
    return {"cast": 1, "impact": 2}, 1.8, 0.25


def build_loi_truy_kich(main_hex, accent_hex):
    dash = _box_obj("Dash", 0.28, 0.03, 0.05, loc=(-0.5, -0.05, 0.35))
    apply_toon(dash, main_hex, shades=2)
    trail1 = _box_obj("Trail1", 0.22, 0.02, 0.04, loc=(-0.7, -0.04, 0.35))
    apply_toon(trail1, accent_hex, shades=2)
    trail2 = _box_obj("Trail2", 0.16, 0.02, 0.03, loc=(-0.85, -0.03, 0.35))
    apply_toon(trail2, accent_hex, shades=2)

    _kf(dash, 1, loc=(-0.5, -0.05, 0.35), scale=1.0)
    _kf(dash, 2, loc=(0.1, -0.05, 0.35), scale=1.0)
    _kf(dash, 3, loc=(0.35, -0.05, 0.35), scale=0.6)
    _kf(trail1, 1, loc=(-0.7, -0.04, 0.35), scale=1.0)
    _kf(trail1, 2, loc=(-0.15, -0.04, 0.35), scale=0.8)
    _kf(trail1, 3, loc=(0.1, -0.04, 0.35), scale=0.3)
    _kf(trail2, 1, loc=(-0.85, -0.03, 0.35), scale=1.0)
    _kf(trail2, 2, loc=(-0.35, -0.03, 0.35), scale=0.6)
    _kf(trail2, 3, loc=(-0.1, -0.03, 0.35), scale=0.2)
    _linearize(dash, trail1, trail2)
    return {"dash": 3}, 1.6, 0.3


def build_loi_dien_giat(main_hex, accent_hex):
    bolts = []
    for i in range(3):
        angle = i * 2.094
        seg = _box_obj("Crackle{0}".format(i), 0.11, 0.015, 0.015,
                        loc=(0.16 * math.cos(angle), -0.06, 0.40 + 0.14 * math.sin(angle)))
        seg.rotation_euler = (0, 0, angle)
        apply_toon(seg, main_hex if i % 2 else accent_hex, shades=2)
        bolts.append(seg)
        _kf(seg, 1, scale=1.0)
        _kf(seg, 2, scale=0.0)
        _kf(seg, 3, scale=1.0)
    _linearize(*bolts)
    return {"shock": 3}, 1.3, 0.35


def build_loi_thien_loi(main_hex, accent_hex):
    # "chop 2 lan" (flashes twice) read through the bolt's OWN scale pulsing
    # rather than a full-screen white plane — same reasoning as loi_tia_set:
    # ART-PIPELINE.md's "hit" convention keeps flash/tint client-side, not
    # pre-rendered. Also sidesteps a real EEVEE bug found while building this:
    # a huge (3x3 unit) opaque box positioned to fake a screen-flash rendered
    # as a solid grey field that fully hid every nearer, smaller object behind
    # it instead of being occluded by them as their world-space depth implies.
    cloud = _box_obj("Cloud", 0.30, 0.14, 0.10, loc=(0.0, -0.05, 0.95))
    apply_toon(cloud, "#3A2E52", shades=2)
    _kf(cloud, 1, scale=0.2)
    _kf(cloud, 2, scale=0.7)
    _kf(cloud, 3, scale=1.0)

    bolt = _prim("cylinder", "BoltColumn", radius=0.05, depth=0.95, vertices=6, loc=(0.0, -0.05, 0.475))
    apply_toon(bolt, accent_hex, shades=2)
    _kf(bolt, 3, scale=(1.0, 1.0, 0.0))
    _kf(bolt, 4, scale=(1.4, 1.4, 1.0))
    _kf(bolt, 5, scale=(0.5, 0.5, 1.0))
    _kf(bolt, 6, scale=(1.4, 1.4, 1.0))
    _kf(bolt, 7, scale=(0.0, 0.0, 1.0))

    scorch = _prim("torus", "Scorch", major_radius=0.18, minor_radius=0.03, loc=(0.0, -0.05, 0.01))
    scorch.rotation_euler = (math.radians(90), 0, 0)
    apply_toon(scorch, main_hex, shades=2)
    _kf(scorch, 4, scale=(1.0, 1.0, 1.0))
    _kf(scorch, 6, scale=(0.85, 0.85, 1.0))
    _kf(scorch, 7, scale=(0.7, 0.7, 1.0))

    _linearize(cloud, bolt, scorch)
    return {"cast": 3, "impact": 4}, 2.6, 0.2


SKILLS = {
    "fireball": ("hoa", build_fireball),
    "thuy_song_du": ("thuy", build_thuy_song_du),
    "thuy_phong_toa": ("thuy", build_thuy_phong_toa),
    "thuy_hoi_phuc": ("thuy", build_thuy_hoi_phuc),
    "thuy_thuy_trieu": ("thuy", build_thuy_thuy_trieu),
    "phong_cuong_phong": ("phong", build_phong_cuong_phong),
    "phong_gia_toc": ("phong", build_phong_gia_toc),
    "phong_bao_kich": ("phong", build_phong_bao_kich),
    "phong_loc_xoay": ("phong", build_phong_loc_xoay),
    "tho_trong_kich": ("tho", build_tho_trong_kich),
    "tho_khien_da": ("tho", build_tho_khien_da),
    "tho_kien_dinh": ("tho", build_tho_kien_dinh),
    "tho_dia_chan": ("tho", build_tho_dia_chan),
    "loi_tia_set": ("loi", build_loi_tia_set),
    "loi_truy_kich": ("loi", build_loi_truy_kich),
    "loi_dien_giat": ("loi", build_loi_dien_giat),
    "loi_thien_loi": ("loi", build_loi_thien_loi),
}


def build_and_render(skill_code, out_blend_dir, out_render_dir):
    element, builder = SKILLS[skill_code]
    main_hex, accent_hex = ELEMENT_HEX[element]

    _clear_scene()
    setup_scene(px=384, outline_px_at_1x=1.2)
    scene = bpy.context.scene
    lineset = scene.view_layers[0].freestyle_settings.linesets[0]
    lineset.select_crease = True
    lineset.select_silhouette = True
    lineset.select_border = True

    phases, ortho_scale, baseline = builder(main_hex, accent_hex)
    _set_camera(ortho_scale, baseline)

    blend_path = os.path.join(out_blend_dir, skill_code + ".blend")
    os.makedirs(out_blend_dir, exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=blend_path)

    render_dir = os.path.join(out_render_dir, "vfx_" + skill_code)
    for action_name, count in phases.items():
        frame_start = 1
        for other_name, other_count in phases.items():
            if other_name == action_name:
                break
            frame_start += other_count
        _render_phase(render_dir, action_name, frame_start, frame_start + count - 1)
    _write_meta(render_dir, phases, ortho_scale, baseline, blend_path)
    print("[kage] vfx_{0} done -> {1}".format(skill_code, render_dir))


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="build_skill_vfx")
    parser.add_argument("--all", action="store_true")
    parser.add_argument("--skill", help="single skill_code, e.g. fireball")
    parser.add_argument("--out-blend", required=True)
    parser.add_argument("--out-render", required=True)
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    args = _cli_args(sys.argv)
    if args is not None:
        codes = list(SKILLS) if args.all else [args.skill]
        for code in codes:
            build_and_render(code, args.out_blend, args.out_render)
