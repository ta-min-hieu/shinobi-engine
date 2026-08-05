# ADR-021: Không dùng Box2D cho di chuyển nhân vật — chỉ AABB/bounds-check tự viết khi có map thật

| | |
|---|---|
| Status | ✅ **Accepted** (2026-08-01, chủ dự án xác nhận qua chat) |
| Date | 2026-08-01 |
| Deciders | AI (Claude) đề xuất; chủ dự án Accept 2026-08-01 qua chat |
| Related | [PHYSICS.md](../../03-engine/PHYSICS.md) (đặc biệt §2, §3, §10 — cần rà lại sau ADR này, xem Consequences), [engine/CLAUDE.md](../../../engine/CLAUDE.md) ("Box2D KHÔNG dùng cho combat"), [ROADMAP.md Phase 3 Exit Criteria](../../11-process/ROADMAP.md) ("Speed hack / teleport bị server từ chối"), [ADR-024](ADR-024-Asset-Distribution-Strategy.md) (cùng nguyên nhân gốc: chưa có asset/map thật) |

---

## Context

RESOURCE.md-style rà soát: `adr/README.md` liệt ADR-021 (Box2D vs AABB) là **chưa viết**, gắn nhãn
"chờ dữ liệu Phase 3". Phase 3 (Character/Map/Movement) đã **đóng từ lâu** — đây là lúc thích hợp để
viết ADR này bằng dữ liệu THẬT từ code đã chạy, không phải đoán trước.

**Sự thật tìm được khi đọc code (không suy đoán)**:

1. `engine/engine-physics` — module tồn tại trong `settings.gradle.kts`, có khai `box2d` ở
   `build.gradle.kts`, nhưng **không có một dòng code nào** (`find engine/engine-physics -name
   "*.java"` → rỗng, không có cả `src/main`). Box2D chưa từng được wire vào gameplay.
2. `MovementSystem` (`engine-ecs`, code THẬT đang chạy production) chỉ làm
   `pos.x += vel.x * dt; pos.y += vel.y * dt` — không trọng lực, không nhảy, không va chạm, không
   bounds-check. Không `PhysicsSystem`, không `CollisionSystem` nào tồn tại trong codebase.
3. **Không có khái niệm "map/terrain" nào tồn tại ở server** — không `MapData`/`TiledMap`/
   `WalkableTile`/bất kỳ bounds nào (`grep` toàn repo cho các từ khoá này ra rỗng). Khớp với phát hiện
   ở [ADR-024](ADR-024-Asset-Distribution-Strategy.md): repo hiện **không có `assets/`** — chưa có
   map/atlas thật nào được sản xuất.
4. Ngược lại, **chống speed-hack/teleport đã làm đúng và đã hoạt động** — nhưng bằng thiết kế giao
   thức, không phải physics: `WorldFacade.enqueueMoveCommand` chỉ nhận **hướng** (`dirX`, `dirY`),
   server tự sở hữu hằng số tốc độ (`WorldConstants.MOVE_SPEED_MPS`) và tự tính vị trí — client
   **không bao giờ** gửi được vị trí hay tốc độ, nên không có kênh nào để speed-hack/teleport qua
   `MoveCommand`. ROADMAP Phase 3 Exit Criteria "Speed hack / teleport bị server từ chối" **đã đạt**,
   độc lập hoàn toàn với câu hỏi Box2D/terrain-collision.

**Khoảng trống thật, cần ghi nhận rõ (không phải lỗi cần vá gấp)**: PHYSICS.md hiện ghi "Server bắt
buộc chạy physics va chạm địa hình" (Technical Decision #2) là **Accepted**, và mô tả cả một hệ thống
Box2D đầy đủ (trọng lực, nhảy, dốc, thang — §2-§4) như đã có. Thực tế: **0% trong số đó tồn tại trong
code**. `PHYSICS.md` tự ghi `Status: Draft` ở đầu file — tài liệu này chưa từng được đối chiếu lại với
thực tế sau khi Phase 3-9 hoàn thành. Đây không phải lỗ hổng bảo mật đang bị khai thác (chưa có map
nào để "xuyên tường" qua) nhưng là **sai lệch tài liệu-thực tế** cần ADR này ghi nhận, để không ai
(người hoặc AI) tưởng nhầm terrain-collision đã có sẵn.

