# BLENDER-SESSION-GUIDE.md

**Phụ lục thao tác: chạy một phiên Blender MCP để ra sprite**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Liên quan | [ART-PIPELINE.md](ART-PIPELINE.md) (thiết kế) · [tools/blender/README.md](../../tools/blender/README.md) (contract hàm) |

> Đây là phần "làm thế nào". Phần "vì sao" nằm ở ART-PIPELINE.md — không lặp lại ở đây.
> Mọi lệnh dưới đây bám 7 quy tắc token ở [ART-PIPELINE §6.1](ART-PIPELINE.md).

---

## 0. Môi trường đã kiểm chứng

| | |
|---|---|
| Blender | **5.2.0 LTS**, `C:\Program Files\Blender Foundation\Blender 5.2\blender.exe` |
| Render engine | `BLENDER_EEVEE` (xem §4 — tên này **đổi theo version**, script tự dò) |
| Script | `tools/blender/kage_sprite_render.py` — đã chạy thật headless, ra PNG + `render-meta.json` |

Toàn bộ lệnh ở §1 dùng hàm có thật trong script đó. Không có lệnh nào ở đây là giả định.

---

## 0.5. Chuẩn bị trước mỗi phiên — 2 chế độ làm việc

Addon MCP là một **server chạy bên trong tiến trình Blender đang mở**. Không có Blender mở → không có kết nối. Vì vậy chọn 1 trong 2 chế độ:

### Chế độ A — MCP (mặc định, hợp việc model/chỉnh tay)

Mỗi phiên, **trước khi** bảo AI làm gì:

1. Mở Blender (mở luôn file `.blend` đang làm).
2. Panel `N` trong viewport → tab **BlenderMCP** → **Connect to Claude**.
3. Để Blender **mở suốt phiên** — đóng giữa chừng là mất kết nối, AI phải dừng.

Bước bật addon trong Preferences chỉ làm **một lần**; các phiên sau chỉ cần bấm Connect.

✅ Nhanh khi cần chỉnh liên tục · ❌ Trạng thái nằm trong phiên Blender, mất là mất (xem §4)

### Chế độ B — Headless hoàn toàn (không cần mở Blender)

AI viết script `.py`, chạy bằng dòng lệnh, rồi **đọc thẳng file PNG** vừa render để tự đánh giá:

```
blender -b <file>.blend -P <script>.py -- ...
```

✅ Không phải mở/giữ Blender · ✅ Mọi thứ thành script **commit được, chạy lại được** · ✅ Không bao giờ mất kết nối
❌ Mỗi vòng lặp chậm hơn (phải khởi động Blender mỗi lần) · ❌ Không "cầm tay nắn" trực tiếp được

**Khuyến nghị**: dựng hình lần đầu và nắn tỷ lệ → chế độ A. Render sheet, dựng lại base, việc lặp đi lặp lại → chế độ B. Không cần chọn cứng một chế độ cho cả dự án.

---

## 1. Trình tự phiên chuẩn — nhân vật mới

Mỗi ô code = **một** lần `execute_blender_code`. Đừng tách nhỏ hơn (mỗi round-trip là token).

### Bước 0 · Mở đầu phiên (1 lần duy nhất)

`get_scene_info` **một lần** để biết đang ở file nào, có object gì. Sau đó không gọi lại — script tự in trạng thái cần thiết.

### Bước 1 · Mở file base + nạp script

Nhân vật mới **luôn bắt đầu từ** `assets/raw/blender/kage_character_base.blend` (đã có sẵn camera/light/Freestyle/render chuẩn + 5 material `toon_<hệ>` + scale guide chibi). Mở file đó trong Blender **bằng tay** rồi:

```python
exec(open(r'E:/study/libgdx/shinobi-engine/tools/blender/kage_sprite_render.py').read())
setup_scene(px=256)   # 256 = nhân vật; boss 512; VFX 384; icon 128 (ART-PIPELINE §4.1)
```

In ra: `[kage] scene ready: engine=BLENDER_EEVEE px=256 outline=5.0px`.
`outline=5.0px` là đúng: art direction yêu cầu 2.5px ở @1x, master là @2x → 5px.

