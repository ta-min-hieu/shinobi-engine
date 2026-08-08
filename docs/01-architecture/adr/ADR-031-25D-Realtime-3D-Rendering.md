# ADR-031: Nhân vật render 3D thời gian thực qua camera orthographic khoá góc (2.5D), không nướng sẵn thành sprite

| | |
|---|---|
| Status | ✅ **Accepted** (2026-08-03, chủ dự án xác nhận qua chat) |
| Date | 2026-08-03 |
| Deciders | Chủ dự án chốt hướng 2.5D và Accept (2026-08-03, qua chat); AI soạn |
| Related | **Amend [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md)** · **thu hẹp phạm vi [ADR-029](ADR-029-Asset-Pipeline-Tool.md)** · [ADR-002](ADR-002-Why-libGDX.md) (libGDX) · [ADR-020](ADR-020-Viewport-Strategy.md) (FitViewport) · [ART-PIPELINE.md §3](../../14-art/ART-PIPELINE.md) · [RENDERING.md](../../03-engine/RENDERING.md) · Điều 36 (thêm dependency phải có ADR) |

---

## Context

[ART-PIPELINE.md §3](../../14-art/ART-PIPELINE.md) chốt từ đầu là **pre-render**: dựng nhân vật 3D
trong Blender, render sẵn ra sprite sheet 2D, client chỉ việc vẽ ảnh. Lý do lúc đó đúng: client 2D
thuần thì nhẹ, không cần shader riêng, và không phải đụng tới phần 3D của libGDX.

Ba dữ kiện mới, tất cả xuất hiện trong ngày 2026-08-03, làm lung lay giả định đó.

**Một — pipeline giờ sản xuất ra model 3D có rig thật, không phải ảnh.** Pilot Text→3D
([kage_text3d_kit.py](../../../tools/blender/kage_text3d_kit.py)) nhận mesh sinh từ Hyper3D Rodin,
chuẩn hoá tỷ lệ, gắn rig 11 xương, gán trọng số và author 7 action. Sprite sheet chỉ còn là **bước
nướng cuối cùng** của một tài sản vốn đã là 3D. Bỏ bước nướng đó không mất gì đã làm.

**Hai — client chưa vẽ sprite nào.** `WorldScreen.render()` hiện vẽ hình chữ nhật bằng
`ShapeRenderer`; toàn bộ `client/` không có một chỗ nào dùng `TextureAtlas`. Không có dòng code nào
đang phụ thuộc vào lựa chọn này. Đây là thời điểm rẻ nhất để đổi, và chi phí sẽ chỉ tăng.

**Ba — nhịp phát sprite bị khoá cứng ở 12 fps.** `PLAYBACK_FPS = 12` trong
[kage_sprite_render.py](../../../tools/blender/kage_sprite_render.py) là cố ý ("on twos", luật
animation 2D), nhưng nó có nghĩa là **màn hình 60 Hz hay 144 Hz cũng không sinh thêm khung tranh
nào** — chỉ vị trí được nội suy mượt hơn (`drawInterpolated(entity, alpha)`). Muốn animation mượt
theo cấu hình máy thì buộc phải bỏ khung nướng sẵn.

Ràng buộc cần cân nhắc kèm:

- **Tổ hợp trang bị.** Hiện `item_template` không có cột hình ảnh nào — trang bị chỉ là chỉ số. Nếu
  sau này muốn trang bị *nhìn thấy được*, sprite nướng sẵn nổ theo tổ hợp (đã 15 sheet chỉ cho
  5 hệ × 3 bậc), còn model 3D chỉ là gắn thêm mesh vào xương.
- **Mobile chưa chốt.** [ADR-023](README.md) (mobile input) vẫn chưa viết vì Android chưa được
  quyết là target. Poly budget của phương án 3D phụ thuộc trực tiếp vào câu đó.
- **VFX và icon không nên đổi.** Repo đã có **17 bộ VFX kỹ năng** và **3 icon vật phẩm** dựng theo
  đường sprite. Làm lại chúng thành hệ hạt 3D là một khối lượng công việc riêng, không nằm trong
  vấn đề mà ADR này giải quyết.

## Decision

**Nhân vật, quái và NPC được render 3D thời gian thực qua một camera orthographic khoá cứng góc
nhìn; VFX kỹ năng, icon vật phẩm và UI vẫn là sprite 2D như hiện nay.** Model glTF nạp bằng
**`com.github.mgsx-dev.gdx-gltf:gltf:2.3.0`** (Apache-2.0), vẻ ngoài cel do shader tự viết đảm nhiệm.

