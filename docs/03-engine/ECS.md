# ECS.md

**Entity Component System — quy tắc và thiết kế**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Luật gốc | [Hiến pháp Điều 14, 25](../00-governance/KAGE-CONSTITUTION.md) |
| ADR | [ADR-006](../01-architecture/adr/ADR-006-Why-ECS.md) |

---

## 1. ECS Principles

```
Entity    = ID (chỉ là số)
Component = Dữ liệu (không logic)
System    = Logic (không dữ liệu riêng của entity)
Family    = Bộ lọc entity theo Component
```

**Câu hỏi ECS trả lời**: "quái này vừa bay, vừa bơi, vừa bắn, vừa có thể bị đóng băng" — OOP kế thừa bó tay, ECS chỉ là **thêm Component**.

---

## 2. Entity

**Entity chỉ là ID.**

| ❌ Cấm | ✅ Đúng |
|---|---|
| Entity chứa logic | Entity chỉ là handle |
| `class Player extends Entity` | `Entity player = world.createEntity()` |
| Override method của Entity | Thêm Component để phân biệt |
| Entity giữ tham chiếu tới Entity khác | Giữ **entity ID**, không giữ object |

⚠️ **Vì sao không giữ tham chiếu Entity**: entity bị huỷ → tham chiếu treo → dùng object đã chết. Giữ ID + tra cứu → phát hiện được entity không còn tồn tại.

**Handle chết fail-fast** (chủ dự án chốt 2026-10-14, README dòng 46): `KEntity` của entity đã gỡ (hoặc có từ trước `EcsWorld.clear()`) có `isAlive() == false`; `has`/`ComponentMapper.has` trả `false`, còn `add` và `ComponentMapper.get` ném `IllegalStateException` — trước đó là no-op im lặng, nên một tham chiếu treo làm mất dữ liệu mà không ai biết. Gỡ giữa lúc `update` lặp là gỡ HOÃN: handle còn sống tới hết System hiện tại. Cùng đợt: `TriggerZoneSystem.setZones` từ chối hai zone trùng `zoneId` (trạng thái "đang ở trong" nhớ theo id, trùng id thì báo vào/ra luân phiên mỗi tick).

⚠️ **Phát hiện thật khi triển khai Phase 1**: Ashley (từ ~1.4 trở đi, gồm 1.7.4 đang dùng) **không expose ID số nguyên trên `Entity`** nữa (khác các bản rất cũ ~1.3.x). `engine-ecs` phải tự cấp `id` (long, tăng dần) lúc `EcsWorld.createEntity()` — độc lập hoàn toàn với Ashley. Đây là lý do thật, cụ thể để không bao giờ dùng ID nội bộ của Ashley: **nó không tồn tại để dùng.**

---

## 3. Component

**Component chỉ chứa dữ liệu.**

```java
// ✅ Đúng
public final class PositionComponent implements Component {
    public float x, y;      // public field — có chủ đích, xem §3.1
}

public final class HealthComponent implements Component {
    public int current;
    public int max;
}

// ❌ Sai — Component có logic gameplay
public final class PlayerComponent implements Component {
    public void attack(Entity target) { ... }   // ❌ logic phải ở System
    public void move(float dx, float dy) { ... } // ❌
}
```

### 3.1 Vì sao Component mutable và field public?

Đây là **ngoại lệ có chủ đích** của Điều 43 (Immutable by Default) và của quy ước "không public field".

| Lý do | Giải thích |
|---|---|
| Immutable Component = allocation mỗi tick | Vi phạm Điều 31 — nghiêm trọng hơn |
| Getter/setter trong hot path | JIT thường inline được, nhưng thêm nhiễu không lợi ích |
| Component **là struct**, không phải object | Data-Oriented Design (Điều 14) — nó là dữ liệu thuần |

**Ranh giới**: ngoại lệ này **chỉ** áp dụng cho ECS Component. Mọi thứ khác (DTO, Packet, Value Object) vẫn immutable. Đã ghi rõ ở [CODING-STANDARDS.md §12](../02-coding-standards/CODING-STANDARDS.md).

### 3.2 Component không được

❌ Gọi service · ❌ Chứa AI · ❌ Chứa business logic · ❌ Truy cập World/Engine · ❌ Chứa collection lớn (phá cache locality)

