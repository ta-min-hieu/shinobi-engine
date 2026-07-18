# CODING-STANDARDS.md

**Quy ước viết code Java trên Project Kage**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Luật gốc | [Hiến pháp Điều 17](../00-governance/KAGE-CONSTITUTION.md#điều-17-thứ-tự-ưu-tiên-khi-đánh-đổi) |

> Coding Convention **không nhằm làm đẹp code**. Nó nhằm **giảm bug và giảm chi phí bảo trì**.
> Thứ gì máy kiểm tra được (format, import order) → Spotless/Checkstyle lo, không tranh luận trong PR.
> Tài liệu này chỉ quy định thứ **máy không kiểm được**.

---

## 1. Nguyên tắc nền

Điều 17 — thứ tự ưu tiên **không bao giờ đảo ngược**:

```
Correctness → Readability → Maintainability → Testability → Performance → Elegance
```

**Code tốt** = đúng · dễ đọc · dễ debug · dễ test · dễ mở rộng · ít phụ thuộc · hiệu năng tốt · không side effect.

Không đánh giá code dựa trên **số dòng**.

---

## 2. Naming

Tên phải mô tả **chính xác vai trò**. Không viết tắt khi không phổ biến.

| | ✅ Đúng | ❌ Sai |
|---|---|---|
| Class | `PlayerInventory`, `SkillCooldown`, `QuestReward` | `PI`, `InvMgr`, `QUtil`, `TmpData` |
| Method | `calculateCriticalDamage()`, `isSkillReady()` | `calc()`, `check()`, `doIt()` |
| Boolean | `isReady`, `hasEnoughChi`, `canAttack` | `ready`, `flag`, `status` |
| Collection | `onlinePlayers`, `equippedItems` | `list`, `data`, `map1` |

**Cấm hậu tố vô nghĩa** — `Manager`, `Helper`, `Common`, `Base`, `Data`, `Info`, `Util` — **trừ khi phản ánh đúng trách nhiệm**.

> `ConnectionManager` quản lý vòng đời connection thật → chấp nhận.
> `PlayerManager` làm mọi thứ về player → **cấm**. Tách theo trách nhiệm thật.

**Naming ở mức module/package**: xem [REPOSITORY-STRUCTURE.md §5](../01-architecture/REPOSITORY-STRUCTURE.md).

---

## 3. Class

**Single Responsibility là bắt buộc.** Một class không nên vừa đọc DB · vừa render · vừa xử lý packet · vừa validate · vừa gửi event.

**Phép thử**: class có **nhiều lý do để thay đổi** → phải tách.

**Thứ tự thành viên trong class:**

```java
public final class Inventory {
    // 1. static final constants
    // 2. instance fields (final trước)
    // 3. constructors
    // 4. public methods
    // 5. private methods
    // 6. nested types
}
```

**Mặc định `final`**: class không thiết kế để kế thừa phải là `final` (Điều 13 — Composition over Inheritance). Muốn kế thừa → phải có lý do "is-a" thật và thiết kế cho việc đó.

---

## 4. Method

| Quy tắc | Ngưỡng |
|---|---|
| Độ dài | Ưu tiên **5–30 dòng**. Không quá **50** trừ trường hợp đặc biệt (có comment giải thích) |
| Mức indent | Không quá **3** |
| Số tham số | Không quá **4** |

Quá 4 tham số → tạo **Command Object** / **Request Object** (record).

```java
// ❌ Sai
void spawnMonster(int mapId, float x, float y, int monsterType, int level, boolean elite, long ownerId)

// ✅ Đúng
record MonsterSpawnRequest(int mapId, Vector2 position, MonsterType type,
                           int level, boolean elite, long ownerId) {}
void spawnMonster(MonsterSpawnRequest request)
```

Tên method mô tả **hành động**, không mô tả cách làm.

---

## 5. Variable

- Khai báo **gần nơi sử dụng**, không khai báo sớm.
- **Không reuse** biến cho nhiều mục đích.
- **Ưu tiên `final`** — mặc định final, bỏ final khi thật sự cần đổi.
- `var` được phép **khi kiểu hiển nhiên từ vế phải**:

```java
var players = new ArrayList<Player>();          // ✅ rõ ràng
var result = service.process(input);            // ❌ kiểu gì? — viết rõ kiểu
```

---

## 6. Record — dùng khi nào

Điều 43 (Immutable by Default).

**Bắt buộc dùng `record` cho**: DTO · Packet · Command · Response · Event · Projection · Value Object.

```java
// ✅ Đúng
public record ItemStack(long templateId, int quantity, int enhanceLevel) {
    public ItemStack {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        if (enhanceLevel < 0 || enhanceLevel > 15)
            throw new IllegalArgumentException("enhanceLevel out of range: " + enhanceLevel);
    }
}
```

**Không dùng class truyền thống nếu chỉ chứa dữ liệu.**

**Compact constructor là nơi validate** (Fail Fast, Điều 16) — record không được phép tồn tại ở trạng thái không hợp lệ.

---

## 7. Sealed Class — dùng khi nào

Khi số lượng implementation **cố định và biết trước**: `Packet`, `GameState`, `SkillTarget`, `MonsterType`.

```java
public sealed interface Packet permits Command, Response, Event {}

public sealed interface Command extends Packet
    permits MoveCommand, UseSkillCommand, BuyItemCommand {}
```

**Lợi ích thật**: switch trên sealed type được compiler kiểm tra **đủ nhánh** — thêm implementation mới mà quên xử lý → **compile error**, không phải bug runtime.

Không dùng interface mở nếu không cần mở rộng.

---

## 8. Enum

Enum **không chỉ chứa constant** — cho phép method, strategy, metadata.

```java
// ❌ Sai
public static final int FIRE = 1;
public static final int WATER = 2;

// ✅ Đúng
public enum Element {
    FIRE(Element::waterBeatsFire),
    WATER(...),
    WIND(...),
    THUNDER(...),
    EARTH(...);

    public boolean isStrongAgainst(Element other) { ... }
}
```

**Cấm `int` constant** cho tập giá trị hữu hạn.

---

## 9. Pattern Matching & Switch

```java
// ❌ Cũ
if (packet instanceof MoveCommand) {
    MoveCommand cmd = (MoveCommand) packet;
    handle(cmd.x(), cmd.y());
}

// ✅ Java 25
switch (packet) {
    case MoveCommand(var x, var y)     -> handleMove(x, y);
    case UseSkillCommand(var skillId)  -> handleSkill(skillId);
    case BuyItemCommand c              -> handleBuy(c);
    // sealed → không cần default, compiler bắt thiếu nhánh
}
```

**Switch expression** thay switch cũ. **Không `default`** khi switch trên sealed type — mất đi lợi ích compiler kiểm tra đủ nhánh.

---

## 10. Null

Điều 16 (Fail Fast).

**Không trả về `null` nếu có thể.** Ưu tiên: `Optional` → Empty Collection → Empty Object → Null Object Pattern.

| Dùng `Optional` | Không dùng `Optional` |
|---|---|
| Kiểu trả về của method | ❌ Field của class |
| | ❌ Tham số method |
| | ❌ **Trong game loop** (allocation — Điều 31) |

Trong hot path: trả sentinel value hoặc dùng `boolean tryGet(out)` pattern, không `Optional`.

---

## 11. Exception

Điều 16.

| Cấm | Vì sao |
|---|---|
| `catch (Exception e)` tổng quát | Nuốt cả lỗi không lường trước |
| `catch (...) {}` rỗng | Giấu bug |
| `e.printStackTrace()` | Không có context, không vào log system |
| `// TODO` trong catch | TODO trong catch = bug đã biết mà không sửa |
| Exception để điều khiển luồng | Chậm + khó đọc |

**Exception phải mang đầy đủ context:**

```java
// ❌ Sai
throw new IllegalStateException("invalid");

// ✅ Đúng
throw new SkillNotReadyException(
    "Skill %d của character %d còn cooldown %dms".formatted(skillId, characterId, remainingMs));
```

---

## 12. Immutability

Điều 43. **Immutable mặc định. Mutable chỉ khi thật cần.**

Bắt buộc immutable: DTO · Packet · Value Object · Configuration · Event.

**Ngoại lệ có chủ đích**: ECS Component **là mutable** — đó là bản chất Data-Oriented Design (Điều 14). Component được System ghi trực tiếp để tránh allocation. Đây không phải vi phạm, mà là đánh đổi có ý thức ở hot path. Xem [ECS.md](../03-engine/ECS.md).

---

## 13. Collection

**Khai báo theo interface:**

```java
List<Player> players = new ArrayList<>();     // ✅
ArrayList<Player> players = new ArrayList<>(); // ❌ (trừ khi thật sự cần API riêng của ArrayList)
```

**Trong game loop:**
- Ưu tiên `for` thay `stream()` nếu benchmark chứng minh hiệu quả hơn.
- Ưu tiên primitive collection (libGDX `IntArray`, `IntMap`) để tránh boxing (Điều 31).
- Pre-size collection khi biết kích thước: `new ArrayList<>(expectedSize)`.

---

## 14. Stream API

**Được phép dùng. Không lạm dụng.**

| Quy tắc |
|---|
| Không pipeline dài hơn **5 bước** |
| Không stream trong game loop nếu tạo allocation đáng kể (Điều 31) |
| Không stream chỉ để thay `for` đơn giản |

Stream **rất tốt** cho: code khởi tạo, tool, test, xử lý dữ liệu ngoài hot path.

---

## 15. Comment

**Comment giải thích TẠI SAO, không giải thích LÀM GÌ.**

```java
// ❌ Sai — nói lại điều code đã nói
// tăng HP lên 10
hp += 10;

// ✅ Đúng — nói điều code không thể nói
// HP giới hạn ở 9999 theo thiết kế cân bằng: vượt ngưỡng này
// công thức damage ở CombatConstants sẽ overflow int.
hp = Math.min(hp + amount, MAX_HP);
```

**Cấm comment**: nói code làm gì · nói ai viết · nói vì sao thay đổi (đó là việc của git) · code bị comment out (xoá đi, git nhớ giùm).

---

## 16. JavaDoc

**Bắt buộc** cho **Public API** (mọi thứ trong `api/` package, mọi `public` method của module được module khác gọi).

**Không bắt buộc** cho: private method · method hiển nhiên · getter/setter.

```java
/**
 * Thêm item vào túi đồ của nhân vật.
 *
 * @param characterId nhân vật nhận item
 * @param stack       item và số lượng; không được null
 * @return kết quả thêm — {@link AddItemResult#full()} nếu túi đầy
 * @throws CharacterNotFoundException nếu characterId không tồn tại
 */
AddItemResult addItem(long characterId, ItemStack stack);
```

---

## 17. Magic Number

**Không tồn tại Magic Number.**

```java
speed *= 1.35;                          // ❌ 1.35 là gì?
speed *= CombatConstants.CRIT_SPEED_MULTIPLIER;  // ✅
```

---

## 18. Constant

**Constant phải theo domain. Cấm tạo `Constants.java` chứa mọi thứ** (Điều 22).

✅ `CombatConstants` · `InventoryConstants` · `QuestConstants` · `NetworkConstants`
❌ `Constants` · `AppConstants` · `Config`

---

## 19. Configuration

Điều 27 — **không hardcode**.

Mọi giá trị gameplay phải cấu hình được: EXP · Damage · Cooldown · Spawn · NPC · Quest · Item · Skill · Drop → **Data-Driven**.

**Phân biệt**:
- **Constant** = hằng số kỹ thuật không đổi khi vận hành (VD: `MAX_PACKET_SIZE`).
- **Configuration** = giá trị gameplay có thể chỉnh mà không build lại (VD: tỉ lệ rơi đồ).

Nhầm hai thứ này → phải build lại server để chỉnh tỉ lệ drop. Đó là lỗi thiết kế.

---

## 20. Dependency Injection

Điều 12 + [ADR-009](../01-architecture/adr/ADR-009-Why-No-Spring-Boot.md).

| ✅ | ❌ |
|---|---|
| Constructor Injection | Field Injection |
| Wiring tường minh ở composition root | Service Locator |
| | DI Container runtime (Spring/Guice) |

**Không lạm dụng DI.** Gameplay không cần container. Nếu một class chỉ có 1 implementation và sẽ không bao giờ có cái thứ hai → **không cần interface**.

---

## 21. Object Creation

Điều 31–32. Trong gameplay: **không new object liên tục**.

Ưu tiên: **Pool** → **Reuse** → **Factory** → **Flyweight**.

```java
// ❌ Sai — tạo rác mỗi frame
public void update(float delta) {
    Vector2 velocity = new Vector2(vx, vy);
    position.add(velocity.scl(delta));
}

// ✅ Đúng — tái sử dụng
private final Vector2 tmpVelocity = new Vector2();

public void update(float delta) {
    tmpVelocity.set(vx, vy).scl(delta);
    position.add(tmpVelocity);
}
```

⚠️ **Chỉ áp dụng trong hot path.** Ngoài game loop, ưu tiên code rõ ràng (Điều 17 — Readability > Performance).

---

## 22. Logging

| Level | Dùng cho | Ví dụ |
|---|---|---|
| `DEBUG` | **Chỉ Development** | Giá trị biến khi điều tra |
| `INFO` | Business event | "Character 123 đăng nhập" |
| `WARN` | Recoverable | "Redis timeout, fallback sang DB" |
| `ERROR` | Unexpected | "Không ghi được inventory của character 123" |

**Cấm**: log spam mỗi frame · log để debug mãi mãi · log chứa mật khẩu/token.

**Log phải có context**: Module · Request · Player · Packet · Timestamp.

```java
// ❌
log.error("failed");

// ✅
log.error("Không lưu được inventory: characterId={}, itemCount={}, sequenceId={}",
          characterId, items.size(), sequenceId, exception);
```

Dùng **parameterized logging** (`{}`), không nối chuỗi — tránh tạo String khi level bị tắt.

---

## 23. Format & Import

**Máy lo, không tranh luận:**

| Việc | Công cụ |
|---|---|
| Format code | **Spotless** (auto-fix) |
| Import order, unused import | **Spotless + Checkstyle** |
| Line length, whitespace | **Checkstyle** |

Quy ước cơ bản: 4 spaces (không tab) · line length 120 · UTF-8 · LF line ending.

**Cấm wildcard import** (`import java.util.*`).

Chi tiết cấu hình: [STATIC-ANALYSIS.md](../10-quality/STATIC-ANALYSIS.md).

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Thứ gì máy kiểm được → giao cho tooling, không viết vào tài liệu để tranh luận | Accepted |
| 2 | Class mặc định `final`; kế thừa phải có lý do "is-a" thật | Accepted |
| 3 | Record bắt buộc cho DTO/Packet/Event/Value Object; validate trong compact constructor | Accepted |
| 4 | Không `default` khi switch trên sealed type | Accepted |
| 5 | `Optional` cho return type, **không** cho field/param/hot path | Accepted |
| 6 | **ECS Component là ngoại lệ của Immutability** — có chủ đích, không phải vi phạm | Accepted |
| 7 | Tối ưu allocation (§21) **chỉ** áp dụng hot path | Accepted |
| 8 | Line length 120 | Accepted |

## ADR cần tạo

Không có. Đây là quy ước, không phải quyết định kiến trúc.

*(Nếu sau này muốn đổi quy ước lớn — VD: cho phép Lombok, đổi sang 2-space indent — thì cần sửa tài liệu này, không cần ADR.)*

## Tài liệu liên quan

- [JAVA-GUIDELINES.md](JAVA-GUIDELINES.md) — sâu hơn về Java 25, memory, concurrency, GC
- [CODE-REVIEW.md](CODE-REVIEW.md) — checklist kiểm tra các quy tắc ở đây
- [STATIC-ANALYSIS.md](../10-quality/STATIC-ANALYSIS.md) — cấu hình công cụ tự động
- [ECS.md](../03-engine/ECS.md) — vì sao Component là ngoại lệ (§12)
- [PERFORMANCE.md](../09-performance/PERFORMANCE.md) — chi tiết §21

## Việc cần làm tiếp theo

1. **Phase 0**: cấu hình Spotless + Checkstyle theo §23 — quy tắc nào máy làm được thì đừng bắt người nhớ.
2. Viết `checkstyle.xml` phản ánh §2 (cấm hậu tố `Manager/Helper/Util`), §23.
3. Cân nhắc ArchUnit test để enforce §3 (class final), §20 (không field injection).
