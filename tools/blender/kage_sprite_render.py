"""Kage sprite render pipeline — Blender side.

Turns a rigged 3D character into flat cel-shaded 2D sprite sheets for the
libGDX client. See docs/14-art/ART-PIPELINE.md for the design rationale.

Two entry points, same file:

  * MCP session (interactive):
        exec(open(r'<repo>/tools/blender/kage_sprite_render.py').read())
        setup_scene(px=256)
        render_preview(r'<repo>/assets/raw/render-out/preview.png', action='idle')

  * Headless batch render:
        blender -b --python-exit-code 1 char.blend -P kage_sprite_render.py -- --all-actions --out <dir>

Hard constraints this script enforces (do not "fix" them without reading the doc):
  * Renders the MASTER at @2x only. Downscaling to @1x / @0.5x belongs to
    tools/asset-pipeline, never to Blender.
  * Character faces +X (screen right). The left-facing sprite is a runtime flip.
  * World origin z=0 is the character's feet; every frame puts that baseline on
    the same pixel row so all sprites share one ground line.
  * Transparent film — the client composites sprites over its own background.
"""

import json
import math
import os
import sys

import bpy
from mathutils import Vector

# --- Constants shared with ART-PIPELINE.md -----------------------------------

# The master render is @2x; the client's reference size is @1x.
MASTER_SCALE = 2

# Playback rate of the exported sheets (anime "on twos"). Blender's own frame
# rate is irrelevant: actions are resampled to a fixed frame count per action.
PLAYBACK_FPS = 12

# Standard action set (ART-PIPELINE.md §4.2): name -> (frame count, loops).
# A looping action drops its final frame because that frame repeats frame 0.
ACTION_SPEC = {
    "idle": (4, True),
    "run": (6, True),
    "attack": (5, False),
    "skill_cast": (6, False),
    "hit": (2, False),
    "jump": (4, False),
    "die": (6, False),
}

# Fraction of the frame height between the bottom edge and the feet baseline.
DEFAULT_BASELINE = 0.08

# Fraction of the frame kept empty on each side so swung limbs are not clipped.
DEFAULT_MARGIN = 0.10

# Outline width in @1x pixels (ART-PIPELINE.md §2.1); scaled up for the master.
DEFAULT_OUTLINE_PX_AT_1X = 2.5

# How far the orthographic camera sits back. Distance does not change framing
# under an orthographic projection; it only has to clear the geometry.
CAMERA_DISTANCE = 10.0

# Preview azimuth for the ADR-031 3D branch: the 3/4 view the pilot was approved
# on. See `view_yaw_deg` for why characters cannot be judged in pure profile.
ADR031_VIEW_YAW_DEG = 35.0

# Cel ramp brightness steps, darkest first, as multipliers of the base colour.
SHADE_STEPS = {
    2: (0.62, 1.0),
    3: (0.55, 0.80, 1.0),
    4: (0.45, 0.68, 0.86, 1.0),
}


# --- Colour helpers ----------------------------------------------------------


def _srgb_to_linear(channel):
    """Blender stores colours in linear space; art tools hand out sRGB hex."""
    if channel <= 0.04045:
        return channel / 12.92
    return ((channel + 0.055) / 1.055) ** 2.4


def hex_to_linear(hex_color):
    """'#E8552D' -> (r, g, b) in linear space."""
    cleaned = hex_color.lstrip("#")
    if len(cleaned) != 6:
        raise ValueError("expected a 6-digit hex colour, got: " + hex_color)
    channels = [int(cleaned[i:i + 2], 16) / 255.0 for i in (0, 2, 4)]
    return tuple(_srgb_to_linear(c) for c in channels)


# --- Scene setup -------------------------------------------------------------


def _resolve_eevee_engine():
    """EEVEE's identifier moved between releases; pick whichever this build has.

    Blender <=4.1 and >=5.0 use BLENDER_EEVEE, 4.2-4.5 use BLENDER_EEVEE_NEXT.
    """
    available = [
        item.identifier
        for item in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items
    ]
    for candidate in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE"):
        if candidate in available:
            return candidate
    raise RuntimeError("no EEVEE engine in this Blender build: " + str(available))


