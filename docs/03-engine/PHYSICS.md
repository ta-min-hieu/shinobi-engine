# PHYSICS.md

**Va chạm địa hình (không phải "physics" theo nghĩa mô phỏng vật lý)**

| | |
|---|---|
| Version | 2.0.0 |
| Status | Draft — khớp lại với thực tế sau [ADR-021](../01-architecture/adr/ADR-021-Box2D-vs-AABB.md) (2026-08-01) |
| Liên quan | [ENGINE.md](ENGINE.md), [ECS.md](ECS.md), [ADR-021](../01-architecture/adr/ADR-021-Box2D-vs-AABB.md) |

> ⚠️ **Đổi lớn 2026-08-01**: bản trước của tài liệu này mô tả một hệ thống Box2D đầy đủ (trọng lực,
> nhảy, dốc, platform, sensor, collision layer bitmask) — **0% trong số đó từng được code**.
> [ADR-021](../01-architecture/adr/ADR-021-Box2D-vs-AABB.md) xác nhận bằng cách đọc code thật (không
> đoán): `engine/engine-physics` chưa từng có 1 dòng nào, `MovementSystem` chỉ là velocity thuần, và
> game này (kiểu Ninja School — 2D side-view, **không phải platformer nhảy dốc**) chưa bao giờ cần
> gravity/jump. Bản này viết lại cho khớp những gì **thật sự chạy**: `Aabb` +
> `TerrainCollisionSystem` (engine-ecs), không Box2D.

> ⚠️ **Quy tắc quan trọng nhất, không đổi**: va chạm địa hình **KHÔNG** dùng để tính damage/combat.
> Combat là logic gameplay thuần ([GAMEPLAY.md](../05-server/GAMEPLAY.md)), tách biệt hoàn toàn khỏi
> câu hỏi "nhân vật có đi xuyên tường không".

---

## 1. Vì sao ranh giới này quan trọng

Cám dỗ tự nhiên: "hitbox skill là một collision sensor, ai chạm thì trúng đòn". **Sai** vì:

| Vấn đề | Hệ quả |
|---|---|
| Physics engine không xác định 100% giữa các máy | Client tính trúng, server tính trượt → tranh chấp |
| Physics chạy theo bước riêng, không khớp tick gameplay | Damage nhân đôi hoặc mất |
| Server phải chạy thêm hệ thống chỉ để tính damage | Tốn CPU vô ích cho thứ `CombatSystem` tự làm được |

**Đúng**: `CombatSystem` tự kiểm tra hình học (distance/range check — xem `CombatConstants.ATTACK_RANGE_METERS`) trong tick của nó, tường minh và xác định. Hoàn toàn tách khỏi `TerrainCollisionSystem`.

```
TerrainCollisionSystem →  "nhân vật có đi xuyên tường không?"
Gameplay (CombatSystem) →  "đòn đánh này có trúng không?", "gây bao nhiêu damage?"
```

---

## 2. `TerrainCollisionSystem` dùng cho gì

| ✅ Dùng | ❌ Không dùng |
|---|---|
| Chặn entity đi vào vùng `Aabb` tĩnh (tường/chướng ngại vật) | Hitbox skill |
| — | Tính damage |
| — | Tầm đánh (range check — `CombatSystem` tự làm bằng distance) |
| — | Aggro range (`AiSystem` tự làm bằng distance) |

Không có trọng lực, không nhảy, không dốc/platform, không vật thể đẩy được, không raycast — game
này không cần (side-view phẳng kiểu Ninja School, không phải platformer). Nếu GDD sau này đổi thể
loại, đây là Revisit Trigger của ADR-021, không sửa trực tiếp tài liệu này trước.

---

## 3. Server có chạy va chạm không?

**Có, nhưng đơn giản hơn nhiều so với bản cũ mô tả.**

| | Client (dự đoán cục bộ) | Server (nguồn chân lý) |
|---|---|---|
| Va chạm địa hình | Chưa implement (client chưa có `TerrainCollisionSystem` — engine-ecs dùng chung cả 2 phía về mặt code, nhưng client chưa tự gọi `setObstacles`) | ✅ Cơ chế đã có (`TerrainCollisionSystem`, nối sau `MovementSystem` trong `WorldFacade`) — **obstacle rỗng mặc định**, chưa có map thật để chặn |

