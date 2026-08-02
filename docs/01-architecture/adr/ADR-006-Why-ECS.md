# ADR-006: Dùng ECS cho gameplay, khởi đầu bằng Ashley

| | |
|---|---|
| Status | Accepted **có điều kiện** — xem Revisit Trigger |
| Date | 2026-07-16 |
| Deciders | Chủ dự án |
| Related | [Hiến pháp Điều 14, 25](../../00-governance/KAGE-CONSTITUTION.md), [ECS.md](../../03-engine/ECS.md), [ADR-002](ADR-002-Why-libGDX.md) |

---

## Context

Quyết định này gồm **hai câu hỏi tách biệt**, thường bị gộp làm một:

1. **Có nên dùng ECS cho gameplay không?**
2. **Nếu có, dùng ECS framework nào?**

Bối cảnh: MMORPG cần update hàng nghìn entity 30–60 lần/giây. Mô hình OOP truyền thống (`class Monster extends Entity`) dẫn tới cây kế thừa sâu, cache miss nhiều, và bài toán kinh điển "quái này vừa bay vừa bơi vừa bắn thì kế thừa từ đâu?".

## Decision

1. **Gameplay xây trên ECS** (Điều 14, 25).
2. **Khởi đầu bằng Ashley ECS**, nhưng bọc kín sau module `engine-ecs`.
3. **Ràng buộc bắt buộc**: `client-core`, `server-world`, và mọi gameplay code **không được import `com.badlogic.ashley.*` trực tiếp**.

Điểm 3 là thứ khiến quyết định này **có thể đảo ngược** mà không phải viết lại gameplay.

## Alternatives Considered

### Câu hỏi 1 — ECS hay OOP?

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **ECS** | Composition thay kế thừa (Điều 13); data-oriented → cache friendly (Điều 14); thêm hành vi mới không sửa class cũ; batch update tự nhiên | Đường cong học tập; khó debug hơn (state phân tán qua Component); over-engineering cho game nhỏ | **Chọn** — game này không nhỏ |
| OOP kế thừa | Trực quan, dễ bắt đầu | Cây kế thừa sâu không tránh được ở MMORPG; cache locality kém; vi phạm Điều 13 | Không chọn |

### Câu hỏi 2 — Framework nào?

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **Ashley** | Tích hợp sẵn hệ sinh thái libGDX; API đơn giản, dễ cho AI/người mới; đủ nhanh cho MVP | ⚠️ **Bảo trì chậm**; không archetype-based → cache locality kém hơn ECS hiện đại; `Family` scan có chi phí | **Chọn tạm** — tốc độ ra vertical slice quan trọng hơn tối ưu cực hạn lúc này |
| Artemis-odb | Archetype-based, hiệu năng tốt hơn Ashley | Cộng đồng nhỏ; tích hợp libGDX kém trực tiếp hơn | Không chọn ở MVP — ứng viên hàng đầu nếu thay Ashley |
| Dominion / Fleks / ECS Java khác | Một số nhanh hơn đáng kể | Trẻ, rủi ro bảo trì; ít người biết | Không chọn |
| **Tự viết "Kage ECS"** | Tối ưu đúng nhu cầu; kiểm soát tuyệt đối; phù hợp Điều 4 ("xây Game Engine") | Chi phí lớn; rủi ro cao khi **chưa validate gameplay loop**; vi phạm Điều 9 và Điều 10 (engine phục vụ gameplay, không ngược lại) | **Không chọn BÂY GIỜ** — nhưng là đích đến hợp lý sau Phase 9 |

## Consequences

**Tích cực:**
- Có ECS chạy được ngay, không tốn tháng viết framework.
- Nhờ ràng buộc "không import Ashley trực tiếp", thay ECS về sau chỉ đụng `engine-ecs`.