**Ngoại lệ hợp lệ**: method **thuần tuý tính toán trên chính dữ liệu của nó**, không side effect:

```java
public final class HealthComponent implements Component {
    public int current, max;

    public boolean isDead()    { return current <= 0; }        // ✅ OK — chỉ đọc chính nó
    public float percentage()  { return (float) current / max; } // ✅ OK
}
```

---

## 4. System

**Mọi gameplay logic nằm trong System.**

```java
public final class MovementSystem extends IteratingSystem {
    public MovementSystem() {
        super(Family.all(PositionComponent.class, VelocityComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        var pos = positionMapper.get(entity);
        var vel = velocityMapper.get(entity);
        pos.x += vel.x * deltaTime;
        pos.y += vel.y * deltaTime;
    }
}
```

**Quy tắc:**
- System **không giữ state của entity** — state ở Component.
- System **có thể** giữ state của chính nó (buffer tạm, cấu hình).
- System nhận phụ thuộc qua **constructor** ([ENGINE.md §6](ENGINE.md)) — không `ServiceRegistry`.
- Một System = một trách nhiệm (Điều 22).

---

## 5. Family

**System chỉ xử lý Entity thuộc Family của nó.**

> ⚠️ **Trạng thái tĩnh của Ashley không an toàn đa luồng** (bảng `Family`, sổ `ComponentType`). Server
> chạy nhiều World trên nhiều luồng, nên `engine-ecs` gác mọi lần chạm hai thứ đó bằng một khoá chung
> (`AshleyStatics`), và server-world **đăng ký trước mọi kiểu Component** lúc nạp `WorldFacade`
> (`WorldComponentTypes`, 2026-10-17). **Thêm Component mới cho server ⇒ thêm vào `WorldComponentTypes.ALL`**
> — `WorldComponentTypesTest` đỏ nếu quên.

| ❌ Cấm | Vì sao |
|---|---|
| Tự tìm Component (`entity.getComponent(...)` để lọc) | Family đã lọc rồi — làm lại là thừa và chậm |
| Tự cast | Dùng `ComponentMapper` |
| Scan toàn bộ Entity | Family là index, scan là O(n) vô ích |
| `getComponent()` trong vòng lặp nóng | Dùng `ComponentMapper` (cache index) |

```java
// ✅ Luôn dùng ComponentMapper — nhanh hơn getComponent nhiều lần
private final ComponentMapper<PositionComponent> posMapper =
        ComponentMapper.getFor(PositionComponent.class);
```

---

## 6. Scheduler — thứ tự System

**Thứ tự tick là cố định, tường minh, và có ý nghĩa.** Không phụ thuộc thứ tự đăng ký ngẫu nhiên.

```
Server tick:
 1. InputCommandSystem     ← áp dụng Command từ client
 2. AISystem               ← AI quyết định hành động
 3. MovementSystem         ← di chuyển
 4. CollisionSystem        ← va chạm (sau di chuyển!)
 5. CombatSystem           ← tính damage
 6. SkillSystem            ← cooldown, hiệu ứng
 7. StatusEffectSystem     ← buff/debuff, DoT
 8. DeathSystem            ← xử lý chết (sau khi mọi damage đã tính)
 9. SpawnSystem            ← sinh entity mới
10. StateSyncSystem        ← gom state gửi client (CUỐI CÙNG)

Client tick: 1..7 tương tự (dự đoán cục bộ) + RenderingSystem (cuối)
```

**Vì sao thứ tự quan trọng** (ví dụ thật):
- `CollisionSystem` **sau** `MovementSystem` — nếu ngược lại, va chạm kiểm tra vị trí cũ.
- `DeathSystem` **sau** `CombatSystem` — nếu ngược lại, entity chết vẫn nhận damage của tick này rồi mới bị xoá.
- `StateSyncSystem` **cuối cùng** — nếu không, client nhận state chưa hoàn tất.

⚠️ **Đổi thứ tự System = đổi behavior gameplay.** Phải coi như thay đổi kiến trúc, không phải "dọn dẹp code".

---

## 7. Event trong ECS

System **không gọi trực tiếp System khác** (Điều 26).