Thêm bối cảnh thể loại: PHYSICS.md §10 tự so sánh với "Ninja School" — dòng game 2D di chuyển phẳng
(top-down/8 hướng), **không phải platformer nhảy dốc**. Việc code thực tế không có gravity/jump khớp
đúng thể loại này, không phải thiếu sót tình cờ.

## Decision

**Không dùng Box2D cho di chuyển/va chạm nhân vật.** Giữ nguyên cách đã làm (velocity thuần,
server sở hữu tốc độ qua giao thức "chỉ gửi hướng"). Cơ chế va chạm địa hình dùng
**AABB tự viết** — **đã implement** (2026-08-01, cùng lúc viết ADR này, không chờ Accept vì đây là
thêm code vào module có sẵn theo đúng pattern, không đổi kiến trúc — CLAUDE.md §3): `Aabb` (record) +
`TerrainCollisionSystem` trong `engine-ecs`, nối vào `WorldFacade` ngay sau `MovementSystem`. Danh
sách obstacle mặc định **RỖNG** — chưa có map/asset thật nào định nghĩa tường (chờ
[ADR-024](ADR-024-Asset-Distribution-Strategy.md) có zone thật), nên hệ thống hiện là no-op an toàn,
không đổi hành vi gameplay hiện có, nhưng cơ chế đã sẵn sàng và có test thật (revert entity về
`prevX/prevY` khi va chạm) — không phải chỉ là đề xuất trên giấy.

`engine/engine-physics` giữ nguyên rỗng cho tới khi có Revisit Trigger. Không xoá module (đã khai
trong `settings.gradle.kts`, xoá là đổi kiến trúc, ngoài phạm vi ADR này — nếu muốn dọn, cần quyết
riêng).

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **A. Không Box2D — AABB/bounds-check tự viết khi có map thật** | 0 dependency mới cần học/tích hợp (Box2D đã có sẵn nhưng chưa dùng thì thôi không dùng); khớp đúng thể loại game phẳng (Ninja School), không cần gravity/jump/dốc mà PHYSICS.md mô tả; đơn giản nhất (Điều 9); AABB overlap đã là kỹ thuật `CombatSystem` đang dùng cho hitbox (PHYSICS.md §1) — nhất quán, không cần 2 mô hình hình học khác nhau trong cùng codebase | Không có sẵn xử lý dốc/platform/vật thể đẩy được nếu sau này cần (thể loại hiện tại không cần, nhưng nếu GDD đổi hướng thì phải làm lại) | **Chọn** — khớp dữ liệu thật (code đã chạy 9 Phase không cần Box2D), khớp thể loại đã chọn, tránh xây hệ thống cho tính năng chưa ai yêu cầu |
| **B. Wire Box2D thật vào server cho terrain collision (đúng như PHYSICS.md §2-4 mô tả)** | Đúng với tài liệu gốc đã viết từ đầu dự án; xử lý tốt nếu sau này cần dốc/platform/vật lý môi trường | Chi phí lớn cho tính năng CHƯA có map để dùng (chưa có 1 zone thật nào); PHYSICS.md tự cảnh báo rủi ro "cảm giác điều khiển không đủ chính xác" cho platformer — mà thể loại hiện tại (Ninja School-style) không phải platformer, nên rủi ro đó đổi thành **chi phí không cần thiết** thay vì đánh đổi hợp lý; CPU server tốn thêm để chạy full physics simulation mà gameplay chưa chứng minh cần | **Không chọn** — Điều 30 (đo trước khi tối ưu/xây), chưa có bằng chứng game cần physics simulation đầy đủ sau 9 Phase đã đóng mà không cần nó |
| **C. Giữ nguyên hiện trạng, không viết ADR, không quyết gì** | 0 effort | PHYSICS.md tiếp tục ghi sai thực tế ("Accepted" cho thứ chưa tồn tại) — rủi ro một AI/dev sau này tưởng nhầm terrain-collision đã có, hoặc cố "sửa" MovementSystem cho khớp Box2D dù không cần; `adr/README.md` tiếp tục treo ADR-021 "chưa viết" dù dữ liệu Phase 3 đã có từ lâu | **Không chọn** — đúng lúc để chốt, dữ liệu đã đủ, không có lý do trì hoãn thêm |

