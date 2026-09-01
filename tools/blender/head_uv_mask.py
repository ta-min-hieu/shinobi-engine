"""Khoanh vung DAU trong khong gian UV cua texture, dua tren trong so xuong (skinning).

CONG CU CHO ADR-038 Dot 3 — xem docs/14-art/ART-AUDIT-TU-TIEN.md §4.4.
Doc thang file .glb (khong can Blender) de KIEM CHUNG phep khoanh vung. Viec TO LAI that su
phai chay tren anh dong goi trong .blend, vi assets/exported/ la build artifact (§4.5).

Vi sao can: bang tran co TAM KIM LOAI phai bi xoa (ADR-038 Dot 3), nhung mot luat mau
toan cuc thi KHONG tong quat duoc — do that 2026-09-01: nguong xam bat trung ao xanh
nhat cua Thuy (55k px) trong khi Tho/Loi gan nhu khong bat gi (186/159 px). Moi nhan vat
do Rodin sinh rieng nen bang mau rieng.

Cach chac chan hon: chi xet pixel thuoc HINH HOC CUA DAU. Trong vung do, thu duy nhat co
mau xam trung tinh sang la tam kim loai — toc den, da hong, khan do deu khong phai.

Ham chinh: head_uv_mask(js, binc) -> (w, h, mask) voi mask[y][x] = 1 neu pixel do bi
tam giac gan xuong 'head' phu len.
"""

import struct

COMPONENT = {5120: ("b", 1), 5121: ("B", 1), 5122: ("h", 2), 5123: ("H", 2), 5125: ("I", 4), 5126: ("f", 4)}
NCOMP = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}


def read_accessor(js, binc, index):
    acc = js["accessors"][index]
    fmt, size = COMPONENT[acc["componentType"]]
    n = NCOMP[acc["type"]]
    bv = js["bufferViews"][acc["bufferView"]]
    base = bv.get("byteOffset", 0) + acc.get("byteOffset", 0)
    stride = bv.get("byteStride") or (size * n)
    out = []
    for i in range(acc["count"]):
        off = base + i * stride
        out.append(struct.unpack_from("<" + fmt * n, binc, off))
    return out


def head_uv_mask(js, binc, tex_w, tex_h, joint_name="head", weight_min=0.5):
    """Rasterise moi tam giac co CA BA dinh gan chu yeu vao xuong `joint_name`."""
    skin = js["skins"][0]
    joint_names = [js["nodes"][j].get("name") for j in skin["joints"]]
    if joint_name not in joint_names:
        return None
    target = joint_names.index(joint_name)

    # Chi lay primitive cua mesh CO texture (than) — phu kien dung material phang.
    tex_mats = {i for i, m in enumerate(js["materials"])
                if m.get("pbrMetallicRoughness", {}).get("baseColorTexture")}
    prim = None
    for mesh in js["meshes"]:
        for p in mesh.get("primitives", []):
            if p.get("material") in tex_mats:
                prim = p
                break
        if prim:
            break
    if prim is None:
        return None

    uvs = read_accessor(js, binc, prim["attributes"]["TEXCOORD_0"])
    joints = read_accessor(js, binc, prim["attributes"]["JOINTS_0"])
    weights = read_accessor(js, binc, prim["attributes"]["WEIGHTS_0"])
    idx = [i[0] for i in read_accessor(js, binc, prim["indices"])]

    def on_head(v):
        w = weights[v]
        mx = max(w) or 1
        total = 0.0
        for k in range(4):
            if joints[v][k] == target:
                total += w[k] / (mx if mx > 1.5 else 1)  # WEIGHTS co the la byte chuan hoa
        norm = sum(x / (mx if mx > 1.5 else 1) for x in w) or 1
        return (total / norm) >= weight_min

    head_vertex = [on_head(v) for v in range(len(uvs))]

    mask = [bytearray(tex_w) for _ in range(tex_h)]
    painted = 0
    for t in range(0, len(idx) - 2, 3):
        a, b, c = idx[t], idx[t + 1], idx[t + 2]
        if not (head_vertex[a] and head_vertex[b] and head_vertex[c]):
            continue
        pts = []
        for v in (a, b, c):
            u, vv = uvs[v]
            pts.append((u * tex_w, vv * tex_h))
        minx = max(0, int(min(p[0] for p in pts)) - 1)
        maxx = min(tex_w - 1, int(max(p[0] for p in pts)) + 1)
        miny = max(0, int(min(p[1] for p in pts)) - 1)
        maxy = min(tex_h - 1, int(max(p[1] for p in pts)) + 1)
        (x0, y0), (x1, y1), (x2, y2) = pts
        den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
        if abs(den) < 1e-9:
            continue
        for py in range(miny, maxy + 1):
            for px in range(minx, maxx + 1):
                cx, cy = px + 0.5, py + 0.5
                l1 = ((y1 - y2) * (cx - x2) + (x2 - x1) * (cy - y2)) / den
                l2 = ((y2 - y0) * (cx - x2) + (x0 - x2) * (cy - y2)) / den
                l3 = 1 - l1 - l2
                if l1 >= -0.02 and l2 >= -0.02 and l3 >= -0.02:
                    if not mask[py][px]:
                        painted += 1
                    mask[py][px] = 1
    return mask, painted
