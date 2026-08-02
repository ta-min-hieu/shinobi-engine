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
| `kage.tick.overrun.count` | Counter | Tick vượt ngân sách ([SERVER.md §3](../05-server/SERVER.md)) — ✅ **implemented 2026-07-28** (Phase 9 lát cắt 8), cùng chỗ log WARN "World tick quá hạn" |
| `kage.players.online` | Gauge | ✅ **implemented 2026-07-28** — `ConnectedCharacterRegistry.onlineCount()` |
| `kage.entities.count` | Gauge | ✅ **implemented 2026-07-28** (Phase 9 lát cắt 9): `EcsWorld.entityCount()`, chỉ đo World "main" (cùng lý do `kage.tick.duration` không đo dungeon/arena — tránh nổ cardinality) |
| `kage.ecs.system.duration` | Timer (tag: system) | ✅ **implemented 2026-07-28**: `EcsWorld.update(float, SystemTimingListener)` đo từng System (`AiSystem`/`MovementSystem`/`CombatSystem`/`SkillSystem`/`BossAiSystem`/`StatusEffectSystem`), Timer pre-build theo tên cố định |

⚠️ **Phải đo p99, không phải trung bình.** Tick trung bình 10ms nhưng p99 = 60ms nghĩa là **1% người chơi bị giật liên tục** — trung bình che mất điều đó.

### 3.2 Network

| Metric | Kiểu | Trạng thái |
|---|---|---|
| `kage.packet.received` / `sent` | Counter (tag: opcode) | ✅ **implemented 2026-07-28** — `GatewayNetworkMetrics`, opcode lạ gộp vào tag `"unknown"` (chống cardinality nổ nếu client giả mạo opcode) |
| `kage.packet.size` | Distribution (tag: direction) | ✅ **implemented 2026-08-02** — `GatewayNetworkMetrics`, đọc `frame.payload().length`/`payload.length` sẵn có tại điểm gọi (không re-serialize), tag `direction=received\|sent` |
| `kage.connection.active` | Gauge | ✅ **implemented 2026-07-28** — đếm ở `channelActive`/`channelInactive`, khác `kage.players.online` (đã chọn nhân vật) |
| `kage.ratelimit.rejected` | Counter (tag: scope) | ✅ **implemented 2026-07-28** — 2 điểm chặn thật: `MoveCommand` (tag `move`) + chat (tag `chat_<CHANNEL>`) |

### 3.3 Database

| Metric | Kiểu | Trạng thái |
|---|---|---|
| `kage.db.query.duration` | Timer (tag: `operation`) | ✅ **implemented 2026-08-02** — `JdbcOperations` đo quanh 5 điểm thực thi JDBC thật (`query(Connection,...)`, `executeUpdate`, `executeInsertReturningId`, `lock`, `batchUpdate` — mọi overload nhận `DataSource`/`*OrThrow` đều delegate qua 1 trong 5 điểm này nên không đếm trùng), qua `DbMetricsPort`/`MicrometerDbMetricsPort` (cùng pattern `TickMetricsPort`). ⚠️ Tag `operation` KHÔNG phải "tên câu query" như dự kiến ban đầu ở cột "Kiểu" — mà là verb đầu câu SQL (SELECT/INSERT/UPDATE/DELETE) lấy từ literal SQL: thread `DbMetricsPort` qua tham số của ~35 `Postgres*Adapter` (131 call site `JdbcOperations`) để có tên query thật là đổi chữ ký hàng loạt, ngoài phạm vi lát cắt này — verb là thoả hiệp an toàn cardinality (§4) không cần đổi call site nào |
| `kage.db.pool.active` / `pending` | Gauge — ⚠️ pending > 0 kéo dài = pool quá nhỏ | ✅ **implemented 2026-07-28** — đọc `HikariPoolMXBean` qua `ServerDatabaseBootstrap` (Điều 23, cùng pattern `totalGold()`) |
| `kage.db.transaction.failed` | Counter | ✅ **implemented 2026-08-02** — `JdbcTransactionRunner.rollbackQuietly` gọi `DbMetricsPort#recordTransactionFailed()` ngay khi action ném `RuntimeException` (transaction coi như thất bại), bất kể bản thân `connection.rollback()` có thành công hay không |

