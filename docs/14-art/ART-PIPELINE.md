# ART-PIPELINE.md

**Sản xuất đồ họa bằng Blender (AI-driven qua Blender MCP) → sprite 2D → cấu hình đồ họa client**

| | |
|---|---|
| Version | 0.1.0 |
| Status | Draft |
| Liên quan | [RESOURCE.md](../04-client/RESOURCE.md) · [RENDERING.md](../03-engine/RENDERING.md) · [GAME-DESIGN-DOCUMENT.md](../13-game-design/GAME-DESIGN-DOCUMENT.md) |

> Thao tác cụ thể → [BLENDER-SESSION-GUIDE.md](BLENDER-SESSION-GUIDE.md). Contract script → [tools/blender/README.md](../../tools/blender/README.md).

---

## 0. Kỷ luật token cho phiên làm art

Phiên làm art bằng Blender MCP **chỉ cần nạp**: tài liệu này + [tools/blender/](../../tools/blender/README.md). Không đọc docs khác (mọi ràng buộc liên quan đã được chưng cất vào §1).

---

## 1. Kết luận nền tảng (đọc 30 giây)

1. Game là **2D side-view, sprite frame-based** ([RENDERING.md §8](../03-engine/RENDERING.md)). Blender **không** render realtime trong game — Blender là **xưởng pre-render**: model 3D một lần, render ra sprite sheet 2D.
2. Side-view nên **chỉ render hướng nhìn phải**. Hướng trái = flip sprite trong code. Đây là khoản tiết kiệm lớn nhất toàn pipeline (½ số frame, ½ atlas).
3. Render **một bản master @2x** duy nhất → `tools/asset-pipeline` hạ mẫu ra @1x và @0.5x → **3 bộ atlas = 3 mức cấu hình đồ họa** (§5). Không render lại nhiều lần theo tier.
4. Ràng buộc bất biến kế thừa từ docs khác: atlas ≤ **2048×2048** (GPU Android cũ), **không** lighting engine runtime, **không** Spine/skeletal, `Animation` object dùng chung (Flyweight), particle bắt buộc pool.
5. Animation chạy "on twos" kiểu anime: **12 fps playback**, ít frame — vừa đúng phong cách vừa tiết kiệm atlas. Số fps này là **thuộc tính asset, không phải setting đồ họa**.

---

## 2. Art Direction

### 2.1 Trụ cột phong cách

| Trụ cột | Chuẩn | Nguồn tham chiếu |
|---|---|---|
| Tỷ lệ nhân vật | Chibi 2–2.5 đầu, đầu to, chi ngắn | Ninja School Online |
| Đường nét | Outline đen/đậm 2–3px (ở @1x), Freestyle | Ninja School Online |
| Màu | Cel 2–3 nấc (base + shadow + highlight), màu phẳng, bão hòa cao | Anime nói chung |
| Readability | Silhouette phải nhận ra được ở 64px (Low tier); mọi asset duyệt ở cả @0.5x | Nguyên tắc mobile |
| VFX skill | Kịch tính, aura/trail đậm, tương phản mạnh với nền; boss & bí cảnh tông tối hơn thế giới thường | The Eminence in Shadow |
| Bối cảnh | Làng ninja Á Đông, học viện, rừng tre, đền — parallax nhiều lớp | Ninja School + Naruto |

**Quy tắc vàng**: mọi asset phải hòa vào cùng một thế giới. Trước khi làm asset mới, so với asset đã duyệt trong `assets/raw/ASSET-LOG.md` (§7) — lệch phong cách thì sửa asset mới, không sửa asset cũ.

### 2.2 Palette theo 5 hệ phái

Khớp [GDD §4](../13-game-design/GAME-DESIGN-DOCUMENT.md) (Hỏa/Thủy/Phong/Lôi/Thổ). Dùng cho trang phục đặc trưng hệ, VFX skill, UI đánh dấu hệ.

| Hệ | Chính | Phụ | VFX ghi chú |
|---|---|---|---|
| Hỏa | `#E8552D` | `#FFB13D` | Lửa dạng lưỡi liềm, ember bay lên, ánh cam ở rìa |
| Thủy | `#2D7FE8` | `#7FD4FF` | Sóng/giọt, ripple tròn, trong mờ nhẹ |
| Phong | `#3DBF7A` | `#CFF5E0` | Vệt gió xoáy, lá cuốn, trail dài theo hướng di chuyển |
| Lôi | `#8C4DE8` | `#FFE84D` | Tia zigzag sắc, flash 1–2 frame, afterimage |
| Thổ | `#A6763D` | `#E0C089` | Khối đá vỡ, bụi thấp sát đất, rung nặng |

### 2.3 Style sheet 5 hệ phái

**Skill code trong bảng dưới là code thật** trong `server/server-world/src/main/java/dev/kage/server/world/SkillRegistry.java` — không phải tên đặt cho vui. Quy tắc đặt tên VFX: **`vfx_<skill_code>`** (ví dụ `vfx_loi_thien_loi`), nên asset map 1-1 với skill, không cần bảng tra riêng.

3 bậc trang bị dùng chung tên gọi: **B1 Học Viên** → **B2 Nhẫn Giả** → **B3 Thượng Nhẫn**. Quy tắc leo bậc chung cho mọi hệ: B1 vải thô/ít phụ kiện, B2 thêm giáp mảnh + khăn/đai màu hệ, B3 thêm 1 chi tiết *chuyển động* (đuôi khăn dài, vòng nguyên tố bay quanh) — đây là thứ khiến người chơi nhìn phát biết "đồ xịn" ngay cả ở @0.5x.

