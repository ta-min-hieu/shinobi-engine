# ART-QUALITY-STANDARD.md

**Chuẩn chất lượng hình ảnh — định nghĩa ĐẠT / KHÔNG ĐẠT cho mọi asset nhân vật/quái/NPC**

| | |
|---|---|
| Version | 1.3.0 |
| Status | Draft |
| Benchmark | **Ninja School Online** (TeaMobi) — mức tối thiểu phải đạt |
| Liên quan | [ART-PIPELINE.md](ART-PIPELINE.md) (pipeline) · [BLENDER-SESSION-GUIDE.md](BLENDER-SESSION-GUIDE.md) (thao tác) · [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md) (2.5D) |

> Phân vai 3 tài liệu art: ART-PIPELINE = *pipeline & định hướng* · BLENDER-SESSION-GUIDE = *thao tác từng bước* · **file này = thước đo "đẹp"**. Phiên làm art nạp cả 3 + [tools/blender/README.md](../../tools/blender/README.md) (contract script) — đúng 4 file, không đọc thêm.

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
- Mọi asset nhân vật/quái/NPC mới phải qua bảng nghiệm thu §2. FAIL 1 tiêu chí = không ghi ASSET-LOG, không giao model (và với nhánh 2D còn lại: không pack atlas).
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

Chấm trên **PNG đã render** (không phải viewport), ở @1x rồi @0.5x. Xem nhiều frame một lần bằng `make_contact_sheet.py` (§7.1 bước 4). **FAIL bất kỳ mục nào = sửa rồi lặp lại, không render tiếp cả sheet.**

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

⚠️ **Q11 chấm được bằng số, đừng chỉ nhìn.** Độ nghiêng thật của thân là góc cộng dồn
`pelvis + spine` (§6). Đo trên `char_thuy-b1` cho thấy `run` nghiêng **−6°** trong khi Q11 đòi 8–15°
— nhìn ảnh thì chỉ thấy "hơi thiếu lực", không ai đoán ra con số. Nguyên nhân: `CHIBI_GAINS` giảm
biên độ thân để `attack`/`die` khỏi gập gãy, nhưng `run` **không swing thân** — nó giữ một độ nghiêng
cố định, nên chỉ bị phạt mà không được lợi gì. Đã tách `ACTION_GAINS` riêng cho `run`
(`kage_text3d_kit.py`): `run` lên −11°, `attack` và `die` **không đổi một độ nào**.

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
   - Mesh **sinh từ Text→3D**: **không dùng Freestyle**. Trên mesh nhiều tóc, Freestyle vẽ contour cho *từng lọn* và mặt đầy nét đen rối. Dùng `add_inverted_hull_outline()` (shell lật normal, cull mặt trước). Kỹ thuật này còn có lợi thế chuyển thẳng sang GLSL runtime được. Độ dày **khác nhau giữa hai mesh cùng thế hệ**: pilot Hỏa đo được 3.0px@1x, nhưng chính con số đó phủ đen gần kín `char_thuy-b1` — mesh đó đo lại ra **1.2px@1x**. Vỏ shell phồng theo normal, nên mesh càng nhiều chi tiết mảnh (tóc, ngón) càng nhoè sớm. Đo từng con, kể cả trong cùng một thế hệ.

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

## 7. Vòng tự nghiệm thu của Sonnet (bắt buộc)

### 7.0 Vì sao vòng lặp này khác vòng lặp cũ

Thế hệ mesh kit dựng lại nguyên vẹn từ 1 dòng lệnh, nên mọi FAIL đều sửa được bằng cách đổi spec rồi
chạy lại. **Text→3D không như vậy**: mesh đến từ job cloud Rodin, chạy lại đúng prompt cũ ra model
khác ([BLENDER-SESSION-GUIDE §1.5](BLENDER-SESSION-GUIDE.md) bước D). Hệ quả chi phối cả mục này:

- **Có hai loại FAIL, chi phí lệch nhau hàng chục lần.** Phải phân loại trước khi sửa (§7.2).
- **Mesh đạt là tài sản.** Save + `pack_all()` **ngay khi mesh qua vòng A** (§7.1 bước 2), trước khi
  tiêu bất kỳ vòng chỉnh nào — hỏng tay ở bước rig không được kéo theo mất mesh.
- **Ngân sách vòng đếm riêng cho từng loại**, không còn một con số 3 duy nhất (§7.3).

### 7.1 Trình tự

Toàn bộ chạy headless — **không cần mở Blender, không cần MCP** (trừ bước 1, xem ghi chú).

