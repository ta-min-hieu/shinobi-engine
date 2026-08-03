# ART-QUALITY-STANDARD.md

**Chuẩn chất lượng hình ảnh — định nghĩa ĐẠT / KHÔNG ĐẠT cho mọi asset nhân vật/quái/NPC**

| | |
|---|---|
| Version | 1.1.0 |
| Status | Draft |
| Benchmark | **Ninja School Online** (TeaMobi) — mức tối thiểu phải đạt |
| Liên quan | [ART-PIPELINE.md](ART-PIPELINE.md) (pipeline) · [BLENDER-SESSION-GUIDE.md](BLENDER-SESSION-GUIDE.md) (thao tác) · [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md) (2.5D) |

> Phân vai 3 tài liệu art: ART-PIPELINE = *pipeline & định hướng* · BLENDER-SESSION-GUIDE = *thao tác từng bước* · **file này = thước đo "đẹp"**. Phiên làm art nạp cả 3 (và chỉ 3).

> ⚠️ **Cập nhật 2026-08-03.** Nguồn mesh nhân vật đã đổi từ **mesh kit thủ tục** sang **Text→3D
> (Hyper3D Rodin)** — chủ dự án duyệt hướng art trên bản pilot Hỏa cùng ngày. Bảng nghiệm thu
> Q1–Q12 (§2), số liệu giải phẫu (§3) và chuẩn animation (§6) **giữ nguyên hiệu lực** và là thứ
> dùng để chấm bản pilot đó. Riêng **§5 (mesh kit) giờ chỉ còn dùng cho vũ khí và phụ kiện** —
> phần dựng cơ thể đã bị thay, xem [ART-PIPELINE §6.4](ART-PIPELINE.md).
> Ngoài ra [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md) chuyển nhân vật
> sang render 3D thời gian thực: ảnh render giờ là **bằng chứng nghiệm thu**, không phải sản phẩm.

---

## 0. Vì sao có file này

Thế hệ nhân vật đầu (`build_box_character.py`) là khối hộp căn trục — đúng vai trò *kiểm chứng pipeline*, nhưng chủ dự án đánh giá **không đạt** ("hình hộp lego, rất xấu", 2026-08-02). Từ nay:

- Nhân vật/quái/NPC dựng bằng **Text→3D qua `tools/blender/kage_text3d_kit.py`** ([ART-PIPELINE §6.4](ART-PIPELINE.md)); `kage_mesh_kit.py` chỉ còn cho vũ khí/phụ kiện (§5). `build_box_character.py` chỉ còn để dựng lại file cũ — **không dùng cho asset mới**.
- Mọi asset nhân vật/quái/NPC mới phải qua bảng nghiệm thu §2. FAIL 1 tiêu chí = không ghi ASSET-LOG, không pack atlas.
- 15 sheet `char_*-pilot/-b2/-b3` cũ + `monster_training_dummy` + `npc_town_elder` = **PLACEHOLDER**, thay dần (§8).
- Checklist kỹ thuật ở [BLENDER-SESSION-GUIDE §2](BLENDER-SESSION-GUIDE.md) **vẫn giữ nguyên** — file này là tầng *thẩm mỹ* bên trên, không thay thế tầng kỹ thuật.

---

## 1. Benchmark "Ninja School Online" nghĩa là gì

AI chưa từng thấy game này nên quy ra đặc điểm cụ thể. NSO là 2D side-view sprite chibi — cùng thể loại với Kage nên so sánh 1-1 được:

| Đặc điểm NSO | Quy ra chuẩn Kage |
|---|---|
| Đầu **tròn**, to ~½ chiều cao nhân vật | Ellipsoid, chiếm 0.4/1.0 unit (chibi 2.5 đầu) |
| Mặt biểu cảm: mắt to rõ, thấy lông mày | Side-view → **1 mắt** phía +X, đọc được ở @1x 128px |
| Tóc spike anime — nhận diện nhân vật từ xa | Silhouette tóc **nhô khỏi** vòm đầu, kiểu tóc khác nhau giữa nhân vật |
| Trang phục nhiều mảng màu, có phụ kiện | ≥ 3 vùng màu tách bạch trên thân |
| Vũ khí nhìn ra hình | Vũ khí có hình dạng thật, không phải hộp |
| Pose động: nghiêng người, khuỵu gối | Khuỷu/gối gập thật (rig 11 xương), có anticipation + follow-through (§6) |