**Đánh đổi phải chấp nhận:**
- **`engine-ecs` là lớp gián tiếp có chi phí thật** — cả về hiệu năng (một tầng gọi) lẫn công sức (phải thiết kế API riêng). Chấp nhận vì đây chính là thứ mua lại quyền thay ECS sau này.
- ⚠️ **Rủi ro lớn nhất**: lớp bọc bị "rò rỉ" — ai đó import Ashley trực tiếp "cho nhanh", và sau 1 năm quyết định này không còn đảo ngược được. → **Phải enforce bằng build tooling** (ADR-011), không bằng nhắc nhở.
- Ashley có thể là bottleneck ở quy mô lớn → chấp nhận và đo ở Phase 9.

**Module bị ảnh hưởng:** `engine-ecs` (bọc), `client-core` + `server-world` (dùng qua wrapper).

## Revisit Trigger

**Bắt buộc đánh giá lại vào cuối [Phase 9 — Optimization](../../11-process/ROADMAP.md)**, hoặc sớm hơn nếu:

- Benchmark cho thấy ECS update > **4ms/tick** với số entity mục tiêu của 1 map đông người (tick 60Hz = ngân sách 16.6ms/tick, ECS không được ăn quá ~25% — xem [ADR-018](ADR-018-Tick-Rate.md)).
- Ashley không có commit/release trong 24 tháng.
- Cần tính năng ECS mà Ashley không hỗ trợ và không thể workaround.

Nếu thay → tạo ADR mới (`Supersedes ADR-006`), ứng viên: Artemis-odb hoặc Kage ECS tự viết. **Không sửa ADR này.**

## Ghi chú — đánh giá lại Phase 9 (2026-07-26)

Revisit Trigger yêu cầu "Benchmark cho thấy ECS update > 4ms/tick với số entity mục tiêu của 1 map
đông người" — đã đo bằng JMH thật (`engine-ecs/src/jmh`, `EcsWorldUpdateBenchmark`, proxy
`MovementSystem` — chỉ đo cơ chế lặp/`ComponentMapper` cốt lõi mà ADR này lo ngại, KHÔNG bao gồm
AI/Combat/Skill/Boss/StatusEffect System thật vì các System đó ở `server-world`, phụ thuộc ngược
vào `engine-ecs`, không benchmark được từ trong module này):

| Entity | `EcsWorld.update()` (avg) | So ngân sách 4ms |
|---|---|---|
| 100 (≈ soak test 2026-07-26, ~82 nhân vật thật) | 1.271 µs | 0.03% |
| 500 | 4.842 µs | 0.12% |
| 1000 | 18.009 µs | 0.45% |
| 5000 | 146.560 µs | 3.66% |

**Kết luận: Revisit Trigger CHƯA kích hoạt** — kể cả ở 5000 entity (gấp ~60 lần quy mô 1 map thật
hiện tại), cơ chế Ashley chỉ ăn 3.66% ngân sách ECS. Ước tính thô cần **hàng chục nghìn entity**
mới chạm 4ms — vượt xa quy mô 1 map MVP hiện có.

⚠️ **Xu hướng đáng ghi lại** (không phải lý do đổi ECS bây giờ): chi phí/entity KHÔNG tuyến tính —
tăng ~3x khi entity tăng 10 lần (500→5000: 0.0097µs/entity → 0.0293µs/entity) — nhất quán với lo
ngại đã ghi ở Alternatives (Ashley không archetype-based). Đây là tín hiệu **theo dõi cho tương
lai** nếu game mở rộng sang bản đồ hàng nghìn entity đồng thời (chưa có kế hoạch), không phải lý do
hành động bây giờ (Điều 9).

**Quyết định giữ nguyên: tiếp tục dùng Ashley.** Không tạo ADR `Supersedes` vì không đổi quyết định.
Số liệu đầy đủ: [ECS.md §9](../../03-engine/ECS.md), `benchmarks/results/engine-ecs-jmh-result.json`.

## Ghi chú — đánh giá lại bằng production metric thật (2026-08-02)

