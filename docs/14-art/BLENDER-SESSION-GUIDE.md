# BLENDER-SESSION-GUIDE.md

**Phụ lục thao tác: chạy một phiên Blender để ra model nhân vật (3D) hoặc sprite (VFX/icon)**

| | |
|---|---|
| Version | 1.1.0 |
| Status | Draft |
| Liên quan | [ART-PIPELINE.md](ART-PIPELINE.md) (thiết kế) · [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md) (2.5D) · [tools/blender/README.md](../../tools/blender/README.md) (contract hàm) |

> Đây là phần "làm thế nào". Phần "vì sao" nằm ở ART-PIPELINE.md — không lặp lại ở đây.
> Mọi lệnh dưới đây bám 7 quy tắc token ở [ART-PIPELINE §6.1](ART-PIPELINE.md).

> ⚠️ **Từ [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md) (Accepted 2026-08-03):**
> nhân vật · quái · NPC **không còn ra sprite**, chúng ra **model glTF**. §1 dưới đây vẫn dùng được
> nhưng bước render giờ chỉ để **nghiệm thu**, không phải để lấy sản phẩm. Quy trình sản xuất nhân
> vật thật nằm ở **§1.5**. VFX · icon · prop vẫn theo §1 nguyên vẹn.

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

> ⚠️ **Nhân vật/quái/NPC không còn đi theo §1 nữa.** Từ 2026-08-03 chúng đi theo **§1.5**
> (Text→3D qua `kage_text3d_kit.py` — [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md));
> `kage_mesh_kit.py` + `build_kage_character.py` chỉ còn dùng cho vũ khí/phụ kiện và dựng lại
> file thế hệ cũ ([ART-QUALITY-STANDARD §5](ART-QUALITY-STANDARD.md)).
> §1 dưới đây vẫn đúng cho những thứ phải model tay **và vẫn ra sprite** (prop, tile, VFX khối, icon).

## 1. Trình tự phiên chuẩn — asset model tay (ra sprite)

Mỗi ô code = **một** lần `execute_blender_code`. Đừng tách nhỏ hơn (mỗi round-trip là token).

### Bước 0 · Mở đầu phiên (1 lần duy nhất)

`get_scene_info` **một lần** để biết đang ở file nào, có object gì. Sau đó không gọi lại — script tự in trạng thái cần thiết.

### Bước 1 · Mở file base + nạp script

Nhân vật mới **luôn bắt đầu từ** `assets/raw/blender/kage_character_base.blend` (đã có sẵn camera/light/Freestyle/render chuẩn + 5 material `toon_<hệ>` + scale guide chibi). Mở file đó trong Blender **bằng tay** rồi:

```python
exec(open(r'E:/study/libgdx/shinobi-engine/tools/blender/kage_sprite_render.py').read())
setup_scene(px=256)   # 256 = nhân vật; boss 512; VFX 384; icon 128 (ART-PIPELINE §4.1)
```

In ra: `[kage] scene ready: engine=BLENDER_EEVEE px=256 outline=5.0px` (số in ra là px trên master @2x, tức gấp đôi giá trị @1x bạn truyền vào).

⚠️ **5.0px chỉ đúng nếu asset choán ít khung.** Với nhân vật chibi cao ~166px trong khung 256px, 2.5px@1x phủ đen gần kín hình — số đo đúng là **1.1px@1x** ([ART-QUALITY-STANDARD §4](ART-QUALITY-STANDARD.md)). Luôn chọn độ dày theo kích thước thật của asset trong khung, đừng mặc định.

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
./gradlew :tools:asset-pipeline:run   # downscale @1x/@0.5x + validate + pack 3 atlas + manifest + upsert art_asset
```

### Bước 7 · Ghi sổ + đóng phiên

Thêm 1 dòng vào `assets/raw/ASSET-LOG.md`:

```
| char_ninja-fire | characters/ninja-fire.blend | idle,run,attack,skill_cast,hit,jump,die | 2026-08-01 | ORIGINAL |
```

→ **Screenshot (c)**: mở 1 frame đại diện để nghiệm thu theo §2.

---

## 1.5. Trình tự sản xuất **nhân vật** (nhánh 3D — [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md))

Toàn bộ hàm nằm ở `tools/blender/kage_text3d_kit.py`. Bước A–B cần phiên **có MCP** (import mesh qua tool `import_generated_asset`); từ bước C trở đi chạy headless hoàn toàn.

### Bước A · Bật Hyper3D bằng code (không cần bấm checkbox trong Blender)

```python
sc = bpy.context.scene
sc.blendermcp_use_hyper3d = True
sc.blendermcp_hyper3d_mode = 'MAIN_SITE'
bpy.ops.blendermcp.set_hyper3d_free_trial_api_key()   # key free-trial, ~0.5 credit/lần
```

Hunyuan3D **không dùng được**: mode mặc định là `LOCAL_API` trỏ `localhost:8081`, tức là cần tự dựng server.

### Bước B · Sinh mesh — gọi thẳng REST API, **không** dùng tool MCP

Tool `generate_hyper3d_model_via_text` không expose các tham số quyết định chất lượng và mặc định ở
`tier=Sketch` (rẻ nhất, texture 512). Gọi thẳng:

```python
requests.post("https://hyperhuman.deemos.com/api/v2/rodin",
    headers={"Authorization": f"Bearer {sc.blendermcp_hyper3d_api_key}"},
    files=[("prompt", (None, PROMPT)), ("tier", (None, "Detail")),
           ("quality", (None, "high")), ("use_hyper", (None, "true")),
           ("TAPose", (None, "true")), ("material", (None, "PBR")),
           ("geometry_file_format", (None, "glb"))])