⚠️ **Tuyệt đối không** `bpy.ops.wm.read_factory_settings()` để "làm sạch scene" — lệnh đó tắt addon MCP và giết kết nối (§4). Cần dựng lại file base thì chạy headless:
`blender -b --factory-startup -P tools/blender/build_character_base.py`

**Scale guide**: 3 khối wireframe (`guide_head`/`guide_torso`/`guide_legs`) — nhân vật cao đúng **1.0 unit**, chân tại `z=0`, đầu 0.4 unit (chibi 2.5 đầu). Model bám theo guide này thì mọi sprite tự động cùng tỷ lệ. Guide đã set `hide_render` nên không bao giờ lọt vào sprite.

### Bước 2 · Dựng/import mesh rồi toon hoá (batch 1 lần)

```python
import bpy
# ... dựng mesh bằng bpy, hoặc import từ Hyper3D/Hunyuan/Sketchfab ...
apply_toon(bpy.data.objects['Body'],  '#E8552D')   # màu chính của hệ, ART-PIPELINE §2.2
apply_toon(bpy.data.objects['Scarf'], '#FFB13D')   # màu phụ
print([o.name for o in bpy.context.scene.objects if o.type == 'MESH'])
```

→ **Screenshot (a)**: `get_viewport_screenshot` để xem khối hình/tỷ lệ.
⚠️ Viewport **không hiện outline Freestyle** (§4) — đừng kết luận "outline hỏng" ở bước này.

### Bước 3 · Action theo bộ chuẩn

Tên action phải **đúng** bộ chuẩn ([ART-PIPELINE §4.2](ART-PIPELINE.md)): `idle` `run` `attack` `skill_cast` `hit` `jump` `die`. Script chỉ nhận đúng các tên này khi chạy `--all-actions`.

```python
import bpy
for action in bpy.data.actions:
    action.use_fake_user = True    # BẮT BUỘC — xem §4, action 0-user bị xoá lúc save
print({a.name: tuple(a.frame_range) for a in bpy.data.actions})
```

### Bước 4 · Preview 1 frame → duyệt phong cách

```python
render_preview(r'E:/study/libgdx/shinobi-engine/assets/raw/render-out/_preview.png', action='idle')
```

→ **Screenshot (b)**: dùng `Read` trên file PNG vừa render (**không** dùng viewport screenshot — chỉ bản render mới có outline + cel shading thật).
**Chốt chặn**: chưa đạt phong cách thì quay lại bước 2. Tuyệt đối không render cả sheet khi preview chưa đạt — đó là chỗ đốt thời gian nhiều nhất.

### Bước 5 · Render cả bộ — headless, chạy nền

Không render dài trong phiên MCP (giữ kết nối bận → dễ mất kết nối, §4). Lưu file rồi thoát sang Bash:

```python
import bpy
bpy.ops.wm.save_mainfile()
```

```bash
"/c/Program Files/Blender Foundation/Blender 5.2/blender.exe" -b \
  assets/raw/blender/characters/ninja-fire.blend \
  -P tools/blender/kage_sprite_render.py \
  -- --all-actions --out assets/raw/render-out/char_ninja-fire
```

Chạy bằng Bash tool với `run_in_background: true`. Kết quả mong đợi:

```
[kage] scene ready: engine=BLENDER_EEVEE px=256 outline=5.0px
[kage] fit: ortho_scale=1.463 pivot=(128,20 from bottom)
[kage] idle: 4 frames (loop)
[kage] attack: 5 frames
[kage] done: 2 actions -> .../char_ninja-fire
```

`render-meta.json` sinh kèm — đây là file `tools/asset-pipeline` đọc để upsert `art_asset` ([ART-PIPELINE §8.3](ART-PIPELINE.md)).

### Bước 6 · Hạ mẫu + pack atlas

```bash
# tools/asset-pipeline — chưa tồn tại, xem ART-PIPELINE §12 việc cần làm
```

### Bước 7 · Ghi sổ + đóng phiên

Thêm 1 dòng vào `assets/raw/ASSET-LOG.md`:

```
| char_ninja-fire | characters/ninja-fire.blend | idle,run,attack,skill_cast,hit,jump,die | 2026-08-01 | ORIGINAL |
```

→ **Screenshot (c)**: mở 1 frame đại diện để nghiệm thu theo §2.

---

