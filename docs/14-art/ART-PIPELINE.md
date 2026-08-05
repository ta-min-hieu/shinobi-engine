# ART-PIPELINE.md

**Sản xuất đồ họa bằng Blender (AI-driven) → model 3D cho nhân vật, sprite 2D cho VFX/icon → cấu hình đồ họa client**

| | |
|---|---|
| Version | 0.5.0 |
| Status | Draft |
| Liên quan | [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md) (2.5D — **đổi kiến trúc tài liệu này**) · [RESOURCE.md](../04-client/RESOURCE.md) · [RENDERING.md](../03-engine/RENDERING.md) · [GAME-DESIGN-DOCUMENT.md](../13-game-design/GAME-DESIGN-DOCUMENT.md) |

> ⚠️ **Đổi lớn 2026-08-03 ([ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md), Accepted).**
> **Nhân vật · quái · NPC không còn nướng ra sprite** — chúng xuất thành model glTF và được client
> render 3D thời gian thực qua camera orthographic khoá góc (2.5D). **VFX kỹ năng · icon vật phẩm ·
> UI vẫn là sprite 2D**, không đổi gì.
> Bản v0.1.0 (pre-render toàn bộ) còn trong lịch sử git nếu cần đối chiếu.

> Thao tác cụ thể → [BLENDER-SESSION-GUIDE.md](BLENDER-SESSION-GUIDE.md). Contract script → [tools/blender/README.md](../../tools/blender/README.md).

---

## 0. Kỷ luật token cho phiên làm art

Phiên làm art bằng Blender MCP **chỉ cần nạp 4 file**: tài liệu này + [ART-QUALITY-STANDARD.md](ART-QUALITY-STANDARD.md) (thước đo ĐẠT/KHÔNG ĐẠT — bắt buộc cho nhân vật/quái/NPC) + [BLENDER-SESSION-GUIDE.md](BLENDER-SESSION-GUIDE.md) (thao tác từng bước) + [tools/blender/README.md](../../tools/blender/README.md) (contract script). Không đọc docs khác (mọi ràng buộc liên quan đã được chưng cất vào §1).

---

## 1. Kết luận nền tảng (đọc 30 giây)

Từ [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md), pipeline **chia làm hai nhánh**. Đọc đúng nhánh của thứ mình đang làm.

**Nhánh 3D — nhân vật · quái · NPC**

1. Game trình bày **như 2D side-view**, nhưng nhân vật được **render 3D thời gian thực** qua camera orthographic khoá cứng góc nhìn (2.5D). Blender là xưởng **dựng model**, không còn là xưởng render.
2. **Hướng nhìn là hằng số runtime**, không nướng vào tài sản. Không còn "chỉ render hướng phải" và không còn trò flip sprite — nhân vật xoay được tự do.
3. Xuất **glTF 2.0 dạng `.glb`** từ Blender (Blender xuất sẵn trong core, không qua công cụ ngoài). 3 mức đồ họa (§5) là **3 mức LOD của model + cỡ texture** — 5k/10k/20k tam giác, đo thật ở §4.4 — không phải 3 bộ atlas.
4. Animation chạy đúng tần số khung hình thật của máy — **không còn trần 12 fps**. Đây là lý do chính đổi sang 2.5D.
5. **Poly budget là ràng buộc thật, đã đo** (2026-08-05). Mesh text→3D ra ~93k tam giác; ngưỡng từng tier ở **§4.4** (5k/10k/20k). Còn thiếu đúng một mảnh: frame time thật, chờ client vẽ được model đầu tiên.

**Nhánh 2D — VFX kỹ năng · icon vật phẩm · UI** *(không đổi)*

6. Vẫn pre-render ra sprite: **một bản master @2x** duy nhất → `tools/asset-pipeline` hạ mẫu @1x và @0.5x → 3 bộ atlas.
7. Animation VFX vẫn chạy "on twos" kiểu anime: **12 fps playback**. Số fps này là **thuộc tính asset, không phải setting đồ họa**.

**Ràng buộc chung cả hai nhánh**

8. Atlas ≤ **2048×2048** (GPU Android cũ), **không** lighting engine runtime, **không** Spine (skeletal của nhánh 3D đã có sẵn trong glTF — không thêm dependency thương mại; nhánh 2D vẫn frame-based thuần, không skeletal), `Animation`/`Model` dùng chung (Flyweight), particle bắt buộc pool.
9. Vẻ ngoài cel phải **giống nhau giữa hai nhánh** — VFX 2D vẽ đè lên nhân vật 3D trong cùng một khung hình. Cel ramp và độ dày viền là hợp đồng chung, xem [ART-QUALITY-STANDARD §4](ART-QUALITY-STANDARD.md).

---

## 2. Art Direction

### 2.1 Trụ cột phong cách

| Trụ cột | Chuẩn | Nguồn tham chiếu |
|---|---|---|
| Tỷ lệ nhân vật | Chibi 2–2.5 đầu, đầu to, chi ngắn | Ninja School Online |
| Đường nét | Outline đen. **Không phải một kỹ thuật, cũng không phải một con số**: mesh thủ tục dùng Freestyle, mesh Text→3D dùng inverted hull — kỹ thuật + số đo ở [ART-QUALITY-STANDARD §4](ART-QUALITY-STANDARD.md) mục 3 (nguồn duy nhất) | Ninja School Online |
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

**Skill code trong bảng dưới là code thật** trong `server/server-world/src/main/java/dev/kage/server/world/SkillRegistry.java` (riêng `fireball` khai báo ở `SkillConstants.java` cùng package — skill chạy qua script engine từ Phase 4) — không phải tên đặt cho vui. Quy tắc đặt tên VFX: **`vfx_<skill_code>`** (ví dụ `vfx_loi_thien_loi`), nên asset map 1-1 với skill, không cần bảng tra riêng.

3 bậc trang bị dùng chung tên gọi: **B1 Học Viên** → **B2 Nhẫn Giả** → **B3 Thượng Nhẫn**. Quy tắc leo bậc chung cho mọi hệ: B1 vải thô/ít phụ kiện, B2 thêm giáp mảnh + khăn/đai màu hệ, B3 thêm 1 chi tiết *chuyển động* (đuôi khăn dài, vòng nguyên tố bay quanh) — đây là thứ khiến người chơi nhìn phát biết "đồ xịn" ngay cả ở @0.5x.

---

#### 🔥 Hỏa — *sát thương liên tục, bùng nổ*

- **Trang phục** — B1: áo khoác ngắn nâu đất, băng tay đỏ. B2: giáp vai trái đơn, khăn quàng `#E8552D` dài ngang hông. B3: áo choàng cụt tay rìa cháy sém, khăn dài phất, 3 đốm lửa `#FFB13D` lơ lửng sau lưng.
- **Vũ khí**: kunai lưỡi rộng ám khói + cuộn giấy phù chú đeo hông.
- **Skill thường `fireball`** — cast: lùi nửa bước, tay kết ấn, quầng cam nhỏ ở lòng bàn tay (2 frame). Bay: cầu lửa lưỡi liềm kéo đuôi ember. Impact: nở hoa lửa 3 frame + tàn lửa bay **lên** (particle runtime, không render sẵn).
- **Ultimate**: *chưa có trong `SkillRegistry`* — Hỏa mới chỉ có `fireball` (MVP đi 1 hệ trước, [GDD](../13-game-design/GAME-DESIGN-DOCUMENT.md)). Khi thêm ult mới làm VFX, **đừng làm trước**.
- **Prompt 3D** (khung chung ở cuối §2.3): `chibi ninja warrior character, 2.5 heads tall, big head, short limbs, sleeveless burnt-edge coat, long scarf, arm wraps, dark knee-length trousers, clearly visible anime face: large glossy black eyes with white highlights, thin sharp eyebrows, small smiling mouth, spiky black hair, toon style, flat colors, T-pose, low poly, clean topology`