Góc camera trở thành **hằng số runtime**, không còn là thuộc tính nướng vào ảnh.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **A. Giữ pre-render sprite** (ART-PIPELINE §3 hiện tại) | Không thêm dependency; client 2D thuần, đã có `tools:asset-pipeline` chạy thật; VRAM đo được và nhỏ (~49 MB cho toàn bộ 598 region) | Animation khoá ở 12 fps; mỗi góc nhìn là một lần render lại; trang bị nhìn thấy được thì nổ tổ hợp; mỗi lần chỉnh art phải render lại 33 khung | **Không chọn** — không đáp ứng yêu cầu "chỉnh fps tuỳ ý", và khoá cứng góc camera vào tài sản |
| **B. 3D bằng `ModelBatch` có sẵn của libGDX, định dạng `.g3db`** | **Không thêm dependency nào**; `ModelBatch` + `AnimationController` đã nằm trong `gdx-core` | Phải đi qua `fbx-conv` — công cụ ngoài, gần như bỏ hoang, và Blender không xuất FBX cho nó một cách tin cậy; `.g3db` là định dạng riêng của libGDX, không công cụ nào khác đọc được | **Không chọn** — tiết kiệm một dependency nhưng đổi lấy một toolchain chết nằm giữa Blender và game |
| **C. 3D bằng `gdx-gltf`, định dạng glTF 2.0** ✅ | Blender **xuất glTF sẵn trong core**, không cần công cụ trung gian; glTF là chuẩn mở, đọc được bằng mọi thứ; Apache-2.0; 2.3.0 phát hành 2026-02-05 nên còn sống | Dependency mới; phát hành qua **JitPack** chứ không phải Maven Central; từng vỡ vì thay đổi collection của libGDX 1.14.x | **Chọn** — glTF nối thẳng Blender với game, không có mắt xích chết ở giữa |
| **D. Nướng sprite 8 hướng thay vì 1 hướng** | Vẫn là 2D thuần; nhân vật quay được | Nhân số tài sản lên **8 lần**, vẫn khoá 12 fps, vẫn nổ tổ hợp với trang bị | **Không chọn** — trả gấp 8 lần chi phí để mua một phần nhỏ của thứ phương án C cho không |

Về rủi ro tương thích của phương án C: libGDX **1.14.2** — đúng phiên bản dự án đang dùng
(`libs.versions.toml`) — đã hoàn tác các thay đổi collection từng làm gdx-gltf crash lúc chạy. Đây là
một lý do cụ thể để **không** nâng libGDX một cách tuỳ tiện sau khi ADR này được chấp nhận.

## Consequences

**Tích cực:**

- Animation chạy đúng tần số khung hình thật của máy, không còn trần 12 fps.
- Góc camera đổi được bất cứ lúc nào bằng một hằng số — câu hỏi "nghiêng thuần 90° hay 3/4 35°"
  đang treo trong pilot art **tự tan**, vì nó không còn được nướng vào tài sản.
- Nhân vật quay hướng tự do; hết trò lật ngang ảnh, hết việc render riêng cho mỗi hướng.
- Trang bị nhìn thấy được (nếu sau này muốn) là gắn mesh vào xương, không phải nhân tổ hợp sheet.
- Sửa art không cần render lại — mở file, sửa, xuất.
- `add_inverted_hull_outline()` trong `kage_text3d_kit.py` **chuyển thẳng sang runtime** được: viền
  vốn chỉ là một lượt vẽ thứ hai với mặt trước bị cull. Cel ramp cũng vậy.

**Đánh đổi phải chấp nhận:**

- Thêm dependency ngoài Maven Central. JitPack **build artifact theo yêu cầu từ GitHub**: repo gốc
  biến mất là build gãy. Phải chốt cách phòng vệ (mirror hoặc vendor jar) trước khi phụ thuộc thật.
- Toon phải viết lại thành GLSL. Freestyle **không tồn tại lúc runtime** — may là pilot đã bỏ
  Freestyle sang inverted hull vì lý do khác (nó vẽ viền cho từng lọn tóc).
