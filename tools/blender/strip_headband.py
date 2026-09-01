"""Xoa TAM KIM LOAI tren bang tran khoi anh dong goi trong .blend (ADR-038 Dot 3 muc #2).

    blender -b <char>.blend -P strip_headband.py -- --seed u,v [--save-as out.blend] [--dry]

TRANG THAI 2026-09-01: CHUA DUNG DUOC HANG LOAT. Da chay tron vong that (Blender headless ->
to lai anh dong goi -> luu .blend -> export_character_lods.py -> render -> diff bang may),
0 luot Rodin, va da chung minh duoc hai thu:

  1. Khoanh vung theo TRONG SO XUONG ('head') hoat dong; luat mau toan cuc thi KHONG
     (bat trung ao Thuy 55k px, mat tam kim loai o Tho/Loi). Xem ART-AUDIT §4.4.
  2. Do TIM tam kim loai hoan toan tu dong thi KHONG hoi tu. Da thu bon cach:
     nguong xam co dinh (bo sot phan bat sang), cum do cao (cat mat tam vi tam la hinh
     chu nhat cao chu khong phai vanh mong), mang lien thong (chi phu duoc mot phan),
     va noi tran do sang (mang lon nhat nhay sang vung khac han). Xem ART-AUDIT §4.6.

Vi vay thiet ke dung la CHI DIEM: nguoi mo texture trong UV editor cua Blender, doc mot toa
do nam tren tam kim loai, truyen qua --seed. Tu do phep loang + hop bao lam not, va no chac
chan. VIEC CON LAI: xac dinh quy uoc u,v that (thu mu 4 toa do deu truot vung dau — quy uoc
V giua texture xuat ra va anh trong .blend khong nhu suy luan), roi chay 15 nhan vat.

CHUA duoc ap dung cho bat ky .blend nao trong repo.
"""
import sys, math
import bpy
from collections import Counter

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
SAVE_AS = argv[argv.index("--save-as") + 1] if "--save-as" in argv else None
DRY = "--dry" in argv
# Toa do MOT diem nam TREN tam kim loai, tinh theo ti le 0..1 cua texture ("--seed u,v").
# Vi sao can: bon vong thu dam do hoan toan tu dong (nguong xam, cum do cao, mang lien thong)
# deu KHONG hoi tu — mat kim loai bat sang trung dai do sang voi long trang mat va vet highlight,
# con hoa tiet khac thi toi ngang toc. Mot diem chi dan bo het mo ho do, va no re: 15 lan chi diem
# cho 15 nhan vat. Xem ART-AUDIT-TU-TIEN §4.6.
SEED = None
if "--seed" in argv:
    su, sv = argv[argv.index("--seed") + 1].split(",")
    SEED = (float(su), float(sv))

PLATE_SPREAD = 18      # bao hoa thap
PLATE_LO, PLATE_HI = 105, 250   # do sang. Tran phai CAO: mat kim loai bat sang toi ~240,
                                # nguong 200 chi bat duoc nua toi cua tam va de lai mang bac.
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
# Do cao (truc Z cua Blender) cua manh hinh hoc phu len tung pixel. Dung de siet mask tu
# CA DAU xuong DUNG DAI BANG TRAN o buoc sau — xuong 'head' bao ca toc/mat/tai nen mot minh
# no qua rong (do that: hop bao vung doi trai gan het texture). Xem ART-AUDIT §4.6.
zmap = [0.0] * (W * H)
marked = 0
for poly in obj.data.polygons:
    for li in poly.loop_indices:
        vi = obj.data.loops[li].vertex_index
        if vi not in head_verts:
            continue
        u, v = uv[li].uv
        z = obj.data.vertices[vi].co.z
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
                zmap[k] = z

buf = list(img.pixels)   # RGBA float, dai W*H*4
# Loang tu DIEM CHI DAN: tu hat giong, lan sang moi pixel lien ke con "giong kim loai"
# (bao hoa thap), BAT KE do sang — nho vay ca phan bat sang lan phan trong bong deu vao, ma
# khong lan sang long trang mat hay highlight o dao UV khac vi chung khong lien thong.
if SEED is None:
    print("KAGE-RESULT need-seed  (chay lai voi --seed u,v tro vao tam kim loai)")
    sys.exit(0)