---

#### 🔥 Hỏa — *sát thương liên tục, bùng nổ*

- **Trang phục** — B1: áo khoác ngắn nâu đất, băng tay đỏ. B2: giáp vai trái đơn, khăn quàng `#E8552D` dài ngang hông. B3: áo choàng cụt tay rìa cháy sém, khăn dài phất, 3 đốm lửa `#FFB13D` lơ lửng sau lưng.
- **Vũ khí**: kunai lưỡi rộng ám khói + cuộn giấy phù chú đeo hông.
- **Skill thường `fireball`** — cast: lùi nửa bước, tay kết ấn, quầng cam nhỏ ở lòng bàn tay (2 frame). Bay: cầu lửa lưỡi liềm kéo đuôi ember. Impact: nở hoa lửa 3 frame + tàn lửa bay **lên** (particle runtime, không render sẵn).
- **Ultimate**: *chưa có trong `SkillRegistry`* — Hỏa mới chỉ có `fireball` (MVP đi 1 hệ trước, [GDD](../13-game-design/GAME-DESIGN-DOCUMENT.md)). Khi thêm ult mới làm VFX, **đừng làm trước**.
- **Prompt 3D**: `chibi ninja boy, 2.5 heads tall, big head, short limbs, sleeveless burnt-edge coat, long scarf, arm wraps, toon style, flat colors, side profile facing right, T-pose, low poly, clean topology`

#### 💧 Thủy — *khống chế, hồi phục*

- **Trang phục** — B1: áo ngắn xanh nhạt, quần lửng, chân trần quấn băng. B2: giáp ngực mỏng `#2D7FE8`, tay áo rộng buông. B3: áo choàng nước rìa gợn sóng, vòng nước `#7FD4FF` xoay chậm quanh cổ tay.
- **Vũ khí**: bình hồ lô đeo lưng + dải lụa nước (không phải kiếm — Thủy là hệ hỗ trợ).
- **Skill thường `thuy_hoi_phuc`** (mục tiêu ALLY) — cast: chắp tay, cúi nhẹ. Impact: **cột nước dịu dâng từ chân lên** đồng đội, kèm vòng ripple ở mặt đất, tông `#7FD4FF` trong mờ. Tránh dùng màu xanh lá — dễ nhầm hệ Phong.
- **Ultimate `thuy_thuy_trieu`** (ENEMY) — cast dài 4 frame: nước dâng quanh chân người thi triển. Impact: **sóng lớn quét ngang màn hình** từ trái sang phải, đối thủ trong sóng bị đẩy lùi; kết bằng màn nước rơi + ripple đọng 2 frame.
- **Prompt 3D**: `chibi ninja, 2.5 heads tall, flowing wide-sleeve robe, gourd flask on back, cloth wraps on bare feet, toon style, flat colors, side profile facing right, T-pose, low poly`

#### 🌪 Phong — *tốc độ, bạo kích*

- **Trang phục** — B1: áo bó sát xanh lục nhạt, ống tay ngắn. B2: áo gile `#3DBF7A` + đai lưng dài bay ngược chiều chạy. B3: khăn choàng mỏng `#CFF5E0` xẻ tà, 2 dải lụa bay tự do — bậc này phải thấy "gió" ngay cả khi đứng yên.
- **Vũ khí**: song đao ngắn cong (đánh nhanh 2 tay).
- **Skill thường `phong_gia_toc`** (buff SELF) — cast: dậm chân, luồng gió xoáy `#3DBF7A` bốc từ chân lên. Trong lúc buff: **afterimage mờ** phía sau khi di chuyển (làm bằng code — vẽ lại sprite cũ với alpha giảm, **không** render frame riêng, tiết kiệm cả atlas lẫn công).
- **Ultimate `phong_loc_xoay`** (ENEMY) — cast: xoay người 1 vòng. Impact: **cột lốc xoáy** dựng tại vị trí mục tiêu, lá cuốn theo xoắn ốc, kéo đối thủ vào giữa; tan dần từ dưới lên.
- **Prompt 3D**: `chibi ninja, 2.5 heads tall, tight sleeveless top, long flowing sash, twin short curved blades, dynamic light armor, toon style, flat colors, side profile facing right, T-pose, low poly`

#### ⚡ Lôi — *đơn mục tiêu, xuyên giáp*

- **Trang phục** — B1: áo tối màu, băng đô vàng `#FFE84D`. B2: giáp vai đôi `#8C4DE8` góc cạnh, găng tay dài. B3: áo choàng tím sẫm rìa răng cưa, **tia điện nhỏ chạy dọc viền giáp** (2 frame lặp).
- **Vũ khí**: trường đao thẳng lưỡi mảnh (hệ xuyên giáp — hình dáng phải "sắc", không tù).
- **Skill thường `loi_tia_set`** (ENEMY) — cast: chỉ tay, tụ sáng ở đầu ngón (1 frame). Impact: **tia zigzag `#FFE84D` đánh thẳng xuống mục tiêu**, kèm **flash trắng toàn màn 1 frame** rồi tắt hẳn — độ giật là bản sắc hệ Lôi, không kéo dài.
- **Ultimate `loi_thien_loi`** (ENEMY) — cast: giơ tay lên trời, mây tối tụ trên đầu 3 frame (báo hiệu để đối thủ né — luật thiết kế PvP: ult phải đọc được). Impact: **cột sét khổng lồ giáng thẳng**, chớp 2 lần, để lại vệt cháy tím `#8C4DE8` mờ dần trên đất.
- **Prompt 3D**: `chibi ninja, 2.5 heads tall, dark hooded jacket, angular shoulder armor, long straight thin blade, headband, toon style, flat colors, side profile facing right, T-pose, low poly`