**Bước 1 · Sinh mesh + chuẩn hoá + rig + action.** Theo [BLENDER-SESSION-GUIDE §1.5](BLENDER-SESSION-GUIDE.md)
bước A–C. Đây là bước duy nhất tiêu credit Rodin. Prompt bám spec trang phục ở
[ART-PIPELINE §2.3](ART-PIPELINE.md).

⚠️ **Chấm ở góc 3/4, không phải góc nghiêng** (đo thật 2026-08-06 khi làm `char_thuy-b1`). Pilot Hỏa
được duyệt trên `pilot_text3d_hoa_v2_goc_camera.png` — tức góc 3/4. Mesh Text→3D có khối tóc lớn, ở
góc nghiêng thuần nó che kín mặt và Q1–Q10 thành không chấm được: cùng một file Thủy đọc ra "một
mảng tối" ở 0° và hiện trọn mắt/lông mày/miệng ở 35°. `kage_sprite_render.py` nay **tự** dùng
`ADR031_VIEW_YAW_DEG = 35°` cho file nhánh 3D (nhận diện qua modifier `KageOutline`) và giữ 0° cho
asset sprite; ép tay bằng `--view-yaw`. ADR-031 để ngỏ góc runtime đúng như thiết kế — 35° ở đây chỉ
là **góc nghiệm thu**, không khoá gì trong game.

**Bước 2 · Chấm vòng A (chất lượng mesh) trên preview `idle`** — trước khi tinh chỉnh bất cứ thứ gì:

```bash
blender -b assets/raw/blender/characters/<hệ>-<bậc>.blend -P tools/blender/kage_sprite_render.py \
  -- --preview idle --out assets/raw/render-out/_review
```

Read PNG → chấm **Q1–Q10**, mỗi mục 1 dòng PASS/FAIL. Phân loại FAIL theo §7.2.
PASS vòng A → **save `.blend` kèm `bpy.ops.file.pack_all()` ngay**, rồi mới đi tiếp.

**Bước 3 · Vòng B (tinh chỉnh trong Blender).** Sửa tham số bước C rồi render lại đúng lệnh ở bước 2.
Không sinh mesh mới.

**Bước 4 · PASS Q1–Q10 → render cả bộ, ghép contact sheet, chấm Q11–Q12 trong _một_ lần xem:**

```bash
blender -b assets/raw/blender/characters/<hệ>-<bậc>.blend -P tools/blender/kage_sprite_render.py \
  -- --all-actions --out assets/raw/render-out/char_<hệ>-<bậc>
blender -b -P tools/blender/make_contact_sheet.py -- \
  --dir assets/raw/render-out/char_<hệ>-<bậc> \
  --frames idle_00 run_01 attack_00 attack_02 die_05 \
  --out assets/raw/render-out/_review/<hệ>-<bậc>.png --scale 2
```

Q11–Q12 FAIL là lỗi biên độ pose → `author_scaled_actions(rig, gains=...)`, thuộc vòng B.

**Bước 5 · PASS → ghi sổ + đóng phiên** theo [BLENDER-SESSION-GUIDE §1](BLENDER-SESSION-GUIDE.md)
bước 7. ⚠️ **Không còn bước hạ mẫu/pack atlas cho nhân vật** — từ [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md)
sản phẩm giao là **model glTF**, ảnh render chỉ là bằng chứng nghiệm thu. PASS xong thì chạy tiếp
**bước E** ([BLENDER-SESSION-GUIDE §1.5](BLENDER-SESSION-GUIDE.md)) — `export_character_lods.py` sinh
cả 3 tier `.glb` + sidecar `model-meta.json`.

> Bước 1 gọi REST API Rodin từ trong Blender. Chạy được headless, nhưng bước import
> (`import_generated_asset`) là tool MCP → **bước A–B cần phiên có MCP**; từ bước C trở đi headless.

### 7.2 Phân loại FAIL trước khi sửa — luật bắt buộc

Sửa sai tầng là cách tiêu credit nhanh nhất: sinh lại mesh vì một con số outline sai thì vừa mất
credit vừa mất luôn mesh đang có.

