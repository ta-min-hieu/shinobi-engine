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