#### ⛰ Thổ — *phòng thủ, khiên*

- **Trang phục** — B1: áo thô nâu `#A6763D`, giáp da vai. B2: giáp ngực dày, tấm che đùi, khăn đầu be `#E0C089`. B3: giáp đá khối, **mảnh đá nhỏ lơ lửng quanh vai** — hệ Thổ phải trông **nặng nhất**: bóng đổ dày hơn, chân bám đất rộng hơn.
- **Vũ khí**: chuỳ đá lớn hoặc găng chiến (không dùng vũ khí mảnh).
- **Skill thường `tho_khien_da`** (buff SELF) — cast: dậm chân mạnh, **3 phiến đá trồi lên từ mặt đất** dựng thành khiên trước mặt; giữ nguyên trong suốt thời gian buff, nứt dần theo lượng damage đã chặn (2 mức nứt là đủ).
- **Ultimate `tho_dia_chan`** (ENEMY) — cast: nhảy lên rồi giáng cả người xuống. Impact: **vết nứt lan trên mặt đất** theo chiều ngang + bụi thấp sát đất + **rung camera** (camera shake là hiệu ứng code, thuộc tier Medium/High §5, tier Low tắt).
- **Prompt 3D**: `chibi ninja, 2.5 heads tall, heavy stone plate armor, bulky shoulders, thick leather chest guard, large stone mace, toon style, flat colors, side profile facing right, T-pose, low poly`

---

⚠️ **Prompt 3D chỉ ra base mesh thô.** Model từ Hyper3D/Hunyuan luôn ra kiểu bán-tả-thực → bắt buộc toon hoá lại bằng `apply_toon()` và chỉnh tỷ lệ về chibi trước khi render ([BLENDER-SESSION-GUIDE §1](BLENDER-SESSION-GUIDE.md) bước 2). Đừng render thẳng.

---

## 3. Kiến trúc pipeline: Blender pre-render

### 3.1 Vì sao pre-render thay vì vẽ tay từng frame

| Lợi ích | Giải thích |
|---|---|
| 1 model → N output | Mọi animation, mọi scale render tự động từ 1 nguồn |
| Đổi trang bị rẻ | Thay mesh phụ kiện, re-render — không vẽ lại toàn bộ frame |
| Nhất quán | Cùng rig/camera/light → mọi nhân vật cùng góc nhìn, cùng tỷ lệ |
| AI tự động hóa được | Toàn pipeline là script Python (bpy), Claude chạy qua MCP không cần bàn tay họa sĩ |

### 3.2 Sơ đồ (thay bước 1–2 của [RESOURCE.md §3](../04-client/RESOURCE.md))

```
assets/raw/blender/*.blend  ── kage_sprite_render.py ──►  assets/raw/render-out/<asset>/<action>_##.png  (master @2x)
        │                                                            │
   (source of truth,                                                 ▼
    commit vào git)                    tools/asset-pipeline: hạ mẫu @1x + @0.5x, validate naming,
                                       pack 3 bộ atlas ≤2048, sinh manifest
                                                            │
                                                            ▼
                                       assets/exported/atlases/{low,med,high}/... → ResourceManager
```

### 3.3 Thiết lập kỹ thuật Blender (chuẩn hóa trong script, không làm tay)

- **Renderer**: EEVEE, `film_transparent = True`.
- **Toon**: Shader-to-RGB + ColorRamp (Constant, 2–3 nấc). **Freestyle** cho outline.
- **Camera**: Orthographic, đặt trên trục −Y nhìn vào +Y (nhân vật quay mặt +X = hướng phải).
- **Light**: 1 Sun key (trên-trước, ~30°) + 1 fill yếu. Không HDRI, không GI — toon không cần.
- Mọi thứ trên nằm sẵn trong **file base dùng chung** `kage_character_base.blend` (§7) — mỗi asset mới mở từ base, không dựng lại.

---

## 4. Chuẩn kỹ thuật asset

### 4.1 Kích thước (master @2x → game @1x → Low @0.5x)

| Loại | Master @2x | @1x | @0.5x |
|---|---|---|---|
| Nhân vật / NPC / quái thường | 256×256 | 128 | 64 |
| Boss / World Boss | 512×512 | 256 | 128 |
| VFX skill (1 ô) | 384×384 | 192 | 96 |
| Icon (skill/item/buff) | 128×128 | 64 | 32 |
| Tile/prop môi trường | theo lưới map, bội số 32 @1x | — | — |

### 4.2 Bộ animation chuẩn cho nhân vật/quái (playback 12 fps)