**Một câu cho mọi phiên art**: *nhân vật phải trông như chú ninja chibi trong anime — không phải robot ghép từ hộp.*

Ảnh chuẩn đã duyệt (2026-08-03, pilot Text→3D): `assets/raw/reference/pilot_text3d_hoa_v2_actions.png` · `pilot_text3d_hoa_v2_goc_camera.png`. Bộ `gold_char_hoa_b1.png` / `gold_5elements_b3.png` (thế hệ mesh kit) **đã bị chủ dự án bác** cùng ngày — chỉ giữ làm lịch sử, không dùng làm chuẩn so.

---

## 2. Bảng nghiệm thu PASS/FAIL

Chấm trên **PNG đã render** (không phải viewport), ở @1x rồi @0.5x. Xem nhiều frame một lần bằng `make_contact_sheet.py` (§7 bước 4). **FAIL bất kỳ mục nào = sửa rồi lặp lại, không render tiếp cả sheet.**

| # | Tiêu chí | Cách kiểm nhanh |
|---|---|---|
| Q1 | Đầu có cung tròn — silhouette đầu KHÔNG phải hình chữ nhật | Nhìn viền đỉnh + gáy |
| Q2 | Thấy ≥ 1 mắt + lông mày; mắt rõ ở @1x | Zoom vùng đầu |
| Q3 | Tóc nhô khỏi vòm đầu, có **chân tóc** (thấy trán), màu tách khỏi màu da | Nhìn viền đỉnh đầu |
| Q4 | Thân ≥ 3 vùng màu (áo / quần / giày hoặc giáp) và **phân biệt được ở @1x** | Đếm mảng màu |
| Q5 | Có chi tiết phá silhouette. **B1**: tóc spike + vũ khí là đủ. **B2/B3**: bắt buộc thêm ≥1 mảnh vải/giáp (khăn, đai, áo choàng) | Nhìn viền quanh thân |
| Q6 | Tay chân thuôn tròn; **bàn tay và bàn chân phân biệt được** (khác màu hoặc khác khối) | Zoom chi |
| Q7 | Vũ khí đọc được đúng loại ở **@1x** (§2.3 ART-PIPELINE) | Nhìn riêng vũ khí ở 128px |
| Q8 | Cel shading ≥ 2 nấc thấy rõ trên thân (không phẳng lì, không gradient mượt) | Nhìn vùng ngực/bụng |
| Q9 | Outline liền mạch, KHÔNG dồn thành mảng đen | Nhìn tổng thể |
| Q10 | @0.5x 64px: vẫn nhận ra là ninja + biết hệ nào | Thu nhỏ 50% |
| Q11 | `run`: thân nghiêng trước 8–15°, khuỷu + gối **gập**, có frame 2 chân rời đất | Xem `run_01` + `run_02` |
| Q12 | `attack`: `attack_00`/`attack_01` lấy đà (rút về), `attack_02` vung hết tầm | Xem `attack_00` + `attack_02` |

Q11–Q12 chỉ chấm sau khi Q1–Q10 pass trên frame `idle`.

**Chuẩn so sánh cuối**: đặt cạnh `assets/raw/reference/pilot_text3d_hoa_v2_actions.png` — asset mới không được "tụt cấp" so với ảnh này.

---

## 3. Số liệu giải phẫu chibi (đơn vị Blender, nhân vật cao 1.0)

Đã cài sẵn trong `kage_mesh_kit.py` — bảng này để **hiểu và chỉnh**, không phải gõ lại.

| Bộ phận | Số liệu | Ghi chú |
|---|---|---|
| Đầu | Ellipsoid tâm `z=0.80`, bán kính `(0.19, 0.17, 0.20)` | Cằm ~0.60, đỉnh ~1.00 |
| Mắt (1, phía +X) | `x=0.092, z=0.782`, cao ~0.11 | Tròng trắng + con ngươi; **phải nằm dưới chân tóc z=0.856** |
| Lông mày | `x=0.086, z=0.838`, nghiêng −12° | Màu tóc |
| Tóc | Dome cắt ngang chân tóc + khối gáy + 4–7 spike ngả về −X | Xem `HAIR_STYLES` |
| Thân | Lathe thuôn, nửa bề dày **0.114** ở ngực | KHÔNG phải hộp |
| Tay | `ARM_Y = 0.125` (xem cảnh báo dưới), vai `z=0.56` → khuỷu `0.43` → cổ tay `0.30` | Bàn tay = cầu màu da |
| Chân | `LEG_Y = 0.058`, hông `z=0.25` → gối `0.13` → cổ chân `0.0` | Bàn chân dài về +X |
| Chân xa (`_l`) | Nhân màu × `FAR_SHADE = 0.70` | Tạo chiều sâu trong góc nhìn nghiêng |

