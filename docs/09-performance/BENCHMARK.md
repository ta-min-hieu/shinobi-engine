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

⚠️ **Rà công cụ đo 2026-10-03 — đọc mục "⛔ Rà công cụ đo 2026-10-03" cuối §5.1 trước khi dùng BẤT KỲ số "trễ gói"/"packetLatency" nào trong tài liệu này.** Tóm tắt: mọi số trễ gói
từ 2026-07-26 đều lệch THẤP (công cụ đo trúng phản hồi cũ); và từ 2026-08-09 công cụ KHÔNG còn tạo
combat (câu "move+combat(auto-target)" ở trên không còn đúng).

**Đo gì:**

| Metric | Ngưỡng | Đo bằng |
|---|---|---|
| **Tick time p99** | ≤ ngân sách ([SERVER.md §11](../05-server/SERVER.md)) | `tools:load-test` (scrape `/metrics`) |
| Packet latency p50/p95/p99 | — | `tools:load-test` (client-side) |
| GC pause p99 | [JVM.md §7](JVM.md) | JFR (bật sẵn từ Phase 0) — ngoài phạm vi `tools:load-test` |
| **Heap sau major GC** | ⚠️ Tăng đều = leak | JFR/GC log — ngoài phạm vi `tools:load-test` |
| DB pool pending | Phải = 0 khi tải ổn định | ✅ `kage_db_pool_pending`/`kage_db_pool_active` (Micrometer HikariCP, xem [OBSERVABILITY.md §3.3](../07-infrastructure/OBSERVABILITY.md)) |
| CPU / RAM | — | OS-level, ngoài phạm vi `tools:load-test` |

✅ **DB pool pending đo thật lần đầu** (2026-08-02) — chạy `tools:load-test` ramp 10→100 client/300s
nhắm `server-gateway` thật (Postgres 17 + Redis 8 qua Docker, `maximumPoolSize=20` —
`PostgresDataSourceFactory.DEFAULT_MAX_POOL_SIZE`), đọc trực tiếp log DEBUG của HikariCP
(`HikariPool-1 - ... stats (total=.../20, active=..., waiting=...)`) xuyên suốt cả lần chạy thay vì chỉ
scrape rời rạc. Trong suốt pha ramp + giữ tải ổn định ở 100 client (20:02-20:06): `active` đạt tối đa
12/20, **`pending` = 0 gần như 100% thời gian** (chỉ 3 mẫu `waiting=1` thoáng qua lúc pool mới khởi
động ở giây đầu) — pool 20 dư sức cho kịch bản 100 client di chuyển+auto-attack+chat+ping. NHƯNG phát
hiện thật quan trọng hơn: khi load-test dừng đột ngột (toàn bộ 100 client ngắt kết nối gần như cùng lúc
trong <1 tick), pool BÃO HOÀ ngay lập tức — `active` chạm trần 20/20 và `pending` vọt lên tới **147**
thread chờ, kéo dài **~21 giây** (20:06:21 → 20:06:42) rồi tự thoát hết hàng đợi (không timeout —
`connectionTimeout=30000ms` đủ dư, không có `SQLTransientConnectionException` nào; chỉ có vài dòng lỗi
Netty "connection reset by peer" vô hại từ phía kết nối đang đóng). **Kết luận**: pool 20 KHÔNG phải
nút thắt khi tải ổn định tới N=100, nhưng **mass-disconnect** (server restart/crash làm nhiều client
rớt cùng lúc, hoặc nhiều người logout đồng loạt sau sự kiện) là kịch bản thật có thể làm nghẽn pool vài
chục giây. Chưa đo ở N>100, chưa đo soak dài, và chưa đo xem 147 pending dồn lại có gây lag cho lượt
chơi tiếp theo hay không — ngoài phạm vi lần đo này (Điều 9, không suy diễn thêm).

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

⚠️ **CẬP NHẬT 2026-07-29 — công thức trên (đo 2026-07-26) ĐÃ LỖI THỜI, giữ lại làm hồ sơ lịch sử,
KHÔNG dùng để lên kế hoạch nữa.** Lý do lỗi thời rộng hơn những gì §5.2/§5.3/§5.8/§5.9 từng nêu (đã
loại trừ sharding và tracing là nguyên nhân, xem 2 mục đó) — bản chất là công thức gốc đo TRƯỚC hàng
loạt thay đổi kiến trúc+nội dung của Phase 9 (AoI 20Hz, spatial-grid, channel sharding,
`WorldTickScheduler`, toàn bộ observability/tracing) VÀ trước rất nhiều nội dung gameplay (skill/
inventory/quest/dungeon/PvP) được thêm từ Phase 4-8. **Đo lại từ đầu**, cùng máy/JDK, kịch bản xấu
nhất giống hệt (tụ 1 điểm), nhưng với code+nội dung game HIỆN TẠI (commit `a93be8a`), N=100/500/1000
đo bằng `./gradlew run` (cấu hình mặc định thật — không ép `-Xmx`, tracing bật, sharding bật —
BENCHMARK.md §5.7 đã chứng minh KHÔNG được ép `-Xmx`), N=2000 tái dùng số đã đo cẩn thận ở §5.4
(cửa sổ đo N=2000 lần này bị lệch do lấy mẫu trễ, không đáng tin, không dùng).

**Đo RAM đổi phương pháp** — thay vì đọc WorkingSet (bị confound bởi heap tự phình, xem §5.4/§5.7),
dùng `jcmd <pid> GC.run` ép full GC rồi `jcmd <pid> GC.heap_info` đọc ĐÚNG heap Java sống (không lẫn
off-heap/native) — không cần ép `-Xmx` nên không gây regression pause như §5.7 phát hiện.

**Công thức MỚI** (N = số người chơi đồng thời, đo 2026-07-29):

```
RAM sống (Java heap, sau GC.run) = ~110-144MB, GẦN NHƯ PHẲNG — KHÔNG tuyến tính theo N trong khoảng
                                    100-2000 (xem bảng bên dưới) — thay đổi bản chất nhất so với công
                                    thức cũ: dữ liệu ECS mỗi người chơi quá nhẹ (Component chỉ chứa
                                    dữ liệu, ECS.md §3.1) để RAM sống trở thành hàm rõ rệt của N; chi
                                    phí RAM THẬT (nếu có) nằm ở off-heap (Netty buffer/connection) —
                                    CHƯA đo tách bạch phần này.
CPU (lõi)                        = KHÔNG fit được 1 đường thẳng đáng tin — xem bảng, độ dốc giữa các
                                    cặp điểm không đều (100→500: +0.0026/người; 500→1000: +0.0091/
                                    người; 1000→2000 dùng lại số §5.4 KHÔNG cùng buổi đo, chỉ tham
                                    khảo). KHÔNG đưa ra công thức tuyến tính mới để tránh lặp lại
                                    đúng sai lầm "ngoại suy quá tự tin" mà công thức cũ mắc phải.
```

| N | RAM sống (Java heap) | CPU | Băng thông client (gửi/nhận) | Lỗi client |
|---|---|---|---|---|
| 100 | 144MB ✅ đo thật | 0.26 lõi ✅ đo thật | 9.5 / 47 KB/s ✅ đo thật | 0 |
| 500 | 122MB ✅ đo thật | 1.57 lõi ✅ đo thật | 46 / 800 KB/s ✅ đo thật | 0 |
| 1000 | 110MB ✅ đo thật | 6.12 lõi ✅ đo thật | 90 / 2870 KB/s ✅ đo thật | 0 |
| 2000 | ~~144MB~~ ❌ **SAI — đo lại 2026-09-05: heap chạm trần (ZGC dùng 2,58GB), ứ đọng chứ không rò rỉ** | ~~~7.3 lõi~~ ❌ **SAI ~3 lần — đo lại 2026-09-05 cấu hình production: 20,9 lõi** | ~168 / ~11100 KB/s ✅ đo thật | 66 |

⚠️ **"Băng thông client (gửi/nhận)"** là góc nhìn CLIENT (gửi = client→server, nhận = server→client)
— NGƯỢC hướng với cột "Băng thông (ra/vào)" ở bảng công thức CŨ phía trên (đo từ góc nhìn SERVER: ra
= server gửi = client nhận; vào = server nhận = client gửi). Đã đối chiếu kỹ trước khi so sánh —
**"nhận" (client nhận, broadcast AoI) khớp khá tốt với ngoại suy công thức CŨ** (2870KB/s đo thật so
với ~2800KB/s công thức cũ dự đoán cho N=1000 — dù công thức cũ CHƯA hề đo ở N=1000 thật, chỉ ngoại
suy); nhưng **"gửi" (client gửi lên server) THẤP HƠN HẲN so với ngoại suy** (90KB/s đo thật so với
~350KB/s dự đoán, ~N=1000) — hành vi client gửi ít dữ liệu hơn so với lúc công thức cũ được thiết kế,
CHƯA điều tra nguyên nhân cụ thể (Điều 9 — ngoài phạm vi lần đo lại này).

⚠️ **Phát hiện THẬT quan trọng nhất của lần đo lại này**: RAM sống KHÔNG tăng theo N như công thức cũ
giả định (368+0.78×N) — **gần như phẳng 110-144MB xuyên suốt N=100→2000**. Việc "RAM tăng theo đầu
người" trong công thức cũ nhiều khả năng đo WorkingSet (bị lẫn overhead cố định + đệm chưa dọn, cùng
lỗi mà §5.4/§5.7 lần này phát hiện) chứ không phải heap sống thật tăng theo N. **CPU vẫn tăng theo N
rõ rệt** (0.26→1.57→6.12 lõi, KHÔNG tuyến tính — độ dốc tăng dần, không giảm) — đây mới là trục cần
capacity-planning thật, không phải RAM.

⛔ **Hai ô N=2000 trên đã bị bác bỏ bằng phép đo lại ngày 2026-09-05 (ramp, cùng buổi với N=1000)
— xem mục "ĐO LẠI N=2000 THẬT" cuối §5.1.** Giữ số cũ gạch ngang làm hồ sơ, không dùng để lên kế hoạch.

**Việc CHƯA làm lúc viết đoạn trên** (Điều 30): (1) ~~chưa đo lại N=2000 trong CÙNG 1 buổi~~
**✅ ĐÃ ĐO 2026-09-05 — và cả hai số cũ đều sai, xem cuối §5.1**; (2) ~~chưa
tách bạch RAM off-heap (Netty buffer/connection)~~ **✅ ĐÃ ĐO — xem ngay bên dưới**; (3) chưa biết
nguyên nhân "gửi" thấp hơn ngoại suy cũ; (4) độ dốc CPU không đều giữa các cặp điểm (N=100-500 vs
N=500-1000) nghĩa là CHƯA CÓ công thức ngoại suy an toàn cho N>2000 — **PHẢI đo thật trước khi trả
lời "N người có an toàn không"**, đúng nguyên tắc §5.1 gốc đã nêu, giờ càng đúng hơn vì đã biết quan
hệ không tuyến tính rõ ràng hơn.

⚠️ **RAM off-heap (2026-07-29, cùng ngày, cùng buổi tiếp theo) — trục RAM thật sự tăng theo N, KHÔNG
phải Java heap.** Đo bằng Native Memory Tracking (`-XX:NativeMemoryTracking=summary` +
`jcmd <pid> VM.native_memory summary`) — bật cờ này CHỈ để đo, không thêm vào cấu hình chạy thật
(overhead NMT nhỏ nhưng không cần bật thường trực). So sánh 2 lần chụp CÙNG 1 process (không restart
giữa 2 lần, tránh lẫn baseline khác nhau): baseline N=0 (vừa khởi động xong) → N=1000 (819-1000 người
online, giữ tải ~40s).

| Danh mục NMT | N=0 (baseline) | N≈1000 | Chênh lệch |
|---|---|---|---|
| Java Heap (committed, CHƯA ép GC — không dùng số này, xem công thức RAM ở trên) | 508MB | 6952MB | +6444MB |
| **GC** (cấu trúc nội bộ ZGC — forwarding table, remembered set) | 10MB | 227MB | **+217MB** |
| **Other** (chủ yếu `DirectByteBuffer` — nghi là buffer Netty pooled direct arena) | 0.3MB | 257MB | **+256MB** |
| Thread (stack, 94→210 thread) | 4.6MB | 15.4MB | +10.8MB |
| Code (JIT compiled) | 19.8MB | 36.6MB | +16.8MB |
| Metaspace + Symbol + còn lại | ~54MB | ~72MB | +18MB |
| **Tổng non-heap** | ~89MB | ~618MB | **~+529MB** |