| Action | Frame | Ghi chú |
|---|---|---|
| idle | 4 | loop |
| run | 6 | loop |
| attack | 5 | frame 3 = impact (khớp hitbox timing server) |
| skill_cast | 6 | có thể giữ frame cuối khi channel |
| hit | 2 | kèm tint flash trong code, không render riêng bản trắng |
| jump | 4 | lên 2 / xuống 2 |
| die | 6 | frame cuối nằm yên, không loop |

→ 1 nhân vật ≈ 33 frame @1x 128px ≈ chiếm ~1/4 atlas 2048 — trong ngân sách.

### 4.3 Quy tắc chung

- Naming theo [RESOURCE.md §4](../04-client/RESOURCE.md): `char_ninja-fire_idle.png`, atlas theo zone.
- **Pivot = chân, giữa** (mọi sprite nhân vật đứng trên cùng baseline).
- Chừa lề trong khung để frame animation không bị cắt (tay vung, tóc bay); padding pack 2px.
- Chỉ render **hướng phải** (§1.2).

---

## 5. Cấu hình đồ họa client (3 mức)

⚠️ **Bảng tier kiểu 3D (shadow/water reflection/GI/SSR/DoF/texture 4096) KHÔNG áp dụng cho game này**: đây là 2D side-view sprite — không có shadow map, không có mặt nước 3D, không có GI; lighting engine đã bị từ chối có chủ đích ([RENDERING.md](../03-engine/RENDERING.md)); atlas 4096 vượt trần phần cứng 2048; và animation fps là thuộc tính asset (§1.5), không phải setting. Bảng đúng cho 2D:

| Thành phần | Low | Medium | High |
|---|---|---|---|
| Bộ atlas | @0.5x | @1x (**đóng gói sẵn trong bản cài**) | @1x + hiệu ứng |
| Texture filter | Linear | Linear | Linear + mipmap khi camera zoom |
| Particle đồng thời (cap toàn màn) | 64 | 256 | 1024 |
| VFX skill người chơi khác | ẩn (giữ của mình + boss) | chỉ trong khung hình | tất cả |
| Lớp parallax nền phụ + ambient anim (lá rơi, đom đóm...) | tắt | bật | bật + mật độ cao |
| Camera effect (shake, hit-flash, vignette) | tối thiểu | bật | bật |
| Post-processing (FBO) | off | off | bloom nhẹ (chỉ desktop) |
| FPS mục tiêu | 30 | 60 | 60 + vsync |

Nguyên tắc thiết kế tier:
- **Tier chỉ tồn tại phía client** — server không biết, không validate, gameplay không đổi (Server Authority không bị đụng).
- Đổi tier = reload zone (đổi bộ atlas) — chấp nhận được, không cần hot-swap texture. **Chỉ MEDIUM được đóng gói trong bản cài**; đổi sang LOW/HIGH thì client tải bộ atlas đó về trước ([ADR-028](../01-architecture/adr/ADR-028-Graphics-Tier-Asset-Delivery.md)).
- Cap particle là cap **pool size** — Low không tạo rồi vứt, mà không tạo từ đầu.
- Code dự kiến: `enum GraphicsQuality { LOW, MEDIUM, HIGH }` trong `client-core`, lưu settings local, `ResourceManager.loadZone()` chọn thư mục atlas theo tier. **Chưa code ở phase hiện tại** — ghi ở §11.

---

## 6. Quy trình phiên làm việc AI + Blender MCP

### 6.1 Bảy quy tắc tiết kiệm token

1. **Logic lặp lại nằm trong repo, không nằm trong chat**: mọi thao tác chuẩn (setup scene, toon material, fit camera, render action) là hàm trong `tools/blender/kage_sprite_render.py`. Qua MCP chỉ gửi `exec(open(r'<repo>/tools/blender/kage_sprite_render.py').read())` rồi gọi hàm với tham số — không bao giờ paste lại script dài.
2. **Batch**: gộp nhiều bước vào 1 lần `execute_blender_code`. Mỗi round-trip MCP là token.
3. `get_scene_info` **chỉ 1 lần đầu phiên**; `get_object_info` chỉ khi debug lỗi cụ thể.
4. **Screenshot đúng 3 checkpoint**: (a) sau khi dựng/import model xong, (b) sau render preview 1 frame, (c) nghiệm thu cuối. Không screenshot sau mỗi lệnh.
5. **Preview trước, render sheet sau**: render 1 frame duy nhất để duyệt phong cách; chỉ khi đạt mới render cả bộ animation.
6. **Ghi sổ thay vì mở lại file**: xong asset nào ghi 1 dòng vào `assets/raw/ASSET-LOG.md` (tên, .blend nguồn, action đã render, ngày). Phiên sau đọc log, không mở .blend để "xem lại".
7. Job render dài (cả bộ animation) → chạy **headless qua CLI** (`blender -b file.blend -P script.py`) bằng Bash tool ở background, không chiếm phiên MCP.

### 6.2 Workflow chuẩn: 1 nhân vật mới

| Bước | Công cụ | Checkpoint |
|---|---|---|
| 1. Mở `kage_character_base.blend`, load script repo | MCP `execute_blender_code` | — |
| 2. Dựng/import mesh (xem 6.3), toon hóa, gắn vào rig base | MCP (batch) | screenshot (a) |
| 3. Làm/điều chỉnh action theo bộ chuẩn §4.2 | MCP (batch) | — |
| 4. `render_preview()` 1 frame idle | MCP | screenshot (b) → **duyệt style** |
| 5. `render_all_actions()` master @2x | Bash headless (background) | — |
| 6. Chạy asset-pipeline hạ mẫu + pack | Bash | validate pass |
| 7. Ghi `ASSET-LOG.md`, save .blend | MCP + Edit | screenshot (c) |