```

`tier=Detail` cho texture **2048**. Lấy `uuid` trong response, poll bằng
`poll_rodin_job_status`, rồi import bằng `import_generated_asset(name, task_uuid=...)` — tool MCP
nhận `task_uuid` của job gọi tay bình thường.

Bốn cái bẫy ở bước này:

- **`bbox_condition` làm hỏng tool MCP** → `Expecting value: line 1 column 1`. Bỏ tham số đó đi.
- **API trả HTTP 201 kèm `{"error": ...}`** — status code không phản ánh lỗi, phải đọc body.
- **`IMAGE_CONTENT_VIOLATION`** nếu prompt có "bare legs" + "boy". Dùng "chibi ninja warrior
  character" + "dark knee-length trousers".
- `mesh_mode=Quad` **không có tác dụng** — GLB trả về vẫn 100% tam giác.

Prompt phải nêu rõ **khuôn mặt** ("clearly visible anime face: large glossy black eyes with white
highlights, thin sharp eyebrows, small smiling mouth, spiky black hair") — thiếu là ra nhân vật đầu
trọc không mặt — và phải **bám đúng spec trang phục** ở [ART-PIPELINE §2.3](ART-PIPELINE.md).
**Bỏ** cụm "side profile facing right": vô nghĩa với mesh 3D và làm mesh dẹt theo chiều sâu.

### Bước C · Chuẩn hoá → rig → action → toon (batch 1 lần)

```python
import kage_text3d_kit as t3d
obj = t3d.clean(t3d.normalise('Rodin_Import', 'Hoa'))
t3d.measure(obj)          # đọc số đo khớp từ bảng in ra, KHÔNG dùng hằng số của nhân vật khác
rig = t3d.rig(obj, 'hoa', arm_y=..., leg_y=..., hip=..., chest=...,
              shoulder=..., elbow=..., wrist=..., knee=...)
t3d.author_scaled_actions(rig)
t3d.toon_textured(obj, shades=3)
t3d.add_far_shade(obj)
t3d.add_inverted_hull_outline(obj, width_px_at_1x=3.0)   # gọi SAU fit_camera
```

### Bước D · Nghiệm thu rồi save

Render preview bằng `kage_sprite_render.py` như §1 bước 4, chấm bảng Q ở
[ART-QUALITY-STANDARD §2](ART-QUALITY-STANDARD.md). **Ảnh render ở đây là bằng chứng nghiệm thu,
không phải sản phẩm giao.**

Save `.blend` có `bpy.ops.file.pack_all()` — texture đến từ job cloud nên không pack là mất.
⚠️ File dạng này **không sinh lại được**: chạy lại prompt cũ ra model khác. Xem
[ART-PIPELINE §7](ART-PIPELINE.md).

### Bước E · Decimate + xuất 3 LOD

Chạy headless, sinh cả 3 tier cùng lúc theo ngân sách đã đo ở [ART-PIPELINE §4.4](ART-PIPELINE.md):

```bash
blender -b assets/raw/blender/characters/<hệ>-<bậc>.blend \
  -P tools/blender/export_character_lods.py -- \
  --object <MeshThân> --rig <tên_Rig> --extras <các,phần,vũ,khí> \
  --asset-key char_<hệ>-<bậc> --out <abs>/assets/exported/models