⚠️ **`ARM_Y` phải LỚN HƠN nửa bề dày thân (0.114).** Lần đầu đặt 0.085 → hai cánh tay **nằm lọt trong lồng ngực và bị thân che hoàn toàn**. Silhouette vẫn trông đúng nên nhìn 1 ảnh tĩnh không phát hiện được — chỉ lộ khi soi tại sao tay "biến mất". Luật chung: mọi bộ phận phải nằm ngoài bề dày của bộ phận che nó.

---

## 4. Kỹ thuật dựng hình bắt buộc

1. **CẤM box trần cho phần cơ thể** (đầu, thân, chi). Box chỉ dùng cho giáp/vũ khí/prop cứng.
2. Cơ thể dựng bằng **mặt tròn xoay** (`_revolve`/`_ellipsoid`/`_taper` trong kit) + `use_smooth`. Cel ramp `CONSTANT` vẫn cho mảng màu phẳng trên mesh trơn — "toon" không có nghĩa là "vuông". Không cần Subdivision modifier: kit sinh thẳng lưới đủ mịn (16–24 cạnh vòng), rẻ hơn và không đụng thứ tự modifier với Armature. *(Từ 2026-08-03 điều này áp dụng cho vũ khí/phụ kiện; cơ thể đến từ Text→3D và vốn đã trơn.)*
3. **Outline không phải hằng số** — và cũng không phải **một** kỹ thuật:
   - Mesh **thủ tục** (kit): Freestyle, **1.1px@1x**. Art direction ghi 2.5px là sai — đo thật ở master 256px thì 2.5px phủ đen gần kín.
   - Mesh **sinh từ Text→3D**: **không dùng Freestyle**. Trên mesh nhiều tóc, Freestyle vẽ contour cho *từng lọn* và mặt đầy nét đen rối. Dùng `add_inverted_hull_outline()` (shell lật normal, cull mặt trước), đo được **3.0px@1x**. Kỹ thuật này còn có lợi thế chuyển thẳng sang GLSL runtime được.

   Con số phải **đo lại cho từng thế hệ mesh**, đừng kế thừa.
4. **Nấc cel ramp cũng không phải hằng số.** Mesh thủ tục dùng `SHADE_STEPS` (đáy 0.55) vì ramp *chính là* nguồn sáng tối duy nhất. Mesh có texture vẽ sẵn bóng thì dùng nhầm bảng đó là **tối hai lần** và nhân vật xỉn hẳn — dùng `TEXTURED_SHADE_STEPS` (đáy 0.80).
5. Mắt/mày/miệng: mesh ốp nổi khỏi mặt đầu, dùng `head_surface_y()` để tính độ sâu — đừng đoán tay. *(Mesh Text→3D đã có sẵn mặt trong texture — mục này chỉ còn cho mesh thủ tục.)*
6. Khăn/đai/vạt áo: dùng driver theo `frame_current` (`_sway_strip`), **không** keyframe theo từng action — vải phải động ở mọi action, và keyframe 7 lần là đúng cách để tái phạm lỗi "sót bone".
7. Vũ khí phải **to hơn tỷ lệ thật**: ở @1x nhân vật chỉ cao ~83px, con dao đúng tỷ lệ sẽ còn 3–4px và thành vệt mờ. Đọc được silhouette quan trọng hơn đúng tỷ lệ.
8. Mọi mesh gán đúng vertex-group theo tên xương của rig 11 xương (§5).

---

## 5. Mesh kit — vũ khí, phụ kiện, rig và action

