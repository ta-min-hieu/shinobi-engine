# OBSERVABILITY.md

**Metrics, Logs, Tracing, Alerts**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Luật gốc | [Hiến pháp Điều 44](../00-governance/KAGE-CONSTITUTION.md#điều-44-observability) |

> Điều 44: **Hệ thống lỗi — không được đoán, phải quan sát được.**
> **Không có Observability thì Production không được coi là hoàn chỉnh.**

---

## 1. Ba trụ cột — dùng cái nào khi nào

| | Trả lời câu hỏi | Ví dụ |
|---|---|---|
| **Metrics** | "Có vấn đề không? Bao nhiêu?" | Tick time p99 = 45ms → **có** vấn đề |
| **Logs** | "Chuyện gì đã xảy ra với case cụ thể này?" | "Character 123 mất item lúc 14:32" |
| **Tracing** | "Chậm ở đâu trong chuỗi gọi?" | Command → world → feature → DB: DB chiếm 80ms |

**Thứ tự dùng khi có sự cố**: Metrics phát hiện → Tracing khoanh vùng → Logs xác định nguyên nhân.

---

## 2. Micrometer vs OpenTelemetry — ranh giới

⚠️ Hai thứ này **chồng lấn** (cả hai làm metrics). Dùng cả hai mà không phân định = **2 hệ metrics song song, số liệu lệch nhau, không ai tin cái nào**.

**Đề xuất phân định:**

| Việc | Công cụ | Vì sao |
|---|---|---|
| **Metrics** | **Micrometer** → Prometheus | Micrometer là facade Java trưởng thành; tích hợp Prometheus tốt |
| **Tracing** | **OpenTelemetry** | Chuẩn ngành cho tracing; Micrometer Tracing thực chất bọc OTel |
| **Logs** | SLF4J + Logback (JSON) | Chuẩn Java |

**Không** dùng OpenTelemetry Metrics. **Không** dùng Micrometer Tracing.

✅ **Chốt** — [ADR-015](../01-architecture/adr/ADR-015-Micrometer-vs-OpenTelemetry.md) (2026-07-26), đúng phân định trên.

**Lựa chọn thay thế đã cân nhắc và không chọn**: dùng OpenTelemetry cho **cả ba** (chuẩn hoá một vendor) — đơn giản hơn về mặt khái niệm, nhưng OTel Metrics cho JVM chưa trưởng thành bằng Micrometer (lý do đầy đủ ở ADR-015).

---

## 3. Metrics — đo gì

### 3.1 Game server (quan trọng nhất)

| Metric | Kiểu | Vì sao |
|---|---|---|
| `kage.tick.duration` | Timer | ⚠️ **Metric quan trọng nhất.** p50/p95/**p99** — ✅ **implemented 2026-07-26** (Phase 9 lát cắt 1): `WorldFacade.tickLoop` ghi qua `TickMetricsPort`/`MicrometerTickMetricsPort`, scrape ở `GET /metrics` (cổng 9404, `MetricsHttpServer`), tag `world` cố định (chỉ `"main"` — dungeon/arena chưa đo, xem ROADMAP.md Phase 9) |
| `kage.tick.overrun.count` | Counter | Tick vượt ngân sách ([SERVER.md §3](../05-server/SERVER.md)) |
| `kage.players.online` | Gauge | — |
| `kage.entities.count` | Gauge | Theo map |
| `kage.ecs.system.duration` | Timer (tag: system) | System nào ăn ngân sách |

⚠️ **Phải đo p99, không phải trung bình.** Tick trung bình 10ms nhưng p99 = 60ms nghĩa là **1% người chơi bị giật liên tục** — trung bình che mất điều đó.

### 3.2 Network

| Metric | Kiểu |
|---|---|
| `kage.packet.received` / `sent` | Counter (tag: opcode) |
| `kage.packet.size` | Distribution |
| `kage.connection.active` | Gauge |
| `kage.ratelimit.rejected` | Counter (tag: opcode) |

### 3.3 Database

| Metric | Kiểu |
|---|---|
| `kage.db.query.duration` | Timer (tag: query name) |
| `kage.db.pool.active` / `pending` | Gauge — ⚠️ pending > 0 kéo dài = pool quá nhỏ |
| `kage.db.transaction.failed` | Counter |

### 3.4 JVM (Micrometer có sẵn)

`jvm.gc.pause` ⚠️ (quan trọng — GC pause giết game loop) · `jvm.memory.used` · `jvm.threads.live`

### 3.5 Business

| Metric | Vì sao |
|---|---|
| `kage.login.success` / `failed` | Phát hiện tấn công brute-force |
| `kage.item.enhance` (tag: result) | Tỉ lệ thành công có khớp thiết kế? |
| `kage.gold.total` | ⚠️ Phát hiện lạm phát / duplicate gold |

⚠️ **`kage.gold.total` là metric chống gian lận**: tổng gold toàn server tăng đột biến = có người duplicate. Không có metric này = phát hiện sau khi kinh tế đã sập.

---

## 4. Cardinality — bẫy chết người

⚠️ **Không bao giờ dùng `characterId` làm tag của metric.**

```java
// ❌ THẢM HOẠ — 10.000 người chơi = 10.000 time series
Timer.builder("kage.command.duration")
     .tag("characterId", String.valueOf(characterId))   // ❌
     .register(registry);

// ✅ Đúng — cardinality thấp
Timer.builder("kage.command.duration")
     .tag("opcode", opcodeName)                          // ~50 giá trị
     .register(registry);
```

**Quy tắc**: tag chỉ nhận giá trị **hữu hạn và nhỏ** (< ~100). ID người chơi, sessionId, item instance id → **thuộc về Log, không thuộc Metric**.

Vi phạm = Prometheus OOM.

---

## 5. Logging

### 5.1 Format

| Môi trường | Format |
|---|---|
| Development | Human-readable |
| Production | **JSON structured** — máy parse được |

```java
// ✅ Parameterized + context đầy đủ
log.error("Không lưu được inventory: characterId={}, itemCount={}, sequenceId={}",
          characterId, items.size(), sequenceId, exception);
```

### 5.2 Level

| Level | Dùng cho |
|---|---|
| `DEBUG` | **Chỉ Development** |
| `INFO` | Business event: đăng nhập, level up, giao dịch |
| `WARN` | Recoverable: Redis timeout → fallback DB; **tick quá hạn** |
| `ERROR` | Unexpected: không ghi được DB |

### 5.3 Cấm

❌ Log mỗi frame/tick · ❌ Log mật khẩu/token/session id · ❌ `printStackTrace()` · ❌ Nối chuỗi (`"a" + b`)

⚠️ **Log trong game tick là nguy hiểm**: 30 tick/giây × 1000 entity = 30.000 dòng/giây → I/O giết tick. Log trong tick chỉ ở mức WARN/ERROR, và phải rate-limit.

---

## 6. Tracing

**Áp dụng từ Phase 9** — khi có nhiều thành phần, trace mới có giá trị.

```
Command từ client
  └─ span: gateway.decode
     └─ span: gateway.validate
        └─ span: world.tick.process
           └─ span: feature.inventory.addItem
              └─ span: db.query.insert_item
```

⚠️ **Không trace mỗi tick** — tick chạy 60 lần/giây, trace tất cả = overhead khổng lồ. **Sampling**: trace 1% Command, hoặc trace 100% Command **chậm** (> ngưỡng).

**Trace context xuyên thread**: Command đi qua EventLoop → tick thread → Virtual Thread (DB). Truyền trace context qua các ranh giới này cần cẩn thận — **Scoped Values** (Java 25) là công cụ phù hợp ([JAVA-GUIDELINES.md §2](../02-coding-standards/JAVA-GUIDELINES.md)).

---

## 7. Health Check

| Endpoint | Trả lời |
|---|---|
| `/health/live` | Process còn sống? (orchestrator restart nếu không) |
| `/health/ready` | Sẵn sàng nhận người chơi? (DB kết nối được? script load xong?) |

⚠️ **Phân biệt quan trọng**: server đang chạy nhưng DB chết → `live` = OK (đừng restart, restart không giúp), `ready` = FAIL (đừng gửi người chơi mới vào).

⚠️ Health check **không** dùng Spring Actuator ([ADR-009](../01-architecture/adr/ADR-009-Why-No-Spring-Boot.md)) → tự implement HTTP endpoint nhẹ.

---

## 8. Alerts

**Alert phải hành động được.** Alert không ai làm gì = nhiễu → sau 2 tuần không ai đọc alert nữa.

| Alert | Ngưỡng (đề xuất — **chưa verify**) | Hành động |
|---|---|---|
| 🔴 Tick p99 > ngân sách | > 16.6ms trong 5 phút (@60Hz) | Điều tra ngay |
| 🔴 Server down | `/health/live` fail | Restart / failover |
| 🔴 DB pool cạn | pending > 0 trong 1 phút | Tăng pool / tìm query chậm |
| 🔴 **Gold tổng tăng bất thường** | > X%/giờ | ⚠️ Nghi duplicate — điều tra ngay |
| 🟡 GC pause > 100ms | p99 | Xem lại GC config |
| 🟡 Login failed tăng vọt | > X/phút | Nghi brute-force |
| 🟢 Người chơi online thấp bất thường | — | Xem có sự cố không |

⚠️ Ngưỡng cụ thể **chưa có** — phải đo baseline ở Phase 9 trước. Đặt ngưỡng bừa = alert giả liên tục = alert fatigue.

---

## 9. Dashboard

| Dashboard | Cho ai | Nội dung |
|---|---|---|
| **Server Health** | Vận hành | Tick time, players online, CPU/RAM, GC |
| **Network** | Vận hành | Packet rate, latency, connection, rate-limit rejected |
| **Database** | Vận hành | Query duration, pool, transaction failed |
| **Business** | Game designer | Người chơi, level distribution, gold tổng, tỉ lệ cường hoá |
| **Anti-cheat** | Vận hành | Rate-limit rejected, login failed, gold bất thường |

---

## 10. Lộ trình

| Phase | Observability |
|---|---|
| 0–2 | Logging cơ bản (SLF4J + Logback) |
| 3 | + Metrics cơ bản (tick time, players, DB) |
| **9** | **+ Prometheus + Grafana + Alerts + Tracing** — bắt buộc để load test có ý nghĩa |
| 5+ | + Business dashboard, anti-cheat dashboard |

⚠️ **Phase 9 không được đóng nếu chưa có observability** (Điều 44). Load test mà không đo được gì thì load test vô nghĩa.

---

## Technical Decisions

| # | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| 1 | **Micrometer cho Metrics, OpenTelemetry cho Tracing** — không chồng lấn | Hai hệ metrics = số liệu lệch, không ai tin | Accepted — [ADR-015](../01-architecture/adr/ADR-015-Micrometer-vs-OpenTelemetry.md) |
| 2 | **Đo p99, không phải trung bình** | Trung bình che giật của 1% người chơi | Accepted |
| 3 | ⚠️ **Không dùng ID làm tag metric** | Cardinality nổ → Prometheus OOM | Accepted |
| 4 | JSON structured log ở production | Máy parse được | Accepted |
| 5 | **Không log mỗi tick** | I/O giết tick | Accepted |
| 6 | **`kage.gold.total` là metric chống gian lận** | Phát hiện duplicate trước khi kinh tế sập | Accepted |
| 7 | Tracing có **sampling**, không trace mọi tick | Overhead | Accepted |
| 8 | `live` vs `ready` phân biệt rõ | DB chết thì restart không giúp | Accepted |
| 9 | Health check tự implement, không Spring Actuator | [ADR-009](../01-architecture/adr/ADR-009-Why-No-Spring-Boot.md) | Accepted |
| 10 | **Ngưỡng alert chưa có** — phải đo baseline Phase 9 | Ngưỡng bừa = alert fatigue | ⏳ |
| 11 | Alert không hành động được → **không tạo** | Nhiễu làm hỏng cả hệ thống alert | Accepted |

## Tài liệu liên quan

- [SERVER.md §3, §11](../05-server/SERVER.md) — tick time, ngân sách
- [JVM.md](../09-performance/JVM.md) — GC metrics
- [BENCHMARK.md](../09-performance/BENCHMARK.md) — đo lúc dev vs đo lúc chạy
- [SECURITY.md](../08-security/SECURITY.md) — audit log, anti-cheat metric
- [DOCKER.md §9](DOCKER.md) — log ra stdout
- [ROADMAP.md](../11-process/ROADMAP.md) — Phase 9

## Việc cần làm tiếp theo

1. ✅ **ADR-015 đã chốt** (2026-07-26).
2. Phase 3: thêm `kage.tick.duration` **sớm nhất có thể** — đây là metric duy nhất mà không có nó thì không biết server có khoẻ không.
3. Phase 9: đo **baseline** trước, đặt ngưỡng alert sau (§8).
4. Phase 5: `kage.gold.total` (§3.5) — trước khi có shop/gold, và bắt buộc trước trade (Phase 7).