Ghi chú Phase 9 ở trên (2026-07-26) đo bằng **JMH micro-benchmark**, chỉ mô phỏng cơ chế lặp/
`ComponentMapper` qua 1 proxy System, KHÔNG bao gồm AI/Combat/Skill/Boss/StatusEffect System thật.
Phase 9 lát cắt 9 (2026-07-28) sau đó thêm metric production thật `kage.ecs.system.duration`
(Timer, tag `system`, [OBSERVABILITY.md §3.1](../../07-infrastructure/OBSERVABILITY.md)) đo TỪNG
System thật (`AiSystem`/`MovementSystem`/`CombatSystem`/`SkillSystem`/`BossAiSystem`/
`StatusEffectSystem`) qua `EcsWorld.update(float, SystemTimingListener)` — nhưng **chưa ai từng cộng
6 System này lại thành 1 con số "ECS update/tick" thật rồi so với ngưỡng 4ms của Revisit Trigger**,
kể cả sau khi Phase 9 đóng (2026-07-30). Đo lần này lấp khoảng trống đó.

**Điều kiện đo** (BENCHMARK.md §5 method — gateway thật + `tools:load-test` thật, không JMH):
máy Intel Core i9-14900HX (24 lõi thật/32 luồng logic, cùng máy mọi lần đo BENCHMARK.md §5), 32GB
RAM, Windows, JDK GraalVM 21.0.8 (`JAVA_HOME` build/run — Gradle toolchain build vẫn nhắm Java 25 qua
`libs.versions.toml`, không đổi gì ở đây). `server-gateway` thật chạy qua `./gradlew run` (KHÔNG
`-Xmx`, TLS bật thật với dev-cert ADR-027, tracing OTLP bật mặc định dù Jaeger không chạy — cùng cấu
hình dev mặc định như mọi lần đo BENCHMARK.md §5.4 trở đi), Postgres 17 + Redis 8 thật (container
standalone cổng khác 5432/6379 mặc định vì máy đang có service khác chiếm 2 cổng đó — không ảnh
hưởng số đo, chỉ là chi tiết môi trường). `tools:load-test` **N=500** người chơi thật (kịch bản xấu
nhất mặc định — tụ 1 điểm luyện tập, move+auto-attack+chat+ping, BENCHMARK.md §5.1), ramp 50→500
(bước 50 mỗi 5s, ~45s ramp), giữ tải ổn định ~2 phút sau ramp — **không phải soak 2 giờ đầy đủ**, đây
là peak/short run đúng tinh thần "N vừa phải, đủ để có số thật" mà kế hoạch đo yêu cầu, không phải
soak test dài. **0 lỗi client suốt cả lần chạy** (log `tools:load-test`), `kage_players_online` đỉnh
đo được = 500 (khớp N mục tiêu — không như 1 số lần đo N=2000 trước đây bị burst-connect làm hụt N
thật).

**Cách đo**: 2 lần scrape `/metrics` thật (không phải 1 lần) cách nhau ~85s, CẢ HAI đều lúc
`kage_players_online=500` (giữa cửa sổ giữ tải ổn định, tránh lẫn giai đoạn ramp/rỗng lúc đầu/cuối) —
lấy **hiệu (delta)** của `_sum`/`_count` giữa 2 lần scrape để có "trung bình đúng trong cửa sổ tải
ổn định", không dùng số cộng dồn từ lúc server khởi động (sẽ bị pha loãng bởi thời gian server chạy
không tải trước khi load-test bắt đầu). `kage.ecs.system.duration` **KHÔNG có percentile** (chỉ
`publishPercentiles` cho `kage.tick.duration`, xem `MicrometerTickMetricsPort` — mỗi Timer System chỉ
có `_count`/`_sum`/`_max`) — không có p95/p99 cho từng System, chỉ có mean (từ `_sum/_count`) và max.