## 2. Checklist nghiệm thu

Chạy checklist **trên file PNG đã render**, không phải trên viewport. Bắt buộc kiểm ở cả @1x và @0.5x (thu nhỏ ảnh xuống 50%/25%) — lỗi readability chỉ lộ ra khi nhỏ.

### 2.1 Nhân vật / NPC (master 256)

- [ ] Nền **trong suốt** hoàn toàn, không viền trắng/xám quanh mép.
- [ ] Outline đen liền mạch, không đứt ở chỗ chi mảnh (tay, vũ khí mỏng).
- [ ] Cel 2–3 nấc rõ ràng — không có gradient mượt (gradient = shader sai, xem §4).
- [ ] Nhân vật **quay mặt sang phải** (+X). Sai hướng = cả bộ sheet vô dụng.
- [ ] Chân chạm đúng baseline: kiểm `pivot_y_px_from_bottom` trong `render-meta.json` **giống nhau** giữa các asset cùng loại.
- [ ] Không frame nào bị **cắt** (tay vung, tóc, vũ khí) — `fit_camera` quét mọi frame nên lỗi này chỉ xảy ra khi thêm action sau lúc fit.
- [ ] Tỷ lệ chibi 2–2.5 đầu; đặt cạnh asset đã duyệt trong ASSET-LOG để so.
- [ ] Ở @0.5x (64px): vẫn nhận ra silhouette là nhân vật gì, hệ nào.

### 2.2 Boss / World Boss (master 512)

Toàn bộ mục 2.1, cộng thêm:

- [ ] Silhouette **khác hẳn** quái thường — người chơi phải nhận ra "đây là boss" trong 1 frame.
- [ ] Tông tối/tương phản mạnh hơn thế giới thường (art direction, The Eminence in Shadow).
- [ ] Khung 512 nhưng vẫn cùng baseline: boss đứng cùng mặt đất với người chơi, không "lơ lửng".
- [ ] Ngân sách atlas: 1 boss @1x 256px × ~33 frame ≈ **trọn 1 atlas 2048**. Boss phải có atlas **riêng**, không nhét chung zone.

### 2.3 VFX skill (master 384)

- [ ] Tên khớp **skill code thật** trong `SkillRegistry.java` → `vfx_<skill_code>` (xem [ART-PIPELINE §2.3](ART-PIPELINE.md)).
- [ ] Đọc được trên **cả nền sáng lẫn nền tối** — VFX chìm vào nền là lỗi phổ biến nhất.
- [ ] Đúng palette hệ; nhìn 1 frame là biết hệ nào.
- [ ] Frame đầu/cuối **không** đột ngột (VFX bật/tắt gắt gây cảm giác giật).
- [ ] Không outline Freestyle cho VFX mềm (lửa, khói) — tắt qua `bpy.context.scene.render.use_freestyle = False` trước khi render VFX; giữ outline cho VFX dạng khối (đá, băng).
- [ ] Đã cân nhắc: hiệu ứng này nên là **sprite render sẵn** hay **particle runtime**? Particle rẻ hơn nhiều cho thứ lặp lại (tàn lửa, bụi) — xem cap particle theo tier ([ART-PIPELINE §5](ART-PIPELINE.md)).

### 2.4 Prop / tile môi trường

- [ ] Kích thước **bội số 32** ở @1x (khớp lưới map).
- [ ] Tile lặp được: mép trái/phải (và trên/dưới nếu cần) khớp liền, không thấy đường nối.
- [ ] Ánh sáng **cùng hướng** với nhân vật (key từ trên-trước-phải) — prop sáng ngược hướng làm cảnh vỡ ngay.
- [ ] Prop nền không "hút mắt" hơn nhân vật: giảm tương phản/độ bão hoà so với nhân vật.
- [ ] Không outline dày như nhân vật — prop nền nên mảnh hơn hoặc bỏ outline, để nhân vật nổi lên.

---

## 3. Bảng tra nhanh: tham số theo loại asset