| FAIL ở | Nguyên nhân nằm ở | Vòng | Cách sửa |
|---|---|---|---|
| **Q1, Q2, Q3, Q6** (hình khối đầu/mặt/tóc/chi) | Hình học Rodin trả về | **A — sinh lại mesh** | Sửa prompt (§1.5 bước B: mô tả mặt phải cụ thể) rồi chạy lại job |
| **Q4, Q5** (mảng màu, phụ kiện trên thân) | Prompt thiếu spec trang phục | **A** | Trang phục nướng vào mesh nên chỉ sửa được bằng prompt ([ART-PIPELINE §2.3](ART-PIPELINE.md)) |
| **Q7** (vũ khí đọc được) | Mesh/vị trí vũ khí | **B — luôn luôn** | Vũ khí **không bao giờ** nằm trong prompt, nó là object rời: `attach_weapon(kind=...)`, chỉnh `offset`/`outline_px_at_1x` rồi render lại. Không tốn credit |
| **Q8** (cel shading) | `toon_textured(shades=)` | **B** | Đổi `shades`/`steps` |
| **Q9** (outline) | `add_inverted_hull_outline(width_px_at_1x=)` | **B** | Đo lại theo §4 mục 3, gọi **sau** `fit_camera` |
| **Q10** (đọc được ở @0.5x) | Tương phản/silhouette | **B trước, A sau** | Thử tăng tương phản + độ dày outline; vẫn FAIL thì là lỗi silhouette gốc → vòng A |
| **Q11, Q12** (biên độ pose) | `CHIBI_GAINS` | **B** | `author_scaled_actions(rig, gains=...)` |
| Tỷ lệ khớp sai, xoay ngược, trượt da | Tham số `rig()` | **B** | `measure(obj)` lại — **không** dùng hằng số của nhân vật khác |

Vòng B **không bao giờ** tiêu credit. Nếu định làm gì đó ở vòng A, ghi rõ trong ASSET-LOG là vì tiêu
chí Q nào — để lần sau biết prompt nào hỏng ở đâu.

### 7.3 Ngân sách vòng

| Vòng | Giới hạn | Chạm trần thì |
|---|---|---|
| **A — sinh mesh** | **3 job Rodin / nhân vật** | Dừng, ghi ASSET-LOG kèm ảnh + prompt của cả 3 lần, đánh dấu chờ Opus |
| **B — chỉnh trong Blender** | **3 vòng / mỗi mesh** | Dừng vòng B; nếu FAIL còn lại thuộc nhóm vòng A theo §7.2 thì đổi sang vòng A (vẫn trong ngân sách 3 job) |

**Không hạ chuẩn, không render cả sheet để "tạm dùng".** Mesh nào PASS vòng A rồi thì giữ `.blend`
đó lại kể cả khi sau đó sinh mesh khác tốt hơn — cho tới lúc đóng phiên mới xoá bản thua, vì bản
thắng không sinh lại được.

Kỷ luật token giữ nguyên [ART-PIPELINE §6.1](ART-PIPELINE.md): batch lệnh, preview trước sheet sau,
headless cho việc dài.

---

## 8. Phân công Sonnet ↔ Opus

| # | Việc | Ai | Trạng thái |
|---|---|---|---|
| 1 | `kage_mesh_kit.py` — toàn bộ hàm §5 | Opus | ✅ **Xong 2026-08-03**, verify bằng render thật |
| 2 | Rig 11 xương + 7 action có khuỷu/gối | Opus | ✅ **Xong** (trong kit) |
| 3 | Gold standard Hỏa B1 + ảnh tham chiếu | Opus | ❌ **Bị bác 2026-08-03** (~1/5 NSO) → thay bằng pilot Text→3D Hỏa, **đã duyệt cùng ngày** (`pilot_text3d_hoa_v2_*.png`) |
| 4 | Spec 5 hệ × 3 bậc | Opus | ✅ **Xong** — bảng `CHARACTERS` trong `build_kage_character.py`; giờ chỉ còn giá trị làm **spec trang phục/vũ khí** tham chiếu cho prompt Text→3D (§2.3 ART-PIPELINE) |
| 5 | ~~Re-render 15 sheet~~ → Sản xuất **14 nhân vật còn lại bằng Text→3D**, qua vòng §7, cập nhật ASSET-LOG | **Sonnet** | ✅ **Hết chặn 2026-08-05** — ngân sách tam giác đã đo ([ART-PIPELINE §4.4](ART-PIPELINE.md)), vũ khí chốt là gắn rời (§2.3), bước E đã code |
| 6 | Quái/NPC/boss mới về sau | Sonnet (boss khó → escalate) | Chờ #5 chạy ổn |
| 7 | VFX skill: giữ chuẩn hiện có (17 VFX đã ship không thuộc diện placeholder) | Sonnet | Không đổi |

**Sonnet bắt đầu từ #5.** Khi làm quái/NPC (#6) mà kit thiếu bộ phận (sừng, đuôi, 4 chân...) → **không sculpt tay**, báo lại để bổ sung hàm vào kit; kit là nơi giữ chất lượng đồng đều.