```java
// ❌ Sai
class CombatSystem {
    void onMonsterDeath(Entity m) {
        questSystem.updateProgress(m);      // coupling!
        lootSystem.dropItems(m);
        rankingSystem.addPoints(m);
    }
}

// ✅ Đúng
class CombatSystem {
    void onMonsterDeath(Entity m) {
        bus.publish(MonsterKilledEvent.of(m));   // ai quan tâm thì tự nghe
    }
}
```

**Ràng buộc bắt buộc** ([ADR-008](../01-architecture/adr/ADR-008-Why-EventDriven.md)):
- Event bus **in-process, đồng bộ, không allocation** trong tick.
- Event là `record` immutable — **hoặc** pooled object nếu benchmark chứng minh cần.
- Tên quá khứ: `MonsterKilledEvent` ✅, `KillMonsterEvent` ❌.
- Không dây chuyền > 2 tầng.

---

## 8. Entity huỷ — cạm bẫy

**Không xoá entity giữa lúc System đang lặp** — sẽ hỏng iterator hoặc bỏ sót entity.

```java
// ✅ Đánh dấu, xoá ở cuối tick
entity.add(new PendingRemovalComponent());
// → RemovalSystem (chạy cuối tick) mới thực sự xoá
```

Đây là lý do có `DeathSystem` riêng ở §6 thay vì xoá ngay trong `CombatSystem`.

---

## 9. Performance

| Nguyên tắc | Chi tiết |
|---|---|
| **ComponentMapper**, không `getComponent()` | Xem §5 |
| Không tạo object trong `processEntity()` | Điều 31 — dùng field tạm |
| Không `Family` phức tạp trong hot path | Family thay đổi động = tính lại index |
| Component nhỏ, phẳng | Cache locality (Điều 14) |
| Không stream trong System | [JAVA-GUIDELINES.md §4.1](../02-coding-standards/JAVA-GUIDELINES.md) |

⚠️ **Giới hạn đã biết của Ashley**: Ashley **không** lưu Component theo archetype liên tục trong bộ nhớ — nó dùng `Bag` + `ComponentMapper` index. Nghĩa là **ta không thực sự có cache locality lý tưởng** mà Data-Oriented Design hứa hẹn.

→ Đây chính là lý do [ADR-006](../01-architecture/adr/ADR-006-Why-ECS.md) đặt Revisit Trigger ở Phase 9. **Không tự lừa mình rằng dùng Ashley = đã đạt DOD.**