### 3.4 JVM (Micrometer có sẵn)

`jvm.gc.pause` ⚠️ (quan trọng — GC pause giết game loop) · `jvm.memory.used` · `jvm.threads.live`

### 3.5 Business

| Metric | Vì sao |
|---|---|
| `kage.login.success` / `failed` | Phát hiện tấn công brute-force — ✅ **implemented 2026-07-28**: `GatewayNetworkMetrics`, ghi trong `handleConnectBlocking` |
| `kage.item.enhance` (tag: result) | Tỉ lệ thành công có khớp thiết kế? — ✅ **implemented 2026-07-28**: tag = tên `InventoryFacade.EnhanceResult` (~7 giá trị cố định) |
| `kage.gold.total` | ⚠️ Phát hiện lạm phát / duplicate gold — ✅ **implemented 2026-07-28** (Phase 9 lát cắt 8): `ServerDatabaseBootstrap.totalGold()` (`SELECT SUM(gold) FROM character_stat`), Gauge đọc lúc scrape |

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

✅ **implemented 2026-07-28** — `JsonLogEncoder` tự viết (`server-gateway/.../logging/`, KHÔNG dùng
`logstash-logback-encoder`, thư viện ngoài không có trong TECHNOLOGY-STACK.md). Kích hoạt qua
`-Dlogback.configurationFile=logback-production.xml` lúc chạy production; `./gradlew run` (dev)
không set flag này nên vẫn dùng cấu hình mặc định Logback (console human-readable).

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

✅ **implemented 2026-07-28** (Phase 9 lát cắt 9) — `GatewayTracing` (OpenTelemetry SDK thật, export
OTLP/HTTP tới Jaeger `docker-compose` service, cổng 4318), `Sampler.traceIdRatioBased(0.01)` (nhánh
"1% Command" — nhánh "100% Command chậm" CHƯA làm, cần `SpanProcessor` tự viết lọc theo duration,
để dành lát cắt sau). Span `gateway.command` (tag `opcode`) bao decode+validate+dispatch tạo ở 1
điểm trung tâm (`GatewayChannelHandler.channelRead0`) — áp dụng cho MỌI opcode (~57 opcode có
handler thật).

