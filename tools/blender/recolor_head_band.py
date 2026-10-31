"""Paint a red forehead band OUT of a Text->3D character's diffuse texture (lot 24, ADR-038 cleanup).

    blender -b <char>.blend --python-exit-code 1 -P tools/blender/recolor_head_band.py -- \
        --body Hoa_D --image texture_diffuse.003 --min-z 0.70 [--min-red 0.45] [--dry --preview out.png]

Why this exists next to `strip_headband.py`: that script (2026-09-01) removed only the METAL PLATE of
`char_hoa-b1`'s ninja band and filled it with the band's own red cloth, so the band itself stayed — and
in the client camera it is the first thing the eye lands on. The body cannot be regenerated (the Rodin
key is out of funds), so the texture is edited in place: every saturated-red texel inside the UV
footprint of faces that sit wholly ABOVE `--min-z` (the forehead and up, i.e. above the eyes and blush)
becomes the hair colour, sampled as the median of the dark texels in that same footprint.

`--dry` changes nothing and writes `--preview`: the texture with every texel that WOULD change painted
pure green, so one look settles whether the mask caught the band and only the band.
"""

import argparse
import sys

import bpy


def _args():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    parser = argparse.ArgumentParser(prog="recolor_head_band")
    parser.add_argument("--body", required=True)
    parser.add_argument("--image", required=True)
    parser.add_argument("--min-z", type=float, required=True)
    # Ngưỡng đỏ tối thiểu. 0.45 bắt thân dải băng; lượt thứ hai 0.22 bắt viền bóng đỏ sẫm dọc mép tóc
    # (trên tóc gần đen, đỏ sẫm trong vùng trán chỉ có thể là băng) — đo trên char_hoa-b1, 2026-10-31.
    parser.add_argument("--min-red", type=float, default=0.45)
    parser.add_argument("--dry", action="store_true")
    parser.add_argument("--preview", default="")
    return parser.parse_args(argv)


def _is_band_red(r, g, b, min_red):
    return r > min_red and r > 1.8 * g and r > 1.8 * b


def _footprint(body, width, height, min_z):
    """Texel indices covered by faces whose every vertex is above `min_z` (world space)."""
    mesh = body.data
    uv = mesh.uv_layers.active.data
    world = body.matrix_world
    zs = [(world @ v.co).z for v in mesh.vertices]
    covered = set()
    for poly in mesh.polygons:
        if min(zs[i] for i in poly.vertices) < min_z:
            continue
        pts = [(uv[li].uv[0] * width, uv[li].uv[1] * height) for li in poly.loop_indices]
        for k in range(1, len(pts) - 1):
            _raster_triangle(pts[0], pts[k], pts[k + 1], width, height, covered)
    return covered


def _raster_triangle(a, b, c, width, height, out):
    min_x = max(0, int(min(a[0], b[0], c[0])))
    max_x = min(width - 1, int(max(a[0], b[0], c[0])) + 1)
    min_y = max(0, int(min(a[1], b[1], c[1])))
    max_y = min(height - 1, int(max(a[1], b[1], c[1])) + 1)
    area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
    if abs(area) < 1e-9:
        return
    for y in range(min_y, max_y + 1):
        py = y + 0.5
        for x in range(min_x, max_x + 1):
            px = x + 0.5
            w0 = (b[0] - px) * (c[1] - py) - (b[1] - py) * (c[0] - px)
            w1 = (c[0] - px) * (a[1] - py) - (c[1] - py) * (a[0] - px)
            w2 = (a[0] - px) * (b[1] - py) - (a[1] - py) * (b[0] - px)
            if (w0 >= 0 and w1 >= 0 and w2 >= 0) or (w0 <= 0 and w1 <= 0 and w2 <= 0):
                out.add(y * width + x)


def main():
    args = _args()
    body = bpy.data.objects[args.body]
    image = bpy.data.images[args.image]
    width, height = image.size
    pixels = list(image.pixels)
    footprint = _footprint(body, width, height, args.min_z)
    band = [i for i in footprint if _is_band_red(pixels[4 * i], pixels[4 * i + 1], pixels[4 * i + 2], args.min_red)]
    dark = sorted((pixels[4 * i] + pixels[4 * i + 1] + pixels[4 * i + 2], i) for i in footprint
                  if pixels[4 * i] + pixels[4 * i + 1] + pixels[4 * i + 2] < 0.36)
    hair_index = dark[len(dark) // 2][1] if dark else None
    hair = (pixels[4 * hair_index], pixels[4 * hair_index + 1], pixels[4 * hair_index + 2]) if dark else (0.06, 0.05, 0.05)
    print("[band] footprint={0} band_red={1} hair={2}".format(len(footprint), len(band),
                                                           tuple(round(c, 3) for c in hair)))
    paint = (0.0, 1.0, 0.0) if args.dry else hair
    for i in band:
        pixels[4 * i], pixels[4 * i + 1], pixels[4 * i + 2] = paint
    if args.dry:
        if args.preview:
            preview = bpy.data.images.new("band_preview", width, height)
            preview.pixels = pixels
            preview.filepath_raw = args.preview
            preview.file_format = "PNG"
            preview.save()
            print("[band] preview -> " + args.preview)
        return
    image.pixels = pixels
    image.pack()
    bpy.ops.wm.save_mainfile()
    print("[band] painted {0} texels, saved {1}".format(len(band), bpy.data.filepath))


main()