sx = min(W - 1, max(0, int(SEED[0] * W)))
sy = min(H - 1, max(0, int(SEED[1] * H)))
seed_k = sy * W + sx
if not mask[seed_k]:
    print("KAGE-RESULT seed-ngoai-vung-dau u=%.4f v=%.4f" % SEED)
    sys.exit(0)

def metallic(k):
    i = k * 4
    r, g, b = buf[i] * 255, buf[i+1] * 255, buf[i+2] * 255
    return (max(r, g, b) - min(r, g, b)) <= 26 and max(r, g, b) >= 90

region = bytearray(W * H)
stack = [seed_k]; region[seed_k] = 1
minx = maxx = sx; miny = maxy = sy; size = 0
while stack:
    k = stack.pop(); size += 1
    y, x = divmod(k, W)
    if x < minx: minx = x
    if x > maxx: maxx = x
    if y < miny: miny = y
    if y > maxy: maxy = y
    for dy in (-1, 0, 1):
        yy = y + dy
        if yy < 0 or yy >= H: continue
        for dx in (-1, 0, 1):
            xx = x + dx
            if xx < 0 or xx >= W: continue
            k2 = yy * W + xx
            if not region[k2] and mask[k2] and metallic(k2):
                region[k2] = 1; stack.append(k2)

grey_only = bytes(region)
PAD = 2
plate = bytearray(W * H)
n = 0
for y in range(max(0, miny - PAD), min(H, maxy + PAD + 1)):
    for x in range(max(0, minx - PAD), min(W, maxx + PAD + 1)):
        plate[y * W + x] = 1; n += 1
print("KAGE-SEED loang=%d hop_bao=(%d,%d)-(%d,%d) phu=%d" % (size, minx, miny, maxx, maxy, n))



comp_id = [0] * (W * H)
comps = []
for k0 in range(W * H):
    if not plate[k0] or comp_id[k0]:
        continue
    cid = len(comps) + 1
    stack = [k0]; comp_id[k0] = cid
    minx = maxx = k0 % W; miny = maxy = k0 // W; size = 0
    while stack:
        k = stack.pop(); size += 1
        y, x = divmod(k, W)
        if x < minx: minx = x
        if x > maxx: maxx = x
        if y < miny: miny = y
        if y > maxy: maxy = y
        for dy in (-1, 0, 1):
            yy = y + dy
            if yy < 0 or yy >= H: continue
            for dx in (-1, 0, 1):
                xx = x + dx
                if xx < 0 or xx >= W: continue
                k2 = yy * W + xx
                if plate[k2] and not comp_id[k2]:
                    comp_id[k2] = cid; stack.append(k2)
    comps.append((size, minx, miny, maxx, maxy))
comps.sort(reverse=True)
biggest = comps[0][0]
keep = [c for c in comps if c[0] >= biggest * 0.25]
PAD = 2
fillbox = bytearray(W * H)
n = 0
for _, minx, miny, maxx, maxy in keep:
    for y in range(max(0, miny - PAD), min(H, maxy + PAD + 1)):
        for x in range(max(0, minx - PAD), min(W, maxx + PAD + 1)):
            k = y * W + x
            if not fillbox[k]:
                fillbox[k] = 1; n += 1
plate = fillbox
print("KAGE-NARROW dao=%d giu=%d lon_nhat=%d phu_tron_hop_bao=%d" % (len(comps), len(keep), biggest, n))
if n == 0:
    print("KAGE-RESULT none-sau-khi-siet vung_dau=%d" % marked)
    sys.exit(0)

votes = Counter()
for k in range(W * H):
    if not grey_only[k]: continue
    y, x = divmod(k, W)
    for dy in (-1, 0, 1):
        yy = y + dy
        if yy < 0 or yy >= H: continue
        for dx in (-1, 0, 1):
            xx = x + dx
            if xx < 0 or xx >= W: continue
            k2 = yy * W + xx
            if grey_only[k2] or not mask[k2]: continue
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