✅ **Retrofit span con sâu hơn — cập nhật 2026-08-02** (khớp ví dụ đầy đủ ở trên, dùng OTel
`Context.current()` chụp TRƯỚC khi rời EventLoop + `Scope` lúc chạy trên Virtual Thread, cùng
attribute `result` = tên enum kết quả nghiệp vụ): mở rộng từ 1 luồng đại diện gốc
(`feature.inventory.enhance`, `EnhanceItemCommand`) sang **3 lệnh chạm gold/item thật khác, cùng
lớp rủi ro** (SECURITY.md, `server/CLAUDE.md` "Transaction + audit log cho: trade, gold, cường
hoá... shop"):

| Span | Lệnh | Vì sao chọn |
|---|---|---|
| `feature.inventory.enhance` | `EnhanceItemCommand` | Luồng đại diện gốc (Phase 9 lát cắt 10) |
| `feature.shop.buy` | `BuyItemCommand` | Chạm gold thật, cùng lớp rủi ro cường hoá |
| `feature.shop.sell` | `SellItemCommand` | Chạm gold thật, cùng lớp rủi ro cường hoá |
| `feature.trade.confirm` | `ConfirmTradeCommand` | NƠI DUY NHẤT `ConfirmTradeUseCase` xác thực lại TOÀN BỘ lời chào LÚC THI HÀNH rồi chạm gold/item thật (feature-trade/CLAUDE.md) — SECURITY.md ghi trade dùng xác thực lại lúc thi hành, đáng để trace |

**CHƯA retrofit phần opcode còn lại (~53/57)** — có chủ đích, không phải thiếu sót: đa số (Move,
SetTarget, UseSkill, chat, dialogue, quest liệt kê...) không chạm DB/gold trong lời gọi từ gateway
(VD `UseSkillCommand`/`MoveCommand` chỉ enqueue vào `server-world` — inbox rẻ, xử lý thật xảy ra
SAU trên tick thread riêng, không đồng bộ với request/response gateway — span con kiểu
"chụp Context trước khi rời EventLoop, Virtual Thread thực thi" không áp dụng được tự nhiên cho
luồng fire-and-forget này; trace tick thread là bài toán khác, chưa làm). Combat/skill-cast vì
vậy KHÔNG có deep span ở lát cắt này — cân nhắc rồi loại, không phải bỏ sót.

Verify span thật sự được tạo (không chỉ đọc code): `GatewayTracing.forTesting(SpanExporter)` (mới
thêm, `Sampler.alwaysOn()` + `SimpleSpanProcessor` export ĐỒNG BỘ, KHÔNG cần OTLP/Jaeger thật —
không thêm dependency mới, `SpanExporter`/`SimpleSpanProcessor` đã có sẵn trong
`opentelemetry-sdk-trace`) dùng ở
`GatewayServerIntegrationTest#clientCommands_shouldEmitDeepChildSpans_forGoldAffectingCommands` —
gửi cả 3 lệnh qua socket thật, xác nhận span đúng tên + attribute `result` xuất hiện. Dây
OTLP→Jaeger thật (docker compose) đã verify tay ở lát cắt gốc (`feature.inventory.enhance`), không
lặp lại ở đây.

Dùng OTLP/HTTP (không phải gRPC) để tránh transitive `grpc-netty-shaded` đụng version với
`netty-all` dự án đã dùng (Điều 23).

---

## 7. Health Check

✅ **Implemented 2026-07-28** (Phase 9 lát cắt 8) — `MetricsHttpServer` (cổng 9404, dùng chung với
`/metrics`, Điều 9 — không dựng thêm process/cổng riêng chỉ để tách health check).

| Endpoint | Trả lời | Điều kiện |
|---|---|---|
| `/health/live` | Process còn sống? (orchestrator restart nếu không) | Luôn 200 — chạy tới được handler này đã là câu trả lời |
| `/health/ready` | Sẵn sàng nhận người chơi? (DB kết nối được?) | 200 nếu `ServerDatabaseBootstrap.isReady()` (`SELECT 1`) OK, 503 nếu không |

⚠️ "Script load xong" (bản gốc mục này) KHÔNG cần kiểm tra ở `/health/ready` — script validation
đã chạy fail-fast TRƯỚC lúc server nghe cổng (`GatewayBootstrap.main`, ném exception nếu lỗi —
process không tồn tại để trả `/health/ready` nếu bước đó thất bại), nên tới lúc handler này chạy
được, phần đó chắc chắn đã qua.

⚠️ **Phân biệt quan trọng**: server đang chạy nhưng DB chết → `live` = OK (đừng restart, restart không giúp), `ready` = FAIL (đừng gửi người chơi mới vào).

⚠️ Health check **không** dùng Spring Actuator ([ADR-009](../01-architecture/adr/ADR-009-Why-No-Spring-Boot.md)) → tự implement HTTP endpoint nhẹ.

---

## 8. Alerts

**Alert phải hành động được.** Alert không ai làm gì = nhiễu → sau 2 tuần không ai đọc alert nữa.

| Alert | Ngưỡng | Hành động |
|---|---|---|
| 🔴 Tick p99 > ngân sách | > 16.6ms trong 5 phút (@60Hz) — ✅ số thật (ADR-018) | Điều tra ngay |
| 🔴 Server down | `up{job="kage-server-gateway"} == 0` trong 1 phút | Restart / failover |
| 🔴 DB pool cạn | pending > 0 trong 1 phút | Tăng pool / tìm query chậm |
| 🔴 **Gold tổng tăng bất thường** | ⚠️ [NGƯỠNG CHƯA VERIFY] > 20%/giờ | Nghi duplicate — điều tra ngay |
| 🟡 GC pause > 100ms | max — ✅ đã có baseline thật (ADR-019: ZGC max đo được 0.052ms ở tải 80 client; 100ms là ngưỡng an toàn tạm, rộng hơn nhiều so với đã đo) | Xem lại GC config |
| 🟡 Login failed tăng vọt | ⚠️ [NGƯỠNG CHƯA VERIFY] > 10/phút | Nghi brute-force |
| 🟢 Người chơi online thấp bất thường | — | **Không tạo alert** (Kỹ thuật quyết định #11: không hành động được → nhiễu) |

✅ **implemented 2026-07-28** (Phase 9 lát cắt 9) — `deploy/docker/prometheus/alert_rules.yml` +
`deploy/docker/alertmanager/alertmanager.yml` (receiver `"null"` — CHƯA có kênh thông báo thật
Slack/email, chỉ xem UI `:9093`). 2 ngưỡng đánh dấu ⚠️ **[NGƯỠNG CHƯA VERIFY]** vẫn là placeholder —
đo baseline thật trước khi tin, đúng cảnh báo gốc của mục này.

⚠️ **PHÁT HIỆN THẬT lúc viết PromQL** (xác nhận bằng cách dump `registry.scrape()` thật, không đoán):
Micrometer/Prometheus registry đổi tên metric Gauge `kage.gold.total` thành **`kage_gold`**, KHÔNG
PHẢI `kage_gold_total` — hậu tố `.total` bị coi là marker Counter `_total` nên bị strip cho Gauge,
không cộng lại. Viết PromQL/dashboard theo tên dotted + `_total` máy móc sẽ SAI thật (query trả rỗng).

---

## 9. Dashboard

| Dashboard | Cho ai | Nội dung | Trạng thái |
|---|---|---|---|
| **Server Health** | Vận hành | Tick time, players online, entities, ECS system duration, GC pause, JVM memory | ✅ `server-health.json` |
| **Network** | Vận hành | Packet rate, connection active, rate-limit rejected | ✅ `network.json` (chưa có latency — `kage.packet.size` chưa implement) |
| **Database** | Vận hành | Pool active/pending | ✅ `database.json` (chưa có query duration/transaction failed — chưa implement) |
| **Business** | Game designer | Login, tỉ lệ cường hoá, gold tổng, người chơi online | ✅ `business.json` (chưa có level distribution — không phải metric hiện có) |
| **Anti-cheat** | Vận hành | Rate-limit rejected, login failed, gold tăng trưởng | ✅ `anti-cheat.json` |

✅ **implemented 2026-07-28** — cả 5 dashboard ở `deploy/docker/grafana/dashboards/`, tự nạp qua
Grafana provisioning (`deploy/docker/grafana/provisioning/`) lúc `docker compose up`, không cần
import tay. Datasource Prometheus (metrics) + Jaeger (traces) cũng provision tự động.

---

## 10. Lộ trình

| Phase | Observability |
|---|---|
| 0–2 | Logging cơ bản (SLF4J + Logback) |
| 3 | + Metrics cơ bản (tick time, players, DB) |
| **9** | **+ Prometheus + Grafana + Alerts + Tracing** — ✅ implemented 2026-07-28 (lát cắt 9) |
| 5+ | + Business dashboard, anti-cheat dashboard — ✅ implemented cùng lát cắt 9 |

✅ **Phase 9 observability hoàn tất** (Điều 44) — Prometheus scrape+alert, Alertmanager, Grafana
(5 dashboard), OpenTelemetry Tracing thật đều chạy được qua `docker compose up`
(`deploy/docker/docker-compose.yml`).

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
| 7 | Tracing có **sampling** (1%, `traceIdRatioBased`), không trace mọi tick | Overhead | Accepted, implemented 2026-07-28 |
| 8 | `live` vs `ready` phân biệt rõ | DB chết thì restart không giúp | Accepted |
| 9 | Health check tự implement, không Spring Actuator | [ADR-009](../01-architecture/adr/ADR-009-Why-No-Spring-Boot.md) | Accepted |
| 10 | **Ngưỡng alert đa số vẫn placeholder** — 2/6 alert (tick p99, GC pause) có baseline thật, 2 alert còn đánh dấu [NGƯỠNG CHƯA VERIFY] | Ngưỡng bừa = alert fatigue | ⏳ một phần |
| 11 | Alert không hành động được → **không tạo** (bỏ "players online thấp") | Nhiễu làm hỏng cả hệ thống alert | Accepted, implemented 2026-07-28 |
| 12 | OTLP/HTTP (không phải gRPC) cho tracing exporter | Tránh transitive grpc-netty-shaded đụng version netty-all (Điều 23) | Accepted, implemented 2026-07-28 |
| 13 | Jaeger all-in-one thay vì Tempo/Collector riêng | Đơn giản nhất cho dự án cá nhân single-instance (cùng tinh thần ADR-014) | Accepted, implemented 2026-07-28 |

## Tài liệu liên quan

- [SERVER.md §3, §11](../05-server/SERVER.md) — tick time, ngân sách
- [JVM.md](../09-performance/JVM.md) — GC metrics
- [BENCHMARK.md](../09-performance/BENCHMARK.md) — đo lúc dev vs đo lúc chạy
- [SECURITY.md](../08-security/SECURITY.md) — audit log, anti-cheat metric
- [DOCKER.md §9](DOCKER.md) — log ra stdout
- [ROADMAP.md](../11-process/ROADMAP.md) — Phase 9

## Việc cần làm tiếp theo

1. ✅ **ADR-015 đã chốt** (2026-07-26).
2. ✅ Phase 9 lát cắt 1: `kage.tick.duration` — xong 2026-07-26.
3. Phase 9: đo **baseline** trước, đặt ngưỡng alert sau (§8) — CHƯA làm (chỉ tick p99 có baseline
   thật, xem BENCHMARK.md §5.2-5.3; các alert khác ở §8 vẫn ⏳ chưa verify ngưỡng).
4. ✅ Phase 9 lát cắt 8 (2026-07-28, trễ hơn Phase 5/7 dự kiến ban đầu — làm ở Phase 9 vì đây mới là
   lúc quay lại đóng khoảng trống observability): `kage.gold.total`, `kage.tick.overrun.count`,
   `kage.players.online`, `/health/live`, `/health/ready` — pure code, KHÔNG thêm dependency mới.
5. ✅ **Phase 9 lát cắt 9 (2026-07-28) — TOÀN BỘ nhóm "pure code" còn lại đã xong**: `kage.entities.count`,
   `kage.ecs.system.duration`, `kage.packet.received`/`sent`, `kage.connection.active`,
   `kage.ratelimit.rejected`, `kage.login.success`/`failed`, `kage.item.enhance`,
   `kage.db.pool.active`/`pending`, JSON structured log production (§5.1). Chi tiết implementation
   ở bảng §3.1-3.3/3.5 và §5.1 phía trên.
6. ✅ **Phase 9 lát cắt 10 (2026-07-28, chủ dự án bật đèn xanh) — Observability lớn xong**:
   OpenTelemetry SDK thật (§6), Prometheus + Alertmanager + Grafana (§8-9) chạy qua
   `docker compose up` (`deploy/docker/docker-compose.yml`). Chi tiết:
   - `libs.versions.toml`: `opentelemetry-bom` 1.63.0 (verify thật lúc viết, không copy số cũ).
   - `GatewayTracing`/`GatewayObservability` (server-gateway) — span `gateway.command` (mọi opcode)
     + span sâu `feature.inventory.enhance` (1 luồng đại diện, `EnhanceItemCommand`).
   - JVM metric binder (`JvmGcMetrics`/`JvmMemoryMetrics`/`JvmThreadMetrics`) bind vào
     `meterRegistry` trong `GatewayBootstrap` — closes gap `jvm.gc.pause` §3.4 (không cần dependency
     mới, binder có sẵn trong `micrometer-core`, chỉ chưa ai gọi `bindTo()` trước lát cắt này).
   - `deploy/docker/prometheus/{prometheus.yml,alert_rules.yml}`,
     `deploy/docker/alertmanager/alertmanager.yml`,
     `deploy/docker/grafana/{provisioning,dashboards}/` — tất cả real, verify bằng
     `docker compose config` + `docker compose up` thật (không chỉ viết YAML suông).
   - ⚠️ Phát hiện thật: `kage.gold.total` scrape ra tên `kage_gold` (không phải `kage_gold_total`) —
     xem §8 phía trên, xác nhận bằng dump `registry.scrape()` thật trước khi viết PromQL.
   - CHƯA làm ở lát cắt này (không phải thiếu sót, chỉ ngoài phạm vi): retrofit tracing sâu cho
     phần opcode còn lại (xem mục 8 — đã mở rộng thêm 3 lệnh); nhánh sampling "100% Command chậm";
     kênh thông báo Alertmanager thật (Slack/email).
7. ✅ **`kage.packet.size` (2026-08-02, lát cắt riêng) + `kage.db.query.duration`/
   `kage.db.transaction.failed` (2026-08-02) — cả 3 metric còn thiếu của mục này đều đã xong**:
   - `kage.db.query.duration`: `JdbcOperations` đo quanh 5 điểm thực thi JDBC thật (không đếm
     trùng — mọi overload `DataSource`/`*OrThrow` delegate qua 1 trong 5 điểm), cấu hình
     `DbMetricsPort` (NO_OP mặc định) 1 LẦN lúc khởi động qua `ServerDatabaseBootstrap` — thread
     port qua tham số của ~35 `Postgres*Adapter` (131 call site) ngoài phạm vi, xem §3.3 phía trên.
   - `kage.db.transaction.failed`: `JdbcTransactionRunner.rollbackQuietly` tăng Counter đúng lúc
     transaction rollback do exception.
   - Xác nhận bằng scrape HTTP thật (`MetricsHttpServerTest.shouldExposeDbMetrics_afterRecordingViaPort`,
     không đoán tên): `kage_db_query_duration_seconds` (tag `operation`) và
     `kage_db_transaction_failed_total` — Timer KHÔNG bị strip hậu tố `.total` như Gauge
     `kage.gold.total` → `kage_gold` (mục 6 phía trên), chỉ Gauge mới bị vậy.
8. ✅ **Retrofit tracing sâu cho 3 lệnh chạm gold/item thật (2026-08-02)** — mở rộng từ 1 luồng đại
   diện (`feature.inventory.enhance`) sang `feature.shop.buy`/`feature.shop.sell` (`BuyItemCommand`/
   `SellItemCommand`, chạm gold) + `feature.trade.confirm` (`ConfirmTradeCommand`, nơi duy nhất
   `ConfirmTradeUseCase` xác thực lại lúc thi hành rồi chạm gold/item thật). Chọn có chủ đích, KHÔNG
   sweep máy móc hết opcode còn lại (CLAUDE.md §"Cấm kỵ" chống scope creep) — combat/skill-cast
   (`UseSkillCommand`) cân nhắc rồi LOẠI vì chỉ enqueue fire-and-forget vào `server-world` (không
   chạm DB, không trả kết quả đồng bộ cho gateway — pattern "chụp Context trước khi rời EventLoop"
   không áp dụng tự nhiên). Guild không có nghiệp vụ treasury/gold nên không có gì để trace. Verify
   bằng test mới `GatewayServerIntegrationTest#clientCommands_shouldEmitDeepChildSpans_forGoldAffectingCommands`
   (thêm `GatewayTracing.forTesting(SpanExporter)` — không dependency mới) gửi cả 3 lệnh qua socket
   thật, xác nhận span đúng tên + attribute `result`. Còn lại ~53/57 opcode chưa có span sâu.