**Vì sao server bắt buộc chạy việc này khi có map thật**: Điều 15 (Server Authority). Nếu server
không kiểm tra va chạm, client hack có thể đi xuyên tường. **Không liên quan tới speed-hack/
teleport** — đó đã được chặn từ tầng giao thức (`MoveCommand` chỉ mang hướng, server tự tính vị trí
— xem `WorldFacade.clampDirection`), độc lập hoàn toàn với việc có map/tường hay không.

⚠️ **Chi phí CPU**: hiện O(số obstacle) mỗi entity mỗi tick (index-loop tuyến tính,
`TerrainCollisionSystem.processEntity`) — obstacle rỗng nên chi phí thật hiện tại là 0. Khi có map
thật với nhiều obstacle, cần đo lại và cân nhắc spatial partition (Điều 30 — đo trước khi tối ưu,
đừng làm trước).

---

## 4. Thứ tự trong tick

`TerrainCollisionSystem` chạy **ngay sau** `MovementSystem`, **trước** `CombatSystem` (khớp
`WorldFacade` constructor — đổi thứ tự này = đổi behavior, engine/CLAUDE.md). Không có khái niệm
"physics step" riêng tách khỏi game tick — chạy đúng 1 lần mỗi tick 60Hz (ADR-018), cùng nhịp với
mọi System khác.

---

## 5. Đơn vị

World unit = mét, dùng chung với rendering ([RENDERING.md §4](RENDERING.md)) — nhân vật cao ~1.8
unit. Giữ nguyên quy ước này dù không còn Box2D (đơn vị nhất quán vẫn cần cho mọi tính toán khoảng
cách/AABB).

---

## 6. Entity là điểm, không có bán kính va chạm

`TerrainCollisionSystem` hiện coi entity là **1 điểm** (`position.x, position.y`), không có
hitbox/bán kính riêng. Không có collision layer/category (không phân biệt PLAYER/MONSTER/NPC khi va
chạm địa hình — obstacle chặn MỌI entity như nhau).

**Player ↔ Player và Player ↔ Monster vẫn KHÔNG va chạm vật lý với nhau** (giữ nguyên từ thiết kế
gốc, chỉ là giờ không có Box2D layer nào để "tắt" — đơn giản là `TerrainCollisionSystem` chỉ nhận
danh sách obstacle tĩnh, không bao giờ coi entity khác là obstacle). Lý do: MMORPG 2D side-view mà
entity đẩy nhau sẽ gây kẹt, đứng chặn cửa, cảm giác điều khiển tệ. Trúng đòn xử lý bằng gameplay
logic (§1), không bằng va chạm.

**Chưa có** (không phải thiếu sót — chưa ai cần): bán kính va chạm riêng theo kích thước nhân vật
thật (phụ thuộc art chưa tồn tại — ADR-021 Revisit Trigger).

---

## 7. Trigger zone / sensor

✅ **Đã implement** (2026-08-01) — `TriggerZoneSystem` (engine-ecs), dùng lại `Aabb`/`NamedZone`.
KHÔNG chặn di chuyển (khác `TerrainCollisionSystem`) — chỉ gọi callback (`BiConsumer<KEntity,
String>`, không dùng `EventBus`/ADR-008 vì server-world hiện chưa dùng EventBus ở đâu, giữ nhất
quán với pattern callback trực tiếp đã có ở `AiSystem`/`CombatSystem`) khi entity **vào** hoặc
**ra** một zone có tên. Entity phải có `TriggerZoneMembershipComponent` mới được theo dõi (opt-in).

Nối vào `WorldFacade` sau `TerrainCollisionSystem`, **zone danh sách rỗng mặc định + chưa entity
nào mang component** — chưa có consumer thật (vùng an toàn PvP, tương tác NPC theo khoảng cách...)
wire vào lúc viết. Xây trước cơ chế để dùng sau, cùng tinh thần `TerrainCollisionSystem`. 5 test
PASS (`TriggerZoneSystemTest`).