✅ **Đã đo bằng JMH thật (2026-07-26)** — xem [ADR-006 "Ghi chú — đánh giá lại Phase 9"](../01-architecture/adr/ADR-006-Why-ECS.md#ghi-chú--đánh-giá-lại-phase-9-2026-07-26): Revisit Trigger CHƯA kích hoạt (5000 entity chỉ ăn 3.66% ngân sách ECS), nhưng chi phí/entity tăng KHÔNG tuyến tính khi entity tăng — tín hiệu theo dõi cho tương lai, không phải hành động ngay.

✅ **Đã đo lại bằng production metric thật (2026-08-02)** — `kage.ecs.system.duration` (không phải
JMH mô phỏng) qua gateway thật + `tools:load-test` N=500 người chơi thật: tổng 6 System thật (AI +
Movement + Combat + Skill + Boss AI + StatusEffect) chỉ **0.0332ms/tick trung bình** (0.83% ngân sách
4ms của Trigger) — xác nhận lại kết luận JMH, lần này với System thật chứ không phải proxy. Chi tiết:
[ADR-006 "Ghi chú — đánh giá lại bằng production metric thật"](../01-architecture/adr/ADR-006-Why-ECS.md#ghi-chú--đánh-giá-lại-bằng-production-metric-thật-2026-08-02),
[BENCHMARK.md §5.10](../09-performance/BENCHMARK.md).

**Ngân sách hiệu năng** (**60Hz → 16.6ms/tick** — [ADR-018](../01-architecture/adr/ADR-018-Tick-Rate.md)):

| Hạng mục | Ngân sách |
|---|---|
| ECS update toàn bộ | ≤ 4ms (~25%) |
| Còn lại | Network, DB, event, dự phòng |

⚠️ Ngân sách ~4ms @60Hz **chặt hơn nhiều** so với ~8ms @30Hz — Ashley dễ chạm trần hơn.

Vượt ngưỡng → kích hoạt Revisit Trigger của ADR-006.

---

## 10. Future Migration — điều kiện để thay Ashley

Đây là **lý do tồn tại** của module `engine-ecs`.

**Ràng buộc bất khả xâm phạm:**

```
✅ client-core, server-world  →  chỉ import  dev.kage.engine.ecs.*
❌ client-core, server-world  →  KHÔNG BAO GIỜ import  com.badlogic.ashley.*
```

**Nếu ràng buộc này bị phá dù chỉ một lần và không ai phát hiện, ADR-006 mất khả năng đảo ngược.**

→ Phải enforce bằng build (ADR-011), không bằng nhắc nhở.

**API `engine-ecs` phải bọc**: `Entity`, `Component`, `System`, `Family`, `ComponentMapper`, `Engine/World`, `EventBus`.

⚠️ **Cảnh báo thiết kế**: đừng bọc bằng cách copy y hệt API Ashley — như vậy khi thay ECS khác (mô hình archetype), API vẫn phải đổi. Bọc ở mức **khái niệm** (tạo entity, thêm component, đăng ký system), không ở mức chữ ký hàm của Ashley.

---

## Technical Decisions

| # | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| 1 | **Component mutable, field public** — ngoại lệ của Điều 43 | Immutable = allocation mỗi tick | Accepted |
| 2 | Component được có method thuần đọc chính nó (`isDead()`) | Không phá DOD, tăng readability | Accepted |
| 3 | Entity giữ **ID**, không giữ tham chiếu Entity | Tránh tham chiếu treo | Accepted |
| 4 | **Thứ tự System cố định, tường minh** — đổi = đổi behavior | §6 | Accepted |
| 5 | Xoá entity **cuối tick**, không xoá giữa lúc lặp | Tránh hỏng iterator | Accepted |
| 6 | `engine-ecs` bọc ở mức **khái niệm**, không copy API Ashley | Để thay ECS archetype-based sau này không phải đổi API | Accepted |
| 7 | **Thừa nhận Ashley không cho cache locality lý tưởng** | Trung thực về giới hạn — không tự lừa mình | Accepted |
| 8 | Ngân sách ECS ≤ 4ms/tick @60Hz | Kích hoạt Revisit ADR-006 nếu vượt | ✅ [ADR-018](../01-architecture/adr/ADR-018-Tick-Rate.md) = 60Hz |

## ADR cần tạo

| ADR | Nội dung | Ưu tiên |
|---|---|---|
| ✅ **ADR-011** | Enforce `engine-ecs` boundary (§10) — **quan trọng nhất cho tài liệu này** | Accepted (2026-08-01) |
| ✅ **ADR-018** | Tick rate 60Hz — §9 ngân sách = 16.6ms | Accepted |
| **ADR-006** | Đánh giá lại Ashley cuối Phase 9 | ✅ Đã đánh giá (2026-08-02, production metric thật N=500) — Trigger CHƯA kích hoạt, xem [ADR-006 "Ghi chú — đánh giá lại bằng production metric thật"](../01-architecture/adr/ADR-006-Why-ECS.md#ghi-chú--đánh-giá-lại-bằng-production-metric-thật-2026-08-02) |

## Tài liệu liên quan

- [ADR-006](../01-architecture/adr/ADR-006-Why-ECS.md) — vì sao ECS, vì sao Ashley
- [ADR-008](../01-architecture/adr/ADR-008-Why-EventDriven.md) — chi tiết §7
- [ENGINE.md](ENGINE.md) — vòng đời, fixed timestep
- [SERVER.md](../05-server/SERVER.md) — ECS phía server
- [GAMEPLAY.md](../05-server/GAMEPLAY.md) — System nào cho tính năng nào
- [CODING-STANDARDS.md §12](../02-coding-standards/CODING-STANDARDS.md) — ngoại lệ immutability

## Việc cần làm tiếp theo

1. **ADR-011 trước Phase 0.** §10 là lý do tồn tại của `engine-ecs` — không enforce = mất trắng.
2. Phase 1: thiết kế API `engine-ecs` theo Decision #6 (bọc khái niệm, không copy Ashley) — sai ở bước này thì Phase 9 không thay ECS được.
3. Phase 9: đo ngân sách §9, kích hoạt Revisit Trigger của ADR-006 nếu vượt.