### 6.3 Nguồn mesh — chọn theo bảng

| Nguồn | Dùng khi | Lưu ý |
|---|---|---|
| Model bằng script (bpy primitives) | Props, vũ khí, tile, VFX mesh đơn giản | Rẻ nhất, ưu tiên |
| Hyper3D / Hunyuan3D (text→3D) | Base mesh nhân vật/quái phức tạp | Ra mesh photoreal-ish → **phải** toon hóa lại (retopo nếu quá dày, apply toon material, chỉnh tỷ lệ về chibi) |
| Sketchfab | Có model hợp sẵn | **Chỉ license CC0/CC-BY** (dự án ưu tiên free-to-use); ghi attribution vào ASSET-LOG |
| PolyHaven | ❌ Tránh — texture/HDRI photoreal, không hợp toon và toon không cần HDRI | — |

➡️ **Lệnh cụ thể từng bước, checklist nghiệm thu 4 loại asset, và bảng lỗi thường gặp**: [BLENDER-SESSION-GUIDE.md](BLENDER-SESSION-GUIDE.md).

---

## 7. Tổ chức file & version control

```
assets/raw/blender/                  # .blend nguồn — COMMIT (source of truth), KHÔNG đóng gói vào game
├── kage_character_base.blend        # base chung: rig + camera + light + toon material chuẩn
├── characters/  ├── monsters/  ├── effects/  └── props/
assets/raw/render-out/               # PNG master @2x — KHÔNG commit (sinh lại được), .gitignore
assets/raw/ASSET-LOG.md              # sổ asset — COMMIT
```

- `.blend` giữ **nhỏ** (không pack texture thừa, purge orphan trước khi save). Nếu file nào vượt ~25MB → dừng, cân nhắc Git LFS (quota free tier có hạn — quyết định treo §10).
- `assets/raw/` vốn đã bị loại khỏi bản build theo [RESOURCE.md §2](../04-client/RESOURCE.md) — cấu trúc trên không đổi luật đó.

---

## 8. Asset Registry trong DB (phục vụ trang quản trị)

**Yêu cầu chủ dự án (2026-08-01)**: mọi vật phẩm đồ họa phải được quản lý qua DB để sau này làm trang quản trị.

### 8.1 Nguyên tắc: DB lưu *metadata*, không lưu *binary*

| Lưu trong DB (bảng `art_asset`) | KHÔNG lưu trong DB |
|---|---|
| asset_key, loại, hệ, trạng thái duyệt, đường dẫn nguồn, bộ action, license/attribution, checksum | File PNG/atlas/.blend (binary) |

Lý do: client nhận asset qua **patch manifest** ([RESOURCE.md §7](../04-client/RESOURCE.md)), không bao giờ nối DB; nhét blob vào Postgres chỉ làm phình DB/backup mà không ai đọc. Trang quản trị đọc metadata từ DB và **preview ảnh bằng đường dẫn** trỏ vào `assets/exported/`.

### 8.2 Bảng đề xuất (⚠️ DB schema change — chỉ tạo Flyway migration khi chủ dự án duyệt thiết kế này)

**Danh mục là bảng riêng, phân cấp 2 tầng** — không dồn tất cả vào 1 cột enum: trang quản trị lọc/duyệt theo cây danh mục, và thêm loại mới không cần migration.

```sql
CREATE TABLE art_category (
    id        BIGSERIAL PRIMARY KEY,
    code      VARCHAR(32) NOT NULL UNIQUE,   -- 'monster-boss', 'vfx-skill'
    name_vi   VARCHAR(64) NOT NULL,          -- hiển thị trên trang quản trị
    parent_id BIGINT REFERENCES art_category (id),  -- NULL = tầng gốc; tối đa 2 tầng
    sort_order SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE art_asset (
    id           BIGSERIAL PRIMARY KEY,
    asset_key    VARCHAR(64)  NOT NULL UNIQUE,          -- 'char_ninja-fire', 'icon_kunai' (khớp naming RESOURCE.md §4)
    category_id  BIGINT       NOT NULL REFERENCES art_category (id), -- luôn gắn vào tầng LÁ (tầng 2)
    element      VARCHAR(8),                            -- HOA/THUY/PHONG/LOI/THO, NULL nếu không thuộc hệ
    status       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT', -- DRAFT → APPROVED → EXPORTED → DEPRECATED
    -- === Đường dẫn: BẮT BUỘC, xem §8.5 ===
    blend_path   VARCHAR(255),                          -- nguồn: assets/raw/blender/characters/ninja-fire.blend
    render_dir   VARCHAR(255),                          -- master @2x: assets/raw/render-out/char_ninja-fire
    preview_url  VARCHAR(255),                          -- ảnh cho trang quản trị: previews/char_ninja-fire.png
    atlas_name   VARCHAR(64),                           -- atlas chứa asset này: 'academy-hub' (đường dẫn từng tier suy ra theo §8.5)
    actions      JSONB,                                 -- {"idle":4,"run":6,...} theo §4.2
    master_px    SMALLINT,                              -- 256/512/384/128 theo §4.1
    license      VARCHAR(16)  NOT NULL DEFAULT 'ORIGINAL', -- ORIGINAL/CC0/CC_BY (nguồn §6.3)
    attribution  VARCHAR(255),                          -- bắt buộc khi license = CC_BY
    checksum     VARCHAR(80),                           -- sha256 của master render, khớp asset-manifest
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_art_asset_category ON art_asset (category_id, status);
```

