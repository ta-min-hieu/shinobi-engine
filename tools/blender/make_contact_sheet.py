"""Tile rendered sprite frames into one image for review.

The acceptance pass in docs/14-art/ART-QUALITY-STANDARD.md §7 has to look at
several frames (idle for the build quality, run and attack for the motion
criteria). Opening them one by one is slow and makes it hard to see whether the
frames belong to the same character; a strip shows drift at a glance.

    blender -b -P tools/blender/make_contact_sheet.py -- \
      --dir assets/raw/render-out/char_hoa-b1 \
      --frames idle_00 run_01 run_02 attack_00 attack_02 die_05 \
      --out assets/raw/render-out/_review/hoa-b1.png [--scale 2]

Runs on a bare Blender (no .blend needed) — bpy is used only as an image
library, so there is nothing to set up and nothing to leave behind.
"""

import os
import sys

import bpy


def _load(path):
    image = bpy.data.images.load(path, check_existing=False)
    # Force the pixel buffer to load now; a lazily-loaded image reports 0x0.
    width, height = image.size
    if width == 0 or height == 0:
        raise RuntimeError("could not read image: " + path)
    return image


def build_sheet(frame_paths, out_path, scale=1, background=(1.0, 1.0, 1.0, 1.0)):
    """Lay the frames out left to right on an opaque background.

    Opaque on purpose: sprites are rendered on transparency, and judging edge
    quality against a checkerboard or a black void is how haloed edges get
    approved by mistake.
    """
    images = [_load(path) for path in frame_paths]
    cell_w = max(image.size[0] for image in images)
    cell_h = max(image.size[1] for image in images)
    sheet_w, sheet_h = cell_w * len(images), cell_h

    sheet = bpy.data.images.new("contact_sheet", width=sheet_w, height=sheet_h,
                                alpha=True)
    buffer = list(background) * (sheet_w * sheet_h)

    for index, image in enumerate(images):
        width, height = image.size
        pixels = list(image.pixels)
        x_offset = index * cell_w + (cell_w - width) // 2
        for row in range(height):
            src = row * width * 4
            dst = ((row * sheet_w) + x_offset) * 4
            for column in range(width):
                s, d = src + column * 4, dst + column * 4
                alpha = pixels[s + 3]
                for channel in range(3):
                    buffer[d + channel] = (pixels[s + channel] * alpha
                                           + buffer[d + channel] * (1.0 - alpha))
                buffer[d + 3] = 1.0

    sheet.pixels = buffer
    if scale != 1:
        sheet.scale(sheet_w * scale, sheet_h * scale)
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    sheet.filepath_raw = out_path
    sheet.file_format = "PNG"
    sheet.save()
    print("[kage] contact sheet: {0} ({1} frames)".format(out_path, len(images)))
    return out_path


def _cli_args(argv):
    if "--" not in argv:
        return None
    import argparse

    parser = argparse.ArgumentParser(prog="make_contact_sheet")
    parser.add_argument("--dir", required=True, help="a render-out/<asset> directory")
    parser.add_argument("--frames", nargs="+", required=True,
                        help="frame stems, e.g. idle_00 run_02 attack_02")
    parser.add_argument("--out", required=True, help="output PNG")
    parser.add_argument("--scale", type=int, default=1,
                        help="integer upscale, for inspecting small masters")
    return parser.parse_args(argv[argv.index("--") + 1:])


if __name__ == "__main__":
    args = _cli_args(sys.argv)
    if args is not None:
        build_sheet([os.path.join(args.dir, stem + ".png") for stem in args.frames],
                    args.out, scale=args.scale)