> ⚠️ **Phần dựng cơ thể của kit đã bị thay** (2026-08-03). Cơ thể nhân vật giờ đến từ Text→3D
> ([ART-PIPELINE §6.4](ART-PIPELINE.md)), vì trần chất lượng của hướng thủ tục chỉ tới ~2.5–3/5 NSO.
> **Còn dùng nguyên vẹn:** `build_rig` · `build_weapon` · `add_accessory` · `add_motion_detail` ·
> `author_actions` (qua `kage_text3d_kit.author_scaled_actions`, có hiệu chỉnh biên độ) ·
> `shade_hex`. **Không còn dùng cho nhân vật:** `build_chibi_body` · `add_face` · `add_hair` ·
> `assemble_character` · `build_kage_character.py`.

✅ **`tools/blender/kage_mesh_kit.py` đã có và chạy thật** (Blender 5.2.0 LTS). API:

| Hàm | Nhiệm vụ |
|---|---|
| `build_rig(name)` | Rig **11 xương**: `pelvis spine head arm_l/r forearm_l/r leg_l/r shin_l/r`. Toạ độ khớp chung giữ nguyên như rig 7 xương cũ nên baseline/pivot không đổi |
| `build_chibi_body(rig, spec)` | Đầu, cổ, thân, hông, 2 tay, 2 chân (kèm khớp cầu vai/khuỷu/gối, bàn tay, bàn chân) |
| `add_face(rig, spec, style)` | Mắt + con ngươi + lông mày + miệng phía +X. `style`: `default` \| `wide` |
| `add_hair(rig, spec, style)` | `spiky_short` \| `spiky_long` \| `bang` \| `topknot` |
| `add_accessory(rig, spec, kind, color, bone)` | `headband` `scarf` `sash` `shoulder_plate` `chest_plate` `thigh_guard` `arm_wrap` `cloak` |
| `build_weapon(rig, spec, kind, ...)` | `kunai` `gourd` `twin_blades` `long_blade` `stone_mace` |
| `add_motion_detail(rig, spec, kind, color)` | B3: `embers` `water_ring` `ribbons` `spark` `stone_shards` |
| `author_actions(rig)` | 7 action chuẩn, pose theo §6 |
| `assemble_character(spec)` | Gọi trọn bộ trên từ 1 dict |
| `shade_hex(hex, factor)` · `head_surface_y(x, z)` | Tiện ích |

Spec dict: xem `CHARACTER_SPEC_KEYS` cuối file kit. Spec thật của **cả 5 hệ × 3 bậc** nằm ở bảng `CHARACTERS` trong `tools/blender/build_kage_character.py` — B2 kế thừa phụ kiện B1, B3 kế thừa cả hai.

Dựng 1 nhân vật = **1 dòng lệnh**, không cần mở Blender:

```bash
blender -b assets/raw/blender/kage_character_base.blend \
  -P tools/blender/build_kage_character.py \
  -- --element hoa --tier b1 --out assets/raw/blender/characters/hoa-b1.blend
```

⚠️ **Màu vải không lấy màu hệ.** Nhân vật mặc nguyên màu hệ từ đầu tới chân sẽ mất tiêu chí Q4 (3 mảng màu). Màu hệ nằm ở phụ kiện/VFX; vải dùng tông trung tính quanh nó. Vải quá tối cũng hỏng: cel ramp nhân 0.55 ở nấc tối nhất, nền `#4E3320` ra gần như đen.

---

## 6. Chuẩn animation (khoá chính giữ nguyên §4.2 ART-PIPELINE)

Tên action và số khoá chính không đổi; đổi là **chất lượng chuyển động bên trong**.

⚠️ **Biên độ góc không dùng chung được giữa hai tỷ lệ thân.** Bảng pose trong `author_actions()`
được author cho tỷ lệ mesh kit. Trên thân Text→3D (~2 đầu, chi rất ngắn) thì cùng bộ góc đó làm
`attack` và `die` gập gãy: đầu chiếm nửa chiều cao nên góc thân swing quá xa, còn chi ngắn thì góc
chi lại không đủ để đọc ra. Dùng `kage_text3d_kit.author_scaled_actions()` — nó nhân hệ số theo
từng xương (`CHIBI_GAINS`) và **giữ nguyên nhịp lẫn độ trễ** của bảng gốc thay vì chép ra bảng thứ hai.