**Phát hiện thật**: "GC" + "Other" cộng lại **+473MB** cho ~819-1000 kết nối (~500-650KB/kết nối) —
đây là chi phí RAM THẬT tăng theo N mà cả công thức cũ (đo WorkingSet, lẫn cả heap tự phình) LẪN
công thức mới ở trên (chỉ đo Java heap sống qua `GC.heap_info`) đều **BỎ SÓT HOÀN TOÀN**. "Other" tăng
gần tuyến tính theo số kết nối khớp đúng bản chất Netty pooled direct `ByteBuf` (1 allocator arena
per-thread, buffer đọc/ghi cấp theo kết nối) — dù NMT không gắn nhãn "Netty" trực tiếp (Netty PHẦN
LỚN cấp phát qua `sun.misc.Unsafe`, không phải lúc nào cũng đi qua đường JVM track được 100% chính
xác, nên +256MB là ước tính CẬN DƯỚI hợp lý, không phải con số tuyệt đối). "GC" tăng theo kích thước
heap đang hoạt động (bảng ZGC forwarding/remembered-set tỉ lệ thuận vùng heap live+churn), không trực
tiếp theo N nhưng gián tiếp qua việc N lớn hơn → nhiều packet hơn → nhiều rác hơn → ZGC cần cấu trúc
theo dõi lớn hơn.

**Công thức RAM ĐẦY ĐỦ hơn** (cộng heap sống + off-heap, dùng N=1000 làm điểm neo — CHƯA đủ điểm đo
để fit công thức tuyến tính đáng tin, chỉ trình bày phép cộng để không ai quên phần off-heap):

```
RAM thật cần cấp phát ≈ RAM sống Java heap (gần phẳng, ~110-144MB) + off-heap tăng theo N
                          (~500-650KB/kết nối, CHỈ 2 điểm đo N=0/N=1000, CHƯA đủ để tin số per-
                          connection này chính xác — cần thêm ít nhất 1 điểm N khác, vd N=2000, để
                          xác nhận có thật sự tuyến tính hay không)
```

**Việc CHƯA làm** (Điều 30): chỉ có 2 điểm đo (N=0, N=1000) — chưa đủ để khẳng định quan hệ tuyến
tính giữa off-heap và N, chỉ đủ để khẳng định off-heap TỒN TẠI và ĐÁNG KỂ (nửa GB cho 1000 kết nối,
không phải số có thể bỏ qua khi lên kế hoạch hạ tầng); "Other" chưa tách bạch được chính xác bao
nhiêu % là Netty buffer thật so với native allocation khác (HikariCP driver, Redis client, GraalJS
runtime nếu có script chạy) — cần NMT `detail` level (`-XX:NativeMemoryTracking=detail`, tốn overhead
hơn `summary`) nếu cần độ chính xác cao hơn.

⚠️ **Thử thêm điểm N=2000 (cùng ngày, cùng buổi) — KẾT QUẢ KHÔNG DÙNG ĐƯỢC, ghi lại lý do thay vì
xoá bỏ (Điều 30, minh bạch cả lần đo hỏng).** Chạy tiếp N=1000 rồi N=2000 trên CÙNG process (không
restart, giữ 1 baseline) để có 3 điểm N=0/1000/2000 nhất quán. N=1000 tái xác nhận tốt (GC +215MB,
Other +256MB — khớp lần đo trước, đáng tin). N=2000 bị hỏng vì TIMING: burst-connect tức thời
(`initialClients=maxClients`, không ramp) khiến `client=2000` (mục tiêu đã spawn) và load test kết
thúc NHANH HƠN dự kiến trong lúc đang chờ ổn định — lúc chụp `jcmd` thì `kage_players_online` đã về
0 (client đã ngắt hết). Số đọc được ("Other" 262842KB≈256.7MB, HẦU NHƯ KHÔNG đổi so với N=1000) do đó
KHÔNG đại diện cho N=2000 thật — `kage_players_online` đo được cao nhất trong lúc test là **1353**
(không phải 2000, do lỗi connect + đo không đúng đỉnh tải), gần hơn nhiều với N=1000's 901 người thật
so với con số N danh nghĩa 2000. **Không kết luận gì về tuyến tính từ lần đo này** — dừng thử lại
(Điều 9, đã đủ bằng chứng "off-heap tồn tại và đáng kể" ở N=0/1000, việc xác nhận tuyến tính chính
xác cần lát cắt riêng có đo `kage_players_online` liên tục để bắt đúng đỉnh tải, chưa làm ở đây).

✅ **Lỗ hổng công cụ nêu trên đã được vá** (2026-08-01, `tools:load-test`): `MetricsScraper` nay
parse thêm dòng `kage_players_online` từ CÙNG response body `/metrics` mỗi lần scrape (không gọi
HTTP riêng), và `LoadTestRunner` giữ 1 running-max (`AtomicInteger`, cập nhật ở đúng lịch scrape có
sẵn của `report()` — không thêm vòng polling thứ 2) suốt cả lần chạy, in ra ở cuối test
("Đỉnh tải thật đo được: kage_players_online cao nhất = ..."). Có unit test verify parse +
running-max. **Chỉ lỗ hổng công cụ được vá — CHƯA đo lại N=2000 thật**: lần chạy N=2000 ở trên vẫn
là kết quả hỏng, chưa bị xoá (Điều 30); việc xác nhận tuyến tính off-heap ở N=2000 vẫn là việc CHƯA
làm, cần 1 lần chạy thật riêng (có thể dùng ramp thay vì burst-connect) mới đóng được.

---

✅ **ĐO LẠI N=2000 THẬT — 2026-09-05.** Đóng khoản nợ mở từ 2026-08-01. Máy đo: cùng máy §5.1
(i9-14900HX, 32 luồng logic, 32GB). Ramp thay cho burst-connect; `kage_players_online` lấy mẫu
**liên tục 15s/lần**; CPU đo bằng `TotalProcessorTime` của **riêng tiến trình server** (CPU của bộ
sinh tải không bị tính nhầm vào). Server chạy qua `installDist` để đặt được `JAVA_OPTS`.

⚠️ **Đo HAI cấu hình, vì lần chạy đầu đã đo nhầm cấu hình DEV.** Đây là bài học riêng, ghi lại đầy
đủ ở cuối mục. Cấu hình production lấy từ `deploy/docker/server.Dockerfile:155`.

| | GC | Netty leak detection | Heap tối đa |
|---|---|---|---|
| **PROD** (số chính thức dưới đây) | ZGC | `simple` | 16GB (`MaxRAMPercentage=50`, production thật dùng 75 — hạ cho an toàn trên máy 32GB) |
| DEV (`./gradlew run`, `installDist` mặc định) | G1 | **`paranoid`** | 8GB (mặc định 25% RAM) |

### Kết quả — cấu hình PRODUCTION (dùng số này để lên kế hoạch)

| N (đỉnh thật) | CPU (lõi, trung vị lúc giữ tải) | Lõi / người chơi | Tick p99 | Trễ gói p95 / p99 | Lỗi client | Băng thông client (gửi/nhận) |
|---|---|---|---|---|---|---|
| **950** | **5,3** | 0,0056 | 0,41-0,44ms | 0,8ms / 3,9-14,7ms | 50 (đứng yên) | 82 / 2700 KB/s |
| **1997** | **20,9** | **0,0105** | *(không đọc được, xem dưới)* | 21ms / 3,8s | 1677 (gần như đứng yên) | 121 / 8957 KB/s |

### Bốn kết luận

**1. Số N=1000 cũ ĐÚNG; số N=2000 cũ SAI ~3 lần.** Bảng 2026-07-29 ghi N=1000 = 6,12 lõi — lần này
đo được 5,3 lõi ở N=950 (quy về N=1000 ≈ 5,6). **Tái lập tốt.** Nhưng ô N=2000 ghi `~7,3 lõi` thì
thực đo là **20,9 lõi**. Ô đó vốn đã tự dán nhãn *"số cũ §5.4, không cùng buổi đo"* — nay xác nhận
nó sai, không phải chỉ là "không cùng buổi".

**2. CPU siêu tuyến tính, nay đo được trong CÙNG process, CÙNG buổi, CÙNG cấu hình production.**
Chi phí mỗi người chơi **0,0056 → 0,0105 lõi** khi N tăng gấp đôi: **xấu đi 1,9 lần**. Ở N=2000
server ăn ~21/32 luồng ≈ **65% toàn máy**. Cảnh báo "CPU rất có thể cũng không tuyến tính" viết
2026-07-26 nay **có số đo xác nhận**, không còn là phỏng đoán. ⇒ **Ngoại suy tuyến tính cho N>2000
sẽ đánh giá THẤP chi phí.**

**3. Câu "RAM sống gần như phẳng 110-144MB xuyên suốt N=100→2000" KHÔNG đúng ở N=2000, nhưng đây là
ỨNG ĐỌNG chứ không phải rò rỉ.** Ở N≈2000: ZGC `used` = **2,58GB**, heap committed **chạm trần**
(16GB), WorkingSet 16,6GB; cấu hình DEV/G1 thì heap sống **4,6GB** và cũng chạm trần (8GB). Cả hai
**trả lại hết khi tải rút**: đo trên G1 sau khi `online=0` + ép GC hai lần → heap sống về **565MB**,
committed co **8,1GB → 2,0GB**. Số "144MB ✅ đo thật" cho N=2000 ở bảng cũ gần như chắc chắn chụp
lúc tải đã rút — đúng lỗi timing mà chính mục trên đã tự thú ở phép đo off-heap.

**4. Off-heap KHÔNG phải trục chính, và "~500-650KB/kết nối" không ngoại suy được.** NMT `Other`
(DirectByteBuffer/Netty) = **263MB** ở N≈2000, gần **y hệt** 256MB đã đo ở N=1000. Nó **đứng yên**
khi N gấp đôi. Trục RAM đáng lo là **Java heap ứ đọng ở đường gửi ra**, không phải buffer Netty.

### Nút thắt nằm ở đâu — ĐO XONG, sau HAI lần kết luận sai

Mục này đã bị viết lại **ba lần trong cùng một ngày**. Giữ nguyên vết để lần sau khỏi đi lại:

1. **Lần 1 (suy luận, không profile)**: "nút thắt ở đường gửi ra". Không có bằng chứng.
2. **Lần 2 (`jdk.ExecutionSample`)**: bác bỏ lần 1 — "`world-tick-pool` 62,9% CPU, mô phỏng tốn gấp
   đôi mạng". **Sai**: **số MẪU không phải thời gian CPU**. Bộ lấy mẫu bắt luồng ở trạng thái
   RUNNABLE theo chu kỳ, nên pool 32 luồng thức dậy 4509 lần/giây (mỗi lần 49µs) bị đếm trội hẳn
   so với vài luồng Netty chạy liên tục.
3. **Lần 3 (`jdk.ThreadCPULoad` — CPU THẬT theo từng luồng, không lấy mẫu)**: quay lại kết luận
   lần 1, lần này có số.

| Nhóm luồng | CPU thật (N=1000) | Tỉ trọng |
|---|---|---|
| **`nioEventLoopGroup`** | **4,65-6,49 lõi** | **~90%** |
| `world-tick-pool` | **0,13-0,25 lõi** | ~3% |
| còn lại (JIT, ForkJoin, JFR) | ~0,3 lõi | ~7% |

Tổng 5,1-7,0 lõi — **khớp** với 5,7 lõi đo từ ngoài bằng `TotalProcessorTime`. Và 0,13-0,25 lõi
**khớp** với 0,22 lõi mà `kage.tick.duration` tự báo (4509 tick/giây × 49µs). Hai phép đo độc lập
đồng ý ⇒ **metric không hề báo thiếu; chính `ExecutionSample` mới là thứ đánh lừa.**

⚠️ **Nhưng hệ quả cho vận hành vẫn nguyên**: `kage.tick.duration` mô tả **3% CPU của server**, nên
**"tick p99 0,44ms, còn thừa 97% ngân sách" KHÔNG phải bằng chứng còn dư sức chứa** — 90% chi phí
nằm ở chỗ metric đó không nhìn thấy. OBSERVABILITY.md §3.1 gọi nó là "metric duy nhất mà không có
nó thì không biết server có khoẻ không"; đúng là cần, nhưng **một mình nó không đủ**, và chính phiên
này đã dựa vào nó như thể đủ. Số liệu thô: cửa sổ ổn định 150s ở N=1000 cho **676.390 tick**
(4509/giây ≈ 75 world × 60Hz) tổng **33,35 giây** ⇒ trung bình **49 µs/tick**.

#### Mô phỏng gần như MIỄN PHÍ; tiền nằm hết ở đường gửi

Chia nhỏ CPU của Netty theo **toàn bộ stack** (không chỉ khung trên cùng), quy ra lõi:

⚠️ **Bảng đầu tiên tôi đưa ra ở đây (TLS 39,5% / buffer 27,7% / socket 20,7%) đã bị RÚT.** Nó phân
loại mỗi mẫu theo "có khung thuộc nhóm X ở BẤT KỲ đâu trong stack", xét theo thứ tự ưu tiên với TLS
đứng đầu ⇒ **mọi thứ SslHandler gọi xuống đều bị dán nhãn TLS**, kể cả khi chu kỳ CPU thật sự cháy ở
lớp cấp phát buffer bên dưới. Bảng đúng dưới đây phân loại theo **khung TRÊN CÙNG** (self time) —
cách duy nhất trả lời được "chu kỳ CPU cháy ở đâu".

| Hạng mục (theo khung trên cùng) | % của Netty | ~lõi | |
|---|---|---|---|
| **Cấp phát / trả buffer pooled** | **22,4%** | **~1,14** | **khoản chi lớn nhất** — 1 buffer mỗi gói |
| Pipeline / event loop Netty | 19,1% | ~0,97 | |
| **Mã hoá THẬT (AES/GCM/MAC)** | 15,9% | ~0,81 | |
| Khung SSLEngine (không phải mã hoá) | 11,0% | ~0,56 | |
| Syscall | 4,6% | ~0,23 | |
| Mã dự án | 3,1% | ~0,16 | |
| Còn lại (rải rác) | 24,0% | ~1,22 | |
| **Protobuf** | **0,9%** | **~0,05** | **không đáng kể** |

⇒ **Toàn bộ TLS = mã hoá thật + khung SSLEngine ≈ 26,9% ≈ 1,37 lõi**, KHÔNG phải 2,13 lõi như bản
đầu. Con số "~36µs TLS mỗi gói" ở trên vì thế phải đọc lại thành **~23µs**. Vẫn rất lớn cho 48 byte.

**Điểm chung của ba khoản lớn nhất (buffer, pipeline, TLS): tất cả đều tính THEO GÓI, không theo
byte.**

#### Nguyên nhân: `writeAndFlush` cho TỪNG gói một

Đo `kage_packet_sent_total` ở N=1000, cửa sổ ổn định 100 giây: **5.976.044 gói ⇒ 59.760 gói/giây**.
Băng thông cùng lúc ~2870 KB/s ⇒ **trung bình 48 byte/gói**.

`ConnectedCharacterRegistry:531` và `GatewayChannelHandler:3174` đều gọi `ctx.writeAndFlush(...)`
cho **mỗi** message. Mỗi lần flush ⇒ **một TLS record riêng** (kèm ~29 byte overhead + dựng GCM +
MAC) và **một syscall `write()` riêng**, cho tải trọng 48 byte.

Phép chia sát thực tế: 2,13 lõi ÷ 59.760 gói/giây = **~36 µs CPU cho TLS mỗi gói**. AES-GCM có
AES-NI mã hoá ở mức GB/s, nên 48 byte lẽ ra là **nano**giây. ⇒ chi phí gần như TOÀN BỘ là
**overhead mỗi record**, không phải mã hoá dữ liệu.

#### Hệ quả cho việc tối ưu — và cải chính khuyến nghị cũ của mục này

⛔ **Bốn "đích tối ưu" mà bản trước liệt kê (tra Component qua `HashMap` ~17-25%, AoI ~19%,
`AiSystem` ~9%, `recordSystemDuration` ~7%) đều nằm TRONG 3% CPU của tick pool.** Quy ra toàn server
chúng chỉ là ~0,5% / ~0,6% / ~0,3% / ~0,2%. **Tối ưu chúng gần như vô nghĩa.** Đặc biệt khuyến nghị
"gỡ `recordSystemDuration` khỏi vòng nóng — thắng ~7%" là **sai một bậc độ lớn**: thực tế ~0,2%.

✅ **Đích DUY NHẤT đáng làm: giảm SỐ LƯỢNG gói gửi ra.** Ba khoản chi lớn nhất đều tính theo gói.

⛔ **Hướng "gộp flush" ĐÃ DỰNG THỬ VÀ BỊ SỐ ĐO BÁC BỎ** (cùng ngày). `FlushConsolidationHandler(256,
true)` đặt ngay sau `SslHandler`, đo cùng kịch bản/cấu hình:

| | Tổng CPU | `nioEventLoopGroup` | Cơ cấu trong Netty |
|---|---|---|---|
| Baseline | 5,52 lõi | 5,09 lõi (92,3%) | TLS 39,5% · buffer 27,7% |
| + gộp flush | 5,36 lõi | 4,87 lõi (91,0%) | TLS **39,7%** · buffer 26,1% |

Giảm ~2,9% — **nằm trong nhiễu**, và tỉ trọng TLS **không đổi** ⇒ số TLS record hầu như không giảm.
**Vì sao**: 59.760 gói/giây ÷ 1000 kết nối = **~60 gói/giây MỖI kết nối**, cách nhau ~16ms — gộp
flush chỉ ăn thua khi nhiều message tới **cùng một kết nối** sát nhau, ở đây **không có gì để gộp**.
Prototype đã gỡ; `master` không mang thay đổi nào của nó.

⇒ Chi phí là **theo MESSAGE**, không phải theo lần flush. Đường còn lại là **gộp nội dung AoI** (một
message mang nhiều thực thể, giảm ~2/3 số gói) — **đổi protocol ⇒ cần ADR**
([ADR-042](../01-architecture/adr/ADR-042-Outbound-Packet-Batching.md), /CLAUDE.md §4).

✅ **Câu hỏi "vì sao TLS đắt thế" ĐÃ TRẢ LỜI**: trong nhóm mẫu còn lại có
**`javax.crypto.Cipher.init(...)` chiếm 1,69% CPU của Netty, gọi thẳng từ
`sun.security.ssl.SSLCipher$T13GcmWriteCipherGenerator$GcmWriteCipher.encrypt`**. Tức
**SSLEngine của JDK khởi tạo lại đối tượng `Cipher` cho MỖI TLS record** ở TLS 1.3 (mỗi record cần
nonce mới). `Cipher.init` tốn key schedule + tra provider — chi phí **cố định mỗi record**, hoàn toàn
độc lập với 48 byte dữ liệu. Cùng lý do khiến `GCTR.doFinal`/`MessageDigest.isEqual` hiện lên như
khung nóng thay vì một intrinsic AES-NI duy nhất.

⇒ **Không có "lỗi bệnh lý" nào để vá trong mã dự án — đây là đặc tính của SSLEngine JDK với record
nhỏ và nhiều.** Hai đường đi tiếp, cả hai đều cần chủ dự án quyết:
1. **Gửi ít record hơn** (gộp nội dung AoI — [ADR-042](../01-architecture/adr/ADR-042-Outbound-Packet-Batching.md) D3).
2. **Đổi provider TLS sang OpenSSL/BoringSSL** qua `netty-tcnative` — thư viện MỚI ⇒ 🛑 CLAUDE.md §4.

#### Bài học phương pháp (đắt nhất trong ngày)

**`jdk.ExecutionSample` đếm MẪU, không đo CPU. Muốn quy trách nhiệm CPU thì dùng `jdk.ThreadCPULoad`.**
Hai kết luận trái ngược nhau đã được viết vào tài liệu này trước khi có phép đo đúng. Phần chia nhỏ
BÊN TRONG một pool thì `ExecutionSample` vẫn dùng được (mọi luồng trong pool cùng đặc tính) — chỉ
so sánh GIỮA các pool mới sai.

### Phát hiện phụ, nhưng đáng sửa: observability CHẾT đúng lúc cần nhất

Ở N=2000, endpoint `/metrics` (cổng 9404) **ngừng trả lời**: bộ sinh tải ghi `serverTickMs=-1.0`
(giá trị sentinel khi scrape hỏng) và bộ lấy mẫu của lần đo này đọc `kage_players_online` lỗi ở
9/17 mẫu. Nghĩa là **ở đúng đỉnh tải — lúc cần số liệu nhất — Prometheus sẽ không thu được gì**.
Chưa điều tra nguyên nhân (nghi cùng gốc với nút thắt ghi mạng: HTTP server metrics đói luồng).

### ⛔ Bài học phương pháp: LẦN CHẠY ĐẦU CỦA CHÍNH LẦN ĐO NÀY ĐÃ ĐO NHẦM CẤU HÌNH DEV

`applicationDefaultJvmArgs` của `server-gateway` đặt `-Dio.netty.leakDetection.level=paranoid` (có
chủ đích, cho dev). Production ghi đè bằng `simple` + ZGC ở `server.Dockerfile`. Chạy `installDist`
mà không đặt `JAVA_OPTS` thì **được cấu hình DEV**, và `paranoid` là chế độ gỡ lỗi rất đắt.

| | CPU N≈2000 | Trễ gói p95 / p99 | Lỗi client | Băng thông nhận |
|---|---|---|---|---|
| PROD (ZGC + `simple`) | **20,9 lõi** | **21ms** / 3,8s | 1677, đứng yên | 8957 KB/s |
| DEV (G1 + `paranoid`) | 27,5 lõi | **1-8 GIÂY** / 10-18 GIÂY | 9560, **vẫn tăng** | 6096 KB/s |

Cấu hình dev tốn **thêm ~32% CPU**, **giảm ~32% thông lượng**, và làm p95 xấu đi **hàng trăm lần**.
⇒ **Quy tắc: mọi phép đo capacity PHẢI xác minh cờ JVM thật trước khi đo** (`jcmd <pid> VM.flags` +
`jcmd <pid> VM.system_properties | grep leakDetection`). Một bảng số đo sai cấu hình còn tệ hơn không
có bảng nào — và mục này suýt nữa đã công bố đúng thứ đó.

### ⛔ Công cụ đo đã HỎNG THEO HAI CÁCH — mọi con số "N lỗi" ở trên phải đọc lại

Phát hiện khi thử đo lại: một lần chạy trả về **1000/1000 lỗi** và trông y hệt server sập vì tải.
Không phải. `SimulatedPlayer.connectCreateSelect` đọc frame **theo THỨ TỰ, không kiểm opcode**, nên:

1. **Server từ chối → client hiểu nhầm thành lỗi mạng.** `ErrorResponse` bị đem parse thành
   `ConnectResponse` ⇒ `InvalidWireTypeException` ⇒ đếm vào cùng `errorCount` với lỗi quá tải. Lý do
   thật, sau khi vá: **`409 error.character_limit_exceeded`** — công cụ **tạo nhân vật mới mỗi lần
   chạy** trên cùng account `loadtest<N>`, nên nó **tự hỏng dần qua các lần chạy** cho tới khi chạm
   trần số nhân vật. Đây cũng là nguồn của **~5000 nhân vật rác** trong DB dev.
2. **Bắt tay không độc quyền dòng byte.** `handleSelectCharacterBlocking` gọi `register()` +
   `attachCharacter()` TRƯỚC khi gửi `SelectCharacterResponse`, nên world đẩy
   `CHARACTER_SPAWNED_EVENT` (303) và `CHAT_MESSAGE_EVENT` (802) xuống trước. Đo được **431 + 63**
   client dính đúng cảnh này ở N=1000. Client THẬT điều phối theo opcode nên không sao — chỉ công
   cụ đọc theo vị trí mới hỏng.

**Đã vá cả hai** (`expectFrame` đòi đúng opcode, bỏ qua sự kiện đẩy xen giữa, và nêu thẳng
`error_key` của server; `reuseOrCreateCharacter` dùng lại nhân vật sẵn có). Kết quả cùng kịch bản
vừa hỏng 1000/1000: **1000/1000 online, 0 lỗi, DB không tăng thêm một nhân vật nào**, băng thông
86/2795 KB/s (khớp 90/2870 của 2026-07-29).

⚠️ **Hệ quả: mọi con số "lỗi" trong §5.1 — kể cả "66 lỗi" ở N=2000 của bảng cũ và 1677/9560 của lần
đo hôm nay — đều TRỘN LẪN lỗi bắt tay với lỗi quá tải, và không nên dùng làm bằng chứng về sức
chứa.** Các con số CPU/băng thông/độ trễ thì không bị ảnh hưởng (đo trên những client ĐÃ vào được).

### ⛔ Rà công cụ đo 2026-10-03 — mọi số "trễ gói" đã ghi đều lệch THẤP

Rà tính đúng đắn của `tools:load-test`. **Không sửa con số nào ở trên** — muốn có số đúng phải đo lại
thật; mục này chỉ nói số nào bị ảnh hưởng và lệch theo CHIỀU nào. Công cụ đã vá (kèm test đơn vị
chứng minh đỏ trước khi sửa: `TimedExchangeTest`, `LatencyRecorderTest`, `MetricsScraperTest`,
`FailureCountersTest`, `SimulatedPlayerTest`).

