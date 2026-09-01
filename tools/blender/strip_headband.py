"""Xoa TAM KIM LOAI tren bang tran khoi anh dong goi trong .blend (ADR-038 Dot 3 muc #2).

TRANG THAI: chay duoc tron vong (Blender headless -> to lai -> xuat lai -> render -> diff),
0 luot Rodin. NHUNG mask hien tai la CA VUNG DAU, chua phai rieng bang tran — xuong 'head'
bao ca toc/mat/tai nen phep to lan sang vai cho khac (do that: hop bao vung doi trai gan het
texture 1024, va anh diff cho thay dom nho o mi mat). Xem ART-AUDIT-TU-TIEN §4.6 truoc khi
chay hang loat. CHUA duoc ap dung cho bat ky .blend nao trong repo.

Khoanh vung theo TRONG SO XUONG (vertex group 'head') chu KHONG theo luat mau toan cuc —
luat mau da thu va hong tren Thuy/Tho/Loi, xem ART-AUDIT-TU-TIEN §4.4.

    blender -b <char>.blend -P strip_headband.py -- --save-as <out.blend> [--dry]
"""
import sys, math
import bpy
from collections import Counter

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
SAVE_AS = argv[argv.index("--save-as") + 1] if "--save-as" in argv else None
DRY = "--dry" in argv

PLATE_SPREAD = 18      # bao hoa thap
PLATE_LO, PLATE_HI = 110, 200   # do sang, thang 0..255
BRUSH = 3              # ban kinh danh dau quanh moi dinh dau (mesh rat day, du phu)

def body_object():
    best = None
    for o in bpy.data.objects:
        if o.type != 'MESH' or not o.data.uv_layers:
            continue
        if 'head' not in [g.name for g in o.vertex_groups]:
            continue
        if best is None or len(o.data.vertices) > len(best.data.vertices):
            best = o
    return best

def diffuse_image():
    cands = [im for im in bpy.data.images
             if im.packed_file and im.size[0] > 0 and 'normal' not in im.name.lower()
             and 'metallic' not in im.name.lower() and 'roughness' not in im.name.lower()]
    return max(cands, key=lambda im: im.size[0]) if cands else None

obj = body_object()
img = diffuse_image()
if obj is None or img is None:
    print("KAGE-RESULT skip lydo=khong-tim-thay-than-hoac-anh")
    sys.exit(0)

head_idx = obj.vertex_groups['head'].index
head_verts = set()
for v in obj.data.vertices:
    tot = sum(g.weight for g in v.groups) or 1.0
    w = sum(g.weight for g in v.groups if g.group == head_idx)
    if w / tot >= 0.5:
        head_verts.add(v.index)

W, H = img.size
uv = obj.data.uv_layers.active.data
mask = bytearray(W * H)
marked = 0
for poly in obj.data.polygons:
    for li in poly.loop_indices:
        vi = obj.data.loops[li].vertex_index
        if vi not in head_verts:
            continue
        u, v = uv[li].uv
        px = int(u * W) % W
        py = int(v * H) % H
        for dy in range(-BRUSH, BRUSH + 1):
            yy = py + dy
            if yy < 0 or yy >= H: continue
            for dx in range(-BRUSH, BRUSH + 1):
                xx = px + dx
                if xx < 0 or xx >= W: continue
                k = yy * W + xx
                if not mask[k]:
                    mask[k] = 1; marked += 1

buf = list(img.pixels)   # RGBA float, dai W*H*4
plate = bytearray(W * H)
n = 0
for k in range(W * H):
    if not mask[k]: continue
    i = k * 4
    r, g, b = buf[i] * 255, buf[i+1] * 255, buf[i+2] * 255
    hi, lo = max(r, g, b), min(r, g, b)
    if hi - lo <= PLATE_SPREAD and PLATE_LO <= hi <= PLATE_HI:
        plate[k] = 1; n += 1

if n == 0:
    print("KAGE-RESULT none vung_dau=%d" % marked)
    sys.exit(0)

votes = Counter()
for k in range(W * H):
    if not plate[k]: continue
    y, x = divmod(k, W)
    for dy in (-1, 0, 1):
        yy = y + dy
        if yy < 0 or yy >= H: continue
        for dx in (-1, 0, 1):
            xx = x + dx
            if xx < 0 or xx >= W: continue
            k2 = yy * W + xx
            if plate[k2] or not mask[k2]: continue
            i2 = k2 * 4
            votes[(round(buf[i2], 3), round(buf[i2+1], 3), round(buf[i2+2], 3))] += 1
if not votes:
    print("KAGE-RESULT nofill plate=%d" % n)
    sys.exit(0)
# Uu tien mau VAI co sac do (khan/bang) thay vi toc den: toc bao quanh tam kim loai nen
# no thang phieu bau neu dem tat ca, va to tam kim loai thanh den la doi mot mieng vá lay
# mot mieng vá khac. Chi khi khong co hang xom nao co sac do moi dung mode tong the.
def chroma(c):
    return max(c) - min(c)
chromatic = [(c, k) for c, k in votes.items() if chroma(c) > 0.10]
fill = max(chromatic, key=lambda t: t[1])[0] if chromatic else votes.most_common(1)[0][0]

if not DRY:
    for k in range(W * H):
        if plate[k]:
            i = k * 4
            buf[i], buf[i+1], buf[i+2] = fill
    img.pixels[:] = buf
    img.pack()
    if SAVE_AS:
        bpy.ops.wm.save_as_mainfile(filepath=SAVE_AS)

print("KAGE-RESULT ok anh=%s kich_thuoc=%dx%d vung_dau=%d tam_kim_loai=%d mau=%s" %
      (img.name, W, H, marked, n, tuple(round(c, 3) for c in fill)))