---

## 8. Tích hợp với ECS

Không còn `PhysicsBodyComponent`/Box2D `Body` — chỉ có `PositionComponent` (đã có sẵn `prevX/prevY`
cho render interpolation, RENDERING.md §7) và `TerrainCollisionSystem` đọc/ghi trực tiếp lên đó.

**Ai là nguồn chân lý về vị trí?** Luôn là `PositionComponent`, ghi bởi `MovementSystem` rồi có thể
bị `TerrainCollisionSystem` sửa lại (revert) NGAY TRONG CÙNG TICK trước khi bất kỳ System nào khác
đọc — không có nguồn chân lý thứ hai, không có vấn đề "ghi hai chiều" mà bản Box2D cũ phải cảnh báo.

**Vòng đời**: không có native memory nào để quản (không Box2D `Body`) — `Aabb` là record thuần Java,
GC lo hết. Cạm bẫy Box2D cũ (destroy body, sửa world giữa step, tunneling...) không còn áp dụng.

---

## Technical Decisions

| # | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| 1 | Va chạm địa hình **KHÔNG** dùng để tính damage/combat | Không xác định, không khớp tick, không kiểm soát được | Accepted — quan trọng nhất |
| 2 | **Không dùng Box2D** — `Aabb` + `TerrainCollisionSystem` tự viết | [ADR-021](../01-architecture/adr/ADR-021-Box2D-vs-AABB.md) | ✅ Accepted (2026-08-01) |
| 3 | `TerrainCollisionSystem` chạy đúng nhịp game tick 60Hz, không có "physics step" riêng | Đơn giản, xác định, tái hiện được | Accepted |
| 4 | World unit = mét, dùng chung với rendering | Nhất quán tính khoảng cách | Accepted |
| 5 | **Player ↔ Player và Player ↔ Monster KHÔNG va chạm vật lý** | Tránh kẹt/chặn đường; hợp thể loại | Accepted — quyết định gameplay |
| 6 | Entity là 1 điểm, không bán kính va chạm | Chưa có kích thước hitbox thật (art chưa tồn tại) | Accepted tạm thời — xem ADR-021 Revisit Trigger |
| 7 | Obstacle mặc định RỖNG cho tới khi có map thật | Chưa có `assets/` nào (ADR-024) | Accepted |
| 8 | Trigger zone/sensor: `TriggerZoneSystem` + callback trực tiếp, không `EventBus` | Nhất quán pattern callback đã có, `EventBus` chưa dùng ở đâu | ✅ Accepted, implemented — §7 |

## ADR cần tạo

Không còn. ✅ **[ADR-021](../01-architecture/adr/ADR-021-Box2D-vs-AABB.md) Accepted 2026-08-01**, đã implement.

## Tài liệu liên quan

- [ENGINE.md §4](ENGINE.md) — fixed timestep
- [ECS.md §6](ECS.md) — thứ tự System
- [RENDERING.md §4](RENDERING.md) — world unit dùng chung
- [GAMEPLAY.md](../05-server/GAMEPLAY.md) — combat tính thế nào (không qua va chạm)
- [ADR-021](../01-architecture/adr/ADR-021-Box2D-vs-AABB.md) — quyết định + implementation đầy đủ

## Việc cần làm tiếp theo

1. Khi asset pipeline (ADR-024) sản xuất map thật đầu tiên có tường → nạp `Aabb` obstacle thật qua
   `TerrainCollisionSystem.setObstacles(...)`, đo CPU cost thật (Điều 30).
2. Trigger zone/sensor (§7) đã có cơ chế — còn thiếu: consumer thật đầu tiên (vd. vùng an toàn PvP)
   gọi `TriggerZoneSystem.setZones(...)` và gắn `TriggerZoneMembershipComponent` cho character khi
   attach, cùng lúc có map/zone data thật (ADR-024).
3. Client-side prediction cho va chạm địa hình (bảng §3) — hiện client CHƯA dùng
   `TerrainCollisionSystem` cục bộ; không cấp bách vì chưa có obstacle thật để dự đoán.