| Action | Yêu cầu tối thiểu |
|---|---|
| `idle` | Nhún thở; tay hơi đưa **trước** (tay buông thẳng trong góc nghiêng chỉ là cái ống giữa ngực, không đọc ra gì); chân **so le** để 2 chân không chồng khít thành một khối |
| `run` | Thân nghiêng trước 8–15°; khuỷu + gối gập; ≥ 1 frame cả 2 chân rời đất; phụ kiện bay ngược hướng chạy |
| `attack` | Frame 0–1 lấy đà (rút vũ khí về sau); **frame index 2 = impact** (khớp hitbox server); frame cuối follow-through |
| `hit` | Bật lùi thân trên + đầu ngửa nhẹ (tint flash vẫn là việc của code) |
| `jump` | Squash bằng **scale** chứ không hạ gốc — scale lấy gốc toạ độ (mặt đất) làm tâm nên bàn chân không thụt xuống đất |
| `die` | **Gục xuống gọn**, không nằm sải dài — xem cảnh báo dưới |

⚠️ **Pose chết quyết định kích thước cả sheet.** `fit_camera()` lấy pose rộng nhất trong **mọi** action để tính `ortho_scale`, nên một xác nằm sải dài (~0.8 unit ngang) sẽ thu nhỏ nhân vật ở cả 33 frame để đổi lấy chân thực ở 6 frame. Gập chân dưới thân giữ silhouette gọn mà vẫn đọc ra là chết.

⚠️ **Không để hình học chìm dưới `z=0`.** Baseline là 0.08 nên mỗi đơn vị dưới đất tốn **12.5×** zoom. `fit_camera()` giờ in cảnh báo khi gặp — thấy cảnh báo thì sửa pose, đừng sửa camera.

⚠️ **Góc cộng dồn theo chuỗi xương**: độ nghiêng thật của thân là `pelvis + spine`, của đầu là `pelvis + spine + head`. Lần đầu tổng đạt −190° làm nhân vật cắm đầu xuyên sàn tới `z=−0.59`.

Nguyên tắc chung: **không bone nào đứng yên tuyệt đối** trong action động. `pose_frame()` tự keyframe cả 11 xương ở mọi frame — đừng viết đường tắt bỏ qua nó (lỗi "gãy gập", [BLENDER-SESSION-GUIDE §4](BLENDER-SESSION-GUIDE.md)).

Số frame render được chọn **rơi đúng vào keyframe** (idle 1..17, run 1..13, attack 1..9, skill_cast 1..11, hit 1..3, jump 1..7, die 1..11) — sprite hiện đúng pose đã dựng, không phải một phép nội suy giữa hai pose.

---

## 7. Vòng tự nghiệm thu của Sonnet (bắt buộc, tối đa 3 vòng)

> ⚠️ **TODO-OPUS (2026-08-03): lệnh trong mục này là của thế hệ mesh kit, đã lỗi thời cho nhân vật.**
> Nhân vật giờ sinh bằng Text→3D ([BLENDER-SESSION-GUIDE §1.5](BLENDER-SESSION-GUIDE.md) bước A–D) —
> không có "1 dòng lệnh dựng lại từ spec" nữa vì mesh đến từ Rodin (không tái sinh được). **Vòng lặp
> chấm Q1–Q12 + giới hạn 3 vòng + kỷ luật token vẫn giữ nguyên hiệu lực**; chỉ phần lệnh dựng
> (`build_kage_character.py`) cần viết lại theo flow Text→3D. Lệnh render preview/contact sheet
> (`kage_sprite_render.py`, `make_contact_sheet.py`) vẫn dùng được y nguyên để nghiệm thu.

```bash
# 1. Dựng + render 1 frame duyệt style
blender -b assets/raw/blender/kage_character_base.blend -P tools/blender/build_kage_character.py \
  -- --element <hệ> --tier <bậc> --out assets/raw/blender/characters/<hệ>-<bậc>.blend
blender -b assets/raw/blender/characters/<hệ>-<bậc>.blend -P tools/blender/kage_sprite_render.py \
  -- --preview idle --out assets/raw/render-out/_review

# 2. Read PNG vừa render → chấm Q1–Q10, ghi mỗi mục 1 dòng PASS/FAIL
```

3. Có FAIL → sửa spec/tham số → lặp. **Tối đa 3 vòng**; vẫn FAIL → dừng, ghi vấn đề + ảnh vào ASSET-LOG, đánh dấu chờ Opus. **Không hạ chuẩn, không render cả sheet để "tạm dùng".**
4. PASS Q1–Q10 → render cả bộ, rồi ghép contact sheet để chấm Q11–Q12 trong **một** lần xem:

```bash
blender -b assets/raw/blender/characters/<hệ>-<bậc>.blend -P tools/blender/kage_sprite_render.py \
  -- --all-actions --out assets/raw/render-out/char_<hệ>-<bậc>
blender -b -P tools/blender/make_contact_sheet.py -- \
  --dir assets/raw/render-out/char_<hệ>-<bậc> \
  --frames idle_00 run_01 attack_00 attack_02 die_05 \
  --out assets/raw/render-out/_review/<hệ>-<bậc>.png --scale 2
```

5. PASS → hạ mẫu/pack/ghi log theo [BLENDER-SESSION-GUIDE §1](BLENDER-SESSION-GUIDE.md) bước 6–7.

Kỷ luật token giữ nguyên [ART-PIPELINE §6.1](ART-PIPELINE.md): batch lệnh, preview trước sheet sau, headless cho việc dài. Toàn bộ vòng trên chạy headless — **không cần mở Blender, không cần MCP**.

---

## 8. Phân công Sonnet ↔ Opus

| # | Việc | Ai | Trạng thái |
|---|---|---|---|
| 1 | `kage_mesh_kit.py` — toàn bộ hàm §5 | Opus | ✅ **Xong 2026-08-03**, verify bằng render thật |
| 2 | Rig 11 xương + 7 action có khuỷu/gối | Opus | ✅ **Xong** (trong kit) |
| 3 | Gold standard Hỏa B1 + ảnh tham chiếu | Opus | ❌ **Bị bác 2026-08-03** (~1/5 NSO) → thay bằng pilot Text→3D Hỏa, **đã duyệt cùng ngày** (`pilot_text3d_hoa_v2_*.png`) |
| 4 | Spec 5 hệ × 3 bậc | Opus | ✅ **Xong** — bảng `CHARACTERS` trong `build_kage_character.py`; giờ chỉ còn giá trị làm **spec trang phục/vũ khí** tham chiếu cho prompt Text→3D (§2.3 ART-PIPELINE) |
| 5 | ~~Re-render 15 sheet~~ → Sản xuất **14 nhân vật còn lại bằng Text→3D**, qua vòng §7 (bản sửa theo TODO-OPUS), cập nhật ASSET-LOG | **Sonnet** | ⏳ Chờ chốt ngân sách tam giác + gắn vũ khí ([ART-PIPELINE §12.1](ART-PIPELINE.md)) |
| 6 | Quái/NPC/boss mới về sau | Sonnet (boss khó → escalate) | Chờ #5 chạy ổn |
| 7 | VFX skill: giữ chuẩn hiện có (17 VFX đã ship không thuộc diện placeholder) | Sonnet | Không đổi |

**Sonnet bắt đầu từ #5.** Khi làm quái/NPC (#6) mà kit thiếu bộ phận (sừng, đuôi, 4 chân...) → **không sculpt tay**, báo lại để bổ sung hàm vào kit; kit là nơi giữ chất lượng đồng đều.

⚠️ **Chỉ commit .blend khi thật cần.** Thế hệ mới sinh lại nguyên vẹn từ 1 dòng lệnh (§7 bước 1) nên `.blend` không còn là "source of truth" không thể thay thế như thế hệ model tay — nguồn thật giờ là **spec dict + kit**, đều là text. Repo đang bật Git LFS với quota hữu hạn, đừng nạp 15 file nhị phân sinh lại được. Hiện chỉ commit `hoa-b1.blend` làm mẫu đối chiếu cho gold standard.

⚠️ **File `.blend` thế hệ box vẫn còn trong git** (`<hệ>-pilot/-b2/-b3.blend`). Khi #5 chạy, chúng bị ghi đè nếu đặt trùng tên — chỉ làm việc đó **sau khi #3 được duyệt**, và biết rằng chúng dựng lại được bằng `build_box_character.py`.

---

## 9. Technical Decisions

| # | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| 1 | Benchmark chất lượng tối thiểu = Ninja School Online | Chủ dự án chốt 2026-08-02 | **Chốt** |
| 2 | Nghiệm thu bằng bảng PASS/FAIL đo trên PNG, AI tự chấm | Chuẩn cảm tính ("đẹp", "chibi") đã cho ra box — phải đo được | Đề xuất |
| 3 | Cơ thể = mặt tròn xoay, cấm box trần | Nguyên nhân trực tiếp của "hình hộp lego"; cel shader không đòi mesh vuông | Đề xuất |
| 4 | Thư viện `kage_mesh_kit.py`, Sonnet chỉ gọi + spec dict | Sculpt tự do tốn token và ra kết quả không đều | Đề xuất |
| 5 | Rig 11 xương (thêm forearm/shin), giữ nguyên toạ độ khớp cũ | Chi 1 đoạn không gập được khuỷu/gối → animation đơ; giữ toạ độ để baseline/pivot không phải verify lại | Đề xuất |
| 6 | 15 sheet box hiện tại = PLACEHOLDER, thay dần, không sửa tại chỗ | Giữ pipeline chạy được trong lúc nâng cấp | Đề xuất |
| 7 | Outline **không phải hằng số** — đo lại cho từng thế hệ mesh, nguồn duy nhất là §4 mục 3 (mesh kit 1.1px@1x Freestyle · Text→3D 3.0px@1x inverted hull), lưu trong .blend để bước render kế thừa | Đo thật: 2.5px@1x phủ đen nhân vật ở master 256px; Freestyle trên mesh Text→3D vẽ viền từng lọn tóc | Đề xuất |
| 8 | `die` là cú gục gọn, không phải nằm sải dài | `fit_camera` lấy pose rộng nhất cho cả sheet — pose chết sải dài thu nhỏ nhân vật ở toàn bộ 33 frame | Đề xuất |
| 9 | Dấu xoay xương tính từ `axis × bone_direction`, không hằng số | Cùng một trục thế giới làm xương hướng lên và xương hướng xuống quay ngược nhau; hằng số chung làm tay chân vung ngược | Đề xuất |
| 10 | Loại nét mặt khỏi outline bằng **collection filter** của Freestyle | `obj.lineart.usage` trông đúng nhưng thuộc Grease Pencil Line Art, **không tác dụng gì** với Freestyle — nó im lặng không làm gì cả | Đề xuất |

## 10. ADR cần tạo

Không. Không thêm dependency, không đổi kiến trúc — chỉ nâng chuẩn nội dung trong pipeline đã có ([ADR-029](../01-architecture/adr/ADR-029-Asset-Pipeline-Tool.md) không đổi).

## 11. Tài liệu liên quan

[ART-PIPELINE.md](ART-PIPELINE.md) (§2 art direction, §4 chuẩn kỹ thuật, §6.1 kỷ luật token) · [BLENDER-SESSION-GUIDE.md](BLENDER-SESSION-GUIDE.md) (thao tác + checklist kỹ thuật §2 + bảng lỗi §4) · [tools/blender/README.md](../../tools/blender/README.md) (contract từng script)

## 12. Việc cần làm tiếp theo

1. ✅ **Đóng 2026-08-03**: gold standard mesh kit **bị bác** (~1/5 NSO); chủ dự án **duyệt hướng Text→3D** trên pilot Hỏa cùng ngày (`pilot_text3d_hoa_v2_*.png` là chuẩn so mới).
2. Sonnet: sản xuất 14 nhân vật còn lại + monster/NPC placeholder bằng Text→3D qua vòng §7 (sau khi viết lại theo TODO-OPUS), cập nhật `ASSET-LOG.md`. **Chặn bởi**: ngân sách tam giác + gắn vũ khí ([ART-PIPELINE §12.1](ART-PIPELINE.md)).
3. ✅ **Xong 2026-08-03**: đã quét sạch con số outline cũ khỏi mọi tài liệu (ART-PIPELINE §2.1, BLENDER-SESSION-GUIDE §1/§3/§8, `tools/blender/README.md`) — giờ chỉ còn **một nguồn duy nhất** là §4 mục 3 của file này, các nơi khác trỏ về đây.
4. Thổ B3 dùng `topknot` nhưng render ra giống **nón lá** hơn búi tóc — hợp hệ Thổ nên tạm giữ, cần thì đổi tên style hoặc dựng lại khối búi.
5. Vũ khí gắn cố định ở hông/lưng, **không vào tay khi `attack`** (thế hệ box cũng vậy) — nếu muốn cầm vũ khí thật thì thêm bone `hand_r` + constraint, là việc riêng.