- **Poly budget thành ràng buộc thật.** Mesh pilot hiện **93k tam giác**; 20 nhân vật trên màn là
  1,9 triệu tam giác mỗi khung. Desktop chịu được, máy yếu thì không. Phải thêm bước decimate vào
  pipeline art, đích ~5–10k tam giác/nhân vật, và **phải đo chứ không đoán** (Điều 9).
- Vi phạm tiềm tàng luật "không cấp phát trong game loop" (CLAUDE.md §6): `ModelBatch` và
  `AnimationController` có nhiều bẫy cấp phát mỗi khung hơn `SpriteBatch`. Phải soi bằng benchmark.
- ART-PIPELINE.md §3 và toàn bộ mô tả bước render phải viết lại.

**Không bị ảnh hưởng như thoạt tưởng:**

- [ADR-029](ADR-029-Asset-Pipeline-Tool.md) (`tools:asset-pipeline` + TexturePacker) **vẫn cần** —
  VFX, icon và UI vẫn đóng gói atlas. ADR này chỉ **thu hẹp phạm vi**, không thay thế.
- [ADR-028](ADR-028-Graphics-Tier-Asset-Delivery.md) (3 tier đồ hoạ) **vẫn đứng vững** — tier chỉ
  đổi ý nghĩa: từ "độ phân giải sprite" sang "mức LOD của model + kích thước texture". Đường code
  `ensureAssetsAvailable(tier)` không đổi. Đây là **amend**, không phải supersede.
- [ADR-020](ADR-020-Viewport-Strategy.md) (FitViewport) không đổi: công bằng PvP tính bằng world
  unit, camera orthographic giữ nguyên tính chất đó.

**Module bị ảnh hưởng:**

- `client:client-core` — thêm đường render 3D; `WorldScreen` chuyển từ `ShapeRenderer` sang
  `ModelBatch` cho nhân vật, `SpriteBatch` cho VFX/UI.
- `client:client-desktop` — khai báo dependency `gdx-gltf` + repository JitPack.
- `tools:asset-pipeline` — thêm bước decimate/xuất glTF; giữ nguyên nhánh atlas cho VFX/icon.
- `engine:engine-core` — nếu tách interface render ra Port thì nằm ở đây (chưa quyết, không thuộc
  phạm vi ADR này).
- **Không** đụng tới `server:*` — Server Authority không quan tâm client vẽ bằng gì.

## Revisit Trigger

Xem lại quyết định này khi **một trong ba** điều kiện sau xảy ra:

1. Benchmark client với **20 nhân vật trên màn** cho frame time GPU **> 8 ms** trên máy tham chiếu
   của dự án, sau khi đã decimate xuống ngưỡng đã chốt.
2. Android được chốt là target **và** máy tầm thấp trong danh sách hỗ trợ không giữ nổi **30 fps**
   ở 10 nhân vật trên màn.
3. JitPack ngừng phục vụ artifact `com.github.mgsx-dev.gdx-gltf`, hoặc gdx-gltf không còn phát hành
   bản tương thích trong **12 tháng** kể từ lần libGDX phát hành lớn kế tiếp.

**Trạng thái điều kiện #1 — đã đo, 2026-08-08 (KHÔNG kích hoạt).**
`CharacterModelFrameBenchmarkTool` (`client-core/src/test/`, Gradle task `runFrameBenchmark`) dựng
20 `Scene` qua đúng `CharacterModelRenderer` sản xuất, tier MEDIUM, cycle 15 tổ hợp hệ phái×bậc trang
phục thật. Kết quả (2 lần chạy × 6000 khung, máy dev iGPU Intel `RaptorLake-S Mobile Graphics
Controller`, `GL_VERSION 4.6.0`): **p95 0,30–0,31 ms, max 1,28–2,92 ms** — dưới ngưỡng 8 ms hơn 25 lần
kể cả ở `max`. Số liệu đầy đủ + 4 giới hạn phải đọc trước khi coi trigger này "đóng vĩnh viễn" (chưa
có máy tham chiếu chính thức của dự án, số đo trên shader mặc định gdx-gltf — chưa có cel shader/viền
bắt buộc theo RENDERING §12.2 sẽ làm tăng chi phí, `CharacterModelRenderer` chưa cull, đo trên desktop
không phải Android) ở [ART-PIPELINE §4.4](../../14-art/ART-PIPELINE.md). **Không tự ý coi là "đóng
hoàn toàn"** — biên độ an toàn lớn (>25 lần) nhưng máy tham chiếu thật của dự án vẫn chưa được chủ dự
án chốt spec; đây là số liệu thật đầu tiên để tham khảo, không phải lời tuyên bố cuối cùng thay chủ dự án.

