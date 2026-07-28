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
   trên; vẫn là khởi điểm hợp lý, chưa phải số đã tune kỹ (chưa có DB pool pending metric qua
   Micrometer để đo lại có còn backlog ở N lớn hơn không — mục "DB pool pending" §5 vẫn ⏳).
2. Chưa xác nhận lại với chủ dự án cách diễn giải "25 khu mặc định" (lazy vs pre-warm) nêu trên.
3. `SPECIAL_CAPACITY=50` chưa có nơi nào trong game gán/dùng (chưa có khái niệm "map đặc biệt") —
   hook đã sẵn, chờ nội dung game thật cần tới.

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