def view_yaw_deg(scene, override=None):
    """Azimuth to preview this .blend from, in degrees around the vertical axis.

    Zero keeps the historical sprite view: on the -Y axis looking +Y, so a
    character facing +X is seen in pure profile. That is right for everything
    still delivered as a sprite, and wrong for characters since ADR-031 — the
    approved pilot was signed off on the 3/4 view (`pilot_text3d_hoa_v2_goc_camera.png`),
    and in profile a generated head hides its own face behind the hair, which
    makes Q1-Q10 unscorable. ADR-031 names 3/4 35 degrees as the candidate and
    leaves the runtime value open precisely because it is no longer baked in.

    The 3D branch is identified the same way as its outline technique, so one
    file can never be a character for one decision and a sprite for the other.
    """
    if override is not None:
        return override
    return ADR031_VIEW_YAW_DEG if uses_shell_outline(scene) else 0.0


def _ensure_camera(scene, yaw_deg=0.0):
    camera = scene.camera
    if camera is None:
        camera_data = bpy.data.cameras.new("KageCam")
        camera = bpy.data.objects.new("KageCam", camera_data)
        scene.collection.objects.link(camera)
        scene.camera = camera
    camera.data.type = "ORTHO"
    # Orbit around the vertical axis, staying level and staying orthographic:
    # at yaw 0 this is the old -Y axis view looking towards +Y.
    yaw = math.radians(yaw_deg)
    camera.rotation_euler = (math.radians(90.0), 0.0, yaw)
    camera.location = (CAMERA_DISTANCE * math.sin(yaw),
                       -CAMERA_DISTANCE * math.cos(yaw),
                       camera.location.z)
    return camera


def _ensure_sun(scene, name, rotation, energy):
    light = bpy.data.objects.get(name)
    if light is None or light.type != "LIGHT":
        light_data = bpy.data.lights.new(name, type="SUN")
        light = bpy.data.objects.new(name, light_data)
        scene.collection.objects.link(light)
    light.data.type = "SUN"
    light.data.energy = energy
    light.data.angle = 0.0  # hard terminator: cel shading wants no soft falloff
    light.rotation_euler = rotation
    return light


def _ensure_freestyle(scene, thickness_px):
    """Freestyle draws the ink outline. It is a render pass, not a material."""
    scene.render.use_freestyle = True
    scene.render.line_thickness_mode = "ABSOLUTE"
    scene.render.line_thickness = thickness_px

    view_layer = scene.view_layers[0]
    view_layer.use_freestyle = True
    settings = view_layer.freestyle_settings
    if not settings.linesets:
        settings.linesets.new("KageOutline")
    lineset = settings.linesets[0]
    lineset.select_silhouette = True
    lineset.select_border = True
    lineset.select_crease = True
    # A lineset created from Python has no linestyle datablock attached
    # (the UI adds one for you, headless does not).
    if lineset.linestyle is None:
        lineset.linestyle = bpy.data.linestyles.new("KageOutlineStyle")
    lineset.linestyle.color = (0.0, 0.0, 0.0)
    lineset.linestyle.thickness = thickness_px


def uses_shell_outline(scene):
    """True when this .blend inks itself with `add_inverted_hull_outline`.

    Detected from the modifier that function leaves behind rather than from a
    flag, so a file can never disagree with itself about which of the two
    outline techniques it uses (ART-QUALITY-STANDARD §4 item 3).
    """
    return any(obj.type == "MESH" and obj.modifiers.get("KageOutline") is not None
               for obj in scene.objects)