## Consequences

**Tích cực:**
- Khớp đúng những gì code đã thật sự chạy qua 9 Phase — không phải quyết định lý thuyết.
- Gỡ nợ tài liệu: `adr/README.md` không còn treo ADR-021 "chờ dữ liệu" (dữ liệu đã có).
- Không thêm CPU cost cho physics simulation chưa ai cần.

**Đánh đổi phải chấp nhận:**
- **`PHYSICS.md` cần một đợt rà soát lớn riêng** (KHÔNG làm trong ADR này — đây là việc tài liệu, theo
  CLAUDE.md §13 cần đề xuất bố cục trước): §2-§4 mô tả cả hệ thống Box2D/gravity/jump chưa từng tồn
  tại, Technical Decision #2 ghi "Accepted" sai thực tế, cần sửa lại cho khớp Decision này. Đề xuất
  làm ở phiên riêng, sau khi ADR này được Accept.
- Terrain collision (chống xuyên tường) **vẫn chưa có** sau ADR này — ADR này chỉ chọn CƠ CHẾ (AABB
  thay vì Box2D) cho lúc cần, không tự implement ngay vì chưa có map để test/dùng thật.
- Nếu sau này GDD đổi hướng sang cần platform/dốc/nhảy thật (khác thể loại hiện tại), quyết định này
  cần xem lại — chi phí đổi lúc đó cao hơn nếu chờ quá lâu, nhưng vẫn thấp hơn xây trước khi biết chắc
  cần.

**Module bị ảnh hưởng:**
- `engine/engine-physics` — không đổi code (giữ rỗng), chỉ đổi trạng thái "quyết định treo" → "quyết
  định đã chốt: chưa dùng".
- `engine/engine-ecs` — thêm mới `system/Aabb.java` (record) +
  `system/TerrainCollisionSystem.java` + `TerrainCollisionSystemTest.java` (4 test case, PASS).
- `server/server-world/WorldFacade.java` — nối `TerrainCollisionSystem` vào `ecsWorld` ngay sau
  `MovementSystem`, obstacle mặc định rỗng. Toàn bộ 26 test `server-world` hiện có (kể cả dungeon/
  boss/combat) verify lại PASS, không hành vi nào đổi.
- `docs/03-engine/PHYSICS.md` — cần rà soát riêng (nêu trên), không đổi trong ADR này.

## Revisit Trigger

- **Khi asset pipeline (ADR-024) sản xuất ra map/zone thật đầu tiên có khái niệm "tường"/khu vực không
  đi được** → bắt đầu implement AABB bounds-check thật (Phương án A), đo CPU cost thật trước khi quyết
  có cần tối ưu thêm không (Điều 30).
- **Nếu GDD chính thức đổi thể loại sang có nhảy/dốc/platform** (khác Ninja School-style hiện tại) →
  xem lại toàn bộ ADR này, khả năng cao chuyển sang Phương án B.
- **Nếu benchmark thật cho thấy AABB tự viết không đủ xử lý số lượng entity/tick mục tiêu** (chưa có
  ngưỡng cụ thể — đo trước khi định số, Điều 30) → cân nhắc Box2D cho riêng phần môi trường (không
  phải combat, giữ đúng ranh giới PHYSICS.md §1 dù dùng Box2D).
- Không có trigger nào khác — nếu không rơi vào 3 trường hợp trên, quyết định "không Box2D" coi là ổn
  định lâu dài.
