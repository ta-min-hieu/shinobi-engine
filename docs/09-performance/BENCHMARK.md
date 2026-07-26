# BENCHMARK.md

**Đo hiệu năng**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft |
| Luật gốc | [Hiến pháp Điều 30](../00-governance/KAGE-CONSTITUTION.md#điều-30-benchmark-trước-tối-ưu-sau) |

> **Benchmark trước. Refactor sau. Mọi tối ưu phải có số liệu.**
> **Không chấp nhận "tôi nghĩ nó nhanh hơn".**

---

## 1. Ba loại đo — đừng nhầm lẫn

| | Đo gì | Công cụ | Khi nào |
|---|---|---|---|
| **Micro-benchmark** | Một method, vài µs | **JMH** | Khi tối ưu một hàm cụ thể |
| **Macro-benchmark** | Cả hệ thống dưới tải | Load test tự viết | Phase 9 |
| **Production monitoring** | Hệ thống thật, người thật | Prometheus | Phase 9+ |

⚠️ **Micro-benchmark nhanh không có nghĩa hệ thống nhanh.** Tối ưu một method từ 100ns xuống 50ns trong khi tick tốn 30ms = **vô nghĩa**.

→ **Luôn đo macro trước để biết bottleneck ở đâu**, rồi mới micro-benchmark chỗ đó.

---

## 2. Vì sao phải dùng JMH (không tự đo)

```java
// ❌ SAI HOÀN TOÀN — cách này cho số liệu vô nghĩa
long start = System.currentTimeMillis();
for (int i = 0; i < 1_000_000; i++) {
    result = calculateDamage(attacker, target);
}
long elapsed = System.currentTimeMillis() - start;
```

**Vì sao sai:**

| Vấn đề | Hậu quả |
|---|---|
| **JIT chưa warm-up** | 1000 vòng đầu chạy interpreter → chậm giả |
| **Dead code elimination** | `result` không dùng → JIT **xoá luôn vòng lặp** → đo 0ns |
| **Constant folding** | Input cố định → JIT tính sẵn kết quả |
| **On-stack replacement** | Vòng lặp bị tối ưu khác với code thật |
| **GC xen giữa** | Số liệu nhiễu |
| Không có warmup/iteration | Không có độ tin cậy thống kê |

→ **JMH giải quyết tất cả những thứ trên.** Tự đo bằng `currentTimeMillis` gần như luôn cho **số liệu sai**, và tệ hơn là **sai theo hướng khiến ta tin nhầm**.

---

## 3. JMH — mẫu

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(value = 2)                 // 2 JVM riêng — tránh nhiễu giữa lần chạy
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
public class CombatBenchmark {

    private Attacker attacker;
    private Target target;

    @Setup
    public void setup() {
        attacker = TestData.attacker();
        target = TestData.target();
    }

    @Benchmark
    public int calculateDamage() {
        return CombatFormula.calculate(attacker, target);  // ✅ RETURN — chống dead code elimination
    }

    @Benchmark
    public void calculateDamageBlackhole(Blackhole bh) {
        bh.consume(CombatFormula.calculate(attacker, target));  // ✅ hoặc dùng Blackhole
    }
}
```

⚠️ **Luôn `return` hoặc `Blackhole.consume()`** — nếu không, JIT xoá code và ta đo con số vô nghĩa.

---

## 4. Benchmark cái gì

| Hạng mục | Vì sao | Ưu tiên |
|---|---|---|
| **ECS update** | Hot path chính; kích hoạt Revisit [ADR-006](../01-architecture/adr/ADR-006-Why-ECS.md) | 🔴 Cao nhất |
| **Packet encode/decode** | Chạy mỗi packet | 🔴 Cao |
| **Công thức combat** | Chạy mỗi đòn đánh | 🟡 Vừa |
| Collision check | Mỗi tick | 🟡 Vừa |
| Pathfinding | Nếu có A* | 🟡 Vừa |
| Script call | Overhead script engine | 🟡 Vừa |

**Không benchmark**: code khởi tạo · tool · thứ chạy 1 lần/giờ · getter/setter ([PERFORMANCE.md §10](PERFORMANCE.md)).

---

## 5. Load Test (macro)

Từ [Phase 9 — Optimization](../11-process/ROADMAP.md).

| Yêu cầu | Chi tiết |
|---|---|
| **Giả lập hành vi thật** | Move + combat + chat + inventory theo pattern người chơi thật |
| ❌ **Không** chỉ gửi ping trống | Cho số liệu đẹp và vô dụng |
| Tăng dần tải | 10 → 50 → 100 → 500... tìm điểm gãy |
| Chạy đủ lâu | ⚠️ **≥ 2 giờ** — leak và GC drift chỉ lộ theo thời gian |

✅ **Công cụ: `tools:load-test`** (Phase 9 lát cắt 2, 2026-07-26) — client Java tự viết, nói qua ĐÚNG
socket TCP + protobuf thật (không HTTP, không tool ngoài như k6/Gatling/JMeter — protocol game là
binary custom, tool HTTP chuẩn không dùng được mà không viết plugin riêng, tốn công hơn tự viết
client tái dùng thẳng `shared-protocol`, Điều 9). Ramp dần `initialClients → maxClients`, mỗi client
giả lập move+combat(auto-target)+chat+ping thật, đo packet latency p50/p95/p99 phía client SONG SONG
với `kage.tick.duration` phía server thật (scrape `/metrics`, Phase 9 lát cắt 1). Chạy:
`./gradlew :tools:load-test:run --args="--maxClients=200 --durationSeconds=7200"` nhắm vào server
đang chạy sẵn (không tự khởi động `GatewayBootstrap` — đo server production-like).

**Đo gì:**

| Metric | Ngưỡng | Đo bằng |
|---|---|---|
| **Tick time p99** | ≤ ngân sách ([SERVER.md §11](../05-server/SERVER.md)) | `tools:load-test` (scrape `/metrics`) |
| Packet latency p50/p95/p99 | — | `tools:load-test` (client-side) |
| GC pause p99 | [JVM.md §7](JVM.md) | JFR (bật sẵn từ Phase 0) — ngoài phạm vi `tools:load-test` |
| **Heap sau major GC** | ⚠️ Tăng đều = leak | JFR/GC log — ngoài phạm vi `tools:load-test` |
| DB pool pending | Phải = 0 | ⏳ chưa có metric (HikariCP — lát cắt sau) |
| CPU / RAM | — | OS-level, ngoài phạm vi `tools:load-test` |

⚠️ **Soak test (chạy dài) quan trọng hơn peak test.** Server chịu 500 người trong 5 phút nhưng leak sau 6 giờ = vẫn sập ở production.

✅ **Soak test thật đã chạy** (2026-07-26, Phase 9 lát cắt 3) — 80 client 2h14, `-XX:+UseZGC`: tick
p99 luôn dưới 16.6ms (dao động 2-10ms), heap Live phẳng 39M suốt 34 chu kỳ GC (~1h33, không leak),
GC STW pause tối đa 0.052ms. Chi tiết + 2 bug phát hiện trong `tools:load-test`:
[ADR-019](../01-architecture/adr/ADR-019-GC-Choice.md), [ROADMAP.md Phase 9 lát cắt 3](../11-process/ROADMAP.md).

### 5.1 Ước tính hạ tầng (Capacity Planning)

⏳ **Sơ bộ** (2026-07-26, Phase 9 lát cắt 5) — dựa trên 2 lần chạy `tools:load-test` nhắm vào
`server-gateway` thật (Postgres/Redis thật qua Docker), kịch bản **xấu nhất**: mọi người chơi giả
lập tụ 1 điểm, cùng đánh 1 quái bù nhìn (chỉ có nội dung này tồn tại trong game lúc đo — chưa có
skill/inventory/shop/quest/dungeon trong kịch bản tải). Hành vi mỗi client: di chuyển + auto-attack
(70%/chu kỳ, đánh thường 1 giây/đòn — `CombatConstants.ATTACK_INTERVAL_SECONDS`) + chat (3%) + ping
(30%), chu kỳ ~0.6-1.5s.

**Máy đo**: Intel Core i9-14900HX (24 lõi thật / 32 luồng logic — số "CPU" trong công thức bên dưới
là luồng logic, khớp `CPUs: 32 total` trong gc.log), 32GB RAM, Windows.

**Công thức** (N = số người chơi đồng thời):

```
RAM (MB)        = 368 + 0.78 × N        — ✅ đáng tin (2 điểm đo ổn định lâu: N=100→446MB, N=500→759.6MB)
CPU (lõi)        = 0.02 + 0.00275 × N    — ✅ đáng tin (cùng 2 điểm đo)
Băng thông nhận  = 0.097 × N^1.483 KB/s  — ⚠️ trung bình (không tuyến tính — AoI broadcast; khớp 2 điểm đo, điểm giữa nhiễu)
Băng thông gửi   = 0.35 × N KB/s         — ✅ khá (gần tuyến tính, ít bị AoI ảnh hưởng)
```

| N | RAM | CPU | Băng thông (ra/vào) |
|---|---|---|---|
| 100 | 446MB ✅ đo thật | 0.29 lõi ✅ đo thật | 90 / 35 KB/s ✅ đo thật |
| 500 | 759.6MB ✅ đo thật | 1.39 lõi ✅ đo thật | 980 / 175 KB/s ✅ đo thật |
| 1000 | ~1.15GB ⚠️ ngoại suy | ~2.77 ⚠️ ngoại suy | ~2.8MB/s / ~350KB/s ⚠️ ngoại suy |

⚠️ **Điều kiện áp dụng** — RAM/CPU dùng an toàn cho MỌI kịch bản (tụ đông hay rải đều, vì tính theo
đầu người chơi); **băng thông chỉ đúng cho kịch bản xấu nhất** (world boss/sự kiện dồn 1 chỗ) — chơi
rải nhiều map sẽ thấp hơn nhiều vì AoI chỉ broadcast trong bán kính gần. Ngoài khoảng 100-500 (đã đo
thật) là ngoại suy — **phải load test thật ở đúng quy mô** trước khi chốt hạ tầng cho N lớn hơn
nhiều (Điều 30, không đoán). Disk I/O chưa đo được đáng tin (Windows perf counter method có vấn đề
— cần polling liên tục cùng 1 session, không phải gọi rời rạc); dự kiến rất thấp về mặt kiến trúc
(state ở RAM/ECS, ghi DB qua network không phải đĩa cục bộ).

⚠️ **KHÔNG gọi số ngoại suy là "an toàn"** (2026-07-26, phản hồi thật khi chủ dự án hỏi "5000 người
có an toàn không?"): công thức CPU fit từ 2 điểm (100, 500) có thể ĐÁNH GIÁ THẤP chi phí thật ở N
lớn — cùng cơ chế AoI broadcast làm băng thông không tuyến tính (§ trên) rất có thể cũng làm CPU
không tuyến tính (mã hoá protobuf + ghi Netty cho mỗi gói broadcast cũng tốn CPU, không chỉ băng
thông), chỉ là 2 điểm đo chưa đủ đông để hiện tượng đó lộ ra ở CPU như đã lộ ở băng thông. Thêm
nữa: DB connection pool (10 connection) và hành vi throttle nhiệt của máy laptop chạy tải cao liên
tục nhiều giờ đều CHƯA được đo ở quy mô lớn. Ngoại suy 10x vùng đã test (500→5000) chỉ nên dùng để
**lên kế hoạch sơ bộ**, không phải căn cứ để tuyên bố "an toàn".

---

## 6. Khi nào chạy

| Loại | Ở PR? | Khi nào |
|---|---|---|
| JMH | ❌ **Không** | Nightly, hoặc thủ công khi tối ưu |
| Load test | ❌ Không | Trước mốc Roadmap quan trọng |
| Soak test | ❌ Không | Phase 9 và trước mỗi vòng Beta/Production |

⚠️ **Vì sao JMH không chạy ở PR:**

1. Chậm (mỗi benchmark vài phút × nhiều benchmark).
2. ⚠️ **Runner CI dùng chung → số liệu không đáng tin.** Máy ảo chia sẻ CPU với job khác → dao động 30–50% giữa các lần chạy. Fail/pass ngẫu nhiên → đội ngũ tắt gate.

→ Chạy JMH trên **máy chuyên dụng, cấu hình ổn định**, hoặc chấp nhận số liệu chỉ dùng để **so sánh tương đối** trong cùng một lần chạy.

---

## 7. Ghi lại kết quả

⚠️ **Xem số 1 lần rồi quên = không phát hiện regression.**

```
benchmarks/results/
├── 2026-07-16-ecs-update.json
├── 2026-08-01-ecs-update.json
└── baseline.json
```

Mỗi kết quả **bắt buộc** ghi:

| Trường | Vì sao |
|---|---|
| Ngày | So sánh theo thời gian |
| Commit SHA | Truy vết thay đổi nào gây regression |
| **CPU / RAM / OS** | Số liệu không so sánh được giữa máy khác nhau |
| **JVM version + flags** | GC khác = số khác hoàn toàn |
| Kết quả (avg, p99, error) | — |

⚠️ **Báo cáo số mà không nói điều kiện đo = số vô dụng.** "ECS update 3ms" nghĩa là gì nếu không biết bao nhiêu entity, máy nào, GC nào?

---

## 8. Báo cáo trung thực

[AI-WORKING-RULES.md §11, §14](../00-governance/AI-WORKING-RULES.md).

| ❌ Cấm tuyệt đối | ✅ Đúng |
|---|---|
| **Bịa số liệu** | "Chưa đo" |
| Ước lượng rồi trình bày như số thật | "Ước lượng, chưa verify" |
| Báo số không nói điều kiện | Kèm máy/JVM/flags/dữ liệu |
| "Nhanh hơn 2 lần" (không có baseline) | "3.2ms → 1.6ms, cùng máy, cùng JVM, n=10" |
| Chọn lần chạy đẹp nhất | Báo cáo trung vị + độ lệch |

⚠️ **Bịa số benchmark là vi phạm nghiêm trọng nhất** mà AI có thể mắc ở dự án này. Một quyết định kiến trúc dựa trên số liệu bịa sẽ sai suốt nhiều năm.

---

## 9. Diễn giải kết quả

| Bẫy | Thực tế |
|---|---|
| "Cải thiện 5%" | ⚠️ Có thể là **nhiễu**. Cần độ lệch chuẩn; 5% thường không có ý nghĩa thống kê |
| "Nhanh trên máy tôi" | Máy dev ≠ server production |
| "Micro-benchmark nhanh 2×" | Hệ thống có nhanh hơn không? Thường **không** |
| "Trung bình tốt" | ⚠️ p99 mới quan trọng ([PERFORMANCE.md §1](PERFORMANCE.md)) |
| Benchmark trên dữ liệu nhỏ | Cache-friendly giả tạo; dữ liệu thật lớn hơn |

**Quy tắc**: cải thiện < 10% và không vượt độ lệch chuẩn → **coi như không cải thiện** → revert nếu code khó đọc hơn.

---

## 10. Quy trình

```
1. Macro: đo hệ thống → tìm bottleneck THẬT
2. Micro: JMH chỗ bottleneck → có baseline
3. Tối ưu
4. JMH lại → cải thiện có vượt nhiễu không?
5. Macro lại → HỆ THỐNG có nhanh hơn không?
6. Không → REVERT
7. Ghi kết quả vào benchmarks/results/
```

⚠️ **Bước 5 hay bị bỏ qua**: method nhanh 2× nhưng tick không đổi = tối ưu nhầm chỗ.

---

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | **JMH bắt buộc** cho micro-benchmark; cấm tự đo bằng `currentTimeMillis` | Accepted |
| 2 | **Macro trước, micro sau** | Accepted |
| 3 | **JMH không chạy ở PR** — runner CI dùng chung không đáng tin | Accepted |
| 4 | Load test **giả lập hành vi thật**, không ping trống | Accepted |
| 5 | **Soak test ≥ 2 giờ** — quan trọng hơn peak test | Accepted |
| 6 | Kết quả **bắt buộc** ghi: commit, máy, JVM, flags | Accepted |
| 7 | **Cải thiện < 10% và trong nhiễu = không cải thiện** | Accepted |
| 8 | ⚠️ **Bịa số liệu = vi phạm nghiêm trọng nhất** | Accepted |
| 9 | Tối ưu không cải thiện hệ thống → **revert** | Accepted |

## ADR cần tạo

Không có — đây là quy trình, không phải quyết định kiến trúc.

## Tài liệu liên quan

- [PERFORMANCE.md](PERFORMANCE.md) — tối ưu gì
- [JVM.md](JVM.md) — JFR, profiler
- [OBSERVABILITY.md](../07-infrastructure/OBSERVABILITY.md) — đo ở production
- [TESTING.md](../10-quality/TESTING.md) — load test
- [CI-CD.md §3](../07-infrastructure/CI-CD.md) — vì sao không gate benchmark
- [AI-WORKING-RULES.md §11](../00-governance/AI-WORKING-RULES.md) — quy tắc AI benchmark

## Việc cần làm tiếp theo

1. ✅ Phase 9 (2026-07-26, trễ hơn dự kiến — không phải Phase 1): dựng JMH harness cho `engine-ecs`
   (`me.champeau.jmh`, `EcsWorldUpdateBenchmark`).
2. ✅ Phase 9: load test + soak test theo §5 → [ADR-019](../01-architecture/adr/ADR-019-GC-Choice.md)
   (GC, Proposed) + [ADR-006](../01-architecture/adr/ADR-006-Why-ECS.md) (Ashley, Revisit Trigger
   đánh giá lại — CHƯA kích hoạt, giữ nguyên Ashley).
3. ✅ Kết quả đầu tiên đã ghi vào `benchmarks/results/engine-ecs-jmh-result.json`.