def setup_scene(px=256, outline_px_at_1x=DEFAULT_OUTLINE_PX_AT_1X, samples=32,
                view_yaw=None):
    """Configure the whole scene for sprite rendering. Idempotent.

    px is the MASTER (@2x) resolution — 256 for characters, 512 for bosses,
    384 for skill VFX, 128 for icons (ART-PIPELINE.md §4.1).
    """
    scene = bpy.context.scene
    scene.render.engine = _resolve_eevee_engine()
    scene.render.film_transparent = True
    scene.render.resolution_x = px
    scene.render.resolution_y = px
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.image_settings.color_depth = "8"
    scene.render.fps = PLAYBACK_FPS

    if hasattr(scene, "eevee") and hasattr(scene.eevee, "taa_render_samples"):
        scene.eevee.taa_render_samples = samples

    # The outline is authored in @1x pixels but rendered on the @2x master.
    if uses_shell_outline(scene):
        # The file already draws its ink with an inverted hull. Freestyle on top
        # of that is what turns a Text->3D character into a solid black blob:
        # it contours every one of the generated hair locks. `setup_scene` runs
        # again on every headless render, so honouring the file's technique has
        # to happen here — `add_inverted_hull_outline` turning Freestyle off at
        # authoring time is undone the moment the CLI renders the saved .blend.
        scene.render.use_freestyle = False
    else:
        _ensure_freestyle(scene, outline_px_at_1x * MASTER_SCALE)
    _ensure_camera(scene, view_yaw_deg(scene, view_yaw))
    # Key from the front-upper-right so the face (facing +X) stays lit.
    _ensure_sun(scene, "KageKey", (math.radians(35.0), 0.0, math.radians(35.0)), 3.0)
    # Fill from the opposite side, weak enough to stay inside the darkest band.
    _ensure_sun(scene, "KageFill", (math.radians(-25.0), 0.0, math.radians(-150.0)), 0.8)

    print("[kage] scene ready: engine={0} px={1} outline={2}px".format(
        scene.render.engine, px, scene.render.line_thickness))
    return scene


# --- Toon material -----------------------------------------------------------


def make_toon_material(name, base_hex, shades=3):
    """Build a flat cel material: Diffuse -> Shader to RGB -> stepped ramp."""
    if shades not in SHADE_STEPS:
        raise ValueError("shades must be one of " + str(sorted(SHADE_STEPS)))

    material = bpy.data.materials.get(name)
    if material is None:
        material = bpy.data.materials.new(name)
    # The graph below is Emission-only — glTF cannot carry it, and the base
    # colour is only recoverable from it by reading the last ColorRamp element
    # and knowing that SHADE_STEPS[n][-1] happens to be 1.0. Record the input
    # instead of leaving the 3D exporter to reverse-engineer the output:
    # `kage_text3d_kit.export_gltf_flat` reads this back.
    material["kage_base_hex"] = base_hex
    material.use_nodes = True
    tree = material.node_tree
    tree.nodes.clear()

    output = tree.nodes.new("ShaderNodeOutputMaterial")
    output.location = (600, 0)
    emission = tree.nodes.new("ShaderNodeEmission")
    emission.location = (400, 0)
    ramp = tree.nodes.new("ShaderNodeValToRGB")
    ramp.location = (150, 0)
    to_rgb = tree.nodes.new("ShaderNodeShaderToRGB")
    to_rgb.location = (-100, 0)
    diffuse = tree.nodes.new("ShaderNodeBsdfDiffuse")
    diffuse.location = (-320, 0)
    diffuse.inputs["Color"].default_value = (1.0, 1.0, 1.0, 1.0)

    base = hex_to_linear(base_hex)
    steps = SHADE_STEPS[shades]
    color_ramp = ramp.color_ramp
    color_ramp.interpolation = "CONSTANT"
    while len(color_ramp.elements) > 1:
        color_ramp.elements.remove(color_ramp.elements[-1])
    color_ramp.elements[0].position = 0.0
    color_ramp.elements[0].color = tuple(min(1.0, c * steps[0]) for c in base) + (1.0,)
    for index in range(1, shades):
        position = index / float(shades)
        element = color_ramp.elements.new(position)
        element.color = tuple(min(1.0, c * steps[index]) for c in base) + (1.0,)

    tree.links.new(diffuse.outputs["BSDF"], to_rgb.inputs["Shader"])
    tree.links.new(to_rgb.outputs["Color"], ramp.inputs["Fac"])
    tree.links.new(ramp.outputs["Color"], emission.inputs["Color"])
    tree.links.new(emission.outputs["Emission"], output.inputs["Surface"])
    return material


def apply_toon(obj, base_hex, shades=3):
    """Replace obj's materials with a single toon material named after it."""
    if obj.type != "MESH":
        raise TypeError("apply_toon expects a mesh, got: " + obj.type)
    material = make_toon_material("toon_" + obj.name, base_hex, shades)
    obj.data.materials.clear()
    obj.data.materials.append(material)
    return material


