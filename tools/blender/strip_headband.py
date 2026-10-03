"""Xoa TAM KIM LOAI tren bang tran khoi anh dong goi trong .blend (ADR-038 Dot 3 muc #2).

    blender -b --python-exit-code 1 <char>.blend -P strip_headband.py -- --seed u,v [--save-as out.blend] [--dry]

DA DUNG THAT 2026-09-01 tren char_hoa-b1 (hoa-text3d-pilot.blend), seed 0.8281,0.1929:
tam kim loai + dinh tan + hoa tiet khac bien mat, thay bang vai do tron lay tu chinh chiec
bang; kiem bang mat trong game, khoi loi con lai tren mesh KHONG lo. 0 luot Rodin.

⚠️ VA DO LA NHAN VAT DUY NHAT CAN NO. Soi bang ung vien cua ca 15 nhan vat: chi char_hoa-b1
co bang tran ninja (no la con pilot Text->3D dau tien); 14 con sinh sau khong co. Xem
docs/14-art/ART-AUDIT-TU-TIEN.md §4.10. Giu script nay lai phong khi co asset moi dinh lai
loi cu, dung de chay hang loat.

KHONG dung --auto: dung tren char_hoa-b1 nhung SAI tren char_hoa-b2 (bat trung mang da).
Bay lan thu mot luat mau chung deu bi phep do bac bo — moi nhan vat Rodin sinh doc lap.
Cach dung dung: xuat bang ung vien, nhin MOT lan, roi --seed u,v.
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

# In luon tham so ma export_character_lods.py can, de driver khong phai doan ten object/rig.
rig = next((o.name for o in bpy.data.objects if o.type == 'ARMATURE'), '')
extras = [o.name for o in bpy.data.objects
          if o.type == 'MESH' and o is not obj and o.data.materials and not o.name.startswith('guide_')]
print('KAGE-PARAMS object=%s rig=%s extras=%s' % (obj.name, rig, ','.join(extras)))

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

def metallic(k):
    i = k * 4
    r, g, b = buf[i] * 255, buf[i+1] * 255, buf[i+2] * 255
    return (max(r, g, b) - min(r, g, b)) <= 26 and max(r, g, b) >= 90
# Loang tu DIEM CHI DAN: tu hat giong, lan sang moi pixel lien ke con "giong kim loai"
# (bao hoa thap), BAT KE do sang — nho vay ca phan bat sang lan phan trong bong deu vao, ma
# khong lan sang long trang mat hay highlight o dao UV khac vi chung khong lien thong.
def flood_from(k0, collect=False):
    """Loang theo 'giong kim loai' tu mot pixel. Tra ve (kich thuoc, hop bao, vung)."""
    reg = bytearray(W * H)
    st = [k0]; reg[k0] = 1
    y0, x0 = divmod(k0, W)
    mnx = mxx = x0; mny = mxy = y0; sz = 0
    while st:
        k = st.pop(); sz += 1
        y, x = divmod(k, W)
        if x < mnx: mnx = x
        if x > mxx: mxx = x
        if y < mny: mny = y
        if y > mxy: mxy = y
        for dy in (-1, 0, 1):
            yy = y + dy
            if yy < 0 or yy >= H: continue
            for dx in (-1, 0, 1):
                xx = x + dx
                if xx < 0 or xx >= W: continue
                k2 = yy * W + xx
                if not reg[k2] and metallic(k2):
                    reg[k2] = 1; st.append(k2)
    return sz, (mnx, mny, mxx, mxy), (reg if collect else None)

if SEED is None:
    # --auto: tam kim loai la vung "giong kim loai" LOANG RA LON NHAT trong vung dau.
    # Kiem chung tren char_hoa-b1: tam loang ra 10.020 px, con long trang mat — dao lon nhat
    # neu chi dem trong mask — chi ra vai nghin. Dem theo phep loang phan biet duoc hai thu do.
    seeds = []
    seen_seed = bytearray(W * H)
    for k in range(W * H):
        if not mask[k] or seen_seed[k] or not metallic(k):
            continue
        sz, box, reg = flood_from(k, collect=True)
        for j in range(W * H):
            if reg[j]: seen_seed[j] = 1
        seeds.append((sz, k, box))
    if not seeds:
        print("KAGE-RESULT khong-thay-vung-kim-loai-nao")
        sys.exit(0)
    seeds.sort(reverse=True)
    for sz, k, box in seeds[:4]:
        print("KAGE-AUTO ung_vien size=%-7d tam=(%d,%d) hop=%s" % (sz, k % W, k // W, box))
    _, best_k, _ = seeds[0]
    sy, sx = divmod(best_k, W)
    print("KAGE-AUTO chon u,v=%.4f,%.4f" % (sx / W, sy / H))
else:
    sx = min(W - 1, max(0, int(SEED[0] * W)))
    sy = min(H - 1, max(0, int(SEED[1] * H)))

# Hat giong bam sang pixel hop le GAN NHAT thay vi doi hoi trung phoc.
# Ly do co that: tam kim loai co HOA TIET KHAC o chinh giua, nen trong tam cua vung sang
# roi vao cho toi ay — va mask (dung bang cach dong dau quanh tung dinh) con co lo tha o
# nhung dao UV thua dinh. Doi hoi trung phoc lam ca bon lan chi diem dung deu bi tu choi.
seed_k = None
for radius in range(0, 40):
    for dy in range(-radius, radius + 1):
        yy = sy + dy
        if yy < 0 or yy >= H: continue
        for dx in range(-radius, radius + 1):
            if radius and max(abs(dx), abs(dy)) != radius: continue
            xx = sx + dx
            if xx < 0 or xx >= W: continue
            k = yy * W + xx
            if mask[k] and metallic(k):
                seed_k = k; break
        if seed_k is not None: break
    if seed_k is not None: break
if seed_k is None:
    print("KAGE-RESULT khong-thay-pixel-kim-loai-quanh-seed u=%.4f v=%.4f" % SEED)
    sys.exit(0)
sy, sx = divmod(seed_k, W)

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
            # KHONG doi hoi mask[k2]: mask dung bang cach dong dau quanh tung dinh nen no RO,
            # va lo rong chan dut phep loang giua tam kim loai (do that: chi phu duoc 1/8 tam).
            # Hat giong da neo dung cho roi; "giong kim loai" du de khong nhay sang dao khac.
            if not region[k2] and metallic(k2):
                region[k2] = 1; stack.append(k2)

grey_only = bytes(region)
PAD = 2
plate = bytearray(W * H)
n = 0
for y in range(max(0, miny - PAD), min(H, maxy + PAD + 1)):
    for x in range(max(0, minx - PAD), min(W, maxx + PAD + 1)):
        plate[y * W + x] = 1; n += 1
print("KAGE-SEED loang=%d hop_bao=(%d,%d)-(%d,%d) phu=%d" % (size, minx, miny, maxx, maxy, n))



# Lay mau vai tu MOT VANH ben ngoai hop bao, khong phai tu sat mep tam kim loai:
# ngay sat mep la duong vien toi cua chinh tam, con vai bang thi nam xa hon vai pixel.
# Da dinh: lay sat mep cho ra mau gan TRANG, lay sau khi noi hop bao cho ra mau DEN (toc).
votes = Counter()
INNER, OUTER = 3, 12
for y in range(max(0, miny - OUTER), min(H, maxy + OUTER + 1)):
    for x in range(max(0, minx - OUTER), min(W, maxx + OUTER + 1)):
        inside_inner = (minx - INNER <= x <= maxx + INNER) and (miny - INNER <= y <= maxy + INNER)
        if inside_inner:
            continue
        k = y * W + x
        if grey_only[k] or not mask[k]:
            continue
        i = k * 4
        votes[(round(buf[i], 3), round(buf[i+1], 3), round(buf[i+2], 3))] += 1

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
