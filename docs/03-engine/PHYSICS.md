# PHYSICS.md

**Physics và va chạm**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Liên quan | [ENGINE.md](ENGINE.md), [ECS.md](ECS.md) |

> ⚠️ **Quy tắc quan trọng nhất**: Box2D dùng cho **va chạm và vật lý môi trường**. **KHÔNG** dùng để tính damage/combat.
> Combat là logic gameplay thuần ([GAMEPLAY.md](../05-server/GAMEPLAY.md)), không phải physics.

---

## 1. Vì sao ranh giới này quan trọng

Cám dỗ tự nhiên: "hitbox skill là một Box2D sensor, ai chạm thì trúng đòn". **Sai** vì:

| Vấn đề | Hệ quả |
|---|---|
| Box2D không xác định 100% giữa các máy | Client tính trúng, server tính trượt → tranh chấp |
| Box2D chạy theo bước physics riêng | Không khớp tick gameplay → damage nhân đôi hoặc mất |
| Damage phụ thuộc thứ tự callback của Box2D | Không kiểm soát được, không tái hiện được bug |
| Server phải chạy full physics chỉ để tính damage | Tốn CPU vô ích |

**Đúng**: `CombatSystem` tự kiểm tra hình học (AABB/circle overlap) trong tick của nó, tường minh và xác định.

```
Box2D  →  "nhân vật có đi xuyên tường không?", "rơi xuống đất chưa?"
Gameplay → "đòn đánh này có trúng không?", "gây bao nhiêu damage?"
```

---

## 2. Box2D dùng cho gì

| ✅ Dùng | ❌ Không dùng |
|---|---|
| Va chạm với địa hình (không đi xuyên tường) | Hitbox skill |
| Trọng lực, nhảy, rơi | Tính damage |
| Platform, dốc, thang | Tầm đánh (range check) |
| Vật thể môi trường (thùng đẩy được) | Aggro range |
| Raycast (kiểm tra tầm nhìn NPC) | Kiểm tra trúng đòn |

---

## 3. Server có chạy physics không?

**Có** — nhưng chỉ phần cần thiết.

| | Client | Server |
|---|---|---|
| Va chạm địa hình | ✅ (dự đoán cục bộ) | ✅ **bắt buộc** — chống hack đi xuyên tường |
| Trọng lực / nhảy | ✅ | ✅ — chống hack bay |
| Vật thể môi trường | ✅ | ✅ nếu ảnh hưởng gameplay |
| Particle vật lý (trang trí) | ✅ | ❌ |

**Vì sao server bắt buộc chạy physics**: Điều 15 (Server Authority). Nếu server không kiểm tra va chạm, client hack có thể đi xuyên tường, bay, teleport.

⚠️ **Đây là chi phí CPU thật của server** — phải tính vào ngân sách tick ([ECS.md §9](ECS.md)).

---

## 4. Physics Tick

Box2D phải chạy **fixed timestep**, khớp với game tick ([ENGINE.md §4](ENGINE.md)).

```java
// ✅ Đúng — fixed step, khớp tick gameplay
world.step(FIXED_DT, VELOCITY_ITERATIONS, POSITION_ITERATIONS);

// ❌ Sai — variable step
world.step(Gdx.graphics.getDeltaTime(), 6, 2);   // không xác định!
```

`VELOCITY_ITERATIONS = 6`, `POSITION_ITERATIONS = 2` là mặc định khuyến nghị của Box2D — **cần đo lại** nếu thấy vật thể xuyên nhau hoặc CPU cao.

**Thứ tự trong tick**: `PhysicsSystem` chạy **sau** `MovementSystem`, **trước** `CollisionSystem` gameplay.

---

## 5. Đơn vị

Box2D **tối ưu cho vật thể kích thước 0.1–10 mét**. Dùng pixel làm đơn vị (nhân vật cao 64 "mét") → simulation không ổn định.

→ Dùng chung world unit với rendering ([RENDERING.md §4](RENDERING.md)): **1 unit = 1 mét**, nhân vật cao ~1.8 unit.