```

⚠️ **`--object` và `--rig` phải ghi rõ, không để script tự đoán.** File nhân vật thật chứa nhiều thế
hệ mesh của cùng nhân vật đó, và `find_armature()` trả về armature **đầu tiên trong scene** — trong
file pilot Hỏa đó lại là rig của NPC town-elder, không phải rig nhân vật.

Ra `low/med/high/<asset_key>.glb` + `<asset_key>.model-meta.json` (`tri_counts` cho `art_asset`,
[ART-PIPELINE §8.6](ART-PIPELINE.md)). Bước xuất **tự bỏ** viền inverted hull, far-shade và vật liệu
toon — shader runtime dựng lại cả ba; giữ viền lại thì nó thành hình học thật và **gấp đôi số tam giác**.

---

## 2. Checklist nghiệm thu

Chạy checklist **trên file PNG đã render**, không phải trên viewport. Bắt buộc kiểm ở cả @1x và @0.5x (thu nhỏ ảnh xuống 50%/25%) — lỗi readability chỉ lộ ra khi nhỏ.

⚠️ Checklist dưới đây là tầng **kỹ thuật**. Nhân vật/quái/NPC còn phải qua tầng **thẩm mỹ** — bảng PASS/FAIL Q1–Q12 ở [ART-QUALITY-STANDARD.md §2](ART-QUALITY-STANDARD.md) (benchmark Ninja School Online). FAIL tầng thẩm mỹ = không render sheet, không ghi log.

### 2.1 Nhân vật / NPC — nghiệm thu trên **ảnh preview**, sản phẩm giao là model

- [ ] Nền **trong suốt** hoàn toàn, không viền trắng/xám quanh mép.
- [ ] Outline đen liền mạch, không đứt ở chỗ chi mảnh (tay, vũ khí mỏng).
- [ ] Cel 2–3 nấc rõ ràng — không có gradient mượt (gradient = shader sai, xem §4).
- [ ] Model **quay mặt +X** trong không gian gốc. Từ ADR-031 đây **không còn là hướng nhìn trên màn** (góc camera là hằng số runtime), nhưng vẫn bắt buộc: rig, bảng pose và `add_far_shade()` đều giả định trục này.
- [ ] Chân chạm đúng baseline: kiểm `pivot_y_px_from_bottom` trong `render-meta.json` **giống nhau** giữa các asset cùng loại.
- [ ] Không frame nào bị **cắt** (tay vung, tóc, vũ khí) — `fit_camera` quét mọi frame nên lỗi này chỉ xảy ra khi thêm action sau lúc fit.
- [ ] Tỷ lệ chibi 2–2.5 đầu; đặt cạnh asset đã duyệt trong ASSET-LOG để so.
- [ ] Ở @0.5x (64px): vẫn nhận ra silhouette là nhân vật gì, hệ nào.

### 2.2 Boss / World Boss (master 512)

Toàn bộ mục 2.1, cộng thêm:

- [ ] Silhouette **khác hẳn** quái thường — người chơi phải nhận ra "đây là boss" trong 1 frame.
- [ ] Tông tối/tương phản mạnh hơn thế giới thường (art direction, The Eminence in Shadow).
- [ ] Khung 512 nhưng vẫn cùng baseline: boss đứng cùng mặt đất với người chơi, không "lơ lửng".
- [ ] ~~Ngân sách atlas: 1 boss @1x 256px × ~33 frame ≈ trọn 1 atlas 2048, atlas riêng~~ — **hết hiệu lực từ [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md)**: boss thuộc nhánh 3D, giao model glTF, không còn atlas. Ràng buộc thay thế là **ngân sách tam giác** ([ART-PIPELINE §4.4](ART-PIPELINE.md)) — boss chưa đo riêng, tạm dùng ngưỡng nhân vật.

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
| Nhân vật/NPC/quái | 256 (preview nghiệm thu) | Tra [ART-QUALITY-STANDARD §4](ART-QUALITY-STANDARD.md) mục 3 — kỹ thuật **và** số đo khác nhau theo thế hệ mesh, đừng chép số vào đây | **Tắt** với mesh Text→3D (dùng `add_inverted_hull_outline()`); chỉ bật cho mesh thủ tục cũ | Bộ chuẩn §4.2 |
| Boss | 512 | như nhân vật (chưa đo riêng — kiểm lại khi làm boss đầu tiên) | như nhân vật | Bộ chuẩn + action riêng của boss |
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
| ✅ **Một bộ phận "biến mất" khỏi bản render dù mesh có thật và silhouette vẫn trông đúng** | Bộ phận nằm **lọt trong bề dày** của bộ phận che nó. Gặp thật: `ARM_Y=0.085` < nửa bề dày thân `0.114` → cả 2 cánh tay nằm trong lồng ngực, mặt thân render đè lên | Mọi bộ phận phải nằm ngoài bề dày của thứ che nó ([ART-QUALITY-STANDARD §3](ART-QUALITY-STANDARD.md)). Không phát hiện được bằng cách nhìn 1 ảnh tĩnh — phải soi đúng câu hỏi "tay đâu?" |
| ✅ Nhân vật ra **một mảng đen kín**, không thấy chi tiết | Outline quá dày so với kích thước nhân vật **trong khung**. Master 256px với nhân vật cao ~166px thì 2.5px@1x (=5px master) phủ gần hết Số đúng khác nhau theo **thế hệ mesh** — tra [ART-QUALITY-STANDARD §4](ART-QUALITY-STANDARD.md) mục 3 (nguồn duy nhất), đừng chép số vào đây. Script dựng ghi sẵn giá trị vào .blend, `kage_sprite_render.py` mặc định **kế thừa** giá trị trong file — chỉ truyền `--outline-px` khi cố ý. Nếu là mesh Text→3D mà mảng đen xuất hiện quanh **tóc/mặt** thì nguyên nhân khác hẳn: Freestyle vẫn đang bật, phải tắt và dùng `add_inverted_hull_outline()` |
| ✅ Loại một object khỏi outline bằng `obj.lineart.usage = "EXCLUDE"` **không có tác dụng** | Thuộc tính đó của **Grease Pencil Line Art**, không phải Freestyle. Nó im lặng không làm gì | Freestyle lọc theo **collection**: `lineset.select_by_collection = True` + `lineset.collection` + `collection_negation = "EXCLUSIVE"` (xem `apply_outline_exclusions()` trong `kage_mesh_kit.py`) |
| ✅ Tay/chân vung **ngược hướng** so với bảng pose | Dùng một dấu xoay chung cho mọi xương. Cùng một trục thế giới làm xương hướng **lên** (spine/head) và xương hướng **xuống** (tay/chân) quay ngược nhau | Tính dấu từ `axis × bone_direction` rồi chuẩn hoá "dương = mũi xương ra trước" (`_swing_channel()` trong kit) |
| ✅ Cả sheet bị thu nhỏ, nhân vật thành chấm nhỏ giữa khung | Một pose duy nhất chìm dưới `z=0`. Baseline 0.08 nên mỗi đơn vị dưới đất tốn **12.5×** zoom; gặp thật ở `die` khi góc cộng dồn `pelvis+spine+head` đạt −190° và cắm đầu xuống `z=−0.59` | `fit_camera()` giờ **in cảnh báo** khi phần dưới đất là yếu tố quyết định. Sửa **pose**, không sửa camera. Nhớ góc cộng dồn theo chuỗi xương |
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
3. ✅ **Chốt 2026-08-02, đã triển khai 2026-08-03** (câu hỏi "box rời vs mesh liền mạch"): chủ dự án đánh
   giá box-pilot không đạt chất lượng ("hình hộp lego") — chuẩn mới là **mesh trơn**, box trần bị cấm cho
   cơ thể nhân vật. Outline đi kèm hoá ra **không phải 2.5px** như dự đoán ban đầu mà là **1.1px@1x** (đo
   thật). Toàn bộ tiêu chuẩn + lộ trình thay thế: [ART-QUALITY-STANDARD.md](ART-QUALITY-STANDARD.md).

⚠️ **Mục 1, 2, 4, 6 dưới đây nói về thế hệ box và đã bị thay thế** — giữ lại làm lịch sử pipeline, không
phải hướng dẫn còn hiệu lực. Nhân vật hiện làm theo **§1.5** của file này (Text→3D), nghiệm thu theo
[ART-QUALITY-STANDARD §7](ART-QUALITY-STANDARD.md).
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
7. ✅ **Xong 2026-08-01**: `tools/asset-pipeline` đã code + chạy thật trên toàn bộ asset ([ADR-029](../01-architecture/adr/ADR-029-Asset-Pipeline-Tool.md) Accepted) — bước 6 hết rỗng.