| # | Lỗi của công cụ | Số bị ảnh hưởng | Chiều lệch |
|---|---|---|---|
| 1 | **Ghép phản hồi theo opcode, không theo `sequenceId`.** Mỗi vòng kết thúc bằng một `MoveCommand` dừng bắn-rồi-quên, server ack CẢ lệnh đó; ack nằm sẵn trong socket suốt 300-800ms ngủ ⇒ lượt Move kế tiếp nhặt ack CŨ, đo ra thời gian giải mã vài frame đã đệm (≈0). Move là ~⅔ số mẫu. Có từ bản đầu (2026-07-26). | **Mọi** "trễ gói p50/p95/p99" / "packetLatency" trong tài liệu (vd. 0,8ms / 3,9-14,7ms ở N=950; 21ms / 3,8s ở N=1997; 1-8s / 10-18s cấu hình DEV; 8460/7298ms ở §5.7) | **THẤP hơn thật.** p50 gần như vô nghĩa (rơi vào mẫu ≈0); p95 thực chất ≈ phân vị ~84 của mẫu thật, p99 ≈ phân vị ~97 |
| 2 | **Lượt hết giờ bị bỏ khỏi histogram** (chỉ đếm lỗi). | p99 lúc quá tải (N≈2000, DEV) | **THẤP** — càng quá tải càng đẹp giả (thiên lệch kẻ sống sót) |
| 3 | **Coordinated omission**: client vòng kín, server khựng L giây chỉ sinh 1 mẫu xấu thay vì ~L/0,55s mẫu. | p95/p99 lúc có khựng (GC pause giây ở §5.7, N=2000) | **THẤP** |
| 4 | **"client=" trong report là số client từng dựng**, kể cả đã bắt tay hỏng/bỏ cuộc/chết. | Chỉ dòng log `[report] client=`; các bảng dùng `kage_players_online` phía server nên **không** bị ảnh hưởng | Cao hơn thật |
| 5 | **Một `errorCount` chung** trộn bắt tay hỏng / hết giờ / mất kết nối / server từ chối. | Mọi cột "Lỗi client" (đã cảnh báo ở mục trên, nay có nguyên nhân thứ ba) | Không xác định — không tách được |
| 6 | **Tick quantile lấy dòng `world=` đọc sau cùng**, không lọc `main`. | "serverTickMs"/"Tick p99" do load-test in ra — **chỉ khi** server từng mở hầm ngục/đấu trường (meter không bị gỡ, percentile của timer nhàn rỗi rơi về 0) | Có thể **THẤP** (về 0); số đọc thẳng từ Prometheus/JFR không bị ảnh hưởng |
| 7 | Report cuối chia byte của khoảng LẺ cho cả `reportIntervalSeconds`. | Băng thông ở dòng report CUỐI (các bảng dùng report định kỳ ⇒ không ảnh hưởng) | THẤP |

⚠️ **Lệch MÔ HÌNH TẢI — không phải lỗi tính toán, chưa sửa, cần chủ dự án quyết:** từ 2026-08-09
(ADR-034) `SetTargetCommand` chỉ NHẮM, không tự đánh; công cụ chưa bao giờ gửi `AttackCommand` ⇒
**mọi phép đo sau ngày đó KHÔNG có combat** (không `AttackResultEvent`, không rơi đồ, nhánh bán đồ
không chạy, `kage_gold` đứng yên) — gồm cả lần đo lại N=2000 ngày 2026-09-05 (20,9 lõi). Phần
combat ⇒ CPU/băng thông mỗi người chơi bị đo **THẤP** so với "move + combat + chat" mà §5 tuyên bố.
Ngược lại công cụ gửi ~3,6 `MoveCommand`/giây/client đều đặn, trong khi client thật chỉ gửi khi
ĐỔI hướng phím ⇒ phần di chuyển có thể bị đo **CAO**. Tổng hợp hai chiều thì chưa biết; 59.760
gói/giây ở N=1000 chủ yếu là sự kiện AoI di chuyển. Cả hai đều dưới trần 30 lệnh/giây của server.

Hệ quả cho các kết luận đang dùng: **kết luận CPU (20,9 lõi, siêu tuyến tính) và "giảm số gói" không
bị lỗi tính toán nào ở trên chạm tới** (đo phía server), nhưng chịu lệch mô hình tải. Kết luận nào
dựa vào **trễ gói phía client** (vd. "PROD p95 21ms" là chấp nhận được) **phải đo lại** trước khi dùng.

### Việc CHƯA làm / giới hạn phương pháp (Điều 30)

- ⚠️ **Bộ sinh tải chạy CÙNG MÁY với server.** Ở N=2000 server đã ăn ~21/32 luồng nên bộ sinh tải bị
  đói CPU. Phần "trễ p99 3,8s" và "1677 lỗi" **KHÔNG tách bạch được** giữa *server không kịp* và
  *bộ sinh tải bị bỏ đói*. **Con số CPU thì vững** (đo riêng tiến trình server). Muốn kết luận chắc
  về chất lượng dịch vụ ở N=2000 phải sinh tải từ **máy khác** — giới hạn này đúng cho MỌI lần đo
  N=2000 từ trước tới nay, chỉ là lần này nó lộ rõ.
- Dùng `MaxRAMPercentage=50` thay vì 75 của production thật (an toàn trên máy 32GB đang chạy cả bộ
  sinh tải). Heap chạm trần ở cả hai cấu hình ⇒ trần cao hơn có thể chỉ dời điểm chạm, không sửa gốc.
- Không phải mọi client đều vào được: 950/1000 và 1997/2000.
- Vẫn **KHÔNG có công thức ngoại suy** cho N>2000, và nay biết chắc ngoại suy tuyến tính sẽ sai theo
  hướng nguy hiểm.
- ~~Chưa profile (JFR)~~ **✅ ĐÃ PROFILE — và nó bác bỏ giả thuyết "đường gửi ra"; xem mục "Nút thắt
  nằm ở đâu" ở trên.** Còn thiếu: profile ở **N=2000** (mới có ở N=1000), và gỡ mâu thuẫn giữa
  `kage.tick.duration` p99 với tỉ trọng CPU của `world-tick-pool` — **✅ đã gỡ bằng
  `jdk.ThreadCPULoad`: metric đúng, `ExecutionSample` sai.**
- Các lần chạy để lại **~2000 account / ~3000 nhân vật rác** trong DB dev (không xoá — thao tác phá
  huỷ, cần chủ dự án gật).

### 5.2 AoI spatial-grid sharding (Phase 9, 2026-07-28)

⏳ **Đo thật, lát cắt tiếp theo sau lát cắt 20Hz** (commit `2198e76` + thay đổi CHƯA commit trong
phiên này — `AreaOfInterestTracker` chuyển từ quét O(n²) toàn world sang lưới spatial hashing, xem
javadoc class). Máy đo: **cùng máy §5.1** (Intel i9-14900HX, 32GB RAM, Windows), JDK
`loom-ea-25-loom+1-11`, `-XX:+UseZGC` (ADR-019). Kịch bản: `tools:load-test` mặc định — **kịch bản
XẤU NHẤT có chủ đích** (mọi client giả lập tụ 1 điểm luyện tập duy nhất hiện có trong game, không
phải trải rộng nhiều khu vực — game CHƯA có nội dung multi-zone để test kịch bản trải rộng thật).

**Trước (commit `2198e76`, chỉ tách 20Hz, chưa sharding) — N=1000, giữ tải ~60s:**

| Metric | Giá trị đo thật |
|---|---|
| Tick p99 (ổn định) | 67–83ms (ngân sách 16.6ms — **vượt ~4-5×**) |
| Lỗi client | 0 |
| HikariCP (pool 10) | Backlog tới ~1790 request chờ lúc ramp connect — không gây lỗi client (kịp trong timeout 5s) |

**Trước — N=2000, giữ tải:** **sập hẳn** — tick p99 leo 20ms→33ms→7.78s→8.59s theo thời gian giữ
tải, tick tối đa đo được **10.76s** (~650× ngân sách), lỗi client leo thang KHÔNG dừng (95→401→1360→
→3553 tới lúc dừng test, chưa thấy chững lại — vòng lặp lỗi tự nuôi lỗi). Server **không crash,
không leak** — hồi phục về tick ~0.03ms ngay khi tải dừng (xác nhận đây là nghẽn tính toán, không
phải rò rỉ hay hỏng state).

**Sau (spatial-grid sharding, cùng máy/JVM/kịch bản) — N=1000:**

| Metric | Giá trị đo thật | So với trước |
|---|---|---|
| Tick p99 (ổn định) | 56–58ms | ↓ ~20-30% — vẫn vượt ngân sách, cải thiện KHIÊM TỐN |
| Lỗi client | 0 | bằng |

**Sau — N=2000:**

| Metric | Giá trị đo thật | So với trước |
|---|---|---|
| Tick p99 đỉnh | ~2.08–2.55s | ↓ ~3.4× (từ 8.59s) — vẫn vượt ngân sách rất xa |
| Tick tối đa | ~3.73s (gauge) | ↓ ~2.9× (từ 10.76s) |
| Lỗi client | Chững ở 177, KHÔNG leo thang tiếp | ↓ ~20× (từ 3553, đang leo không dừng) |
| Throughput (packet xử lý/10s) | ~36.000–43.000 | Cao hơn nhiều lần so với lúc trước sập xuống còn ~900-3800/10s |

⚠️ **Diễn giải trung thực (Điều 30, KHÔNG phóng đại)**: cải thiện N=2000 là THẬT và đáng kể (lỗi
giảm ~20×, hệ thống chuyển từ "sập theo cấp số nhân" sang "quá tải nhưng ổn định"), nhưng **N=2000
tụ 1 điểm vẫn KHÔNG đạt ngân sách** — đúng như dự đoán trong javadoc `AreaOfInterestTracker`: lưới
spatial hashing chỉ giảm chi phí khi entity TRẢI RỘNG hơn 1 ô lưới; khi toàn bộ N tụ trong 1-2 ô
(đúng kịch bản test hiện có, vì game chỉ có 1 điểm luyện tập), thuật toán bên trong ô đó vẫn xấp xỉ
O(n²), chỉ khác là có thêm chi phí dựng lưới. **Chưa verify được kịch bản THẬT SỰ trải rộng** (N
tổng 1000-2000 nhưng ≤50/khu vực theo giả định mật độ của dự án) — cần multi-zone/multi-spawn-point
thật trong game content để đo, hoặc benchmark tổng hợp mức World (ngoài phạm vi lát cắt này).

**Việc chưa làm / follow-up thật sự cần** (2 mục còn lại sau §5.3 bên dưới):
1. ✅ **Chính sách giới hạn số người/khu vực — ĐÃ CODE, xem §5.3.**
2. HikariCP `maximumPoolSize=10` là điểm nghẽn phụ lúc burst connect (chưa gây lỗi ở N=1000/2000
   nhưng backlog hàng nghìn request — đáng tăng trước khi test N lớn hơn hoặc test soak dài).
3. Multi-zone THẬT trong game content (nhiều điểm luyện tập/map vật lý khác nhau) vẫn chưa tồn tại
   — `MainWorldChannelRegistry` (§5.3) giải quyết bài toán mật độ bằng CHANNEL (nhiều bản sao World
   song song), không phải bằng cách chia nhỏ bản đồ vật lý; cả 2 hướng đều hợp lệ, chưa cần làm
   thêm hướng kia trừ khi có lý do gameplay cụ thể (Điều 9).

### 5.3 Giới hạn người/khu vực — `MainWorldChannelRegistry` (Phase 9 lát cắt 7, 2026-07-28)

✅ **Đo thật, giải quyết dứt điểm khoảng trống nêu ở §5.2** — theo yêu cầu chủ dự án: mỗi khu vực
(channel) tối đa **20 người mặc định** (`MainWorldChannelRegistry.DEFAULT_CAPACITY`), khu vực đặc
biệt tối đa **50 người** (`SPECIAL_CAPACITY`, chưa có map nào dùng — hook sẵn cho sau); khu vực mới
tự mở khi tổng người ĐẠT 80% tổng sức chứa hiện tại (`AUTO_OPEN_UTILIZATION_THRESHOLD`). Mỗi khu
vực là 1 {@code WorldFacade} riêng — cùng kỹ thuật `DungeonInstanceRegistry`/`ArenaInstanceRegistry`
đã có (ADR-026: World riêng cùng process), áp dụng cho MAP CHÍNH thay vì dungeon/duel. Chat Thế
giới/Bang/Party/Riêng/Bạn bè/Mail KHÔNG bị ảnh hưởng (định tuyến qua `ConnectedCharacterRegistry`
dùng chung mọi khu vực, kiến trúc sẵn có từ trước) — chỉ AoI (di chuyển/combat) + chat Khu vực cục
bộ theo channel, đúng ý nghĩa "khu vực" vốn có.

