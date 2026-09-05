# JAVA-GUIDELINES.md

**Dùng Java 25 cho game engine — thực hành sâu**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Liên quan | [ADR-001](../01-architecture/adr/ADR-001-Why-Java25.md), [CODING-STANDARDS.md](CODING-STANDARDS.md) |

> [CODING-STANDARDS.md](CODING-STANDARDS.md) nói *viết code thế nào*. Tài liệu này nói *dùng Java thế nào cho một game engine* — nơi GC pause và allocation quan trọng hơn hầu hết ứng dụng Java khác.

---

## 1. Nguyên tắc chi phối

Java được thiết kế cho throughput, không cho latency ổn định. Game cần **latency ổn định**.

→ Toàn bộ tài liệu này xoay quanh một câu: **giảm allocation để giảm GC**, mà không hy sinh Điều 17 (Correctness > Readability > ... > Performance).

**Quan trọng**: mọi kỹ thuật dưới đây chỉ áp dụng **trong hot path** (game loop, packet codec, ECS update). Ngoài hot path, viết code rõ ràng bình thường — tối ưu ở đó là **lãng phí và có hại**.

---

## 2. Java 25 — dùng gì cho gì

| Tính năng | Dùng cho | **Không** dùng cho |
|---|---|---|
| `record` | DTO, Packet, Event, Value Object | ECS Component (cần mutable) |
| `sealed` | Hierarchy đóng: Packet, GameState | Hierarchy cần mở rộng bởi plugin |
| Pattern matching | Thay `instanceof` + cast | — |
| Switch expression | Thay switch cũ | — |
| Text block | SQL dài, JSON mẫu trong test | — |
| **Virtual Thread** | **Chỉ blocking I/O** | ❌ **Game loop** · ❌ ECS update · ❌ tác vụ CPU-bound |
| Scoped Values | Truyền context (requestId) qua call stack | Thay thế tham số method bình thường |
| Structured Concurrency | Fan-out I/O có kiểm soát vòng đời | Trong game tick |

---

## 3. Virtual Thread — ranh giới bắt buộc

Đây là hiểu lầm nguy hiểm nhất khi dùng Java mới cho game.

```
✅ ĐƯỢC dùng Virtual Thread:
   - Gọi database (blocking JDBC)
   - Gọi API bên ngoài
   - Đọc/ghi file
   - Tác vụ nền không nằm trong tick

❌ CẤM dùng Virtual Thread:
   - Game loop            → luôn Platform Thread
   - ECS update           → luôn Platform Thread
   - Netty EventLoop      → Netty tự quản thread
   - Bất cứ thứ gì CPU-bound
```

**Vì sao**: Virtual Thread giải quyết bài toán *nhiều thread chờ I/O*. Game loop **không chờ I/O** — nó tính toán liên tục. Chạy game loop trên Virtual Thread chỉ thêm chi phí scheduling và mất khả năng ghim thread/kiểm soát ưu tiên.

⚠️ **Bẫy pinning**: Virtual Thread bị "pin" vào carrier thread khi ở trong `synchronized` block. Dùng `ReentrantLock` thay `synchronized` trong code chạy trên Virtual Thread.

---

## 4. Memory & Allocation

### 4.1 Thứ tạo rác mà không ai để ý

| Nguồn rác | Sửa |
|---|---|
| **Autoboxing**: `Map<Integer, Player>` | Dùng `IntMap<Player>` (libGDX) |
| **Iterator**: `for (Player p : list)` trên `ArrayList` | Trong hot path: dùng index loop `for (int i = 0; ...)` |
| **Varargs**: `String.format(...)` | Parameterized logging, hoặc nối chuỗi thủ công |
| **Lambda capture**: lambda bắt biến ngoài | Dùng lambda không capture, hoặc field |
| **Stream** | `for` trong hot path |
| **String concat trong vòng lặp** | `StringBuilder` (có sẵn), hoặc tránh hẳn |
| **`Optional`** | Sentinel value trong hot path |
| **`new Vector2()` mỗi frame** | Reuse field tạm |

### 4.2 Escape analysis không cứu được ta

JIT **có thể** loại bỏ allocation nếu object không "escape". Nhưng:
- Không đảm bảo — phụ thuộc JIT có inline được không.
- Không kiểm soát được — thay đổi code nhỏ có thể phá escape analysis.

→ **Không dựa vào escape analysis.** Cứ giảm allocation tường minh.

### 4.3 Object Pool — dùng đúng cách