**Cây danh mục seed** (migration seed sẵn, admin chỉnh sau):

| Tầng 1 | Tầng 2 (asset gắn vào đây) |
|---|---|
| `character` | `character-player` · `character-npc` |
| `monster` | `monster-normal` · `monster-elite` · `monster-boss` · `monster-world-boss` |
| `vfx` | `vfx-skill` · `vfx-hit` · `vfx-aura-buff` · `vfx-ambient` |
| `icon` | `icon-item` · `icon-skill` · `icon-buff` |
| `environment` | `env-tile` · `env-prop` · `env-parallax` |
| `ui` | `ui-frame` · `ui-button` · `ui-misc` |

Quy tắc: asset **luôn gắn tầng lá**; tầng 1 chỉ để gom nhóm/lọc. Giữ tối đa 2 tầng — sâu hơn là khó duyệt chứ không dễ hơn. Prefix của `asset_key` (`char_`, `icon_`, `vfx_`...) phải khớp tầng 1 — `tools/asset-pipeline` validate điều này khi upsert.

### 8.3 Luồng dữ liệu & liên kết

- **Ai ghi**: `tools/asset-pipeline` (bước cuối) **upsert** `art_asset` từ ASSET-LOG + manifest — pipeline là nguồn sự thật kỹ thuật; trang quản trị chỉ sửa phần con người quyết (`status`, `attribution`, metadata mô tả).
- **Liên kết gameplay**: theo convention `item_template.code = 'kunai'` ↔ `art_asset.asset_key = 'icon_kunai'` (soft-link, không FK — asset có thể làm trước/sau item). Nếu sau này cần FK cứng (`item_template.icon_asset_key`) → schema change riêng, hỏi lại.
- **ASSET-LOG.md vẫn giữ** (§7): là sổ tay token-rẻ cho phiên AI; DB là nguồn chuẩn cho trang quản trị. Pipeline đồng bộ log → DB nên hai bên không lệch.
- **Trang quản trị** (tương lai, [ADMIN.md](../12-devtools/ADMIN.md)): duyệt theo **cây danh mục** (tầng 1 gom nhóm → tầng 2 danh sách asset), lọc thêm theo element·status, duyệt DRAFT→APPROVED, quản lý CRUD danh mục (thêm tầng 2 mới không cần migration), xem attribution để tuân thủ license, preview ảnh.

### 8.4 Lưu file asset: **nginx đọc từ đĩa**

Chốt 2026-08-01: file tĩnh (atlas, manifest, ảnh preview) phục vụ bằng **nginx đọc thẳng từ đĩa**. Không thêm object storage — tức **không thêm dependency hạ tầng nào**, không cần ADR.

| Nhu cầu | Giải pháp |
|---|---|
| Phát patch cho client | nginx serve `assets/exported/` với `Cache-Control: public, immutable` — an toàn vì tên file patch đã có content hash ([RESOURCE.md §7](../04-client/RESOURCE.md)) |
| Preview ảnh cho trang quản trị | Cùng nginx đó, thêm `location` riêng có kiểm tra auth của admin |
| Kho `.blend` + master render @2x | Thư mục ngoài git + backup định kỳ (restic/rsync) |
| Khi bandwidth lớn | Đặt **CDN trước nginx**; nếu lên cloud thì đi thẳng **Cloudflare R2** (egress = 0đ, hợp phát patch game) |

Client **không** import SDK lưu trữ nào — chỉ tải qua URL HTTP trong manifest, đúng luật "gameplay chỉ phụ thuộc abstraction" ([/CLAUDE.md §5](../../CLAUDE.md)). Nhờ vậy đổi origin (nginx → CDN → R2) chỉ là đổi base URL trong manifest, không sửa code.

Cột `storage_url VARCHAR(255)` trong `art_asset` giữ đường dẫn **tương đối** (ví dụ `atlases/high/academy-hub.atlas`) — không nhúng hostname, để đổi origin không phải sửa DB.

> **Đã cân nhắc và loại: MinIO** (và object storage nói chung). Ba lý do, ghi lại để khỏi đề xuất lại:
> 1. **Không nhanh hơn.** MinIO cũng ghi object xuống filesystem rồi cộng thêm tầng HTTP + S3 protocol + chữ ký SigV4 + tra metadata. nginx đọc file bằng `sendfile()` zero-copy — cùng một byte, MinIO làm nhiều việc hơn.
> 2. **Community edition đã archive 2026-04-25**, ngừng phát hành binary dựng sẵn, và tính năng quản trị đã bị gỡ khỏi Console community từ 5/2025. Vẫn free (AGPLv3) nhưng **không còn bản vá bảo mật** — không nên nằm trên đường phát hành file cho client.
> 3. Lý lẽ "S3-compatible để sau đổi cloud dễ" **không áp dụng**: patcher dùng URL HTTP thường, không dùng S3 SDK, nên vốn đã không có lock-in để chống.
>
> Chỉ quay lại bài toán này nếu thật sự cần **nhiều máy cùng đọc/ghi một kho**; khi đó xét cả Garage/SeaweedFS, và bắt buộc có ADR trước khi wire.