| Loại | `setup_scene(px=)` | Outline | Freestyle | Bộ action |
|---|---|---|---|---|
| Nhân vật/NPC/quái | 256 | 2.5px @1x (mặc định) | Bật | Bộ chuẩn §4.2 |
| Boss | 512 | 2.5px @1x | Bật | Bộ chuẩn + action riêng của boss |
| VFX khối | 384 | 1.5px @1x | Bật | 1 action `skill_cast` |
| VFX mềm (lửa/khói) | 384 | — | **Tắt** | 1 action `skill_cast` |
| Icon | 128 | 2px @1x | Bật | Không (1 frame) |
| Prop/tile | bội số 32 ×2 | 1–1.5px @1x | Tuỳ | Không |

---

## 4. Lỗi thường gặp

Các mục ✅ dưới đây là lỗi **đã gặp thật** khi chạy Blender 5.2 trong dự án này, không phải suy đoán.

| Triệu chứng | Nguyên nhân | Xử lý |
|---|---|---|
| ✅ `AttributeError: 'NoneType' object has no attribute 'color'` khi bật Freestyle | Lineset tạo bằng Python **không** tự có `linestyle` (UI mới tự tạo) | Script đã tự tạo `bpy.data.linestyles.new(...)`. Nếu tự viết code Freestyle riêng thì phải làm y hệt |
| ✅ Action biến mất sau khi save, `--all-actions` render thiếu | Action **0 user** bị Blender xoá lúc save | `action.use_fake_user = True` cho mọi action (bước 3) |
| ✅ `engine` gán `BLENDER_EEVEE_NEXT` báo lỗi enum | Tên engine **đổi theo version**: ≤4.1 và ≥5.0 là `BLENDER_EEVEE`; 4.2–4.5 là `BLENDER_EEVEE_NEXT` | Đừng hardcode. `setup_scene()` tự dò từ enum thật của bản Blender đang chạy |
| ✅ Không thấy outline trong viewport screenshot | Freestyle là **render pass**, viewport không vẽ | Duyệt outline bằng `render_preview()` rồi `Read` file PNG, không bằng `get_viewport_screenshot` |
| Màu ra nhạt/bệch so với mã hex | Blender dùng **linear**, hex là **sRGB** | Luôn qua `hex_to_linear()` của script; không gán thẳng hex vào `default_value` |
| Bóng đổ mượt, không ra cel | Ramp đang `LINEAR` thay vì `CONSTANT`, hoặc thiếu Shader-to-RGB | Dùng `make_toon_material()`; Shader-to-RGB **chỉ chạy trên EEVEE**, không có trên Cycles |
| Sprite bị cắt mất tay/vũ khí ở vài frame | Thêm action **sau** khi đã `fit_camera` | Chạy lại `render_all_actions()` (tự fit lại toàn bộ action) |
| Nhân vật "lơ lửng"/thụt chân giữa các asset | Gốc toạ độ không đặt ở chân | Đặt origin ở chân (world z=0) trước khi render; kiểm `pivot_y_px_from_bottom` trong meta |
| ✅ **Mất kết nối MCP ngay sau khi chạy `bpy.ops.wm.read_factory_settings()`** | Lệnh này **reset preferences → tắt luôn addon blender-mcp**, tức tự cắt đường kết nối đang dùng | **Không bao giờ** gọi `read_factory_settings` trong phiên MCP. Cần file sạch thì chạy **headless** (`tools/blender/build_character_base.py`), hoặc xoá object thủ công bằng `bpy.data.objects.remove()`. **Khôi phục**: xem §4.1 |
| ✅ `AttributeError: 'Context' object has no attribute 'active_object'` | Context của MCP **không có active object** — mọi `bpy.ops.mesh.primitive_*_add()` rồi đọc `active_object` đều hỏng | Dựng mesh trực tiếp bằng `bpy.data.meshes.new()` + `from_pydata()` (xem `_make_box()` trong `build_character_base.py`), không dùng operator phụ thuộc context |
| Mất kết nối MCP giữa chừng | Blender bận render dài → không trả lời addon | Không render dài trong MCP: dùng headless (bước 5). Mất rồi thì kiểm Blender còn chạy, gọi lại `get_scene_info` |
| Blender treo, không nhận lệnh nào | Có dialog/modal operator đang mở | Đóng thủ công trong Blender; tránh operator mở hộp thoại (`bpy.ops.wm.*` dạng invoke) |
| ✅ Nhân vật đứng thẳng ở action A lại "gãy gập" theo pose cuối của action B | `fit_camera()` quét **mọi** action để đo bounds; bone **không có keyframe** ở action đang active giữ nguyên giá trị được ghi lúc quét action khác (Blender không tự reset về 0 khi đổi action) | Khi author action cho rig nhiều bone dùng chung, keyframe **mọi** bone ở **mọi** frame của **mọi** action (kể cả set về 0 tường minh) — đừng chỉ keyframe bone đang "diễn". Xem `full_pose_kf()` trong ASSET-LOG entry `char_hoa-pilot` |
| ✅ Outline 2.5px@1x (mặc định) phủ gần kín cả nhân vật thành mảng đen lớn | Nhân vật ghép từ **nhiều box rời** (không hàn liền) — mỗi box có đủ 12 cạnh riêng, `select_crease` bắt outline ở **mọi** cạnh 90° kể cả cạnh nối giữa 2 box chạm nhau, cạnh dày đặc so với kích thước 256px | Với rig kiểu "nhiều khối rời cứng" (không phải mesh liền mạch điêu khắc), hạ outline xuống ~0.8px@1x qua `setup_scene(outline_px_at_1x=0.8)` (MCP) hoặc `--outline-px 0.8` (CLI, đã thêm vào script). **Không** tắt `select_crease` để "sửa" — thử tắt cho thấy `select_silhouette`/`select_border` một mình **không** bắt được biên ngoài thật của khối hộp căn trục thẳng dưới camera ortho (nghi ngờ là trường hợp suy biến dot-product=0 khi mặt bên vuông góc hoàn toàn với hướng nhìn), outline biến mất hoàn toàn thay vì chỉ mỏng đi |
| Output MCP quá dài, tốn token | `print()` cả list object lớn | In số lượng/tên ngắn; script đã in gọn 1 dòng mỗi bước |