```java
// libGDX có sẵn Pool
private final Pool<DamageEvent> eventPool = Pools.get(DamageEvent.class);

DamageEvent e = eventPool.obtain();
e.set(attackerId, targetId, damage);
bus.publish(e);
// ⚠️ BẮT BUỘC trả về pool, nếu không = memory leak thầm lặng
eventPool.free(e);
```

⚠️ **Pool là con dao hai lưỡi**:
- Quên `free()` → leak.
- `free()` hai lần → object dùng ở hai nơi cùng lúc → bug cực khó tìm.
- Pool object bị giữ tham chiếu sau khi free → dữ liệu bị ghi đè bất ngờ.

→ **Chỉ pool khi benchmark chứng minh cần** (Điều 30). Không pool mọi thứ theo phản xạ.

---

## 5. Concurrency

### 5.1 Nguyên tắc

**Không chia sẻ mutable state.** Ưu tiên theo thứ tự:

```
Message Passing  →  Event Queue  →  Immutable Object  →  (cuối cùng) Lock
```

Game server Kage: **game tick chạy single-thread**. Đây là quyết định thiết kế quan trọng — nó loại bỏ phần lớn bài toán concurrency trong gameplay.

```
Netty EventLoop (nhiều thread)
        ↓ enqueue Command (thread-safe queue)
Game Tick Thread (MỘT thread)   ← toàn bộ ECS chạy ở đây, KHÔNG lock
        ↓ enqueue I/O task
Virtual Thread pool (blocking DB call)
```

### 5.2 Nếu buộc phải lock

| Ưu tiên | Khi nào |
|---|---|
| `Atomic*` / `LongAdder` | Counter đơn giản; `LongAdder` khi ghi nhiều, đọc ít |
| `ReadWriteLock` | Đọc nhiều hơn ghi rõ rệt |
| `StampedLock` | Đọc rất nhiều, chấp nhận optimistic read |
| `ReentrantLock` | Cần tryLock/timeout, hoặc chạy trên Virtual Thread |
| `synchronized` | **Hạn chế** — pin Virtual Thread; không có timeout |

**Cấm `synchronized` tràn lan.** Mỗi lock phải trả lời được: bảo vệ **cái gì**, ai giữ nó, thứ tự lấy lock là gì (chống deadlock).

### 5.3 Cấm trong game tick

❌ Lock chờ lâu · ❌ `Thread.sleep` · ❌ blocking I/O · ❌ `CompletableFuture.get()`

Cần dữ liệu từ DB trong tick → **đọc trước vào cache**, hoặc xử lý bất đồng bộ rồi apply ở tick sau. Không block tick.

---

## 6. GC

Chi tiết chọn GC + flags: [JVM.md](../09-performance/JVM.md). Ở mức viết code:

| Nguyên tắc | Vì sao |
|---|---|
| **Object sống ngắn là bạn** | Young gen collect rất rẻ |
| **Object sống trung bình là kẻ thù** | Bị promote sang old gen → gây major GC |
| Cache lớn giữ object mãi mãi | Object vào old gen, tăng thời gian major GC |
| Tránh finalizer / `Cleaner` | Trì hoãn thu hồi, khó đoán |

**Nghịch lý cần biết**: Object Pool giữ object **sống mãi** → chúng nằm ở old gen. Pool sai chỗ có thể **làm GC tệ hơn** là cứ để young gen thu dọn. Đây là lý do §4.3 nói "chỉ pool khi benchmark chứng minh cần".

---

## 7. FFM API (Foreign Function & Memory)

**Trạng thái: KHÔNG dùng ở MVP.**

FFM cho phép truy cập bộ nhớ ngoài heap và gọi native code không cần JNI.

| Có thể hữu ích cho | Nhưng |
|---|---|
| Buffer ngoài heap cho network/rendering | Netty đã có `ByteBuf` direct buffer làm việc này rồi |
| Gọi native library | libGDX/LWJGL đã bọc native sẵn |

→ **Chưa có nhu cầu thật.** YAGNI (Điều 16). Xem lại nếu profiling chỉ ra bottleneck cụ thể mà FFM giải được.

---

## 8. Vector API (SIMD)

**Trạng thái: KHÔNG dùng ở MVP.**

- Vector API vẫn là **incubator** qua nhiều bản Java → vi phạm "không dùng Preview/Incubator trong Production khi chưa đánh giá kỹ" ([Hiến pháp Điều mục Java](../00-governance/KAGE-CONSTITUTION.md)).
- Lợi ích chỉ rõ rệt với tính toán số lượng lớn dạng mảng (physics batch, particle).