⚠️ **`.blend` nhân vật Text→3D BẮT BUỘC commit** — đảo ngược so với thế hệ mesh kit. Mesh đến từ job cloud Rodin, chạy lại prompt cũ ra model khác, nên `.blend` (đã `pack_all()`) là **bản duy nhất tồn tại**: mất là mất hẳn, không có "spec dict + kit" nào dựng lại được. Đổi lại chúng rất nặng (pilot Hỏa: 47 MB) — Git LFS đang bật cho `assets/raw/**` với quota hữu hạn, nên **chỉ commit bản đã PASS nghiệm thu §7**, xoá các bản thua trước khi đóng phiên. Chi tiết: [ART-PIPELINE §7](ART-PIPELINE.md).

⚠️ **File `.blend` thế hệ box vẫn còn trong git** (`<hệ>-pilot/-b2/-b3.blend`). Khi #5 chạy, chúng bị ghi đè nếu đặt trùng tên — chỉ làm việc đó **sau khi #3 được duyệt**, và biết rằng chúng dựng lại được bằng `build_box_character.py`.

---

## 9. Technical Decisions

| # | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| 1 | Benchmark chất lượng tối thiểu = Ninja School Online | Chủ dự án chốt 2026-08-02 | **Chốt** |
| 2 | Nghiệm thu bằng bảng PASS/FAIL đo trên PNG, AI tự chấm | Chuẩn cảm tính ("đẹp", "chibi") đã cho ra box — phải đo được | Đề xuất |
| 3 | Cấm box trần cho hình khối hữu cơ; dùng mặt tròn xoay | Nguyên nhân trực tiếp của "hình hộp lego"; cel shader không đòi mesh vuông | **Còn hiệu lực cho vũ khí/phụ kiện.** Cơ thể nay đến từ Text→3D nên vốn đã trơn |
| 4 | Thư viện `kage_mesh_kit.py`, Sonnet chỉ gọi + spec dict | Sculpt tự do tốn token và ra kết quả không đều | **Còn hiệu lực — chỉ cho vũ khí/phụ kiện/rig/action** (§5), không còn dựng cơ thể |
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
2. Sonnet: sản xuất 14 nhân vật còn lại + monster/NPC placeholder bằng Text→3D qua vòng §7, cập nhật `ASSET-LOG.md`, rồi chạy bước E. **Không còn bị chặn** từ 2026-08-05.
3. ✅ **Xong 2026-08-05** (bắt đầu 2026-08-03, đợt đầu còn sót): mọi con số outline rải rác đã trỏ về **một nguồn duy nhất** là §4 mục 3 của file này. Ba chỗ sót được tìm ra sau đó: bảng tra nhanh BLENDER-SESSION-GUIDE §3 (còn kê `1.1px` **và** Freestyle=Bật, sai với mesh Text→3D), bảng lỗi §4, và mục lịch sử §8 — nay đều đã sửa hoặc đánh dấu là số của thế hệ mesh cũ.
4. Thổ B3 dùng `topknot` nhưng render ra giống **nón lá** hơn búi tóc — hợp hệ Thổ nên tạm giữ, cần thì đổi tên style hoặc dựng lại khối búi.
5. Vũ khí gắn cố định ở hông/lưng, **không vào tay khi `attack`** (thế hệ box cũng vậy) — nếu muốn cầm vũ khí thật thì thêm bone `hand_r` + constraint, là việc riêng. Lưu ý: đây là câu hỏi *vũ khí gắn ở đâu*, đã tách khỏi câu hỏi *vũ khí có nằm trong prompt không* — cái sau chốt rồi ([ART-PIPELINE §2.3](ART-PIPELINE.md): luôn gắn rời).
6. ⚠️ **`hoa-text3d-pilot.blend` là một ngăn kéo tạp** — chứa 8 mesh của cùng nhân vật Hỏa (`Hoa_Raw` `Hoa_V2` `Hoa_V3` `Hoa_P3` `Hoa_Reg2` `Hoa_Detail` `Hoa_Pilot` `Hoa_D`), 3 rig, cộng cả `Elder_Body`/`Elder_Rig` của NPC khác. Chỉ **`Hoa_D` + `hoa_d_Rig`** là bản đã duyệt. Đây là lý do file nặng 47 MB, và §7.3 vốn đã yêu cầu "xoá bản thua trước khi đóng phiên" — chưa làm. Trước khi sản xuất 14 nhân vật còn lại, dọn file này, nếu không 15 nhân vật × ~47 MB sẽ ngốn hết quota Git LFS.
