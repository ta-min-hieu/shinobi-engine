# PERFORMANCE.md

**Hiệu năng theo từng tầng**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Luật gốc | [Hiến pháp Điều 29–32](../00-governance/KAGE-CONSTITUTION.md#chương-vi--nguyên-tắc-hiệu-năng) |

> Điều 30: **Benchmark trước, tối ưu sau. Không chấp nhận "tôi nghĩ nó nhanh hơn".**
> Điều 17: Performance đứng **thứ 5** trong thứ tự ưu tiên. Không đánh đổi Correctness lấy Performance.

---

## 1. Nguyên tắc

| Nguyên tắc | Nghĩa là |
|---|---|
| **Đo trước** | Không tối ưu thứ chưa đo |
| **Chỉ tối ưu hot path** | Tối ưu code khởi tạo/tool = làm hại readability vô ích |
| **p99, không phải trung bình** | Trung bình 10ms + p99 60ms = 1% người chơi giật liên tục |
| **Latency ổn định > throughput cao** | Game cần đều đặn, không cần nhanh nhất |

⚠️ **Hot path của Kage là gì**: game tick (60 lần/giây) · packet encode/decode · ECS update · rendering. **Mọi thứ khác không phải hot path.**

---

## 2. Ngân sách — bức tranh tổng thể

**Server tick (60Hz = 16.6ms — [ADR-018](../01-architecture/adr/ADR-018-Tick-Rate.md)):**

| Hạng mục | Ngân sách | Chi tiết |
|---|---|---|
| ECS update | ≤ 4ms | [ECS.md §9](../03-engine/ECS.md) |
| Physics | ≤ 2.5ms | [PHYSICS.md](../03-engine/PHYSICS.md) |
| AI | ≤ 2.5ms | [AI.md §9](../03-engine/AI.md) |
| State sync + encode | ≤ 2.5ms | [SERVER.md §6](../05-server/SERVER.md) |
| Event dispatch | ≤ 1ms | — |
| **Dự phòng (GC, spike)** | ~4ms | — |

**Client frame:**

| | Desktop (60 FPS) | Android (30 FPS) |
|---|---|---|
| Ngân sách | 16.6ms | 33ms |
| Draw call | ≤ 100 | ≤ 50 |
| RAM | ≤ 1GB | ≤ 400MB |

⚠️⚠️ **TOÀN BỘ SỐ LIỆU TRÊN CHƯA ĐƯỢC ĐO.** Đây là **ngân sách đề xuất để có mục tiêu**, không phải sự thật đã kiểm chứng. Phải đo ở Phase 3 (client) và Phase 9 (server) rồi cập nhật ([AI-WORKING-RULES.md §14](../00-governance/AI-WORKING-RULES.md)).

---

## 3. CPU

| Nguồn tốn CPU | Giảm bằng |
|---|---|
| ECS iteration | ComponentMapper, không `getComponent()` ([ECS.md §5](../03-engine/ECS.md)) |
| AI mọi entity mỗi tick | Time-slicing + LOD ([AI.md §9](../03-engine/AI.md)) |
| Physics | Chỉ entity cần physics; ngủ (sleep) entity đứng yên |
| Pathfinding | Giới hạn số lần tìm/tick |
| Collision check n×m | Spatial grid — ⚠️ **chỉ khi đo thấy cần** |
| Packet encode | Reuse buffer |

---

## 4. Memory

Chi tiết: [JAVA-GUIDELINES.md §4](../02-coding-standards/JAVA-GUIDELINES.md).

| Nguồn rác | Sửa |
|---|---|
| Boxing (`Map<Integer,...>`) | Primitive collection |
| `new Vector2()` mỗi frame | Field tạm reuse |
| Iterator trong hot loop | Index loop |
| Stream trong tick | `for` |
| `String.format` trong log | Parameterized `{}` |
| Lambda capture | Lambda không capture |

⚠️ **Object Pool là con dao hai lưỡi** ([JAVA-GUIDELINES.md §4.3](../02-coding-standards/JAVA-GUIDELINES.md)): pool giữ object sống mãi → chúng vào old gen → **có thể làm GC tệ hơn**. Chỉ pool khi đo thấy cần.

---

## 5. GC

Chi tiết: [JVM.md](JVM.md).

| Nguyên tắc | Vì sao |
|---|---|
| Object sống ngắn = **bạn** | Young gen collect rất rẻ |
| Object sống trung bình = **kẻ thù** | Bị promote → major GC |
| Cache lớn giữ mãi | Old gen phình → major GC lâu |

**GC pause là kẻ thù số 1 của game loop.** Pause 100ms = 3 tick bị bỏ = người chơi thấy giật.

✅ Chọn GC: **ADR-019** — ZGC, Accepted (2026-07-28, số liệu thật từ soak test Phase 9).

---

## 6. Rendering

Chi tiết: [RENDERING.md](../03-engine/RENDERING.md).

| Nguồn chậm | Giảm bằng |
|---|---|
| **Draw call nhiều** | Gom sprite cùng atlas |
| **Texture switch** | 1 atlas/zone |
| Vẽ thứ ngoài màn hình | Culling |
| Overdraw | Sort đúng, không vẽ chồng vô ích |
| Particle không giới hạn | Pool + giới hạn số lượng |

⚠️ **Draw call là chi phí chính của 2D rendering**, không phải số pixel.

⚠️ **Nhánh 3D có thêm một nguồn chậm mà bảng trên không có: số tam giác.** Từ
[ADR-031](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md), nhân vật render bằng
`ModelBatch`. Mesh sinh từ text→3D ra ~93k tam giác — 20 nhân vật trên màn là 1,9 triệu tam giác
mỗi khung. Ngân sách LOD **đã đo 2026-08-05**: 5k/10k/20k cho low/med/high, tức 20 nhân vật ở tier
Medium là **200k tam giác/khung** ([ART-PIPELINE §4.4](../14-art/ART-PIPELINE.md)). Con số còn thiếu
là **frame time GPU thật** — đo được khi `client-core` vẽ được model đầu tiên; đó cũng là điều kiện #1
ở [ADR-031 "Revisit Trigger"](../01-architecture/adr/ADR-031-25D-Realtime-3D-Rendering.md).
Thêm nữa, `ModelBatch` và `SpriteBatch` **không trộn được** — mỗi lần đổi qua lại là một flush.

---

## 7. Network

Chi tiết: [NETWORK.md](../05-server/NETWORK.md).

| Vấn đề | Giảm bằng |
|---|---|
| **O(n²) khi đông người** | ⚠️ **AoI bắt buộc** ([SERVER.md §6](../05-server/SERVER.md)) |
| Gửi state không đổi | Delta compression |
| Entity xa gửi mỗi tick | Tần suất theo khoảng cách |
| Packet nhỏ nhiều | Gom (batching) |
| Băng thông | Nén — ⚠️ **chỉ khi đo thấy cần** |

⚠️ **AoI không phải tối ưu — là bắt buộc.** Không có AoI, map 100 người sẽ sập băng thông ngay Phase 3.

---

## 8. Database

Chi tiết: [DATABASE.md](../05-server/DATABASE.md).

| Vấn đề | Giảm bằng |
|---|---|
| Ghi mỗi thay đổi | Ghi định kỳ cho dữ liệu tái tạo được ([SERVER.md §8](../05-server/SERVER.md)) |
| Query chậm | Index — ⚠️ sau khi `EXPLAIN ANALYZE` |
| Pool cạn | Tăng pool / tìm query giữ connection lâu |
| Transaction dài | Giữ transaction ngắn nhất |
| **Query trong tick thread** | ⚠️ **Cấm** — Virtual Thread |

⚠️ **Index không miễn phí**: tăng tốc đọc, **làm chậm ghi**. Không thêm theo cảm tính (Điều 30).

---

## 9. Redis

| Vấn đề | Giảm bằng |
|---|---|
| Round-trip nhiều | Pipeline / MGET |
| Key không TTL | Luôn set TTL cho dữ liệu tạm |
| `KEYS *` | ⚠️ **Cấm tuyệt đối** — O(n), block Redis |
| Value lớn | Chia nhỏ |

---

## 10. Điều KHÔNG nên tối ưu

⚠️ Quan trọng ngang việc biết tối ưu gì.

| Không tối ưu | Vì sao |
|---|---|
| Code khởi động | Chạy 1 lần |
| Tool, script build | Không ảnh hưởng người chơi |
| Test | Rõ ràng > nhanh |
| Code xử lý 1 lần/giờ | Không đáng |
| **Thứ chưa đo** | Điều 30 |
| Micro-optimization không đo được | `i++` vs `++i` — vô nghĩa |

**Nếu ai đó tối ưu code ngoài hot path và làm nó khó đọc hơn → đó là làm hại dự án** (Điều 17).

---

## 11. Quy trình tối ưu

```
1. Đo → có số liệu baseline
2. Xác định bottleneck THẬT (profiler, không đoán)
3. Đặt mục tiêu cụ thể ("giảm tick p99 từ 45ms xuống 30ms")
4. Tối ưu
5. Đo lại → có cải thiện thật không?
6. Không cải thiện → REVERT
7. Ghi lại số liệu để so sánh regression
```

⚠️ **Bước 6 hay bị bỏ qua.** Tối ưu không hiệu quả mà giữ lại = code khó đọc mà không lợi ích = tệ hơn không tối ưu.

---

## 12. Bẫy hiệu năng đã biết

| Bẫy | Ở đâu |
|---|---|
| Ashley không cache-friendly như DOD hứa | [ECS.md §9](../03-engine/ECS.md) — **thừa nhận, không tự lừa** |
| Object Pool có thể làm GC tệ hơn | [JAVA-GUIDELINES.md §4.3](../02-coding-standards/JAVA-GUIDELINES.md) |
| Escape analysis không đáng tin | [JAVA-GUIDELINES.md §4.2](../02-coding-standards/JAVA-GUIDELINES.md) |
| Server chạy physics tốn CPU thật | [PHYSICS.md §3](../03-engine/PHYSICS.md) |
| Sort Y xung đột với gom atlas | [RENDERING.md §8](../03-engine/RENDERING.md) |
| Quest kiểm tra mọi event → O(n×m) | [GAMEPLAY.md §9](../05-server/GAMEPLAY.md) |
| Script chạy trong tick thread | [SCRIPTING.md §7](../06-plugin/SCRIPTING.md) |
| Metric cardinality nổ | [OBSERVABILITY.md §4](../07-infrastructure/OBSERVABILITY.md) |

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | **Đo trước, tối ưu sau** — không có số = không merge | Accepted |
| 2 | **Chỉ tối ưu hot path**; ngoài hot path ưu tiên readability | Accepted |
| 3 | **p99, không phải trung bình** | Accepted |
| 4 | Latency ổn định > throughput cao | Accepted |
| 5 | **AoI là bắt buộc**, không phải tối ưu | Accepted |
| 6 | Index/nén/spatial partition: **chỉ sau khi đo** | Accepted |
| 7 | **Tối ưu không cải thiện → revert** | Accepted |
| 8 | ⚠️ **Toàn bộ ngân sách §2 chưa đo** | ⏳ Đo Phase 3 (client), Phase 9 (server) |

## ADR cần tạo

| ADR | Nội dung | Ưu tiên |
|---|---|---|
| ✅ **ADR-018** | **Tick rate = 60Hz** (16.6ms/tick) — đã chốt | [ADR-018](../01-architecture/adr/ADR-018-Tick-Rate.md) |
| **ADR-019** | Chọn GC (§5) | 🟢 Chặn Phase 9 |

## Tài liệu liên quan

- [BENCHMARK.md](BENCHMARK.md) — **đo thế nào**
- [JVM.md](JVM.md) — GC, flags, profiler
- [JAVA-GUIDELINES.md](../02-coding-standards/JAVA-GUIDELINES.md) — kỹ thuật cụ thể
- [ECS.md](../03-engine/ECS.md) · [RENDERING.md](../03-engine/RENDERING.md) · [SERVER.md](../05-server/SERVER.md)
- [OBSERVABILITY.md](../07-infrastructure/OBSERVABILITY.md) — đo ở production

## Việc cần làm tiếp theo

1. ✅ ADR-018 đã chốt **60Hz** — §2 ngân sách = 16.6ms/tick.
2. Phase 3: đo **client** thật trên Android tầm trung → cập nhật §2.
3. Phase 9: đo **server** thật dưới tải → cập nhật §2.
4. Cho tới khi đo: **không tối ưu gì cả** (Điều 30).