#### 💧 Thủy — *khống chế, hồi phục*

- **Trang phục** — B1: áo ngắn xanh nhạt, quần lửng, chân trần quấn băng. B2: giáp ngực mỏng `#2D7FE8`, tay áo rộng buông. B3: áo choàng nước rìa gợn sóng, vòng nước `#7FD4FF` xoay chậm quanh cổ tay.
- **Vũ khí**: bình hồ lô đeo lưng + dải lụa nước (không phải kiếm — Thủy là hệ hỗ trợ).
- **Skill thường `thuy_hoi_phuc`** (mục tiêu ALLY) — cast: chắp tay, cúi nhẹ. Impact: **cột nước dịu dâng từ chân lên** đồng đội, kèm vòng ripple ở mặt đất, tông `#7FD4FF` trong mờ. Tránh dùng màu xanh lá — dễ nhầm hệ Phong.
- **Ultimate `thuy_thuy_trieu`** (ENEMY) — cast dài 4 frame: nước dâng quanh chân người thi triển. Impact: **sóng lớn quét ngang màn hình** từ trái sang phải, đối thủ trong sóng bị đẩy lùi; kết bằng màn nước rơi + ripple đọng 2 frame.
- **Prompt 3D**: `chibi ninja warrior character, 2.5 heads tall, big head, short limbs, flowing wide-sleeve robe, gourd flask on back, dark knee-length trousers, cloth wraps on feet, clearly visible anime face: large glossy black eyes with white highlights, thin sharp eyebrows, small smiling mouth, spiky black hair, toon style, flat colors, T-pose, low poly, clean topology`

#### 🌪 Phong — *tốc độ, bạo kích*

- **Trang phục** — B1: áo bó sát xanh lục nhạt, ống tay ngắn. B2: áo gile `#3DBF7A` + đai lưng dài bay ngược chiều chạy. B3: khăn choàng mỏng `#CFF5E0` xẻ tà, 2 dải lụa bay tự do — bậc này phải thấy "gió" ngay cả khi đứng yên.
- **Vũ khí**: song đao ngắn cong (đánh nhanh 2 tay).
- **Skill thường `phong_gia_toc`** (buff SELF) — cast: dậm chân, luồng gió xoáy `#3DBF7A` bốc từ chân lên. Trong lúc buff: **afterimage mờ** phía sau khi di chuyển (làm bằng code — vẽ lại sprite cũ với alpha giảm, **không** render frame riêng, tiết kiệm cả atlas lẫn công).
- **Ultimate `phong_loc_xoay`** (ENEMY) — cast: xoay người 1 vòng. Impact: **cột lốc xoáy** dựng tại vị trí mục tiêu, lá cuốn theo xoắn ốc, kéo đối thủ vào giữa; tan dần từ dưới lên.
- **Prompt 3D**: `chibi ninja warrior character, 2.5 heads tall, big head, short limbs, tight sleeveless top, long flowing sash, dynamic light armor, dark knee-length trousers, clearly visible anime face: large glossy black eyes with white highlights, thin sharp eyebrows, small smiling mouth, spiky black hair, toon style, flat colors, T-pose, low poly, clean topology` — song đao gắn rời bằng `attach_weapon(kind='twin_blades')`

#### ⚡ Lôi — *đơn mục tiêu, xuyên giáp*

- **Trang phục** — B1: áo tối màu, băng đô vàng `#FFE84D`. B2: giáp vai đôi `#8C4DE8` góc cạnh, găng tay dài. B3: áo choàng tím sẫm rìa răng cưa, **tia điện nhỏ chạy dọc viền giáp** (2 frame lặp).
- **Vũ khí**: trường đao thẳng lưỡi mảnh (hệ xuyên giáp — hình dáng phải "sắc", không tù).
- **Skill thường `loi_tia_set`** (ENEMY) — cast: chỉ tay, tụ sáng ở đầu ngón (1 frame). Impact: **tia zigzag `#FFE84D` đánh thẳng xuống mục tiêu**, kèm **flash trắng toàn màn 1 frame** rồi tắt hẳn — độ giật là bản sắc hệ Lôi, không kéo dài.
- **Ultimate `loi_thien_loi`** (ENEMY) — cast: giơ tay lên trời, mây tối tụ trên đầu 3 frame (báo hiệu để đối thủ né — luật thiết kế PvP: ult phải đọc được). Impact: **cột sét khổng lồ giáng thẳng**, chớp 2 lần, để lại vệt cháy tím `#8C4DE8` mờ dần trên đất.
- **Prompt 3D**: `chibi ninja warrior character, 2.5 heads tall, big head, short limbs, dark hooded jacket, angular shoulder armor, headband, dark knee-length trousers, clearly visible anime face: large glossy black eyes with white highlights, thin sharp eyebrows, small smiling mouth, spiky black hair, toon style, flat colors, T-pose, low poly, clean topology` — trường đao gắn rời bằng `attach_weapon(kind='long_blade')`

#### ⛰ Thổ — *phòng thủ, khiên*

- **Trang phục** — B1: áo thô nâu `#A6763D`, giáp da vai. B2: giáp ngực dày, tấm che đùi, khăn đầu be `#E0C089`. B3: giáp đá khối, **mảnh đá nhỏ lơ lửng quanh vai** — hệ Thổ phải trông **nặng nhất**: bóng đổ dày hơn, chân bám đất rộng hơn.
- **Vũ khí**: chuỳ đá lớn hoặc găng chiến (không dùng vũ khí mảnh).
- **Skill thường `tho_khien_da`** (buff SELF) — cast: dậm chân mạnh, **3 phiến đá trồi lên từ mặt đất** dựng thành khiên trước mặt; giữ nguyên trong suốt thời gian buff, nứt dần theo lượng damage đã chặn (2 mức nứt là đủ).
- **Ultimate `tho_dia_chan`** (ENEMY) — cast: nhảy lên rồi giáng cả người xuống. Impact: **vết nứt lan trên mặt đất** theo chiều ngang + bụi thấp sát đất + **rung camera** (camera shake là hiệu ứng code, thuộc tier Medium/High §5, tier Low tắt).
- **Prompt 3D**: `chibi ninja warrior character, 2.5 heads tall, big head, short limbs, heavy stone plate armor, bulky shoulders, thick leather chest guard, dark knee-length trousers, clearly visible anime face: large glossy black eyes with white highlights, thin sharp eyebrows, small smiling mouth, spiky black hair, toon style, flat colors, T-pose, low poly, clean topology` — chuỳ đá gắn rời bằng `attach_weapon(kind='stone_mace')`

---

**Khung chung của 5 prompt trên** — đổi phần trang phục/vũ khí, giữ nguyên phần còn lại:

```
chibi ninja warrior character, 2.5 heads tall, big head, short limbs,
<trang phục + vũ khí theo bậc>,
dark knee-length trousers,
clearly visible anime face: large glossy black eyes with white highlights,
thin sharp eyebrows, small smiling mouth, spiky black hair,
toon style, flat colors, T-pose, low poly, clean topology
```

⚠️ **Vũ khí KHÔNG bao giờ nằm trong prompt.** Ranh giới: *thứ biến dạng theo cơ thể thì nướng vào mesh,
thứ cầm cứng hoặc thay được thì gắn lên xương.*

| | Vào prompt Rodin | Gắn rời |
|---|---|---|
| Trang phục (áo, khăn, giáp, băng tay) — định nghĩa bậc B1/B2/B3 | ✅ | |
| Vũ khí (`kunai` `gourd` `twin_blades` `long_blade` `stone_mace`) | | ✅ `attach_weapon()` |

Bốn lý do, không phải sở thích:

1. **Vũ khí nướng vào mesh sẽ bị biến dạng.** Mesh sinh ra là *một khối hàn liền* rồi mới gán trọng số
   theo khoảng cách tới xương (`weight_by_proximity`). Lưỡi đao vươn ra khỏi bàn tay sẽ bị chia trọng số
   cho nhiều xương và **uốn dẻo như cao su** mỗi lần gập khuỷu. Trang phục thì ngược lại — nó *phải*
   biến dạng theo thân, nên nướng vào là đúng.
2. **Chi phí sửa lệch nhau hàng chục lần.** Vũ khí gắn rời: Q7 FAIL là **vòng B miễn phí**
   ([ART-QUALITY-STANDARD §7.2](ART-QUALITY-STANDARD.md)). Vũ khí trong mesh: Q7 FAIL là **vòng A tốn
   credit Rodin**, và sinh lại thì mất luôn cơ thể đang đạt.
3. **Trùng khớp với [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md)** ("trang bị
   nhìn thấy được là gắn mesh vào xương, không nhân tổ hợp sheet") và §3.1. Trang bị là **chỉ số thay
   đổi được** trong `item_template`; nướng vũ khí vào thân là khoá cứng người chơi vào một vũ khí.
4. **Bản pilot đã duyệt vốn làm đúng cách này** — kunai của Hỏa nằm trong file dưới dạng 3 object rời
   (`hoa_KunaiBlade` · `hoa_KunaiGrip` · `hoa_KunaiRing`), không phải một phần của `Hoa_D`. Prompt Hỏa
   và Thủy vốn đã không có vũ khí; ba prompt còn lại là chỗ lệch chuẩn, nay đã sửa.

Cả 5 kiểu vũ khí **đã có sẵn** trong `kage_mesh_kit.build_weapon()` — không có khoảng trống nào phải lấp.

> Vì sao **trang phục** vẫn nướng vào mesh dù cũng là "trang bị": `item_template` hiện **không có cột hình
> ảnh nào** (ADR-031 Context) nên chưa có gì để thay lúc chạy, và trang phục là thứ tạo ra 3 vùng màu của
> Q4/Q5. Điều kiện đảo lại quyết định này: ngày `item_template` có cột hình ảnh thật.

Ba mệnh đề **không được bỏ**, mỗi cái mua bằng một lần hỏng thật ([BLENDER-SESSION-GUIDE §1.5](BLENDER-SESSION-GUIDE.md) bước B):

- **Cả cụm mô tả khuôn mặt** — thiếu là ra nhân vật đầu trọc không mặt.
- **`warrior character` + `dark knee-length trousers`** thay cho `boy` + chân trần — tổ hợp cũ bị Rodin chặn `IMAGE_CONTENT_VIOLATION`.
- **KHÔNG có `side profile facing right`** — vô nghĩa với mesh 3D và làm mesh **dẹt theo chiều sâu**. (Cụm này là di sản thời pre-render sprite; ADR-031 bỏ hẳn khái niệm "hướng nướng vào tài sản".)

⚠️ **Prompt chỉ ra base mesh thô.** Model Rodin trả về luôn kiểu bán-tả-thực → bắt buộc chuẩn hoá + toon hoá lại bằng `kage_text3d_kit` (`normalise` → `clean` → `toon_textured`) trước khi nghiệm thu ([BLENDER-SESSION-GUIDE §1.5](BLENDER-SESSION-GUIDE.md) bước C). Đừng render thẳng.

---

## 3. Kiến trúc pipeline

### 3.1 Vì sao Blender là nguồn duy nhất cho cả hai nhánh

| Lợi ích | Giải thích |
|---|---|
| 1 model → N output | Nhánh 3D xuất glTF; nhánh 2D render sprite — cùng một file nguồn |
| Đổi trang bị rẻ | Nhánh 3D: gắn mesh vào xương lúc chạy, không nhân tổ hợp tài sản |
| Nhất quán | Cùng rig/tỷ lệ/palette → mọi nhân vật khớp nhau |
| AI tự động hóa được | Toàn pipeline là script Python (bpy), chạy headless không cần bàn tay họa sĩ |

### 3.2 Sơ đồ — nhánh 3D (nhân vật · quái · NPC)

```
assets/raw/blender/characters/*.blend  ── kage_text3d_kit.py ──►  model đã rig + 7 action
        │                                                                  │
   (source of truth,                                                       ▼
    commit vào git)                    export_character_lods.py: decimate → .glb (Blender core)
                                                                           │
                                                                           ▼
                                   assets/exported/models/{low,med,high}/<asset>.glb
                                   + <asset>.model-meta.json (tri_counts cho art_asset)
                                                                           │
                                                                           ▼
                                   client-core: gdx-gltf nạp → ModelBatch + shader cel tự viết
                                   camera orthographic khoá góc (hằng số runtime)
```

### 3.3 Sơ đồ — nhánh 2D (VFX · icon · UI) *(không đổi)*

```
assets/raw/blender/*.blend  ── kage_sprite_render.py ──►  assets/raw/render-out/<asset>/<action>_##.png  (master @2x)
                                                                     │
                                                                     ▼
                             tools/asset-pipeline: hạ mẫu @1x + @0.5x, validate naming,
                             pack 3 bộ atlas ≤2048, sinh manifest
                                                                     │
                                                                     ▼
                             assets/exported/atlases/{low,med,high}/... → ResourceManager
```

### 3.4 Thiết lập kỹ thuật Blender (chuẩn hóa trong script, không làm tay)

Nhánh 2D (`kage_sprite_render.py`):

- **Renderer**: EEVEE, `film_transparent = True`.
- **Toon**: Shader-to-RGB + ColorRamp (Constant, 2–3 nấc).
- **Camera**: Orthographic, đặt trên trục −Y nhìn vào +Y.
- **Light**: 1 Sun key (trên-trước, ~30°) + 1 fill yếu. Không HDRI, không GI — toon không cần.

Nhánh 3D (`kage_text3d_kit.py`) — vẻ ngoài do **shader runtime** quyết định, không phải render setting. Phía Blender chỉ chuẩn bị dữ liệu cho shader đó:

- **Chuẩn hóa**: cao 1.0, chân z=0, mặt +X (`normalise()`).
- **Rig**: 11 xương của `kage_mesh_kit.build_rig()`, đặt theo số đo **đo lại cho từng mesh** (`measure()`), không có hằng số chung.
- **Toon**: `toon_textured()` giữ nguyên texture đã vẽ, chỉ lượng tử hoá ánh sáng — nấc ramp dùng `TEXTURED_SHADE_STEPS` chứ **không** dùng `SHADE_STEPS` của nhánh 2D (texture sinh ra đã có sẵn bóng, dùng nhầm là tối hai lần).
- **Viền**: `add_inverted_hull_outline()` — shell lật normal, cull mặt trước. **Không dùng Freestyle**: Freestyle không tồn tại lúc runtime, và trên mesh nhiều tóc nó vẽ viền cho từng lọn. Kỹ thuật shell chuyển thẳng sang GLSL được.

---

## 4. Chuẩn kỹ thuật asset

### 4.1 Kích thước sprite — **chỉ còn áp dụng cho nhánh 2D**

| Loại | Master @2x | @1x | @0.5x |
|---|---|---|---|
| VFX skill (1 ô) | 384×384 | 192 | 96 |
| Icon (skill/item/buff) | 128×128 | 64 | 32 |
| Tile/prop môi trường | theo lưới map, bội số 32 @1x | — | — |

Nhân vật / NPC / quái / boss **không còn ở bảng này** — chúng là model 3D, "kích thước" của chúng
là chiều cao world unit (chuẩn hoá **1.0**) và ngân sách tam giác (§4.4), không phải pixel.

### 4.2 Bộ animation chuẩn cho nhân vật/quái

Bộ **7 action** giữ nguyên tên và ý nghĩa; cột "Frame" giờ chỉ mô tả **số khoá chính** đã author
trong Blender, không còn là số ảnh render ra.

| Action | Khoá chính | Ghi chú |
|---|---|---|
| idle | 4 | loop |
| run | 6 | loop |
| attack | 5 | khoá thứ 3 = impact, khớp hitbox timing server |
| skill_cast | 6 | có thể giữ tư thế cuối khi channel |
| hit | 2 | kèm tint flash trong code |
| jump | 4 | lên 2 / xuống 2 |
| die | 6 | dừng ở tư thế cuối, không loop |

**Khác biệt lớn nhất so với bản v0.1.0:** client **nội suy giữa các khoá này theo tần số khung hình
thật**, thay vì phát 33 ảnh cố định ở 12 fps. Tư thế `die` vẫn phải **gục gọn**, không nằm sải dài —
ràng buộc này đến từ khung hình camera, không phải từ việc render (xem ART-QUALITY-STANDARD).

VFX ở nhánh 2D vẫn giữ nguyên playback **12 fps** như cũ.

### 4.3 Quy tắc chung

- Naming theo [RESOURCE.md §4](../04-client/RESOURCE.md).
- **Gốc toạ độ model = chân, giữa** — mọi nhân vật đứng trên cùng một baseline, y hệt quy ước pivot cũ.
- Nhánh 2D: chừa lề trong khung để frame animation không bị cắt; padding pack 2px.
- **Không còn quy tắc "chỉ render hướng phải"** — hướng nhìn là hằng số runtime (§1.2).

### 4.4 Ngân sách tam giác (nhánh 3D) — **đã đo 2026-08-05**

Mesh Rodin trả về **93.165 tam giác** (pilot Hỏa, gồm cả kunai). Ngưỡng dưới đây đo thật trên chính
mesh đó bằng `tools/blender/measure_triangle_budget.py`, không phải ước lượng.

| Tier | Tam giác | Texture | `.glb` |
|---|---|---|---|
| Low | **5.000** | 512 | 0,59 MB |
| Medium (đóng gói trong bản cài) | **10.000** | 1024 | 1,38 MB |
| High | **20.000** | 2048 | 3,60 MB |

**Cách đo** — hai lượt quét tách biệt, vì trộn hai biến thì ra một con số không hành động được:

1. *Hình học*: giữ texture cố định 1024, hạ mesh xuống 5k/10k/20k/40k, xuất `.glb`, render `idle`.
2. *Texture*: giữ mesh cố định 10k, đổi texture 512/1024/2048.

Rồi so từng pixel với bản gốc (kênh RGB, chỉ tính pixel có alpha):

| Mức | Lệch trung bình | Lệch lớn nhất | % pixel lệch >2/255 | `.glb` |
|---|---|---|---|---|
| 40k tam giác | 0,0039 | 0,76 | 7,9% | 2,42 MB |
| 20k tam giác | 0,0088 | 0,92 | 15,4% | 1,74 MB |
| 10k tam giác | 0,0148 | 0,93 | 20,7% | 1,38 MB |
| 5k tam giác | 0,0250 | 0,93 | 26,2% | 1,19 MB |
| texture 1024 | 0,0015 | 0,07 | 2,5% | 1,38 MB |
| texture 512 | 0,0039 | 0,30 | 10,1% | 0,78 MB |

Ba kết luận rút ra từ bảng trên:

- **Mesh 93k là dư thừa, không phải cần thiết.** Ở kích thước nhân vật chiếm trên màn, bản 5k và bản
  93k **nhìn bằng mắt không phân biệt được** — 93k chỉ phản ánh việc Rodin không biết mesh dùng để làm gì.
- **Hạ texture rẻ hơn hạ mesh.** Cùng mức lệch trung bình 0,0039, hạ texture 2048→512 chỉ gây lệch
  lớn nhất **0,30** còn hạ mesh 93k→40k gây **0,76**. Texture mờ đi đều; mesh thì **vỡ silhouette cục bộ**
  — mà silhouette chính là thứ Q10 chấm. Nên khi cần cắt dung lượng, cắt texture trước.
- **Dung lượng hình học ≈ 35 byte/tam giác**, tuyến tính trong toàn dải đo.

⚠️ **Chưa đo được frame time**, và ngân sách này **chưa đóng hoàn toàn**. Điều kiện #1 ở
[ADR-031 "Revisit Trigger"](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md) nói về GPU frame
time với 20 nhân vật trên màn; đo được nó cần `gdx-gltf` trên classpath và đường render 3D trong
`client-core`, **cả hai đều chưa có**. Bảng trên chốt phần *đo được hôm nay* (số tam giác, dung lượng,
chất lượng ảnh); dòng frame time phải điền khi client vẽ được model đầu tiên. Ngân sách 5–10k khớp với
mức ADR-031 đoán ban đầu — 20 nhân vật × 10k = **200k tam giác/khung**, thay vì 1,9 triệu.

---

## 5. Cấu hình đồ họa client (3 mức)

⚠️ **Vẫn KHÔNG phải bảng tier kiểu 3D thông thường** (shadow map / water reflection / GI / SSR / DoF / texture 4096). 2.5D không đổi điều đó: lighting engine đã bị từ chối có chủ đích ([RENDERING.md](../03-engine/RENDERING.md)), vẻ ngoài là cel phẳng nên không có gì để bật thêm, atlas 4096 vẫn vượt trần phần cứng 2048. Cái **thực sự đổi** so với v0.1.0 là hai dòng đầu và dòng FPS:

| Thành phần | Low | Medium | High |
|---|---|---|---|
| Model nhân vật (nhánh 3D) | 5k tam giác, texture 512 (0,59 MB) | 10k tam giác, texture 1024 (1,38 MB — **đóng gói sẵn trong bản cài**) | 20k tam giác, texture 2048 (3,60 MB) |
| Bộ atlas VFX/icon (nhánh 2D) | @0.5x | @1x (**đóng gói sẵn trong bản cài**) | @1x + hiệu ứng |
| Texture filter | Linear | Linear | Linear + mipmap khi camera zoom |
| Particle đồng thời (cap toàn màn) | 64 | 256 | 1024 |
| VFX skill người chơi khác | ẩn (giữ của mình + boss) | chỉ trong khung hình | tất cả |
| Lớp parallax nền phụ + ambient anim (lá rơi, đom đóm...) | tắt | bật | bật + mật độ cao |
| Camera effect (shake, hit-flash, vignette) | tối thiểu | bật | bật |
| Post-processing (FBO) | off | off | bloom nhẹ (chỉ desktop) |
| FPS mục tiêu | 30 | 60 | 60 + vsync |

Nguyên tắc thiết kế tier:
- **FPS mục tiêu giờ là mục tiêu thật.** Ở v0.1.0 nó gần như vô nghĩa vì animation khoá cứng 12 fps bất kể máy; với 2.5D thì animation nhân vật thực sự chạy theo tần số khung hình.
- **Tier chỉ tồn tại phía client** — server không biết, không validate, gameplay không đổi (Server Authority không bị đụng).
- Đổi tier = reload zone (đổi bộ model + atlas) — chấp nhận được, không cần hot-swap texture. **Chỉ MEDIUM được đóng gói trong bản cài**; đổi sang LOW/HIGH thì client tải bộ đó về trước ([ADR-028](../01-architecture/adr/ADR-028-Graphics-Tier-Asset-Delivery.md), vẫn còn hiệu lực — ADR-031 chỉ đổi *nội dung* của tier, không đổi cơ chế).
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

> ⚠️ **Nhân vật/quái/NPC hình người không đi qua bảng này.** Từ 2026-08-03 chúng đi theo
> [§6.4](#64-workflow-nhân-vật-nhánh-3d). Bảng dưới chỉ còn cho thứ phải model tay và **vẫn ra
> sprite**: VFX kỹ năng, icon, prop, tile.

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
| **Hyper3D Rodin (text→3D)** | **Nhân vật · quái · NPC hình người — đường chính** | Phải gọi thẳng REST API để chỉnh `tier`/`quality`/`use_hyper`/`TAPose`; tool MCP không expose và mặc định ở tier rẻ nhất. Chi tiết ở [BLENDER-SESSION-GUIDE](BLENDER-SESSION-GUIDE.md) |
| Sketchfab | Có model hợp sẵn | **Chỉ license CC0/CC-BY** (dự án ưu tiên free-to-use); ghi attribution vào ASSET-LOG |
| PolyHaven | ❌ Tránh — texture/HDRI photoreal, không hợp toon và toon không cần HDRI | — |

### 6.4 Workflow nhân vật (nhánh 3D)

Chạy headless bằng `tools/blender/kage_text3d_kit.py`. Không bước nào là thao tác tay.

| Bước | Hàm | Vì sao không bỏ được |
|---|---|---|
| 1. Sinh mesh | REST API Rodin (§6.3) | Prompt phải nêu rõ khuôn mặt và **đúng spec trang phục §2.3** — sai spec ở đây là hỏng cả nhân vật |
| 2. Chuẩn hoá | `normalise()` | Cao 1.0, chân z=0, mặt +X. Phải biến đổi ở cấp mesh data |
| 3. Hàn đỉnh | `clean()` | glTF tách đỉnh theo seam UV; không hàn thì bước 5 gãy |
| 4. Đo giải phẫu | `measure()` | Không có hằng số chung — mỗi mesh một tỷ lệ |
| 5. Rig + trọng số | `rig()` | Tự chuyển sang `weight_by_proximity()` khi bone-heat gãy |
| 6. Bảy action | `author_scaled_actions()` | Hiệu chỉnh biên độ theo tỷ lệ thân, giữ nguyên nhịp |
| 7. Toon + viền | `toon_textured()` · `add_inverted_hull_outline()` | Xem §3.4 — dùng nhầm nấc ramp là tối hai lần |
| 8. Nghiệm thu | render preview → bảng Q ở [ART-QUALITY-STANDARD](ART-QUALITY-STANDARD.md) | Preview vẫn render bằng `kage_sprite_render.py`, nhưng **chỉ để nghiệm thu**, không phải sản phẩm |
| 9. Decimate + xuất 3 LOD | `export_character_lods.py` (gọi `decimate()` + `export_gltf()`) | Sinh cả 3 tier §4.4 + sidecar `model-meta.json`. Bỏ viền/far-shade/vật liệu toon: shader runtime dựng lại, giữ lại thì **gấp đôi số tam giác** |

➡️ **Lệnh cụ thể từng bước, checklist nghiệm thu, và bảng lỗi thường gặp**: [BLENDER-SESSION-GUIDE.md](BLENDER-SESSION-GUIDE.md).

---

## 7. Tổ chức file & version control

```
assets/raw/blender/                  # .blend nguồn — COMMIT (source of truth), KHÔNG đóng gói vào game
├── kage_character_base.blend        # base chung: rig + camera + light + toon material chuẩn
├── characters/  ├── monsters/  ├── effects/  └── props/
assets/raw/render-out/               # PNG master @2x — KHÔNG commit (sinh lại được), .gitignore
assets/raw/ASSET-LOG.md              # sổ asset — COMMIT
assets/exported/models/{low,med,high}/   # glTF đã decimate — KHÔNG commit (sinh lại được), .gitignore
```

- `.blend` giữ **nhỏ** (không pack texture thừa, purge orphan trước khi save). Git LFS **đã bật** cho `assets/raw/**` (`.gitattributes`, 2026-08-01) — quota free tier có hạn, đừng commit thêm binary không cần thiết.
- ⚠️ **Ngoại lệ quan trọng với nhánh 3D:** `.blend` chứa mesh sinh từ Rodin **không sinh lại được** — chạy lại prompt cũ ra model khác. Quy tắc "không commit thứ sinh lại được" **không** áp dụng cho chúng; mất là mất hẳn. Đổi lại, chúng buộc phải `pack_all()` texture nên nặng hơn nhiều (bản pilot Hỏa: 47 MB).
- ⚠️ **Ngoại lệ cho luật "COMMIT .blend nguồn" — chỉ áp dụng cho thế hệ mesh kit**: asset dựng bằng `kage_mesh_kit.py` / `build_box_character.py` sinh lại nguyên vẹn từ 1 dòng lệnh nên **không commit** hàng loạt (nguồn thật là spec dict + kit, đều là text). **Không** áp dụng cho nhân vật Text→3D — xem gạch đầu dòng trên và [ART-QUALITY-STANDARD §8](ART-QUALITY-STANDARD.md).
- `assets/raw/` vốn đã bị loại khỏi bản build theo [RESOURCE.md §2](../04-client/RESOURCE.md) — cấu trúc trên không đổi luật đó.

---

## 8. Asset Registry trong DB (phục vụ trang quản trị)

**Yêu cầu chủ dự án (2026-08-01)**: mọi vật phẩm đồ họa phải được quản lý qua DB để sau này làm trang quản trị.

### 8.1 Nguyên tắc: DB lưu *metadata*, không lưu *binary*

| Lưu trong DB (bảng `art_asset`) | KHÔNG lưu trong DB |
|---|---|
| asset_key, loại, hệ, trạng thái duyệt, đường dẫn nguồn, bộ action, license/attribution, checksum | File PNG/atlas/.blend (binary) |

Lý do: client nhận asset qua **patch manifest** ([RESOURCE.md §9–§10](../04-client/RESOURCE.md)), không bao giờ nối DB; nhét blob vào Postgres chỉ làm phình DB/backup mà không ai đọc. Trang quản trị đọc metadata từ DB và **preview ảnh bằng đường dẫn** trỏ vào `assets/exported/`.

### 8.2 Bảng `art_asset` / `art_category` — **đã thi hành** (`V19__create_art_registry.sql`, 2026-08-01)

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
| Phát patch cho client | nginx serve `assets/exported/` với `Cache-Control: public, immutable` — chỉ an toàn khi **tên file phát hành mang content hash**; manifest [RESOURCE.md §9](../04-client/RESOURCE.md) hiện để sha256 ở field riêng, nên `tools/asset-pipeline` phải đưa hash vào tên file khi xuất |
| Preview ảnh cho trang quản trị | Cùng nginx đó, thêm `location` riêng có kiểm tra auth của admin |
| Kho `.blend` + master render @2x | Thư mục ngoài git + backup định kỳ (restic/rsync) |
| Khi bandwidth lớn | Đặt **CDN trước nginx**; nếu lên cloud thì đi thẳng **Cloudflare R2** (egress = 0đ, hợp phát patch game) |

Client **không** import SDK lưu trữ nào — chỉ tải qua URL HTTP trong manifest, đúng luật "gameplay chỉ phụ thuộc abstraction" ([/CLAUDE.md §5](../../CLAUDE.md)). Nhờ vậy đổi origin (nginx → CDN → R2) chỉ là đổi base URL trong manifest, không sửa code.

Mọi cột đường dẫn trong `art_asset` (`blend_path`, `render_dir`, `preview_url`, `atlas_name` — §8.2/§8.5) đều giữ đường dẫn **tương đối** — không nhúng hostname, để đổi origin không phải sửa DB.

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

💡 **`preview_url` là để tối ưu danh sách, không phải vì bị giới hạn.** Xem §8.7 — trang quản trị xem được **toàn bộ** asset, kể cả animation chạy thật.

**Thi hành — không dựa vào trí nhớ con người**: bước cuối của `tools/asset-pipeline` **upsert** mọi asset vào `art_asset`, và **fail build nếu có file trong `assets/exported/` không truy ra được dòng DB tương ứng**. `ASSET-LOG.md` chỉ là sổ tay token-rẻ cho phiên AI; **DB là nguồn chuẩn** cho trang quản trị.

Bảng cột trên viết cho **nhánh 2D**. Nhánh 3D (nhân vật · quái · NPC sau [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md)) giao model glTF chứ không giao atlas — xem §8.6 (schema) và §8.8 (trang quản trị).

### 8.6 Nhánh 3D trong `art_asset` — **thiết kế đề xuất, chờ chủ dự án duyệt**

⚠️ **Đây là DB schema change** ([/CLAUDE.md §4](../../CLAUDE.md)) — mục này mô tả thiết kế, **chưa
được thi hành**. Không sửa `V19` tại chỗ; khi được duyệt thì viết `V20__extend_art_asset_for_3d.sql`.

**Vấn đề.** `blend_path`/`render_dir`/`preview_url`/`atlas_name`/`master_px` đều giả định sản phẩm
giao là sprite trong atlas. Model glTF xuất vào `assets/exported/models/{low,med,high}/`
([§3.2](#32-sơ-đồ--nhánh-3d-nhân-vật--quái--npc)) không có chỗ đứng trong bảng — nên ngày bước
decimate/xuất glTF được code (§10), luật §8.5 "fail build nếu file trong `assets/exported/` không
truy ra được dòng DB" sẽ **tự vỡ**: file model nào cũng là mồ côi.

**Đề xuất: thêm cột vào `art_asset`, không tách bảng phụ.**

| Cột mới | Kiểu | Ý nghĩa |
|---|---|---|
| `asset_kind` | `VARCHAR(8) NOT NULL DEFAULT 'SPRITE'` | `SPRITE` \| `MODEL` — quyết định nhóm cột nào có nghĩa, và pipeline soi thư mục nào khi kiểm mồ côi |
| `model_name` | `VARCHAR(64)` | Tên gốc của model, ví dụ `char_ninja-fire`. Đường dẫn từng tier **suy ra** như atlas: `models/{low\|med\|high}/{model_name}.glb` |
| `tri_counts` | `JSONB` | `{"low":5107,"med":10107,"high":20107}` — số tam giác thật sau decimate từng tier, đọc thẳng từ sidecar `model-meta.json` |

Ràng buộc chống lẫn nhánh (rẻ, bắt sai ngay lúc insert):

```sql
CONSTRAINT art_asset_kind_branch CHECK (
    (asset_kind = 'SPRITE' AND model_name IS NULL AND tri_counts IS NULL)
 OR (asset_kind = 'MODEL'  AND atlas_name IS NULL AND master_px  IS NULL))
```

Cột dùng chung, **không** đổi nghĩa giữa hai nhánh: `asset_key`, `category_id`, `element`, `status`,
`blend_path`, `preview_url`, `actions`, `license`, `attribution`, `checksum`.
`render_dir` giữ nguyên tên và vẫn dùng cho nhánh 3D, nhưng đổi *vai trò*: với `MODEL` nó là
**bằng chứng nghiệm thu** ([ART-QUALITY-STANDARD §7](ART-QUALITY-STANDARD.md)), không phải nguồn để
pack. Hai nhánh vì thế vẫn dùng chung trang quản trị và chung câu lọc theo category/element.

**Vì sao thêm cột chứ không tách bảng `art_model`:**

1. Nhánh 3D có **đúng cùng một hình dạng bài toán** với atlas — một cái tên + một quy ước sinh ra 3
   đường dẫn tier. Đã giải bằng 1 cột cho atlas thì cũng là 1 cột cho model, chưa đủ để tách bảng.
2. `art_asset` **vốn đã** là bảng cột-tuỳ-loại: `actions` null với icon, `atlas_name` null với asset
   chưa pack. Thêm cột chỉ có nghĩa với một `asset_kind` là đúng khuôn đang có, không phải ngoại lệ mới.
3. Trang quản trị liệt kê **toàn bộ kho** trong một lưới (§8.3/§8.7). Tách bảng biến mọi truy vấn
   danh sách thành `LEFT JOIN` chỉ để lấy thêm 1–2 cột.
4. `CHECK` ở trên cho lại đúng thứ mà tách bảng mua được (không thể điền nhầm cột chéo nhánh), với
   giá 4 dòng SQL và không thêm bảng.

**Việc kèm theo khi thi hành** (không được quên, nếu không luật §8.5 vẫn hở):

- `ArtRegistryUpserter` đọc thêm `asset_kind` từ ASSET-LOG/manifest và điền `model_name`/`tri_counts`.
- Bước kiểm mồ côi soi **cả hai** thư mục: `assets/exported/atlases/` (nhánh SPRITE) và
  `assets/exported/models/` (nhánh MODEL).
- `tri_counts` không phải đo lại: `export_character_lods.py` đã ghi sẵn nó (cùng `glb_bytes`,
  `texture_px`, `actions`) vào sidecar **`<asset_key>.model-meta.json`** đặt cạnh thư mục model —
  đúng vai trò `render-meta.json` giữ cho nhánh sprite.

⚠️ **Đuôi file là `.glb`, không phải `.gltf`.** Xuất dạng một file tự chứa thay vì bộ ba
`.gltf` + `.bin` + `.png`: patcher băm và phát **theo từng file** ([RESOURCE.md §9](../04-client/RESOURCE.md)),
nên asset 3 file là 3 cơ hội để một lần tải dở dang để lại model nạp được nhưng mất texture.

### 8.7 Trang quản trị xem được những gì

> Mục này mô tả nhánh **SPRITE** (VFX · icon · môi trường · UI). Ví dụ `char_ninja-fire` bên dưới là minh hoạ từ thời pre-render — sau [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md) nhân vật là `MODEL`, không còn nằm trong atlas; cơ chế parse `.atlas` vẫn nguyên giá trị cho mọi asset SPRITE.

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
| **Animation chạy thật** | Lần lượt vẽ các frame theo `setInterval` **12 fps** (`PLAYBACK_FPS`, §1.7) — đúng tốc độ trong game |
| **Toàn bộ atlas** kèm khung viền từng region | Vẽ ảnh atlas gốc + overlay các `bounds` |
| **So sánh 3 tier cạnh nhau** | Cùng `atlas_name`, đổi thư mục `low`/`med`/`high` (§8.5) |
| **Kiểm tra baseline/pivot** | `render-meta.json` có `pivot_x_px` + `pivot_y_px_from_bottom` — kẻ 1 đường ngang là thấy chân có thẳng hàng không |
| Danh sách 200 asset | Dùng `preview_url` (thumbnail nhẹ) — **đừng** tải atlas 4MB cho mỗi ô lưới |

**Quy ước đặt tên region — bắt buộc để cả game lẫn trang quản trị đều tra được**: pack với region name `<asset_key>_<action>` + `index` tăng dần (`char_ninja-fire_idle` index 0..3). Đây là dạng libGDX gốc: game gọi `atlas.findRegions("char_ninja-fire_idle")` là ra đúng mảng frame cho `Animation`; trang quản trị lọc cùng tiền tố đó. Nếu pack thành region rời tên `..._idle_00` thì `findRegions()` **không** gom được — hỏng cả hai phía.

⚠️ **Không copy toạ độ region vào DB.** File `.atlas` đã là nguồn sự thật và đọc được bằng máy; nhân bản vào DB chỉ tạo thêm chỗ để lệch. DB giữ *asset là gì*, `.atlas` giữ *nó nằm đâu trong ảnh*.

### 8.8 Trang quản trị xem asset `MODEL` — **thiết kế đề xuất, đi kèm V20**

⚠️ Cùng trạng thái với [§8.6](#86-nhánh-3d-trong-art_asset--thiết-kế-đề-xuất-chờ-chủ-dự-án-duyệt): mô tả
thiết kế, **chưa thi hành**. Không viết code trang quản trị khi `asset_kind` chưa tồn tại.

**Điểm khác cốt lõi so với nhánh SPRITE**: với sprite, thứ mà tier thay đổi **nhìn thấy được trong
ảnh** (@0.5x mờ hơn @1x). Với model, ba tier khác nhau ở **số tam giác và cỡ texture** — tức **con số,
không phải pixel** ([§4.4](#44-ngân-sách-tam-giác-nhánh-3d) đo được: bản 5k và bản 93k *nhìn không
phân biệt được*). Nên đặt 3 ảnh cạnh nhau để "so tier" là dùng sai công cụ: đúng cách là **một bảng số**.

| Xem được | Cách | Dữ liệu lấy từ |
|---|---|---|
| Danh sách kho (chung cả 2 nhánh) | `preview_url` thumbnail — **không đổi**, đó là lý do cột này dùng chung | `art_asset.preview_url` |
| **Bằng chứng nghiệm thu** | Contact sheet đã render lúc chấm Q1–Q12 ([ART-QUALITY-STANDARD §7](ART-QUALITY-STANDARD.md)) | `render_dir` — với `MODEL` cột này đổi *vai trò*, xem §8.6 |
| **So 3 tier** | Bảng `tri_counts` × `texture_px` × dung lượng `.glb` | `model-meta.json` → `art_asset.tri_counts` |
| Bộ action có thật trong model | Danh sách animation đọc từ sidecar, đối chiếu bộ chuẩn §4.2 | `model-meta.json.actions` |
| Model từ **góc khác** góc nghiệm thu | Turntable — xem dưới | Chưa code |

**Turntable là thứ nhánh sprite chưa từng cần.** Trước [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md)
mọi asset chỉ tồn tại ở đúng một góc nhìn, nên "nhìn từ góc khác" là câu hỏi vô nghĩa. Giờ nhân vật
xoay tự do lúc chạy, và **mặt sau của mesh Rodin chưa từng được ai nghiệm thu** — vòng Q1–Q12 chấm trên
`idle` nhìn nghiêng. Đề xuất: `export_character_lods.py` render thêm **8 frame quay quanh trục Z** vào
`render_dir`, trang quản trị phát như ảnh động. Rẻ (dùng lại `kage_sprite_render`), không thêm dependency,
và bịt đúng một lỗ nghiệm thu có thật.

**Viewer 3D trong trình duyệt (`<model-viewer>` / three.js) — đề xuất KHÔNG làm**, ít nhất tới khi có
nhu cầu thật: nó thêm một dependency JavaScript vào trang quản trị để trả lời câu hỏi mà turntable 8
frame đã trả lời gần hết, trong khi thứ *thực sự* quyết định model đạt hay không là bảng số ở trên. Nếu
sau này cần xoay/zoom tự do thì `.glb` là chuẩn mở nên mở lại lựa chọn này lúc nào cũng được — không có
gì bị khoá.

⚠️ **Không dựng viewer bằng cách giải nén `.glb` phía server.** Cùng lý do với luật "không copy toạ độ
region vào DB" ở trên: file đã là nguồn sự thật, nhân bản ra chỗ khác chỉ tạo thêm chỗ để lệch.

---

## 9. Technical Decisions

| # | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| 1 | ~~Blender pre-render ra sprite, không realtime 3D~~ → **nhân vật render 3D thời gian thực (2.5D); VFX/icon vẫn pre-render** | Bỏ trần 12 fps, bỏ khoá góc nhìn vào tài sản, mở đường cho trang bị nhìn thấy được | 🔀 **Thay bởi [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md)** (Accepted 2026-08-03) |
| 2 | ~~Chỉ render hướng phải, trái = flip code~~ | Không còn ý nghĩa — hướng nhìn là hằng số runtime | 🔀 Bỏ theo ADR-031 |
| 3 | Master @2x duy nhất → downscale @1x/@0.5x tại asset-pipeline | Render 1 lần, 3 tier atlas | **Còn hiệu lực — chỉ cho nhánh 2D** |
| 4 | 3 mức đồ họa LOW/MEDIUM/HIGH, chỉ tồn tại client-side | Bảng §5; không đụng Server Authority | Còn hiệu lực (ADR-031 đổi *nội dung* tier, không đổi cơ chế) |
| 5 | Animation 12fps on-twos, số frame cố định theo asset | Phong cách anime + ngân sách atlas; không phải setting | **Còn hiệu lực — chỉ cho VFX 2D**; nhân vật chạy theo fps thật |
| 5b | Nhân vật xuất **glTF 2.0**, client nạp bằng `gdx-gltf`; vẻ ngoài cel do shader runtime | Blender xuất glTF sẵn trong core, không qua công cụ ngoài đã bỏ hoang | **Chốt** — [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md) |
| 6 | Script chuẩn trong `tools/blender/`, MCP chỉ gọi hàm | Kỷ luật token §6.1 | Đề xuất |
| 7 | DB lưu **metadata** asset (bảng `art_asset`), binary ở file/patch | §8 — trang quản trị cần registry, client không nối DB | ✅ **Thi hành 2026-08-01** (`V19`, §8.2, verify Postgres thật); riêng phần mở rộng nhánh 3D (§8.6) chờ duyệt |
| 8 | Danh mục = bảng `art_category` phân cấp 2 tầng, asset gắn tầng lá | Chủ dự án yêu cầu chia category rõ ràng; thêm loại không cần migration | ✅ **Thi hành 2026-08-01** (`V19`, seed 6 tầng 1 + 19 tầng 2) |
| 9 | **nginx đọc đĩa** phục vụ file tĩnh; không dùng object storage | §8.4 — nhanh hơn (`sendfile()` zero-copy), không thêm dependency, patcher vốn dùng URL HTTP nên không có lock-in để chống | **Chốt** 2026-08-01 (chủ dự án loại MinIO) |
| 10 | VFX đặt tên `vfx_<skill_code>` khớp `SkillRegistry` | Asset map 1-1 với skill thật, không cần bảng tra | Đề xuất |
| 11 | Mọi asset **bắt buộc** có dòng trong `art_asset` kèm đường dẫn; pipeline fail build nếu thiếu | §8.5 — chủ dự án yêu cầu quản toàn bộ kho đồ họa qua trang quản trị; ràng buộc bằng công cụ, không bằng trí nhớ | Đề xuất |
| 12 | Pipeline xuất thêm **thumbnail rời** mỗi asset (`preview_url`) | §8.7 — chỉ để danh sách nhiều asset không phải tải atlas 4MB mỗi ô; **không** phải vì bị giới hạn hiển thị | Đề xuất |
| 13 | Region trong atlas đặt tên `<asset_key>_<action>` + `index` (dạng libGDX gốc) | §8.7 — `atlas.findRegions()` gom được frame cho `Animation`; trang quản trị lọc cùng tiền tố. Đặt sai kiểu `..._idle_00` là hỏng cả hai phía | Đề xuất |
| 14 | **Trang phục nướng vào mesh Text→3D; vũ khí luôn gắn rời lên xương** | §2.3 — ranh giới là "biến dạng theo thân hay không": vũ khí trong khối hàn liền sẽ bị `weight_by_proximity` bẻ cong khi gập khuỷu; ngoài ra Q7 FAIL thành vòng B miễn phí thay vì vòng A tốn credit. Trang phục chưa cần thay lúc chạy vì `item_template` không có cột hình ảnh | **Chốt** — suy ra từ [ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md) đã Accepted, không phải quyết định mới. Đảo lại khi `item_template` có cột hình ảnh |
| 15 | Xuất glTF **bỏ** viền inverted hull, far-shade và vật liệu toon; chỉ mang base-color texture | §6.4 bước 9 — cả ba là mẹo lúc render cho camera cố định, shader runtime dựng lại được; giữ lại thì viền biến thành hình học thật và **gấp đôi số tam giác**, còn vật liệu Emission thì glTF xuất ra **không có texture nào** | **Chốt** — đo thật, xem §4.4 |

## 10. ADR cần tạo / quyết định treo

- 🔀 **[ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md) Accepted 2026-08-03** — nhân vật chuyển sang render 3D thời gian thực (2.5D). Thay quyết định #1 và #2 ở §9. **Hai việc treo do ADR này sinh ra:**
  - ✅ **Ngân sách tam giác — đã đo 2026-08-05** (§4.4): 5k/10k/20k tam giác cho low/med/high, đo trên mesh pilot bằng `measure_triangle_budget.py`. Còn thiếu **dòng frame time**, chờ client vẽ được model đầu tiên.
  - 📝 **Phòng vệ JitPack — đã có [ADR-032](../01-architecture/adr/ADR-032-JitPack-Dependency-Defence.md), chờ chủ dự án duyệt.** Kiểm chứng thật: `gdx-gltf` **không có trên Maven Central**, jar 318 KB, Apache-2.0, phụ thuộc bắc cầu duy nhất là `gdx` vốn đã có. Đề xuất đóng gói vào `third-party/m2/`. **Chưa được thêm vào `libs.versions.toml`** cho tới khi ADR-032 được duyệt.
  - 🛑 **Mở rộng `art_asset` cho nhánh 3D — chờ chủ dự án duyệt.** Thiết kế đã viết đầy đủ ở §8.6 (schema) + §8.8 (trang quản trị)
    (3 cột `asset_kind`/`model_name`/`tri_counts` + `CHECK` chống lẫn nhánh, không tách bảng phụ).
    Là **DB schema change** nên không tự thi hành ([/CLAUDE.md §4](../../CLAUDE.md)); duyệt xong thì
    viết `V20__extend_art_asset_for_3d.sql`. **Chặn**: bước xuất glTF (§10 gạch đầu dòng trên) —
    ngày bước đó chạy mà chưa có cột này thì luật §8.5 "không có asset mồ côi" hở với mọi model.
- ✅ **ADR "Graphics Quality Tiers & Pre-render Pipeline"** — quyết định #3–#4 chốt qua thực tế đã triển khai (không tách ADR riêng, xem [ADR-028](../01-architecture/adr/ADR-028-Graphics-Tier-Asset-Delivery.md) cho phần phát hành).
- ✅ **Migration registry** (§8.2): `V19__create_art_registry.sql` (2026-08-01) — `art_category` + seed cây danh mục + `art_asset`, verify thật qua Postgres 17.
- **Lưu trữ file: không cần ADR** — §8.4 chốt nginx + đĩa, không thêm dependency nào.
- ✅ **Phát hành asset**: [ADR-028](../01-architecture/adr/ADR-028-Graphics-Tier-Asset-Delivery.md) Accepted (2026-08-01) — bản cài đóng gói **đúng 1 tier mặc định (MEDIUM)**, LOW/HIGH tải theo yêu cầu khi người chơi đổi mức đồ họa; client **luôn** gọi `ensureAssetsAvailable(tier)` kể cả khi asset có sẵn (chống nhánh code mục). Amend [ADR-024](../01-architecture/adr/ADR-024-Asset-Distribution-Strategy.md) ở đúng điểm "đóng gói toàn bộ"; phần hoãn CDN/OBB của ADR-024 vẫn giữ nguyên.
- ✅ **`tools/asset-pipeline`**: [ADR-029](../01-architecture/adr/ADR-029-Asset-Pipeline-Tool.md) Accepted (2026-08-01) — module `tools:asset-pipeline` + `gdx-tools` TexturePacker đóng gói atlas, **đã nối** bước upsert `art_asset` (V19) qua `ArtRegistryUpserter`.
- **Treo**: có làm bộ atlas @2x cho desktop High không (tốn thêm dung lượng patch)? · Blender tooling không cần ADR dependency (chạy dev-time, không vào runtime game).
- ✅ **Git LFS**: đã bật cho `assets/raw/**` từ 2026-08-01 (`.gitattributes`) — hết treo. Kỷ luật quota: xem §7 + [ART-QUALITY-STANDARD §8](ART-QUALITY-STANDARD.md).

## 11. Tài liệu liên quan

[ART-QUALITY-STANDARD.md](ART-QUALITY-STANDARD.md) (**thước đo ĐẠT/KHÔNG ĐẠT + vòng nghiệm thu A/B**) · [BLENDER-SESSION-GUIDE.md](BLENDER-SESSION-GUIDE.md) (thao tác từng bước) · [RESOURCE.md](../04-client/RESOURCE.md) (pipeline hạ nguồn, naming, atlas) · [RENDERING.md](../03-engine/RENDERING.md) (animation flyweight, particle pool, shader) · [GDD](../13-game-design/GAME-DESIGN-DOCUMENT.md) (5 hệ phái) · [tools/blender/README.md](../../tools/blender/README.md) (contract script) · [DATABASE.md](../05-server/DATABASE.md) + [ADMIN.md](../12-devtools/ADMIN.md) (registry §8)

## 12. Việc cần làm tiếp theo

1. ✅ **Hướng art đã được duyệt 2026-08-03.** Pilot Hỏa dựng bằng Text→3D (Hyper3D Rodin tier Detail)
   qua `kage_text3d_kit.py` — chủ dự án duyệt sau 3 vòng. Sheet thế hệ cũ (5 hệ × 3 bậc, khối hộp,
   bị bác 2026-08-02) giờ là **PLACEHOLDER**, sẽ bị thay khi 14 nhân vật còn lại được sản xuất.
   ✅ **Hết chặn từ 2026-08-05**: ngân sách tam giác đã đo (§4.4), cách gắn vũ khí đã chốt (§2.3 —
   luôn gắn rời), bước E đã code (`export_character_lods.py`). Sonnet chạy được ngay.
2. ✅ Dựng `kage_character_base.blend` (phiên Blender MCP đầu tiên, theo §3.3) — xong.
3. ✅ `tools/asset-pipeline` code xong (2026-08-01, [ADR-029](../01-architecture/adr/ADR-029-Asset-Pipeline-Tool.md)
   Accepted) — downscale @1x/@0.5x + validate tên + pack 3 bộ atlas (`gdx-tools` TexturePacker thật) +
   sinh `asset-manifest.json` đều chạy thật trên toàn bộ asset đang có (`./gradlew :tools:asset-pipeline:run`).
   HIGH tier sao chép trực tiếp từ MEDIUM (cùng độ phân giải @1x, chỉ khác hiệu ứng runtime — xem
   `GraphicsTier` javadoc).
4. ✅ `V19__create_art_registry.sql` viết + verify thật qua Postgres 17 thật (2026-08-01) — `art_category`
   (seed 6 tầng 1 + 19 tầng 2) + `art_asset`. Bước upsert đã nối vào `tools/asset-pipeline`
   (`ArtRegistryUpserter`, tuỳ chọn qua biến môi trường `DB_PASSWORD` — thiếu thì bỏ qua, không fail),
   verify thật: 37 asset đang có upsert đúng category/element/blend_path/actions, chạy 2 lần cho ra
   đúng 37 dòng (idempotent, không trùng). Phát hiện thật lúc verify: pgjdbc gửi `TimeZone.getDefault()`
   của JVM cho Postgres lúc connect, máy dev múi giờ "Asia/Saigon" (alias tzdata cũ) làm Postgres 17
   FATAL — cùng bug đã gặp ở `PostgresDataSourceFactory` (server-database), sửa lại đúng cách đã verify
   ở đó (`TimeZone.setDefault(UTC)`, không phải connection property).
5. Code `GraphicsQuality` trong client-core khi tới phase thích hợp (không code trước — Phase discipline).