⚠️ **Diễn giải đúng "25 khu mặc định/map" trong yêu cầu gốc**: KHÔNG tạo sẵn 25 khu vực rỗng lúc
khởi động (tốn tick thread cho world trống) — khu vực mở **lazy** từ #1, đúng tiền lệ
`DungeonInstance`/`DuelInstance` (chỉ tạo khi cần, đóng khi rỗng — trừ #1, luôn giữ). Số "25" chỉ
dùng làm mốc tham chiếu capacity-planning, không phải số khu vực khởi tạo cứng. Đây là diễn giải
của AI, CHƯA xác nhận lại với chủ dự án — nếu cần pre-warm nhiều khu vực từ đầu, đó là 1 dòng đổi.

**2 bug thật phát hiện+sửa khi làm lát cắt này** (không phải trong logic capacity, mà trong cách
nối dây):
1. `MainWorldChannelRegistry` ban đầu dùng `HashMap`/`ArrayList` thường — bị gọi đồng thời từ NHIỀU
   thread (`authExecutor` của `GatewayChannelHandler`/`DungeonInstanceRegistry` VÀ
   `ArenaInstanceRegistry.settlementExecutor` khi settle duel xong) → race điều kiện thật, gây
   `GatewayServerIntegrationTest` treo ngẫu nhiên (`shouldFightDuelToDeathAndUpdateRating_overRealSockets`,
   `partyShouldEnterAndExitDungeonAsSeparateWorld_overRealSockets`). Sửa: `synchronized` trên toàn
   bộ method đọc/ghi state (cùng lý do `ArenaInstanceRegistry` tự dùng `ConcurrentHashMap`).
2. 2 test trên VẪN treo sau khi sửa race — nguyên nhân THẬT KHÁC: test gửi lệnh rời world chính
   (enter dungeon/duel) ngay sau khi nhân vật chứng nhân (C) kết nối, KHÔNG đợi AoI `sync()` (20Hz,
   §5.2) chạy ít nhất 1 lần để thiết lập quan hệ "C đang thấy A/B" — nếu detach xảy ra TRƯỚC lượt
   sync() đầu tiên, `visibleTo` của A/B vẫn rỗng nên KHÔNG có ai để báo despawn (không phải lỗi
   despawn, mà quan hệ AoI chưa từng được thiết lập). Đây là lỗ hổng đồng bộ hoá CÓ SẴN từ khi AoI
   sync tách khỏi 60Hz (lát cắt 1), chỉ hiếm khi lộ ra vì 3 round-trip TCP thật (connect/create/select)
   thường đủ chậm để sync() kịp chạy — bị lộ rõ khi máy chạy nhiều vòng test liên tục dưới tải nặng
   (đúng kịch bản mà comment sẵn có trong test đã cảnh báo). Sửa: 2 test thêm bước đợi C THẬT SỰ
   thấy A/B spawn (đọc `CharacterSpawnedEvent`) trước khi cho A/B rời world chính — làm test tất
   định thay vì dựa vào may rủi thời gian.

Verify: toàn bộ `server-gateway:test` (39 test, gồm `GatewayServerIntegrationTest`
`GatewayPipelineTest`) PASS ổn định qua nhiều lần chạy lại sau khi sửa cả 2 bug.

**Số liệu trước/sau — cùng máy/JVM/kịch bản §5.2, DB Postgres XOÁ SẠCH giữa các lần đo (tránh lẫn
account cũ chạm giới hạn 4 nhân vật/account):**

| N | Trước (chỉ 20Hz, `2198e76`) | + spatial-grid sharding (§5.2) | + giới hạn người/khu vực (lát cắt này) |
|---|---|---|---|
| 1000 tick p99 (ổn định) | 67–83ms | 56–58ms | **0.82–1.57ms** |
| 2000 tick p99 (đỉnh/ổn định) | 8.59s (đỉnh) | 2.08–2.55s (đỉnh) | **0.98–3.15ms** (ổn định suốt) |
| 2000 tick tối đa | 10.76s | ~3.73s | **28.6ms** (1 đỉnh đơn lẻ, phần còn lại <5ms) |
| 2000 lỗi client | 3553, đang leo | 177, hết leo | **0** |

✅ **Cả N=1000 và N=2000 đều trong ngân sách 16.6ms** (trừ 1 đỉnh 28.6ms đơn lẻ ở N=2000, ~1.7×
ngân sách — không lặp lại, không kèm lỗi client). Đây là kết quả trực tiếp của việc tách 2000 người
thành nhiều khu vực ≤20 người thay vì để AoI phải xử lý 2000 người trong 1 World — xác nhận đúng lý
do sharding + giới hạn mật độ ở tầng gameplay BỔ SUNG cho nhau (không phải giải pháp thay thế nhau):
spatial-grid tối ưu CÁCH TÍNH trong 1 World; giới hạn người/khu vực đảm bảo 1 World KHÔNG BAO GIỜ
phải chứa quá đông người để cách tính đó có ý nghĩa.

**Việc chưa làm / follow-up còn lại**:
1. ✅ HikariCP `maximumPoolSize` 10→20 (2026-07-28, cùng ngày) — dựa trên backlog thật đo được ở
   trên; vẫn là khởi điểm hợp lý, chưa phải số đã tune kỹ (~~chưa có DB pool pending metric qua
   Micrometer để đo lại có còn backlog ở N lớn hơn không — mục "DB pool pending" §5 vẫn ⏳~~).
   ✅ 2026-10-02: metric `kage.db.pool.active`/`pending` có trong `GatewayBootstrap`, và §5 dòng "DB pool pending"
   đã ✅ — đo thật lần đầu 2026-08-02 (ramp 10→100 client).
2. Chưa xác nhận lại với chủ dự án cách diễn giải "25 khu mặc định" (lazy vs pre-warm) nêu trên.
3. `SPECIAL_CAPACITY=50` chưa có nơi nào trong game gán/dùng (chưa có khái niệm "map đặc biệt") —
   hook đã sẵn, chờ nội dung game thật cần tới.

### 5.4 Shared tick-pool — `WorldTickScheduler` (commit `94befe0`, 2026-07-29)

✅ **Đo thật, verify follow-up nêu ở đầu §5.3** — §5.2/5.3 phát hiện số kênh (`MainWorldChannelRegistry`,
capacity 20/kênh, tự mở ở 80%) tỉ lệ thẳng với N, mỗi kênh trước đây tự spawn 1 `Thread` OS riêng
(N=1000 ⇒ ~50-63 kênh ⇒ ~50-63 thread). `WorldTickScheduler` thay bằng 1
`ScheduledExecutorService` dùng chung, kích thước cố định = `Runtime.availableProcessors()` (xem
plan gốc). Mục tiêu đo: xác nhận trần thread THẬT (không chỉ suy luận kiến trúc).

**Máy đo**: cùng máy §5.1-5.3 (Intel i9-14900HX, 24 lõi thật/32 luồng logic, 32GB RAM, Windows). JDK
`loom-ea-25-loom+1-11`. Chạy qua `./gradlew :server:server-gateway:run` (dev command, giống các lần
đo trước) — flags thật đang bật: `-XX:+UseZGC` (qua `JAVA_TOOL_OPTIONS`) +
`-Dio.netty.leakDetection.level=paranoid` (mặc định dev của `applicationDefaultJvmArgs`, KHÔNG phải
flag production JVM.md §3) + **KHÔNG có `-Xmx`** (JVM tự chọn heap tối đa theo RAM máy — ảnh hưởng
số RAM đo được, xem diễn giải bên dưới). Postgres/Redis thật qua Docker (chỉ 2 service này, không
kèm observability stack). DB Postgres mới tinh (volume mới tạo) trước lần đo.

⚠️ **`.gradlew build -x pmdMain -x pmdTest -x pmdJmh`**: `server-world:test`+`server-gateway:test`
đều UP-TO-DATE (xanh, khớp code đã commit) trước khi chạy — bước 1 của Verification trong plan coi
như xác nhận lại, không chạy lại từ đầu vì input không đổi từ lần chạy trước.

**Xác nhận trần thread (bước 2 + 3 của Verification) — `jstack` thật lúc đang tải**:

| N | Số kênh ước tính (N/(20×0.8)) | Thread `world-tick-pool` đếm được (`jstack`) |
|---|---|---|
| 1000 | ~50-63 | **32** |
| 2000 | ~100-125 | **32** |

`jstack` đếm CHÍNH XÁC 32 thread tên `world-tick-pool` ở CẢ 2 lần đo — đúng bằng
`availableProcessors()` của máy (32 luồng logic), KHÔNG tăng theo N dù số kênh tăng gấp đôi. Đây là
bằng chứng trực tiếp (không phải suy luận) rằng trần thread đã bị chặn đúng như thiết kế — so với
kiến trúc cũ (1 thread OS/kênh) thì N=2000 lẽ ra đã cần ~100-125 thread độc lập.

**Tick p99 (scrape `/metrics`, LƯU Ý quan trọng bên dưới) — cùng kịch bản xấu nhất §5.2/5.3 (tụ 1
điểm), giữ tải ~80-90s:**

| N | Tick p99 ổn định (channel "main") | Lỗi client |
|---|---|---|
| 1000 | 0.06–0.72ms (2 lần đo lặp lại, 1 lần có đỉnh khởi động 950ms) | 0 (lần 2) / 149 (lần 1) |
| 2000 | 0.06–0.72ms | 856 |

⚠️ **Caveat PHẢI đọc trước khi so `p99` này với §5.3** (✅ ĐÃ VÁ — xem §5.5 bên dưới, giữ nguyên đoạn
này làm hồ sơ lịch sử): tại thời điểm đo ở trên, `MicrometerTickMetricsPort` thật **CHỈ gắn vào kênh
#1** (tag `"main"`) — mọi kênh mở thêm sau (kênh #2, #3... tới ~50-125 kênh ở N lớn) dùng
`TickMetricsPort.NO_OP`. Nghĩa là **con số p99 ở bảng trên chỉ phản ánh 1 kênh trong số ~50-125 kênh
đang chạy**, KHÔNG phải toàn hệ thống — khác với số p99 ở §5.3 (đo TRƯỚC khi channel sharding tồn
tại, lúc đó chỉ có 1 World nên p99 = toàn hệ thống thật). **Không kết luận "nhanh hơn §5.3"** từ
bảng này — đó là so sánh sai đối tượng đo (1 kênh nay vs toàn hệ thống trước).

**Tín hiệu CHÉO kênh duy nhất có được lúc đó** (log `WARN "World tick quá hạn"`, bắn ra ở MỌI kênh kể
cả NO_OP — xem `WorldFacade.tickTask()`): **31 lần overrun** (tick > 16.6ms) trong suốt ~5 phút tải
thật (2 lần N=1000 + 1 lần N=2000, tổng hàng triệu lượt tick trên ~50-125 World cộng lại) — lớn nhất
đo được **95ms** (~5.7× ngân sách), phần lớn 22–57ms, đều xảy ra đúng lúc burst connect (ramp tức
thời `initialClients=maxClients`, không ramp dần). Tỉ lệ overrun cực nhỏ (~0.002% số lượt tick ước
tính) và không kèm crash.

**RAM/CPU đo qua Windows (`Get-Process`, lấy mẫu 5s/lần suốt thời gian giữ tải) — LẦN ĐẦU đo bằng
công cụ OS-level thay vì suy luận (khoảng trống §5.1 nêu rõ chưa từng làm việc này):**

| N | RAM (WorkingSet, đỉnh) | CPU trung bình (đo qua CPU-time delta) | Thread process tổng |
|---|---|---|---|
| 1000 | ~7.7GB | ~6.2 lõi (cửa sổ 80s hoạt động) | ~209-217 |
| 2000 | ~7.7GB | ~7.3 lõi (cửa sổ 80s hoạt động) | ~209-217 |

⚠️ **RAM 7.7GB KHÔNG phải "chi phí thật/N" — đây là confound của cách chạy test, PHẢI sửa trước khi
dùng số này** (Điều 30, không được dùng để cập nhật công thức §5.1): (1) **không đặt `-Xmx`** ⇒ JVM
tự chọn heap tối đa lớn (mặc định ~25% RAM máy = ~8GB trên máy 32GB) ⇒ ZGC chỉ dọn rác khi gần đầy,
không phải khi cần; (2) `-Dio.netty.leakDetection.level=paranoid` là cấu hình DEV
(`applicationDefaultJvmArgs`), tốn bộ nhớ theo dõi hơn nhiều so với `simple` ở production (JVM.md
§3). Bằng chứng RAM 7.7GB là RÁC CHƯA DỌN chứ không phải dữ liệu sống thật: đọc trực tiếp
`gateway-gc.log` (ZGC) bắt được **1 lần GC thật ở N=2000: "Young Generation 6878M(85%)->220M(3%)"**
— nghĩa là ngay trước khi GC chạy, heap đã đầy 6.9GB RÁC, nhưng dữ liệu SỐNG THẬT sau khi dọn chỉ
còn **220MB** (thấp hơn NHIỀU so với công thức ngoại suy §5.1 cho N=2000, ~2.9GB) — không leak (log
kết thúc bằng `"Uncommitted: 76M(1%)"` sau khi client ngắt kết nối, heap trả gần hết về hệ điều
hành). **Follow-up bắt buộc trước khi coi RAM đã đo xong**: chạy lại với `-Xmx` production-like +
`leakDetection=simple` để có số RAM committed/uptime thực tế thay vì "đầy tới ngưỡng GC tự chọn".

CPU trung bình (~6.2-7.3 lõi) đo được **cao hơn** công thức ngoại suy §5.1
(`0.02 + 0.00275×N` ⇒ 2.77 lõi @1000, 5.52 lõi @2000) — khoảng cách thu hẹp dần khi N tăng (2.2× ở
N=1000 → 1.3× ở N=2000). **Không kết luận nguyên nhân chắc chắn** (nhiều thay đổi kiến trúc xảy ra
CÙNG LÚC giữa lần đo công thức gốc và lần đo này — channel sharding lẫn thread-pool đều mới, công
thức gốc đo TRƯỚC khi sharding tồn tại nên không tính chi phí per-channel bookkeeping của ~50-125
World song song, ví dụ mỗi kênh có `AreaOfInterestTracker`/training-dummy/NPC riêng) — chỉ ghi nhận
số đo thật, KHÔNG cập nhật công thức §5.1 tới khi có phép đo tách bạch riêng chi phí sharding khỏi
chi phí thread-pool.

**Phát hiện phụ ngoài phạm vi lát cắt này** (đáng ghi lại, không phải bug của
`WorldTickScheduler`): burst connect tức thời (`initialClients=maxClients`, không ramp dần) gây
**149 lỗi `Connection refused` ở N=1000 (lần đo 1), 856 lỗi ở N=2000** — khớp với dòng log Netty lúc
khởi động `"Failed to get SOMAXCONN ... Default: 200"` (hàng đợi backlog kernel mặc định chỉ 200 kết
nối chờ accept). Lần đo N=1000 thứ 2 (cùng tham số) ra **0 lỗi** — không tất định, phụ thuộc thời
điểm OS xử lý backlog. Chưa sửa (ngoài phạm vi plan này); follow-up: tăng backlog Netty
(`ServerBootstrap.option(ChannelOption.SO_BACKLOG, ...)`) nếu kịch bản burst-connect thật (world
boss, mở server) là kịch bản cần tối ưu.

**Kết luận** (đúng phạm vi plan gốc, KHÔNG suy rộng): mục tiêu duy nhất của
`WorldTickScheduler` — chặn trần thread OS ở `POOL_SIZE` bất kể N — **xác nhận ĐÚNG bằng số đo thật**
(32 thread cố định ở cả N=1000 và N=2000, jstack). Tick p99 kênh "main" và tần suất overrun cross-kênh
đều không cho thấy regression. RAM/CPU đo được có giá trị tham khảo nhưng **CHƯA đủ sạch** (confound
`-Xmx`/leak-detection cho RAM, thiếu tách bạch sharding-vs-pool cho CPU) để thay thế công thức §5.1 —
đây là việc CHƯA xong, không phải đã xong.

### 5.5 Vá lỗ hổng đo p99 toàn hệ thống (2026-07-29, cùng ngày)

✅ **Đã vá lỗ hổng nêu ở §5.4** — mọi khu vực map chính giờ mang `TickMetricsPort` THẬT (không còn
`NO_OP`), cùng tag `"main"`. Cách làm: `MainWorldChannelRegistry` nhận thêm `MeterRegistry` qua
constructor, `openChannel()` tạo `new MicrometerTickMetricsPort(meterRegistry, "main")` cho MỖI khu
vực mở thêm thay vì `TickMetricsPort.NO_OP`. Dựa trên hành vi CHUẨN của Micrometer: `Timer`/`Counter`
đăng ký (`register()`) nhiều lần với CÙNG tên+tag sẽ trả về CÙNG 1 đối tượng dùng chung — không tạo
bản sao, không đổi cardinality (vẫn đúng tag CỐ ĐỊNH `"main"`, KHÔNG dùng ID khu vực làm tag,
OBSERVABILITY.md). Vì vậy `kage.tick.duration`/`kage.tick.overrun.count`/`kage.ecs.system.duration`
từ NAY phản ánh ĐÚNG toàn bộ khu vực map chính, không chỉ khu vực #1.

⚠️ **Ngoại lệ CÓ CHỦ Ý, chưa vá**: `kage.entities.count` là `Gauge` bọc 1 `AtomicInteger` — khác
`Timer`/`Counter`, Micrometer KHÔNG gộp nhiều `Gauge` cùng tag (đăng ký sau chỉ trả lại `Gauge` đã có,
không đổi đối tượng nó đọc) — metric này vẫn CHỈ phản ánh khu vực #1, y hệt trước khi vá. Không sửa
trong lát cắt này (Điều 9 — phạm vi yêu cầu là lỗ hổng đo p99, không phải toàn bộ observability); nếu
cần số entity tổng đúng, cần lát cắt riêng (cộng dồn thủ công qua `MainWorldChannelRegistry.channels`,
giống cách `totalPlayers()` đã làm — không đi qua `TickMetricsPort`).

**Verify — cả 2 bước đều đo thật, không chỉ đọc code:**

1. `server-gateway:test`+`server-world:test` (89 test, `--rerun`, xanh 100%) + `checkstyleMain`/
   `checkstyleTest` sạch. 2 file test (`GatewayPipelineTest`, `GatewayServerIntegrationTest`) cập
   nhật constructor `MainWorldChannelRegistry` (thêm `SimpleMeterRegistry` dùng chung với
   `GatewayNetworkMetrics` đã có sẵn trong 2 test đó).
2. **Đo thật qua gateway đang chạy** (cùng máy/JDK, DB xoá sạch tránh lẫn account cũ chạm giới hạn 4
   nhân vật — bài học rút ra khi lần đầu chạy lại verify này bị 100% lỗi `CharacterLimitExceededException`
   do volume Postgres cũ từ §5.4 chưa xoá): 200 client thật (`tools:load-test`, `initialClients=
   maxClients=200`) → auto-open ra ~13 khu vực (`kage.players.online`=199). Đếm
   `kage_tick_duration_seconds_count{world="main"}` tăng **782 lần/giây** trong cửa sổ đo 10s — khớp
   ĐÚNG với ~13 khu vực × 60Hz (782/60 ≈ 13.04), so với **60/giây cố định TRƯỚC khi vá** (bằng chứng
   trực tiếp: chạy lại y hệt kịch bản này TRƯỚC khi đổi constructor, tick count không đổi dù N tăng).
   `kage_tick_overrun_count_total{world="main"}` cũng tăng (19 lần trong lượt đo ngắn) — trước đây
   con số này gần như luôn 0 vì hầu hết khu vực là `NO_OP`.

**Kết luận**: `/metrics` giờ là số liệu THẬT cho toàn bộ khu vực map chính, không còn cần đọc log
`WARN` làm tín hiệu gián tiếp như §5.4 phải làm. §5.1's công thức capacity-planning cũ và caveat RAM/
CPU ở §5.4 KHÔNG đổi (lỗ hổng đã vá là lỗ hổng QUAN SÁT, không phải lỗ hổng HIỆU NĂNG — không có bằng
chứng mới nào về RAM/CPU/tick timing thật ở N=1000/2000 từ lát cắt này).

### 5.6 `SO_BACKLOG` — sửa phát hiện phụ ở §5.4 (2026-07-29, cùng ngày)

✅ **Đo thật, sửa follow-up "Netty SOMAXCONN=200" nêu ở §5.4** — đọc lại code
(`GatewayServer.start()`) phát hiện diễn giải ở §5.4 CHƯA ĐÚNG NGUYÊN NHÂN: dòng log Netty
`"Failed to get SOMAXCONN ... Default: 200"` chỉ là Netty tự dò backlog mặc định của HĐH lúc KHÔNG
set `SO_BACKLOG` tường minh — code này LUÔN set tường minh (`ChannelOption.SO_BACKLOG`), nên giá trị
áp dụng THẬT là con số hardcode cũ **128** (thấp hơn cả "200" bị nghi oan), không phải OS default.
Sửa: nâng lên **1024** (khởi điểm hợp lý hơn dựa trên bằng chứng lỗi thật ở §5.4, cùng tinh thần
HikariCP 10→20 — chưa test tới điểm gãy mới, sẽ tinh chỉnh tiếp nếu cần), đặt tên hằng số
`SO_BACKLOG` (trước là magic number trần, /CLAUDE.md §11 cấm). Giữ nguyên cảnh báo: giá trị THẬT áp
dụng lúc production còn bị OS clamp thêm (Linux `net.core.somaxconn` lúc deploy, DOCKER.md — ngoài
phạm vi code Java) — set cao ở code là điều kiện CẦN, không ĐỦ.

**Verify — load-test lại ĐÚNG kịch bản gây lỗi ở §5.4** (burst connect tức thời
`initialClients=maxClients`, cùng máy/JDK, DB xoá sạch trước mỗi N):

| N | Lỗi client TRƯỚC (§5.4, backlog=128) | Lỗi client SAU (backlog=1024) |
|---|---|---|
| 1000 | 149 (lần 1) / 0 (lần 2 — không tất định) | **89** |
| 2000 | 856 | **95** |

N=2000 cải thiện rõ rệt (~9× ít lỗi hơn). N=1000 nằm trong khoảng nhiễu đã ghi nhận ở §5.4 (0-149,
không tất định theo đúng bản chất race OS xử lý backlog) — không đủ để nói "chắc chắn tốt hơn" riêng
lẻ, nhưng N=2000 (mẫu lớn hơn, chênh lệch lớn hơn nhiều so với nhiễu quan sát được) đủ thuyết phục.
⚠️ Tick p99 lúc N=2000 lần này cao hơn §5.4 (0.02-2.75ms so với 0.06-0.72ms) — **không phải
regression**: vì ÍT bị từ chối kết nối hơn (95 lỗi so với 856), nhiều client hơn THẬT SỰ vào world
tạo tải thật (auto-open nhiều khu vực hơn, mỗi khu xử lý combat/AoI thật) — số liệu p99 lần này
ĐẠI DIỆN đúng hơn cho tải thật ở N=2000, vẫn trong ngân sách 16.6ms.

**Kết luận**: giảm lỗi client thật khi burst-connect ở N lớn — chưa test tới điểm gãy mới của
backlog=1024 (Điều 30: chưa biết N nào làm 1024 không đủ nữa), chỉ xác nhận cải thiện so với 128 ở
đúng 2 điểm N=1000/2000 đã có baseline.

### 5.7 Đo lại RAM "sạch" — follow-up bắt buộc từ §5.4 (2026-07-29, cùng ngày)

⚠️ **Đo thật, nhưng KẾT QUẢ NGƯỢC KỲ VỌNG** — §5.4 nói RAM 7.7GB là "rác chưa dọn" và yêu cầu đo lại
với `-Xmx` production-like + `leakDetection=simple` để có số "sạch". Lần này chạy gateway TRỰC TIẾP
bằng `java` (không qua `./gradlew run`, tránh `-Dio.netty.leakDetection.level=paranoid` bị cứng
trong `applicationDefaultJvmArgs` — build.gradle.kts) với `-Xmx2g -Xms2g -XX:+UseZGC
-XX:+ExitOnOutOfMemoryError`, không set `leakDetection` (mặc định Netty = `simple`). Máy/JDK/kịch
bản giống hệt §5.4 (DB xoá sạch trước mỗi N, `docker compose down -v`).

**Kết quả — WorkingSet giờ đúng là "sạch"/bị chặn trần như mong đợi:**

| N | `-Xmx` | WorkingSet ổn định | GC pause lớn nhất | Lỗi client | packetLatency p99 |
|---|---|---|---|---|---|
| 1000 | 2g | ~2.4GB | 0.166s | 192 | (bình thường) |
| 2000 | 2g | ~2.5GB | **2.811s** | 268 | **8460ms** |
| 2000 | 4g | ~4.6GB | **1.240s** | 261 | **7298ms** |

⚠️ **Đây KHÔNG phải "đã sạch, đóng việc"** — ép heap nhỏ lại (2GB, rồi 4GB) tại N=2000 gây GC pause
tới **hàng giây** (một số cycle "Young Generation" còn cho thấy usage TĂNG thay vì giảm giữa lúc đo,
vd. `748M(37%)->882M(43%) 2.811s` — dấu hiệu allocation-pressure thật, không phải nhiễu đo) và
packet latency phía client tăng vọt lên **7-8 GIÂY** — tệ hơn HẲN so với heap KHÔNG giới hạn ở §5.4
(pause <0.06ms, packet latency bình thường, dù lúc đó có confound khác là SO_BACKLOG=128 gây nhiều
lỗi connect hơn — nhưng pause time đo trực tiếp từ `gc.log` không liên quan gì tới backlog, so sánh
này vẫn hợp lệ). Ngay cả 4GB (gấp đôi 2GB) vẫn CHƯA đủ để tránh pause giây — chưa biết ngưỡng heap
nào mới đủ ở N=2000 tụ 1 điểm.

**Diễn giải trung thực**: mục tiêu ban đầu ("số RAM sạch để cập nhật công thức §5.1") đã đạt được
đúng nghĩa đen — WorkingSet giờ phản ánh committed thật thay vì trần tự chọn — nhưng phát hiện QUAN
TRỌNG HƠN nhiều: **ép `-Xmx` nhỏ để "đo sạch" tự nó tạo ra regression hiệu năng thật dưới tải đỉnh**,
đúng cơ chế ZGC cần khoảng trống heap rộng rãi để tránh pha "dọn dưới áp lực" (JVM.md §2 "ZGC pause
gần như bằng 0 bất kể heap lớn cỡ nào" — ngầm định heap ĐỦ RỘNG, không phải bất kỳ kích thước nào).
Xác nhận lại đúng khuyến nghị SẴN CÓ ở JVM.md §3 ("Không dùng `-Xmx` cố định trong container") —
KHÔNG đề xuất đổi cấu hình hiện tại (không set `-Xmx`, để JVM tự chọn) dựa trên số đo này; ngược
lại, số đo này là bằng chứng THẬT ủng hộ giữ nguyên cách làm cũ, không phải lý do để đổi.

**Việc CHƯA làm** (Điều 30 — không suy rộng quá số liệu đang có): chưa tìm được heap cap nào (nếu
có) đủ lớn để vừa bounded vừa không gây pause giây ở N=2000 — có thể cần ≥6-8GB (gần bằng đúng mức
JVM tự chọn ở §5.4) hoặc có thể vấn đề không nằm ở kích thước heap mà ở tốc độ SINH rác lúc burst
connect (churn protobuf/Netty buffer khi 2000 kết nối cùng lúc) — chưa tách bạch được nguyên nhân,
cần lát cắt riêng nếu muốn heap cap nhỏ mà vẫn an toàn.

### 5.8 Tách bạch CPU: sharding hay thread-pool? (2026-07-29, cùng ngày) — follow-up cuối của §5.4

⚠️ **Đo thật, BÁC BỎ giả thuyết nêu ở §5.4** — §5.4 đoán chi phí CPU cao hơn công thức §5.1
(~6.2-7.3 lõi đo được so với 2.77-5.52 lõi dự đoán) có thể do "chi phí bookkeeping riêng của mỗi
kênh (`AreaOfInterestTracker`/training-dummy/NPC) khi sharding tạo ra ~50-125 World song song".
Kiểm chứng: sửa TẠM `MainWorldChannelRegistry.DEFAULT_CAPACITY` 20→2500 (ép CHỈ 1 kênh duy nhất dù
N=1000 — tắt hẳn sharding, không commit, revert ngay sau khi đo — `git diff` sạch), build lại, chạy
N=1000 với cùng máy/JDK/flags như §5.4 (`./gradlew run`, `-XX:+UseZGC`, DB xoá sạch).

**Kết quả — N=1000, KHÔNG sharding (1 World duy nhất):**

| Metric | Có sharding (§5.4, mặc định 20/kênh) | KHÔNG sharding (thử nghiệm này) |
|---|---|---|
| CPU trung bình | ~6.2 lõi | **~8.18 lõi** |
| Tick p99 | 0.06–0.72ms | **48.23–60.82ms** (vượt ngân sách ~3-4×) |
| Lỗi client | 0-149 (SO_BACKLOG, không liên quan) | 96 |

**Sharding RẺ HƠN CPU, không đắt hơn** — ngược hẳn giả thuyết ở §5.4. Kết quả này khớp với chính lý
do sharding được tạo ra ở §5.2/5.3: 1 World lớn phải tính AoI cho toàn bộ 1000 người tụ 1 điểm (gần
O(n²) dù đã có spatial-grid — mọi người vẫn rơi vào CÙNG vài ô lưới), tốn CPU nhiều hơn hẳn so với
chia nhỏ thành ~50 World độc lập mỗi World chỉ tính AoI cho ~20 người. **Không chạy lại N=2000
không-sharding** — §5.2 đã ghi nhận kịch bản này (trước khi sharding tồn tại) sập hẳn ở N=2000 (tick
tối đa 10.76s, lỗi client leo thang không dừng), lặp lại chỉ tốn thời gian mà không thêm thông tin
mới, và số CPU đo được lúc server quá tải/backpressure không đáng tin (Điều 30).

⚠️ **Vậy nguyên nhân CPU cao hơn công thức §5.1 THẬT SỰ là gì?** Vẫn CHƯA biết — thí nghiệm này chỉ
loại trừ được 1 giả thuyết (sharding), không xác nhận được giả thuyết đúng. Ứng viên còn lại, CHƯA
kiểm chứng: (a) toàn bộ observability thêm ở Phase 9 sau khi công thức §5.1 được đo (Micrometer
Timer/Counter/Gauge cho MỌI kênh từ §5.5, JVM GC/Memory/Thread metrics binder, OpenTelemetry tracing
— log thấy tracing liên tục thử kết nối `localhost:4318` (Jaeger) và THẤT BẠI mỗi vài giây vì không
chạy Jaeger lúc đo, mỗi lần thất bại tốn CPU dựng exception/stack trace — tần suất thấy được (~16
lần/phiên đo) có vẻ không đủ giải thích hết chênh lệch nhưng CHƯA đo tách bạch được đóng góp thật);
(b) nội dung gameplay (skill/quest/dungeon/PvP) đã thêm rất nhiều từ Phase 4 tới nay, trong khi công
thức §5.1 đo lúc "chỉ có nội dung này tồn tại trong game" (Phase 9 lát cắt 5, ít tính năng hơn hẳn
hiện tại) — có thể mỗi tick giờ tốn hơn CHỈ vì có nhiều System/component hơn phải chạy qua, không
liên quan gì tới sharding/thread-pool.

**Kết luận**: `WorldTickScheduler` (chủ đề chính của toàn bộ chuỗi lát cắt §5.4-5.8) và sharding đều
KHÔNG phải nguyên nhân CPU cao hơn công thức cũ — ngược lại, cả 2 đều đang giúp giảm chi phí so với
kịch bản không có chúng. Công thức §5.1 vẫn đo dưới điều kiện QUÁ KHÁC hiện tại (ít observability
hơn, ít nội dung game hơn) để so sánh trực tiếp — **không sửa công thức §5.1** (Điều 30, chưa có bằng
chứng đủ tách bạch để biết con số mới nên là bao nhiêu), chỉ ghi nhận: công thức đó đã LỖI THỜI theo
nghĩa rộng hơn những gì §5.2/§5.3 từng nêu (không chỉ vì sharding), cần đo lại từ đầu (2 điểm N mới,
đúng nội dung/observability hiện tại) nếu cần con số capacity-planning đáng tin cho production.

### 5.9 Tracing/OTLP có tốn CPU thừa không? (2026-07-29, cùng ngày) — kiểm chứng ứng viên còn lại ở §5.8

⚠️ **Đo thật, BÁC BỎ nốt ứng viên đầu tiên nêu ở §5.8** — nghi ngờ: `GatewayTracing` liên tục thử
xuất span qua OTLP tới `localhost:4318` (Jaeger) và THẤT BẠI mỗi vài giây khi không chạy Jaeger lúc
đo (thấy trong log §5.4/§5.8), có thể là nguồn CPU thừa (dựng exception + stack trace mỗi lần thất
bại). Kiểm chứng: sửa TẠM `GatewayBootstrap.wireFacades`-lân cận (`new GatewayTracing()` →
`GatewayTracing.disabled()`, dùng đúng `Sampler.alwaysOff()` sẵn có cho test — không tạo network
call OTLP nào), build lại, đo N=1000 (cùng máy/JDK/flags/kịch bản §5.8), rồi **revert ngay** (`git
diff` sạch sau khi đo).

**Kết quả — N=1000, tracing TẮT hẳn (0 lần log thấy `4318`/OTLP, xác nhận đã tắt thật):**

| Metric | Tracing BẬT (§5.4, mặc định) | Tracing TẮT (thử nghiệm này) |
|---|---|---|
| CPU trung bình | ~6.2 lõi | **~6.5 lõi** |
| Tick p99 | 0.06–0.72ms | 0.79ms |
| Lỗi client | 0-149 | 93 |

**Không có khác biệt có ý nghĩa** — 6.5 lõi tắt tracing so với 6.2 lõi bật tracing nằm trong biên độ
nhiễu đo được xuyên suốt cả chuỗi lát cắt này (Điều 30 §9: "cải thiện <10% và trong nhiễu = không
cải thiện" — áp dụng ngược lại ở đây: KHÔNG có sự khác biệt đáng kể để nói tracing tốn CPU). Loại
trừ tracing/OTLP khỏi danh sách nghi phạm.

**Kết luận toàn chuỗi §5.4-5.9**: đã kiểm chứng và loại trừ CẢ 2 giả thuyết cụ thể có thể kiểm tra
được (sharding §5.8, tracing §5.9) cho khoảng cách CPU-so-với-công-thức-§5.1. Ứng viên còn lại
("nội dung gameplay tăng từ Phase 4") khó kiểm chứng bằng cách bật/tắt 1 flag như 2 lần trên — đòi
hỏi so sánh 2 checkout code khác nhau (Phase 4 vs hiện tại) hoặc đo lại công thức §5.1 từ đầu trực
tiếp, không phải việc "tắt 1 công tắc". Dừng điều tra thêm ở đây (Điều 9 — không đào sâu vô hạn 1
câu hỏi phụ); nếu cần công thức capacity-planning đáng tin, hướng đi rõ ràng nhất là đo lại từ đầu
(gợi ý đã ghi ở kết luận §5.8), không phải tiếp tục loại trừ từng giả thuyết nhỏ.

### 5.10 ECS update/tick thật — đóng câu hỏi factual Revisit Trigger ADR-006 (2026-08-02)

✅ **Đo thật lần đầu bằng metric production `kage.ecs.system.duration`** (không phải JMH) — mục đích
DUY NHẤT: trả lời câu hỏi factual mà [ADR-006](../01-architecture/adr/ADR-006-Why-ECS.md) "Revisit
Trigger" đặt ra ("ECS update > 4ms/tick với số entity mục tiêu của 1 map đông người?"), Phase 9 đã
đóng (2026-07-30) nhưng chưa ai thật sự cộng 6 Timer System (`kage.ecs.system.duration`, Phase 9 lát
cắt 9) lại thành 1 con số rồi so ngưỡng — JMH 2026-07-26 (ghi ở ADR-006) chỉ đo proxy
`MovementSystem`, không có AI/Combat/Skill/Boss/StatusEffect thật.

**Máy/điều kiện**: cùng máy mọi lần đo ở §5 (Intel i9-14900HX, 24 lõi thật/32 luồng logic, 32GB RAM,
Windows), JDK GraalVM 21.0.8 chạy `./gradlew run` (KHÔNG `-Xmx`, TLS bật thật dev-cert ADR-027,
tracing OTLP mặc định dù không có Jaeger — cấu hình dev y hệt §5.4 trở đi). Postgres 17 + Redis 8
thật (container standalone, cổng khác mặc định vì máy đang chạy sẵn service khác chiếm 5432/6379 —
không ảnh hưởng số đo). `tools:load-test` **N=500**, ramp 50→500 (bước 50/5s), giữ tải ổn định ~2
phút — **peak run ngắn, KHÔNG phải soak 2 giờ** (Điều 30: ghi rõ đây không phải soak). **0 lỗi
client** suốt lần chạy, `kage_players_online` đỉnh = 500 (khớp đúng N mục tiêu, không hụt như 1 số
lần đo N=2000 trước đây bị burst-connect).

**Cách đo**: 2 lần scrape `/metrics` cách nhau ~85s, CẢ HAI lúc `kage_players_online=500` (giữa cửa
sổ tải ổn định) — lấy **delta** `_sum`/`_count` giữa 2 lần để có mean đúng trong cửa sổ tải, tránh bị
pha loãng bởi thời gian server chạy không tải trước đó. ⚠️ `kage.ecs.system.duration` **không có
percentile** — code (`MicrometerTickMetricsPort`) chỉ gọi `publishPercentiles(0.5, 0.95, 0.99)` cho
`kage.tick.duration`, KHÔNG cho Timer System — mỗi Timer System chỉ phơi `_count`/`_sum`/`_max`, nên
mục này chỉ có **mean** (từ `_sum/_count`) và **max**, không có p95/p99 per-System.

| System | Δsum (85s, 42.810 tick) | Mean/tick |
|---|---|---|
| AiSystem | 0.394126s | 0.00921 ms |
| BossAiSystem | 0.015393s | 0.00036 ms |
| CombatSystem | 0.307498s | 0.00718 ms |
| MovementSystem | 0.426417s | 0.00996 ms |
| SkillSystem | 0.184637s | 0.00431 ms |
| StatusEffectSystem | 0.093353s | 0.00218 ms |
| **Tổng ECS update/tick** | **1.421424s** | **0.03320 ms** |

**So ngưỡng**: 0.0332ms / **4ms** (Revisit Trigger) = **0.83%**. 0.0332ms / **16.6ms** (ngân sách tick
đầy đủ @60Hz, ADR-018) = **0.20%**. Đối chiếu: tổng thời gian tick đầy đủ đo cùng cửa sổ (delta
`kage.tick.duration`, 42.818 tick) = 0.15942 ms/tick trung bình — ECS chiếm ~20.8% tick, nhưng bản
thân tick đã cực nhỏ so với 16.6ms nên tỉ lệ % này không đáng lo. Tick p99 (Micrometer tự tính,
rolling) trong cửa sổ N=500 ổn định: 1.7–2.36ms — khớp `serverTickMs` p99 mà `tools:load-test` tự
báo cáo cùng lúc, cả 2 đều xa dưới ngân sách 16.6ms.

**Kết luận: Revisit Trigger CHƯA kích hoạt.** Số production thật (có đủ AI/Combat/Skill/Boss/
StatusEffect System, không phải proxy JMH) xác nhận lại kết luận JMH 2026-07-26 — ECS ăn chưa tới 1%
ngân sách Trigger. Xem [ADR-006](../01-architecture/adr/ADR-006-Why-ECS.md#ghi-chú--đánh-giá-lại-bằng-production-metric-thật-2026-08-02)
cho phần diễn giải quyết định đầy đủ (không lặp lại ở đây, Điều 13 CLAUDE.md).

⚠️ **Outlier thật, không giấu**: `kage_ecs_system_duration_seconds_max{system="AiSystem"}` đo được
**21.281ms** (cộng dồn từ lúc server khởi động, không phải trong cửa sổ delta 85s ở trên) — tự nó
vượt NGUYÊN ngân sách tick 16.6ms. 1 mẫu đơn lẻ trong 187.569 mẫu — nhiều khả năng JIT chưa warm-up/
GC pause/burst-connect (ramp 50→500 dồn AI System xử lý nhiều entity mới cùng lúc), **chưa điều tra
nguyên nhân** (ngoài phạm vi câu hỏi factual của Trigger, Điều 9). Không đủ để kích hoạt Trigger (đòi
hỏi mức ổn định > 4ms, không phải 1 outlier), nhưng đáng theo dõi nếu lặp lại thường xuyên hơn ở N
lớn hơn hoặc soak dài hơn.

**Việc CHƯA làm** (Điều 30): chỉ đo N=500 bằng metric này (chưa N=1000/2000); chỉ ~2 phút giữ tải
(chưa soak 2 giờ); chưa điều tra outlier AiSystem 21ms. Không chặn kết luận Trigger — N=500 với 0 lỗi
client và tick p99 xa dưới ngân sách đã đủ để trả lời "> 4ms/tick?" là KHÔNG, còn cách xa — nhưng nếu
sau này cần con số ở N/soak lớn hơn, đây là điểm bắt đầu tiếp theo, không phải kết luận cuối cùng.

### 5.11 Cấp phát mỗi khung của client — `FrameAllocationTest` (commit `152a7cf`, 2026-10-03)

✅ **Đo thật lần đầu** luật "không `new` mỗi frame" ([CLAUDE.md §6](../../CLAUDE.md)) phía client —
trước đó chỉ có suy đoán từ đọc code. Không phải JMH, không phải load test: một unit test headless
trong `client-core` (`FrameAllocationTest`) đếm byte bằng
`com.sun.management.ThreadMXBean#getCurrentThreadAllocatedBytes()` — bộ đếm cấp phát của CHÍNH luồng
test, chính xác tới byte, không phụ thuộc GC.

**Cảnh**: 20 người chơi khác, 15 quái, 3 NPC, mọi thực thể đều có model (asset giả thuần dữ liệu qua
`WorldModelRenderer.preloadAsset` — không có GL context). Cứ 3 khung (20Hz AoI ở 60fps) một nửa người
chơi + quái nhận gói di chuyển; phần nạp gói KHÔNG nằm trong cửa sổ đo. **Cách đo**: warm-up
**30.000 khung** (đủ cho C2), rồi **5 vòng × 5.000 khung**, lấy **vòng thấp nhất** chia số khung.

| Đường mỗi khung | Trước `152a7cf` | Sau | Cổng trong test |
|---|---|---|---|
| `RemoteCharacterPresenter.sync` | 2.400 B/khung | **0** | đúng 0 |
| `MonsterPresenter.sync` | 2.520 B/khung | **0** | ≤ 64 B |
| `NpcPresenter.sync` | 320 B/khung | **32** | ≤ 64 B |
| `RemoteCharacterPresenter.tick`, `MonsterPresenter.tick`, model người chơi local (`setPosition`/`follow`) | — | **0** | đúng 0 |
| `CombatHudController` (tick + các lần đọc HUD) | — | **24** | ≤ 64 B |

**Thủ phạm: boxing khoá `Long`** khi tra `HashMap<Long, …>` — `scenesById`/`animationStateById` của
`WorldModelRenderer` và bộ đếm giờ của `RemoteMotionTracker` (~120–170 B mỗi thực thể mỗi khung).
**KHÔNG phải iterator**: escape analysis của C2 đã khử được iterator `HashMap`, nhưng không khử được
boxing. Sửa bằng `com.badlogic.gdx.utils.LongMap` (có sẵn trong libGDX — không thêm thư viện).

⚠️ **Số 0 của các đường còn dùng `HashMap` phụ thuộc C2.** Đo tay lúc làm commit với
`-XX:TieredStopAtLevel=1` (chỉ C1): `MonsterPresenter.sync` = **296 B/khung** (số này không nằm trong
test). Vì vậy test chia hai cổng: đường đã sang `LongMap` không cấp phát theo CẤU TRÚC ⇒ phải **đúng
0**; đường còn phụ thuộc JIT (Monster/Npc sync, HUD) ⇒ **≤ 64 B** (`JIT_DEPENDENT_LIMIT_BYTES`) — vẫn
đủ chặt để bắt lại một lần boxing cho mỗi thực thể.

**Còn lại, CHƯA sửa**: `NpcPresenter.sync` 32 B (iterator `HashMap`) và HUD 24 B (một `Long` của
`targetState()`). Gỡ nốt cần đổi kiểu kho quái/`npcs()` — chạm ~4 lớp — nên đưa lên bảng chờ chủ dự
án ([README dòng 57](../README.md)), không tự làm.

**Không đo được ở đây** (cần GPU thật): `WorldModelRenderer.render`, các `render` vẽ hộp của
`MonsterPresenter`/`NpcPresenter`, `CombatHud` (Stage + font FreeType). Đây cũng chưa phải số
frame time — chỉ là áp lực cấp phát.

---

### 5.12 Một lần eval GraalJS — Engine riêng hay Engine dùng chung (2026-10-17, README dòng 70(c))

**Câu hỏi.** `ScriptSandbox.eval` dựng một `Context` MỚI mỗi lần (không rò trạng thái giữa hai script).
Context không gắn Engine thì GraalJS dựng ngầm một Engine riêng cho nó — tốn bao nhiêu, và gắn vào một
Engine dùng chung có rẻ hơn rõ không?

**Đo.** `engine/engine-scripting/src/jmh/.../ScriptContextBenchmark` (JMH, `./gradlew :engine:engine-scripting:jmh`,
fork 2, warmup 5×1 s, đo 10×1 s, máy dev, JDK 25), script sát thương cỡ script skill thật, cấu hình
Context đúng như `ScriptSandbox.buildContext()` (CONSTRAINED):

| Cách | µs/lần eval |
|---|---|
| Context tự dựng Engine ngầm (cách cũ) | 348 ± 28 |
| Context trên Engine dùng chung (chép tay) | 88 ± 3 |
| `ScriptSandbox.eval` sau khi đổi | 94 ± 2 |

**Kết luận.** Nhanh ~3,7 lần ⇒ đã đổi: mỗi trần câu lệnh một Engine + một `ResourceLimits`, sống cả đời
JVM. Lưu ý jmh: uber jar của plugin làm mất `Multi-Release: true` mà Truffle cần — `engine-scripting`
tự thêm lại vào manifest của `jmhJar`. ⚠️ Con số "5–20 ms mỗi eval" ghi ở README dòng 70(c) trước đó là
đo thô lúc JVM chưa ấm, không phải trạng thái ổn định.

### 5.13 Từng script skill/boss THẬT trên tick (2026-10-23, README dòng 70(b), lô 18)

**Đo.** `engine/engine-scripting/src/jmh/.../ProductionScriptBenchmark` (JMH, `./gradlew :engine:engine-scripting:jmh
-PjmhIncludes=ProductionScriptBenchmark`; `java -jar` thẳng jar JMH không nạp được GraalJS). Chạy đủ 20 script skill +
2 script boss đang ship, qua đúng `ScriptSandbox.eval` (Engine chung, Context mới mỗi lần, trần 10.000 câu lệnh), host
API đúng hình dạng `SkillSystem`/`BossAiSystem`. Máy dev, JDK 25 (JBR), máy yên (không build nào chạy song song),
2 fork × 10 lượt đo.

| Script | µs/lần (trung bình ± sai số 99,9%) |
|---|---|
| 20 script skill | **88,0 – 94,3** (mỗi script ± 1,4–2,8) |
| `boss/sample_boss` | 93,1 ± 2,3 |
| `boss/blood_sand_enforcer` | 130,6 ± **82,1** (dao động lớn — một fork lệch; nội dung script dài nhất) |

**Đọc.** Mọi script cùng một bậc với phép đo "Engine chung" ở §5.12 (94 µs) ⇒ chi phí **gần như toàn bộ là dựng
Context**, không phải nội dung script — script skill hiện tại chỉ là `return { ... }` vài phép tính. Chốt chống treo
500 ms gấp **~5.000 lần** số đo. Ngân sách tick 16,6 ms chứa được ~180 lần cast trong MỘT tick trước khi script một
mình ăn hết tick.

**Đề xuất (chưa đổi trần — chủ dự án chọn "chỉ đo + báo số")**: nếu đặt ngân sách riêng cho cast trên tick thì
**5 ms** (ví dụ ở SCRIPTING.md) vẫn gấp ~50 lần số đo xấu nhất lúc yên, nhưng bài học 70(d) cho thấy trần thời gian
thật bị GC/máy tải cắt nhầm script HỢP LỆ — trần 5 ms sẽ từ chối cast hợp lệ dưới GC dừng. Hai hướng rẻ hơn, đáng
cân nhắc trước khi hạ trần: (a) giữ 500 ms chống treo, thêm metric thời gian eval theo mã script để PHÁT HIỆN script
chậm thay vì cắt; (b) giảm chi phí dựng Context (cache Source, tái dùng Context theo World) — đo lại sau đổi.

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
4. ✅ **(2026-08-02, sau khi Phase 9 đã đóng)** — đóng khoảng trống "chưa ai cộng
   `kage.ecs.system.duration` thành 1 con số thật rồi so Trigger": xem §5.10. Xác nhận lại bằng
   production metric thật (N=500, có đủ AI/Combat/Skill/Boss/StatusEffect System) đúng kết luận JMH
   — Trigger CHƯA kích hoạt (0.83% ngân sách 4ms).
5. ✅ **(2026-10-03)** — đo cấp phát mỗi khung phía client lần đầu (`FrameAllocationTest`), xem
   §5.11: ba đường sync từ 2.400/2.520/320 B/khung xuống 0/0/32. Còn NPC 32 B + HUD 24 B chờ chủ dự án.