### 4.1 Bật lại addon MCP sau khi mất kết nối

1. **Khởi động lại Blender.** `read_factory_settings` chỉ reset preferences **trong bộ nhớ**, chưa ghi xuống đĩa — mở lại là preferences cũ (đã bật addon) quay về. Đây là cách nhanh nhất, thử trước.
2. Mở panel `N` trong viewport 3D → tab **BlenderMCP** → bấm **Connect to Claude**.
3. Nếu không thấy tab: `Edit → Preferences → Add-ons`, tìm "MCP", tick bật lại.
4. Kiểm chứng từ phía Claude bằng `get_scene_info` — trả về JSON scene là xong.

⚠️ Nếu lỡ bấm **Save Preferences** sau khi reset thì preferences mặc định đã bị ghi đè xuống đĩa — lúc đó bắt buộc làm bước 3.

**Việc nào cần addon, việc nào không:**

| Việc | Cần addon MCP? |
|---|---|
| Dựng file base, render cả bộ sheet (`--all-actions`) | ❌ Chạy headless bằng dòng lệnh |
| Model/sửa mesh, chỉnh tỷ lệ, preview lặp, viewport screenshot | ✅ Bắt buộc |

---

## 5. Technical Decisions

| # | Quyết định | Lý do |
|---|---|---|
| 1 | Duyệt phong cách bằng **file render**, không bằng viewport | Viewport không có Freestyle + cel thật → duyệt sai |
| 2 | Render dài chạy **headless nền**, MCP chỉ dựng và preview | Giữ phiên MCP ngắn, tránh mất kết nối |
| 3 | Nghiệm thu bắt buộc kiểm ở @0.5x | Lỗi readability chỉ lộ khi thu nhỏ (tier Low) |
| 4 | VFX mềm **tắt Freestyle** | Outline trên lửa/khói làm hỏng cảm giác mềm |

## 6. ADR cần tạo

Không. Tài liệu này là thao tác, mọi quyết định kiến trúc nằm ở [ART-PIPELINE §10](ART-PIPELINE.md).

## 7. Tài liệu liên quan

[ART-PIPELINE.md](ART-PIPELINE.md) · [tools/blender/README.md](../../tools/blender/README.md) · [RESOURCE.md](../04-client/RESOURCE.md)

## 8. Việc cần làm tiếp theo