**Cập nhật cùng ngày (2026-08-08), sau khi cel shader/viền (RENDERING §12.2) hoà vào cùng bản:** đo lại
trên chính máy đó — **p95 0,52 ms**, tăng đúng bằng việc thêm lượt vẽ viền (2 lượt/nhân vật thay vì 1)
như dự đoán ở giới hạn thứ hai phía trên, vẫn dưới ngưỡng 8 ms hơn 15 lần. Giới hạn "chưa có cel
shader/viền" coi như đóng; 3 giới hạn còn lại (máy tham chiếu, culling, Android) vẫn mở nguyên. Chi
tiết ở [ART-PIPELINE §4.4](../../14-art/ART-PIPELINE.md).

---

## Technical Decisions

- Nguồn model: glTF 2.0 xuất trực tiếp từ Blender, không qua `fbx-conv`.
- Thư viện: `com.github.mgsx-dev.gdx-gltf:gltf:2.3.0`, Apache-2.0, qua JitPack.
- Khoá libGDX ở `1.14.2` cho tới khi kiểm chứng gdx-gltf trên bản mới hơn.
- Phạm vi 3D: nhân vật · quái · NPC. Phạm vi 2D giữ nguyên: VFX kỹ năng · icon vật phẩm · UI.

## ADR cần tạo

- **ADR-023** (mobile input) vẫn đang chặn: poly budget ở Revisit Trigger #2 không đóng được chừng
  nào Android chưa được chốt là target hay không.
- Cần một ADR riêng nếu chọn cách phòng vệ JitPack bằng vendor jar vào repo (đụng chính sách
  dependency).

## Tài liệu liên quan

- [ART-PIPELINE.md](../../14-art/ART-PIPELINE.md) — §3 phải viết lại sau khi ADR này Accepted
- [RENDERING.md](../../03-engine/RENDERING.md) — nơi mô tả đường render client
- [RESOURCE.md](../../04-client/RESOURCE.md) — §4, §5, §9 mô tả đóng gói tài sản
- [TECHNOLOGY-STACK.md](../TECHNOLOGY-STACK.md) — phải thêm gdx-gltf sau khi Accepted

## Việc cần làm tiếp theo

1. ✅ Chủ dự án đã Accept (2026-08-03, qua chat).
2. ✅ **Xong 2026-08-06** — cách phòng vệ JitPack chốt ở
   [ADR-032](ADR-032-JitPack-Dependency-Defence.md) (**Accepted**, thi hành cùng ngày): `gdx-gltf`
   đóng gói vào `third-party/m2/`, `jitpack.io` bỏ hẳn khỏi build. Điều kiện Revisit Trigger #3 ở
   trên **tự vô hiệu**.
3. ✅ **Đo poly budget — xong 2026-08-05.** Decimate mesh pilot xuống 5k/10k/20k/40k, so ảnh từng
   pixel, xuất `.glb` thật: ngưỡng 5k/10k/20k cho low/med/high, số liệu ở
   [ART-PIPELINE §4.4](../../14-art/ART-PIPELINE.md). Cảnh báo "mesh pilot 93k tam giác" ở phần
   Consequences là **đo đúng**. Phần **chưa** làm được: frame time GPU — cần chính việc #2 và #5 xong trước.
4. ✅ **Xong** — ART-PIPELINE §3 viết lại 2026-08-03; [RENDERING.md](../../03-engine/RENDERING.md)
   viết lại 2026-08-07 (v2.0.0, mô tả **cả hai** nhánh render + tình trạng code thật).
5. ✅ **ĐÃ NHÌN THẤY CHẠY THẬT — 2026-08-07.** Client + server + Postgres/Redis thật, có ảnh chụp:
   nhân vật Hoả đứng thẳng, có texture, thay cho hình hộp. Ba bug thật chỉ lộ ra khi chạy, không
   test nào bắt được, đều đã sửa:
   - Asset root chưa nối — `Gdx.files.internal` phân giải theo working directory, mà cwd mặc định
     của task `run` là thư mục **subproject** (`27a2f12`).
   - **`"texCoord": -1` trong MỌI file `.glb`** — mesh vũ khí không có UV map nhưng vẫn được gán
     material có texture, Blender xuất ra giá trị glTF không hợp lệ, gdx-gltf sinh shader hỏng và
     **sập cả màn hình**, không riêng vũ khí (`1b4bc61`).
   - Model Y-up (đúng chuẩn glTF) trong thế giới Z-up ⇒ nhân vật nằm nghiêng (`0108857`).