# --- Camera framing ----------------------------------------------------------


def _iter_action_frames(armature, action_names):
    """Yield every integer frame of every named action, restoring state after."""
    if armature is None or armature.animation_data is None:
        yield None
        return
    original = armature.animation_data.action
    try:
        for name in action_names:
            action = bpy.data.actions.get(name)
            if action is None:
                continue
            armature.animation_data.action = action
            start, end = action.frame_range
            for frame in range(int(math.floor(start)), int(math.ceil(end)) + 1):
                yield frame
    finally:
        armature.animation_data.action = original


def _world_bounds(objects, depsgraph):
    """Axis-aligned world bounds of the evaluated (deformed) meshes."""
    min_v = Vector((math.inf, math.inf, math.inf))
    max_v = Vector((-math.inf, -math.inf, -math.inf))
    for obj in objects:
        if obj.type != "MESH":
            continue
        evaluated = obj.evaluated_get(depsgraph)
        matrix = evaluated.matrix_world
        for corner in evaluated.bound_box:
            world = matrix @ Vector(corner)
            for axis in range(3):
                min_v[axis] = min(min_v[axis], world[axis])
                max_v[axis] = max(max_v[axis], world[axis])
    return min_v, max_v


def _half_width_across_view(low, high, yaw_rad):
    """Half-extent of an axis-aligned box along the camera's horizontal axis.

    At yaw 0 this is just the X extent, which is what the sheet pipeline always
    measured. Once the camera orbits, world X stops being what the frame is wide
    in, and fitting against it clips the character off the side of a 3/4 shot.
    Projecting the four vertical edges of the box is conservative — a box is
    never tighter than the mesh inside it — so a pose can be framed loosely but
    never cropped.
    """
    cos_yaw, sin_yaw = math.cos(yaw_rad), math.sin(yaw_rad)
    return max(abs(x * cos_yaw + y * sin_yaw)
               for x in (low.x, high.x) for y in (low.y, high.y))


def fit_camera(objects, margin=DEFAULT_MARGIN, armature=None, actions=None,
               baseline=DEFAULT_BASELINE, yaw_deg=None):
    """Frame the character so no pose is ever clipped and feet stay put.

    Sweeps every frame of every action in `actions` (defaults to the standard
    set) rather than the current pose, because a swung weapon on frame 3 of
    `attack` is what decides the safe ortho_scale for the whole sheet.

    The feet (world z=0) always land at `baseline` of the frame height measured
    from the bottom edge, so every sprite shares one ground line and the pivot
    is a constant (px/2, baseline*px).
    """
    scene = bpy.context.scene
    depsgraph = bpy.context.evaluated_depsgraph_get()
    meshes = [obj for obj in objects if obj.type == "MESH"]
    if not meshes:
        raise ValueError("fit_camera needs at least one mesh object")

    names = list(actions) if actions is not None else list(ACTION_SPEC)
    original_frame = scene.frame_current
    yaw = math.radians(view_yaw_deg(scene, yaw_deg))
    min_z = math.inf
    max_z = -math.inf
    half_width = 0.0

    for frame in _iter_action_frames(armature, names):
        if frame is not None:
            scene.frame_set(frame)
            depsgraph = bpy.context.evaluated_depsgraph_get()
        low, high = _world_bounds(meshes, depsgraph)
        half_width = max(half_width, _half_width_across_view(low, high, yaw))
        min_z, max_z = min(min_z, low.z), max(max_z, high.z)
    scene.frame_set(original_frame)

    # Horizontal extent is symmetric about the view axis to keep the pivot centred.
    needed_horizontal = (2.0 * half_width) / max(1e-6, 1.0 - 2.0 * margin)
    needed_up = max(0.0, max_z) / max(1e-6, 1.0 - baseline - margin)
    needed_down = max(0.0, -min_z) / max(1e-6, baseline)
    ortho_scale = max(needed_horizontal, needed_up, needed_down)

    camera = _ensure_camera(scene, math.degrees(yaw))
    camera.data.ortho_scale = ortho_scale
    camera.location.z = ortho_scale * (0.5 - baseline)

    # Anything below z=0 has to fit in the thin strip under the baseline, so a
    # pose that sinks through the floor costs 1/baseline (12.5x) of zoom and
    # silently shrinks the character to a speck. Loud is better than subtle.
    if needed_down > max(needed_horizontal, needed_up):
        print("[kage] WARNING: geometry reaches z={0:.3f}, below the ground plane; "
              "it alone forces ortho_scale={1:.2f}. Fix the pose instead of the "
              "camera.".format(min_z, ortho_scale))

    pivot_y_px = baseline * scene.render.resolution_y
    print("[kage] fit: ortho_scale={0:.3f} pivot=({1:.0f},{2:.0f} from bottom)".format(
        ortho_scale, scene.render.resolution_x / 2.0, pivot_y_px))
    return ortho_scale