1. ✅ **Xong 2026-08-01**: cả **5 hệ** (`char_hoa-pilot`, `char_thuy-pilot`, `char_phong-pilot`,
   `char_loi-pilot`, `char_tho-pilot`) chạy hết §1, checklist §2 dùng thật (xem `assets/raw/ASSET-LOG.md`).
   Từ nhân vật thứ 2 trở đi dùng `tools/blender/build_box_character.py` (tách ra từ recipe Hỏa) chạy
   **hoàn toàn headless**, không cần mở Blender/MCP nữa — chỉ cần chọn hệ + 2 màu hex. 3 lỗi pipeline
   thật đã ghi vào §4. Đây vẫn là khối hộp thô giống hệt nhau giữa 5 hệ (chỉ đổi màu) — **chưa phải art
   final**, mục đích là kiểm chứng pipeline đủ 5 hệ hoạt động nhất quán (đã xác nhận: cả 5 asset cùng
   `pivot_y_px_from_bottom=20.48`).
2. ✅ **Xong 2026-08-01 (cùng ngày, sau khi so 5 hệ cạnh nhau)**: mỗi hệ giờ có 1 prop vũ khí gắn hông
   riêng theo đúng dòng "Vũ khí" ở ART-PIPELINE §2.2 (kunai/hồ lô/song đao/trường đao/chuỳ đá), Thổ tăng
   `bulk=1.22` để trông nặng hơn — xem `ELEMENT_SPECS` trong `build_box_character.py`. Đây vẫn là mức
   "khối hộp có phụ kiện", chưa phải costume/vũ khí điêu khắc thật theo B1/B2/B3 — nếu cần silhouette
   tinh hơn (tóc, gai, dáng đứng khác nhau thật sự) thì phải làm model riêng cho nhân vật thật.
3. Cân nhắc: rig kiểu "nhiều box rời + outline mỏng" (đã dùng cho cả 5 pilot) so với "mesh liền mạch
   điêu khắc + outline chuẩn 2.5px" cho nhân vật thật — pilot chọn cách đầu vì nhanh, nhưng chưa rõ cách
   nào cho chất lượng cuối tốt hơn.
4. ✅ **Xong 2026-08-01 (cùng ngày, lần 3)**: B2 + B3 cho cả 5 hệ (`char_<hệ>-b2`, `char_<hệ>-b3`), đúng
   3 bậc B1/B2/B3 ở ART-PIPELINE §2.2. B2 = B1 + giáp mảnh/đai (`GEAR_SPECS_B2`, gắn bone tuỳ vị trí —
   riêng tấm che đùi Thổ gắn `leg_l`/`leg_r` chứ không phải `pelvis` vì cần di chuyển theo chân). B3 = B2
   + 1 khối phụ kiện bay quanh đầu bằng **driver** dựa `frame_current` (`_add_orbit_prop`) — chọn driver
   thay vì keyframe theo action vì phải động **bất kể action nào đang chạy**, và keyframe riêng cho từng
   action sẽ tốn công + dễ lặp lại bug "để sót bone" đã gặp ở `die` (mục 2 log này).
6. ✅ **Xong 2026-08-01 (cùng ngày, lần 4 — chủ dự án yêu cầu tách B3 khác nhau thật)**: mục 4 ban đầu
   dùng 1 kiểu orbit chung cho cả 5 hệ, chỉ khác màu — chưa đạt. Sửa: `_add_b3_detail()` dispatch theo
   từng hệ sang 1 trong 3 cơ chế riêng biệt (`_add_orbit_prop` nhiều instance lệch pha, `_add_sway_prop`
   xoay quanh trục Y cho hiệu ứng đung đưa, `_add_flicker_prop` bật/tắt scale theo `floor(frame)%period`
   cho hiệu ứng chớp — không phải chuyển động liên tục). Khớp đúng mô tả B3 từng hệ ở §2.2: Hỏa 3 đốm
   lửa, Thủy 1 vòng quanh cổ tay, Phong 2 dải lụa đung đưa, Lôi tia điện chớp 2-frame, Thổ 2 mảnh đá
   orbit chậm/to. Vẫn box-prototype (không phải VFX shader/particle), nhưng giờ 5 cơ chế thật khác nhau.
7. Viết `tools/asset-pipeline` để bước 6 hết rỗng — giờ có 15 bộ sheet thật (5 hệ × 3 bậc) để test.