6. ✅ **Chủ dự án duyệt 2026-08-07** — `CharacterSpawnedEvent` đã có `faction` + `costume_tier`
   (`92e9f15`), mỗi hệ phái hiện đúng model của mình. `costume_tier` vẫn là hằng số `B1` vì gameplay
   chưa có khái niệm bậc trang bị nào để suy ra; protocol đã sẵn field cho lúc có.
7. ✅ **Đóng 2026-08-07 — KHÔNG PHẢI bug engine, là bug công cụ chụp màn hình.** In log thật
   (`Gdx.graphics.getWidth/Height`, `getBackBufferWidth/Height`, `viewport.getScreenX/Y/Width/Height`,
   `camera.position/direction/up`) xác nhận toán camera VÀ GL viewport đều đúng: resize log ra
   `width=1280 height=720 backBufferW=1280 backBufferH=720 viewport.screen=(280,0) size=(720,720)`
   (đúng letterbox `FitViewport` 10×10 đơn vị cho cửa sổ 1280×720), follow log ra
   `camera.position=(9.93,14.19,10.0)` khớp chính xác phép tính tay từ `target=(0,0,0)` +
   `VIEW_YAW_DEGREES`/`VIEW_PITCH_DEGREES`/`CAMERA_DISTANCE`. Script PowerShell chụp màn hình
   (`PrintWindow`) dùng ở việc #5 chạy trong tiến trình KHÔNG khai báo DPI-aware, nên
   `GetWindowRect` trả về toạ độ đã bị Windows chia theo tỉ lệ scale màn hình (ví dụ 1280×720 thật
   báo về 745×448) — bitmap đích chỉ to bằng kích thước bị thu nhỏ đó nên `PrintWindow` chỉ chép
   được góc trên-trái của khung hình thật, cắt cụt phần dưới-phải. Nhân vật vốn đã đứng đúng giữa
   khung 1280×720 thật; ảnh cũ chỉ là ảnh bị **crop nhầm**, không phải camera lệch. Sửa script chụp
   bằng `SetThreadDpiAwarenessContext(-4)` trước khi gọi `GetWindowRect`/`PrintWindow` → ảnh chụp
   lại cho thấy nhân vật đúng giữa màn hình. Bài học: mọi tool chụp màn hình Win32 trên máy này
   PHẢI khai báo DPI-aware trước. Revisit Trigger #1 (frame time GPU) vẫn CHƯA đo — việc #8 (chặn
   trước đó) đã xong, chỉ còn việc #9 (ngân sách tam giác, chờ chủ dự án) đứng giữa.
8. ✅ **Xong 2026-08-08 — xuất lại đủ 15 nhân vật × 3 tier (45 file).** Chạy qua Blender MCP: mở
   từng `.blend`, tự nhận diện mesh thân bằng số vertex lớn nhất trong các mesh có modifier
   `ARMATURE` (mesh vũ khí/phụ kiện nhỏ hơn nhiều, ~16-24 vertex so với ~44-48k của thân) — khớp
   100% với ground truth đã biết của `char_hoa-b1` (object `Hoa_D`, rig `hoa_d_Rig`). Xác nhận
   `hoa-b1.blend` (15 file `.blend` per-tier trên đĩa) **không phải** nguồn thật của `char_hoa-b1` —
   sidecar `char_hoa-b1.model-meta.json` ghi rõ nguồn là `hoa-text3d-pilot.blend`; `hoa-b1.blend` là
   file thừa không dùng tới. ⚠️ **Gotcha**: gọi `bpy.ops.wm.open_mainfile()` rồi thao tác luôn trong
   CÙNG một lần gọi `execute_blender_code` làm `bpy.ops.object.modifier_move_up` báo lỗi "context is
   incorrect" — phải tách `open_mainfile` và phần export/decimate thành **hai lần gọi MCP riêng**,
   để Blender kịp ổn định context giữa hai bước. Grep xác nhận không còn `"texCoord":-1` trong bất
   kỳ file nào trong 45 file. Chạy `tools:asset-pipeline` xác nhận cả 45 file đọc/đo tam giác được
   bình thường (không sập nữa) — fail đúng vào ngân sách tam giác như việc #9 mô tả, không phải lỗi
   mới.