### 8.5 Đường dẫn của **mọi** asset bắt buộc vào DB

**Luật (chủ dự án yêu cầu 2026-08-01)**: vẽ xong asset nào là đường dẫn asset đó phải nằm trong DB — không có asset "mồ côi" chỉ tồn tại trên đĩa. Đây là điều kiện để trang quản trị quản được toàn bộ kho đồ họa.

⚠️ **Một asset ≠ một file.** Sau [ADR-028](../01-architecture/adr/ADR-028-Graphics-Tier-Asset-Delivery.md), mỗi asset được pack vào atlas **riêng cho từng tier**, nên không lưu được "một đường dẫn" duy nhất. Cách giải: lưu 4 mốc, phần còn lại **suy ra theo quy ước**.

| Cột | Ví dụ | Ý nghĩa |
|---|---|---|
| `blend_path` | `assets/raw/blender/characters/ninja-fire.blend` | Nguồn để render lại |
| `render_dir` | `assets/raw/render-out/char_ninja-fire` | Master @2x (không ship) |
| `preview_url` | `previews/char_ninja-fire.png` | **Ảnh rời cho trang quản trị** |
| `atlas_name` | `academy-hub` | Atlas chứa asset này |

Đường dẫn phát hành từng tier **suy ra**, không lưu cứng:
`atlases/{low|med|high}/{atlas_name}.atlas` (+ file `.png` cùng tên)

Suy ra thay vì lưu cứng để đổi tier hoặc đổi origin không phải cập nhật hàng loạt dòng DB.

💡 **`preview_url` là để tối ưu danh sách, không phải vì bị giới hạn.** Xem §8.6 — trang quản trị xem được **toàn bộ** asset, kể cả animation chạy thật.

**Thi hành — không dựa vào trí nhớ con người**: bước cuối của `tools/asset-pipeline` **upsert** mọi asset vào `art_asset`, và **fail build nếu có file trong `assets/exported/` không truy ra được dòng DB tương ứng**. `ASSET-LOG.md` chỉ là sổ tay token-rẻ cho phiên AI; **DB là nguồn chuẩn** cho trang quản trị.

### 8.6 Trang quản trị xem được những gì

**Không bị giới hạn ở ảnh tĩnh.** File `.atlas` của libGDX là **text thuần, đọc được**, không phải định dạng nhị phân đóng:

```
academy-hub.png
size: 2048, 2048
char_ninja-fire_idle
  bounds: 2, 2, 128, 128
  index: 0
char_ninja-fire_idle
  bounds: 132, 2, 128, 128
  index: 1
```

Nên trang quản trị **tự cắt được mọi vùng** bằng `canvas.drawImage(img, sx, sy, sw, sh, ...)` hoặc CSS `background-position`. Từ đó làm được:

| Xem được | Cách |
|---|---|
| **Từng frame** của mọi action | Parse `.atlas`, lọc region theo tiền tố `<asset_key>_<action>`, sắp theo `index` |
| **Animation chạy thật** | Lần lượt vẽ các frame theo `setInterval` **12 fps** (`PLAYBACK_FPS`, §1.5) — đúng tốc độ trong game |
| **Toàn bộ atlas** kèm khung viền từng region | Vẽ ảnh atlas gốc + overlay các `bounds` |
| **So sánh 3 tier cạnh nhau** | Cùng `atlas_name`, đổi thư mục `low`/`med`/`high` (§8.5) |
| **Kiểm tra baseline/pivot** | `render-meta.json` có `pivot_x_px` + `pivot_y_px_from_bottom` — kẻ 1 đường ngang là thấy chân có thẳng hàng không |
| Danh sách 200 asset | Dùng `preview_url` (thumbnail nhẹ) — **đừng** tải atlas 4MB cho mỗi ô lưới |

**Quy ước đặt tên region — bắt buộc để cả game lẫn trang quản trị đều tra được**: pack với region name `<asset_key>_<action>` + `index` tăng dần (`char_ninja-fire_idle` index 0..3). Đây là dạng libGDX gốc: game gọi `atlas.findRegions("char_ninja-fire_idle")` là ra đúng mảng frame cho `Animation`; trang quản trị lọc cùng tiền tố đó. Nếu pack thành region rời tên `..._idle_00` thì `findRegions()` **không** gom được — hỏng cả hai phía.

⚠️ **Không copy toạ độ region vào DB.** File `.atlas` đã là nguồn sự thật và đọc được bằng máy; nhân bản vào DB chỉ tạo thêm chỗ để lệch. DB giữ *asset là gì*, `.atlas` giữ *nó nằm đâu trong ảnh*.

---

## 9. Technical Decisions

| # | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| 1 | Blender **pre-render** ra sprite, không realtime 3D | Game 2D side-view; tái dùng model; AI tự động hóa được | Đề xuất |
| 2 | Chỉ render hướng phải, trái = flip code | ½ frame, ½ atlas | Đề xuất |
| 3 | Master @2x duy nhất → downscale @1x/@0.5x tại asset-pipeline | Render 1 lần, 3 tier atlas | Đề xuất |
| 4 | 3 mức đồ họa LOW/MEDIUM/HIGH, chỉ tồn tại client-side | Bảng §5; không đụng Server Authority | Đề xuất |
| 5 | Animation 12fps on-twos, số frame cố định theo asset | Phong cách anime + ngân sách atlas; không phải setting | Đề xuất |
| 6 | Script chuẩn trong `tools/blender/`, MCP chỉ gọi hàm | Kỷ luật token §6.1 | Đề xuất |
| 7 | DB lưu **metadata** asset (bảng `art_asset`), binary ở file/patch | §8 — trang quản trị cần registry, client không nối DB | Đề xuất (chủ dự án đã yêu cầu có DB, thiết kế bảng chờ duyệt) |
| 8 | Danh mục = bảng `art_category` phân cấp 2 tầng, asset gắn tầng lá | Chủ dự án yêu cầu chia category rõ ràng; thêm loại không cần migration | Đề xuất |
| 9 | **nginx đọc đĩa** phục vụ file tĩnh; không dùng object storage | §8.4 — nhanh hơn (`sendfile()` zero-copy), không thêm dependency, patcher vốn dùng URL HTTP nên không có lock-in để chống | **Chốt** 2026-08-01 (chủ dự án loại MinIO) |
| 10 | VFX đặt tên `vfx_<skill_code>` khớp `SkillRegistry` | Asset map 1-1 với skill thật, không cần bảng tra | Đề xuất |
| 11 | Mọi asset **bắt buộc** có dòng trong `art_asset` kèm đường dẫn; pipeline fail build nếu thiếu | §8.5 — chủ dự án yêu cầu quản toàn bộ kho đồ họa qua trang quản trị; ràng buộc bằng công cụ, không bằng trí nhớ | Đề xuất |
| 12 | Pipeline xuất thêm **thumbnail rời** mỗi asset (`preview_url`) | §8.6 — chỉ để danh sách nhiều asset không phải tải atlas 4MB mỗi ô; **không** phải vì bị giới hạn hiển thị |
| 13 | Region trong atlas đặt tên `<asset_key>_<action>` + `index` (dạng libGDX gốc) | §8.6 — `atlas.findRegions()` gom được frame cho `Animation`; trang quản trị lọc cùng tiền tố. Đặt sai kiểu `..._idle_00` là hỏng cả hai phía | Đề xuất |

## 10. ADR cần tạo / quyết định treo

- **ADR đề xuất — "Graphics Quality Tiers & Pre-render Pipeline"**: gộp quyết định #1–#4 (ảnh hưởng cấu trúc `assets/exported/`, ResourceManager, và là cam kết dài hạn về cách sản xuất art). Chủ dự án Accept thì tài liệu này chuyển Status → Approved.
- **Migration registry** (§8.2): là DB schema change — tạo Flyway migration `V19__create_art_registry.sql` (gồm `art_category` + seed cây danh mục + `art_asset`) khi chủ dự án duyệt cột/kiểu; kèm quyết định #7–#8 vào ADR trên (không cần ADR riêng).
- **Lưu trữ file: không cần ADR** — §8.4 chốt nginx + đĩa, không thêm dependency nào.
- ✅ **Phát hành asset đã có ADR riêng**: [ADR-028](../01-architecture/adr/ADR-028-Graphics-Tier-Asset-Delivery.md) (Proposed 2026-08-01) — bản cài đóng gói **đúng 1 tier mặc định (MEDIUM)**, LOW/HIGH tải theo yêu cầu khi người chơi đổi mức đồ họa; client **luôn** gọi `ensureAssetsAvailable(tier)` kể cả khi asset có sẵn (chống nhánh code mục). ADR-028 **amend** [ADR-024](../01-architecture/adr/ADR-024-Asset-Distribution-Strategy.md) ở đúng điểm "đóng gói toàn bộ"; phần hoãn CDN/OBB của ADR-024 vẫn giữ nguyên.
- **Treo**: có làm bộ atlas @2x cho desktop High không (tốn thêm dung lượng patch)? · **Git LFS cho .blend lớn — vẫn treo** (MinIO từng được coi là lời giải, nay đã loại) · Blender tooling không cần ADR dependency (chạy dev-time, không vào runtime game).

## 11. Tài liệu liên quan

[RESOURCE.md](../04-client/RESOURCE.md) (pipeline hạ nguồn, naming, atlas) · [RENDERING.md](../03-engine/RENDERING.md) (animation flyweight, particle pool, shader) · [GDD](../13-game-design/GAME-DESIGN-DOCUMENT.md) (5 hệ phái) · [tools/blender/README.md](../../tools/blender/README.md) (contract script) · [DATABASE.md](../05-server/DATABASE.md) + [ADMIN.md](../12-devtools/ADMIN.md) (registry §8)

## 12. Việc cần làm tiếp theo

1. Chủ dự án duyệt Art Direction §2 bằng **1 asset thí điểm** (nhân vật Hỏa) trước khi sản xuất hàng loạt.
3. Dựng `kage_character_base.blend` (phiên Blender MCP đầu tiên, theo §3.3).
4. `tools/asset-pipeline` (đã hứa ở RESOURCE.md, chưa tồn tại): downscale @1x/@0.5x + pack 3 bộ atlas + **upsert registry vào DB** (§8.3, kèm validate prefix↔category).
5. Chủ dự án duyệt schema §8.2 → tạo `V19__create_art_registry.sql`.
6. Code `GraphicsQuality` trong client-core khi tới phase thích hợp (không code trước — Phase discipline).