# --- Rendering ---------------------------------------------------------------


def _set_action(armature, action_name):
    if action_name is None:
        return None
    action = bpy.data.actions.get(action_name)
    if action is None:
        raise KeyError("no action named '{0}' in this .blend".format(action_name))
    if armature.animation_data is None:
        armature.animation_data_create()
    armature.animation_data.action = action
    return action


def _sample_frames(action, count, loop):
    """Even sample of an action. Looping actions exclude the duplicate end."""
    start, end = action.frame_range
    if count <= 1:
        return [start]
    span = end - start
    divisor = count if loop else count - 1
    return [start + span * index / float(divisor) for index in range(count)]


def _render_still(filepath):
    scene = bpy.context.scene
    scene.render.filepath = filepath
    bpy.ops.render.render(write_still=True)


def render_preview(path, action=None, frame=None, armature=None):
    """Render exactly one frame, for style approval before the full sheet."""
    scene = bpy.context.scene
    if action is not None:
        target = armature if armature is not None else find_armature()
        chosen = _set_action(target, action)
        scene.frame_set(int(frame if frame is not None else chosen.frame_range[0]))
    elif frame is not None:
        scene.frame_set(int(frame))
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    _render_still(os.path.splitext(path)[0])
    print("[kage] preview -> " + path)
    return path


def render_action(armature, action_name, n_frames, out_dir, loop=None):
    """Render one action as <action>_00.png .. <action>_NN.png."""
    if loop is None:
        loop = ACTION_SPEC.get(action_name, (n_frames, False))[1]
    action = _set_action(armature, action_name)
    scene = bpy.context.scene
    os.makedirs(out_dir, exist_ok=True)

    written = []
    for index, frame in enumerate(_sample_frames(action, n_frames, loop)):
        whole = int(math.floor(frame))
        scene.frame_set(whole, subframe=frame - whole)
        filepath = os.path.join(out_dir, "{0}_{1:02d}".format(action_name, index))
        _render_still(filepath)
        written.append(filepath + ".png")
    print("[kage] {0}: {1} frames{2}".format(
        action_name, len(written), " (loop)" if loop else ""))
    return written


def render_all_actions(armature, mapping=None, out_dir=".", objects=None):
    """Render the standard sheet set and write render-meta.json beside it.

    The sidecar is what tools/asset-pipeline reads to fill the art_asset row
    (ART-PIPELINE.md §8.3) — frame counts, pivot and ortho_scale.
    """
    scene = bpy.context.scene
    spec = mapping if mapping is not None else {
        name: count for name, (count, _) in ACTION_SPEC.items()
        if bpy.data.actions.get(name) is not None
    }
    if not spec:
        raise RuntimeError("no standard actions found in this .blend")

    meshes = objects if objects is not None else renderable_meshes(scene)
    fit_camera(meshes, armature=armature, actions=list(spec))

    rendered = {}
    for action_name, count in spec.items():
        rendered[action_name] = len(
            render_action(armature, action_name, count, out_dir))

    meta = {
        "master_px": scene.render.resolution_x,
        "master_scale": MASTER_SCALE,
        "playback_fps": PLAYBACK_FPS,
        "actions": rendered,
        "pivot_x_px": scene.render.resolution_x / 2.0,
        "pivot_y_px_from_bottom": DEFAULT_BASELINE * scene.render.resolution_y,
        "ortho_scale": scene.camera.data.ortho_scale,
        "blend_file": bpy.data.filepath,
    }
    meta_path = os.path.join(out_dir, "render-meta.json")
    with open(meta_path, "w", encoding="utf-8") as handle:
        json.dump(meta, handle, indent=2, sort_keys=True)
    print("[kage] done: {0} actions -> {1}".format(len(rendered), out_dir))
    return meta