9. ✅ **Đóng 2026-08-08.** Chủ dự án chốt: decimate xuống `ngưỡng − vũ khí` (giữ nguyên ý nghĩa
   "ngưỡng" §4.4 = tổng đã ship, không sửa tài liệu). Sửa `export_character_lods.py` (`b774932`) rồi
   xuất lại đủ 45 file qua Blender MCP cùng phiên. `tools:asset-pipeline` xác nhận
   `ngân sách tam giác: OK (45/45)`, chạy hết tới bước đóng gói atlas + `xong ->` mà không lỗi (chỉ
   bỏ qua upsert `art_asset` vì không có `DB_PASSWORD` lúc chạy — nạp thật khi có Postgres, không
   phải lỗi). Revisit Trigger #1 (frame time GPU) giờ không còn gì chặn.
10. ✅ **Đo frame time GPU thật — xong 2026-08-08, cùng phiên.** Xem "Trạng thái điều kiện #1" ở mục
    Revisit Trigger phía trên và [ART-PIPELINE §4.4](../../14-art/ART-PIPELINE.md) cho số liệu đầy đủ +
    giới hạn phép đo. Kết luận ngắn: **không kích hoạt** trigger (p95 ≈0,3 ms, dưới ngưỡng 8 ms hơn 25
    lần), nhưng "máy tham chiếu của dự án" nêu ở điều kiện #1 chưa từng được chốt spec cụ thể ở đâu
    trong docs — số đo trên máy dev (iGPU Intel) là bằng chứng thật đầu tiên, không phải phép đo trên
    máy tham chiếu đã duyệt (vì máy đó chưa tồn tại bằng văn bản).
11. ✅ **Kiểm chứng bằng mắt qua `WorldScreen` thật — phát hiện bug thật 2026-08-08, ĐÃ SỬA phiên
    sau (cùng ngày).** Dựng lại môi trường dev đầy đủ (client + server-gateway TLS + Postgres/Redis
    dev container), tạo nhân vật hệ Hỏa qua UI thật, quan sát idle/di chuyển/bị đánh trúng. Cel
    shading + viền render đúng, nhưng **nhân vật hiện ra nằm ngang thay vì đứng thẳng** ở cả ba trạng
    thái — khác ảnh xác nhận "đứng thẳng" của việc #5 (2026-08-07, nhưng đó là bind pose, TRƯỚC khi
    animation được nối, `89f1e19`).

    Nghi vấn ban đầu (node gốc `.glb` mang sẵn quaternion xoay ~35° quanh Y, trùng `VIEW_YAW_DEGREES`)
    **SAI, đã loại trừ hoàn toàn** — không phải asset/Blender, không cần re-export bất kỳ file `.glb`
    nào trong 15 file. Nguyên nhân thật là **2 lỗi Java/GLSL** ở `client-core/world/`: (1)
    `CelBodyShader`/`CelOutlineShader` cố ý bỏ nhân `u_worldTrans` dựa trên tiền đề sai (đã kiểm bằng
    cách đọc thẳng `ModelInstance.getRenderable()` thật trong `gdx-1.14.2-sources.jar` — với mesh có
    skin, `Renderable.worldTransform` = `ModelInstance.transform`, KHÔNG bao giờ nằm trong
    `bones[i]`); (2) `CharacterModelRenderer.Y_UP_TO_Z_UP` có dấu sai (`+90f` thay vì `-90f`) — lỗi
    này vô hại khi lỗi (1) còn tồn tại nên chưa từng lộ ra trước đó. Xác nhận sửa đúng bằng ảnh chụp
    thật qua đúng đường vẽ production (không phải suy đoán), cả clip `idle` lẫn `run`. `./gradlew
    build` xanh sau khi sửa. Chi tiết đầy đủ (từng bước điều tra + bằng chứng) ở
    [RENDERING.md §11.1 "Kiểm chứng bằng mắt 2026-08-08"](../../03-engine/RENDERING.md), bao gồm cả
    việc sửa lại nguyên văn chẩn đoán sai ban đầu ở §12.2 mục 2 (để làm bài học, không xoá lịch sử).
    Revisit Trigger #1 (frame time) không bị ảnh hưởng bởi bug này lẫn cách sửa — đo hiệu năng vẫn
    đúng.