| System | Δsum (85s, 42.810 tick) | Mean/tick |
|---|---|---|
| AiSystem | 0.394126s | 0.00921 ms |
| BossAiSystem | 0.015393s | 0.00036 ms |
| CombatSystem | 0.307498s | 0.00718 ms |
| MovementSystem | 0.426417s | 0.00996 ms |
| SkillSystem | 0.184637s | 0.00431 ms |
| StatusEffectSystem | 0.093353s | 0.00218 ms |
| **Tổng ECS update/tick** | **1.421424s** | **0.03320 ms** |

**So Revisit Trigger (4ms/tick)**: 0.0332ms / 4ms = **0.83%** ngân sách trigger. So ngân sách tick
đầy đủ (16.6ms @60Hz, ADR-018): 0.0332ms / 16.6ms = **0.20%**. Đối chiếu chéo: tổng thời gian 1 tick
đầy đủ đo cùng cửa sổ (delta `kage.tick.duration`, 42.818 tick) = 0.15942 ms/tick trung bình — ECS
chiếm ~20.8% thời gian tick đó, nhưng bản thân tick đã cực nhỏ so với ngân sách 16.6ms nên tỉ lệ %
này không đáng lo (khác hẳn ý nghĩa nếu tick đã gần chạm ngân sách). Tick p99 do Micrometer tự tính
(rolling, không phải delta) trong cửa sổ giữ tải 500 client: 1.7–2.36ms — khớp `tools:load-test` tự
báo cáo `serverTickMs` p99 cùng khoảng (7-12ms packet latency phía client, không phải tick server).

**Kết luận: Revisit Trigger CHƯA kích hoạt** — số đo production thật (không phải JMH mô phỏng) ở
N=500 người chơi thật xác nhận lại đúng kết luận JMH 2026-07-26: ECS update ăn chưa tới 1% ngân sách
4ms, kể cả đã tính đủ AI/Combat/Skill/Boss/StatusEffect System thật (JMH trước đây KHÔNG đo được các
System này). **ADR-006 giữ nguyên — không cần chủ dự án re-litigate**, đúng phạm vi câu hỏi factual
mà Revisit Trigger đặt ra.

⚠️ **Outlier thật, ghi lại trung thực (không giấu)**: `kage_ecs_system_duration_seconds_max{system="AiSystem"}`
đo được **21.281ms** (cộng dồn từ lúc server khởi động, không phải trong cửa sổ delta ở trên) — tự nó
đã VƯỢT NGUYÊN NGÂN SÁCH TICK 16.6ms. Đây là 1 mẫu đơn lẻ (không lặp lại — `_count` 187.569 mẫu,
p99-tương-đương-ước-lượng từ mean 0.0332ms cho thấy đây là ngoại lệ cực hiếm), nhiều khả năng xảy ra
lúc JIT chưa warm-up hoặc GC pause hoặc burst-connect (ramp 50→500 dồn dập tạo AI System phải xử lý
nhiều entity mới cùng lúc) — **CHƯA điều tra nguyên nhân chính xác** (ngoài phạm vi câu hỏi factual
của Revisit Trigger, đúng Điều 9). Không phải lý do kích hoạt Trigger (Trigger đòi hỏi mức ổn định,
không phải 1 outlier), nhưng đáng theo dõi nếu lặp lại thường xuyên hơn ở N lớn hơn.

**Việc CHƯA làm** (Điều 30): chưa đo N=1000/2000 bằng metric production này (chỉ N=500 lần này); chưa
soak dài (chỉ ~2 phút giữ tải, không phải 2 giờ); chưa điều tra outlier AiSystem 21ms. Không chặn kết
luận Trigger (N=500 đã đủ để trả lời câu hỏi factual "> 4ms/tick?" — CHƯA, còn cách xa), nhưng nếu
sau này muốn con số ở N lớn hơn/soak dài hơn, đây là điểm bắt đầu tiếp theo.

Số liệu đầy đủ: [BENCHMARK.md §5.10](../../09-performance/BENCHMARK.md),
[ECS.md §9](../../03-engine/ECS.md).