→ Xem lại **sau Phase 9**, nếu benchmark chỉ ra hot path phù hợp SIMD. Không dùng trước.

---

## 9. Effective Java — điều quan trọng nhất cho dự án này

| Item | Áp dụng ở Kage |
|---|---|
| Ưu tiên immutability | Điều 43 — record cho mọi DTO |
| Ưu tiên composition | Điều 13 — nền tảng của ECS |
| Dùng enum thay int constant | [CODING-STANDARDS.md §8](CODING-STANDARDS.md) |
| Tránh `null` | [CODING-STANDARDS.md §10](CODING-STANDARDS.md) |
| Đừng dùng `finalize`/`Cleaner` | §6 |
| Ưu tiên `List`/`Map` interface | [CODING-STANDARDS.md §13](CODING-STANDARDS.md) |
| Kiểm tra tham số ở đầu method | Fail Fast, Điều 16 |
| `equals`/`hashCode` đi cùng nhau | Record lo giùm — thêm lý do dùng record |

---

## 10. Bẫy Java trong game — danh sách kiểm tra

| Bẫy | Triệu chứng | Cách tránh |
|---|---|---|
| Boxing ngầm trong `Map<Integer,...>` | GC tăng đều, không rõ nguồn | Primitive collection |
| `ByteBuf` không release (Netty) | Memory leak chậm | Bật leak detector ở dev; `try/finally` |
| Pool object dùng sau khi `free()` | Dữ liệu sai ngẫu nhiên, cực khó tái hiện | Set tham chiếu = null sau free; test kỹ |
| Virtual Thread pin bởi `synchronized` | Throughput I/O tụt bất thường | `ReentrantLock` |
| `String.format` trong log | Tạo rác kể cả khi log tắt | Parameterized logging `{}` |
| Lambda capture trong hot loop | Allocation mỗi vòng lặp | Lambda không capture / method reference |
| `Collections.unmodifiableList` mỗi lần gọi getter | Allocation mỗi lần gọi | Tạo một lần, lưu field |
| Static mutable state | Bug concurrency + không test được | Không dùng |

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Game tick chạy **single-thread** — loại bỏ phần lớn bài toán concurrency gameplay | Accepted |
| 2 | Virtual Thread **chỉ** cho blocking I/O, cấm cho game loop | Accepted |
| 3 | `ReentrantLock` thay `synchronized` ở code có thể chạy trên Virtual Thread | Accepted |
| 4 | **FFM API: không dùng ở MVP** | Accepted — xem lại nếu profiling chỉ ra nhu cầu |
| 5 | **Vector API: không dùng ở MVP** (còn incubator) | Accepted — xem lại sau Phase 9 |
| 6 | Object Pool chỉ dùng khi benchmark chứng minh — không pool theo phản xạ | Accepted |
| 7 | Không dựa vào escape analysis của JIT | Accepted |
| 8 | Không dùng Lombok (thêm annotation processing; record đã đủ) | Accepted |

## ADR cần tạo

| ADR | Nội dung | Ưu tiên |
|---|---|---|
| ~~**ADR-019**~~ | ✅ **Accepted 2026-07-28 (ZGC)** — [ADR-019](../01-architecture/adr/ADR-019-GC-Choice.md), có số liệu soak test thật | Hết chặn |
| ✅ **ADR-018** | Tick rate server **60Hz** (16.6ms/tick) — đã chốt | Accepted |

## Tài liệu liên quan

- [CODING-STANDARDS.md](CODING-STANDARDS.md) — quy ước cơ bản
- [JVM.md](../09-performance/JVM.md) — chọn GC, JVM flags, profiling
- [PERFORMANCE.md](../09-performance/PERFORMANCE.md) — tối ưu theo tầng
- [BENCHMARK.md](../09-performance/BENCHMARK.md) — cách đo trước khi tối ưu
- [ECS.md](../03-engine/ECS.md) — vì sao Component mutable

## Việc cần làm tiếp theo

1. Phase 1: xác lập §5.1 (game tick single-thread) trong code `engine-ecs` — đây là quyết định nền, khó đổi sau.
2. Phase 9: đo GC pause thật → chốt ADR-019.
3. Xem lại §7 (FFM) và §8 (Vector API) sau Phase 9 dựa trên profiling, **không** dựa trên "nghe nói nhanh".