def renderable_meshes(scene):
    """Meshes the camera must frame — everything the render will actually show.

    `hide_render` objects are excluded on purpose. A Text->3D character file
    keeps the raw Rodin import beside the normalised copy (it cannot be
    regenerated, see `kage_text3d_kit.normalise`), and that import still sits at
    the generator's own scale and origin. Framing it drags the camera down to
    whatever is below the floor: on the first Thuy build it alone pushed
    ortho_scale from 1.47 to 11.86 and rendered the character as a speck. The
    scale guides are hidden for the same reason.
    """
    return [obj for obj in scene.objects
            if obj.type == "MESH" and not obj.hide_render]


def find_armature():
    """First armature in the scene — the character rig by convention."""
    for obj in bpy.context.scene.objects:
        if obj.type == "ARMATURE":
            return obj
    raise RuntimeError("no armature in this .blend")


# --- Headless CLI ------------------------------------------------------------


def _cli_args(argv):
    """Args after the '--' separator Blender uses to hand over to the script."""
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="kage_sprite_render")
    parser.add_argument("--all-actions", action="store_true",
                        help="render every standard action found in the file")
    parser.add_argument("--preview", metavar="ACTION",
                        help="render a single frame of ACTION into --out and stop; "
                             "this is the style-approval gate before a full sheet "
                             "(BLENDER-SESSION-GUIDE §1 step 4)")
    parser.add_argument("--preview-frame", type=int, default=None,
                        help="frame index within --preview's action (default: first)")
    parser.add_argument("--out", required=True, help="output directory")
    parser.add_argument("--px", type=int, default=256,
                        help="master (@2x) resolution, see ART-PIPELINE §4.1")
    parser.add_argument("--view-yaw", type=float, default=None,
                        help="preview azimuth in degrees around the vertical axis. "
                             "Default: 0 for sprite assets, {0:.0f} (the approved "
                             "3/4 view) for ADR-031 character files, detected from "
                             "the outline technique the file uses.".format(
                                 ADR031_VIEW_YAW_DEG))
    parser.add_argument("--outline-px", type=float, default=None,
                        help="outline width in @1x px. Default: keep whatever the .blend "
                             "was built with, because the right width depends on how big "
                             "the character is inside the frame and the build script "
                             "already picked it. Passing 2.5 (the art-direction figure) "
                             "onto a 256px character sheet floods it into a black blob.")
    return parser.parse_args(argv[argv.index("--") + 1:])


def _resolve_outline(requested):
    """Explicit width wins; otherwise reuse the one baked into the .blend."""
    if requested is not None:
        return requested
    saved = bpy.context.scene.render.line_thickness
    if saved > 0.0:
        return saved / MASTER_SCALE
    return DEFAULT_OUTLINE_PX_AT_1X


def _main():
    args = _cli_args(sys.argv)
    if args is None:
        return
    args.outline_px = _resolve_outline(args.outline_px)
    if args.preview:
        setup_scene(px=args.px, outline_px_at_1x=args.outline_px,
                    view_yaw=args.view_yaw)
        armature = find_armature()
        # Frame the preview exactly as the sheet will be framed, so what gets
        # approved here is what comes out of the batch render.
        fit_camera(renderable_meshes(bpy.context.scene), armature=armature,
                   yaw_deg=args.view_yaw)
        frames = None
        if args.preview_frame is not None:
            action = bpy.data.actions[args.preview]
            count, loop = ACTION_SPEC.get(args.preview, (1, False))
            frames = _sample_frames(action, count, loop)[args.preview_frame]
        render_preview(os.path.join(args.out, "_preview_" + args.preview + ".png"),
                       action=args.preview, frame=frames, armature=armature)
        return
    if args.all_actions:
        setup_scene(px=args.px, outline_px_at_1x=args.outline_px,
                    view_yaw=args.view_yaw)
        render_all_actions(find_armature(), out_dir=args.out)


if __name__ == "__main__":
    _main()