---

## 6. Collision Layers (Category / Mask)

Box2D dùng bitmask để lọc va chạm:

```java
public final class CollisionCategory {
    public static final short TERRAIN      = 1 << 0;
    public static final short PLAYER       = 1 << 1;
    public static final short MONSTER      = 1 << 2;
    public static final short NPC          = 1 << 3;
    public static final short PROJECTILE   = 1 << 4;
    public static final short ITEM_DROP    = 1 << 5;
    public static final short TRIGGER_ZONE = 1 << 6;
}
```

| Ai | Va chạm với |
|---|---|
| PLAYER | TERRAIN, TRIGGER_ZONE, ITEM_DROP |
| MONSTER | TERRAIN |
| PROJECTILE | TERRAIN |
| PLAYER ↔ PLAYER | **Không** — người chơi đi xuyên nhau |
| PLAYER ↔ MONSTER | **Không** — tránh bị chặn đường/đẩy |

⚠️ **Quyết định gameplay**: người chơi và quái **không va chạm vật lý với nhau**. Lý do: MMORPG 2D side-view mà entity đẩy nhau sẽ gây kẹt, đứng chặn cửa, và cảm giác điều khiển tệ. Trúng đòn xử lý bằng gameplay logic (§1), không bằng va chạm vật lý.

---

## 7. Sensor

Sensor = fixture phát hiện chạm nhưng **không** tạo phản lực.

| Dùng cho | Ví dụ |
|---|---|
| Vùng kích hoạt | Bước vào hang → load bản đồ |
| Vùng an toàn | Vào Học Viện → tắt PvP |
| Vùng NPC | Lại gần NPC → hiện nút tương tác |
| Vùng bẫy | Bước vào → kích hoạt trap |

**Không** dùng sensor cho hitbox skill (§1).

---

## 8. Tích hợp với ECS

```java
public final class PhysicsBodyComponent implements Component {
    public Body body;        // tham chiếu Box2D body
}
```

**Ai là nguồn chân lý về vị trí?**

```
Entity CÓ physics body:  Box2D  →  PositionComponent   (physics ghi vào ECS)
Entity KHÔNG có physics: gameplay ghi thẳng PositionComponent
```

⚠️ **Cấm ghi hai chiều.** Nếu vừa set `body.setTransform()` vừa sửa `PositionComponent` → hai nguồn chân lý → giật, rung, bug không tái hiện được.

**Quy tắc**: entity có physics body → chỉ điều khiển qua **lực/vận tốc** của Box2D, không set position trực tiếp (trừ teleport, và khi teleport phải `setTransform` + reset velocity).

**Vòng đời**: entity bị huỷ → **bắt buộc** `world.destroyBody(body)`. Quên = leak native memory (Box2D là native, GC của Java không thu hồi giùm).

---

## 9. Cạm bẫy Box2D

| Bẫy | Triệu chứng | Cách tránh |
|---|---|---|
| Không destroy body khi entity chết | Leak native memory, chậm dần | Destroy ở `RemovalSystem` ([ECS.md §8](ECS.md)) |
| Sửa world giữa lúc `step()` | Crash native | Hoãn tạo/xoá body tới sau `step()` |
| Vật thể nhỏ + tốc độ cao | Xuyên qua tường (tunneling) | Bật `bullet = true` cho projectile |
| Dùng pixel làm đơn vị | Simulation rung, không ổn định | §5 |
| Body density = 0 với dynamic body | Hành vi lạ | Luôn set density > 0 |
| Gọi callback rồi sửa world ngay trong callback | Crash | Đưa vào queue, xử lý sau `step()` |

---

## 10. Có nên dùng Box2D không?

Câu hỏi trung thực: game side-view kiểu Ninja School có cần **full physics engine** không?

| Ủng hộ Box2D | Phản đối |
|---|---|
| Có sẵn, tích hợp libGDX | Overkill nếu chỉ cần AABB + trọng lực |
| Xử lý dốc/platform phức tạp tốt | Native memory phải quản thủ công |
| Raycast tiện cho AI | Server tốn CPU chạy physics |
| Không phải tự viết + debug | Khó kiểm soát chính xác cảm giác điều khiển platformer |

**Quyết định hiện tại: dùng Box2D** — vì có sẵn và Điều 9 (không tự viết thứ đã có).

⚠️ **Nhưng ghi nhận rủi ro**: nhiều game platformer 2D cuối cùng **bỏ Box2D** vì cảm giác điều khiển không đủ chính xác (Box2D mô phỏng vật lý thật, còn platformer cần vật lý "giả" cho vui tay). Nếu Phase 3 thấy điều khiển không đạt → cân nhắc AABB collision tự viết cho nhân vật, giữ Box2D cho môi trường.

---

## Technical Decisions

| # | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| 1 | **Box2D KHÔNG dùng cho combat/damage** | Không xác định, không khớp tick, không kiểm soát được | Accepted — quan trọng nhất |
| 2 | Server **bắt buộc** chạy physics va chạm địa hình | Điều 15 — chống hack xuyên tường/bay | ⚠️ Accepted về nguyên tắc, **chưa implement** — xem [ADR-021](../01-architecture/adr/ADR-021-Box2D-vs-AABB.md) |
| 3 | Fixed timestep khớp game tick | Xác định, tái hiện được | Accepted |
| 4 | World unit = mét, dùng chung với rendering | Box2D ổn định ở 0.1–10 unit | Accepted |
| 5 | **Player ↔ Player và Player ↔ Monster KHÔNG va chạm vật lý** | Tránh kẹt/chặn đường; hợp thể loại | Accepted — quyết định gameplay |
| 6 | Entity có physics body → **chỉ** điều khiển qua lực/vận tốc | Tránh hai nguồn chân lý về vị trí | Accepted |
| 7 | Bắt buộc `destroyBody()` khi entity chết | Native memory không được GC | Accepted |
| 8 | Dùng Box2D, **nhưng ghi nhận rủi ro** cảm giác điều khiển | Điều 9 — không tự viết thứ đã có | ⏳ **Superseded (đề xuất) bởi [ADR-021](../01-architecture/adr/ADR-021-Box2D-vs-AABB.md)** — không dùng Box2D |

## ADR cần tạo

✅ **ADR-021 đã viết** (2026-08-01, Status: Proposed) —
[ADR-021](../01-architecture/adr/ADR-021-Box2D-vs-AABB.md): không dùng Box2D, chỉ AABB tự viết khi có
map thật. Dựa trên dữ liệu THẬT sau khi Phase 3-9 đã đóng (không cần chờ nữa — dữ liệu đã có):
`engine-physics` chưa từng có code, `MovementSystem` chỉ là velocity thuần, chưa có map/terrain nào
tồn tại để va chạm. ⚠️ **Hệ quả**: Technical Decision #2 dưới đây và §2-§4 phía trên mô tả một hệ
thống Box2D/gravity/jump **chưa từng được implement** — tài liệu này cần một đợt rà soát lớn riêng
sau khi ADR-021 được Accept (không sửa trong lần cập nhật này — xem ADR-021 "Consequences").

## Tài liệu liên quan

- [ENGINE.md §4](ENGINE.md) — fixed timestep
- [ECS.md §6](ECS.md) — thứ tự System
- [RENDERING.md §4](RENDERING.md) — world unit dùng chung
- [GAMEPLAY.md](../05-server/GAMEPLAY.md) — combat tính thế nào (không qua physics)
- [JAVA-GUIDELINES.md](../02-coding-standards/JAVA-GUIDELINES.md) — native memory

## Việc cần làm tiếp theo

1. Phase 3: đánh giá cảm giác điều khiển với Box2D — nếu không đạt, mở ADR-021 sớm (đổi càng muộn càng đắt).
2. Phase 3–4: đo sơ bộ chi phí CPU của physics phía server; đo chính thức ở Phase 9.
3. Bật Box2D debug renderer ở dev build để nhìn thấy body/sensor — bắt buộc để debug §9.
