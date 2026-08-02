# JVM.md

**GC, JVM flags, profiling**

| | |
|---|---|
| Version | 1.0.0 |
| Status | 🟡 Draft (từng phần) — GC choice (§2): ✅ [ADR-019](../01-architecture/adr/ADR-019-GC-Choice.md) **Accepted** (2026-07-28, chủ dự án đã xác nhận — KHÔNG còn chờ). JVM flags (§3): tập flag GC/bộ nhớ/JFR/GC-log cốt lõi đã chạy thật trong soak test 2h14 (2026-07-26) và đang dùng thật trong `server.Dockerfile` production, nhưng còn khác biệt đường dẫn log chưa đồng bộ giữa 2 nơi và bản thân việc đóng gói Docker chưa có soak test riêng — xem ghi chú chi tiết ở §3. Ngưỡng đo GC ở §7 vẫn là đề xuất chưa verify. |
| Liên quan | [PERFORMANCE.md](PERFORMANCE.md), [JAVA-GUIDELINES.md](../02-coding-standards/JAVA-GUIDELINES.md) |

---

## 1. Vấn đề cốt lõi

Java được tối ưu cho **throughput**. Game cần **latency ổn định**.

```
Web service: GC pause 200ms → 1 request chậm → không ai chết
Game server: GC pause 200ms → 12 tick bị bỏ (@60Hz) → toàn bộ người chơi giật
```

→ **Chọn GC là quyết định tối ưu latency, không phải throughput.**

---

## 2. So sánh GC

| GC | Pause | Throughput | Bộ nhớ | Phù hợp Kage? |
|---|---|---|---|---|
| **Serial** | Rất dài | Thấp | Nhỏ | ❌ Không |
| **Parallel** | Dài | **Cao nhất** | Vừa | ❌ Pause quá dài cho game |
| **G1** (mặc định) | Vừa (~50–200ms) | Tốt | Vừa | 🟡 Ứng viên — có `MaxGCPauseMillis` |
| **ZGC** | ✅ **< 1ms** | Hơi thấp hơn G1 | ⚠️ Cao hơn | 🟢 **Ứng viên hàng đầu** |
| **Shenandoah** | ✅ < 10ms | Hơi thấp hơn G1 | Cao hơn | 🟢 Ứng viên |

### Phân tích

**ZGC** — pause gần như bằng 0 bất kể heap lớn cỡ nào. Đánh đổi: tốn RAM hơn, throughput thấp hơn G1 ~5–15%.

→ **Với game server, đánh đổi này đáng**: thà chậm 10% đều đặn còn hơn nhanh nhưng giật 200ms mỗi phút.

**G1** — mặc định, quen thuộc, `-XX:MaxGCPauseMillis=X` là *mục tiêu*, **không phải đảm bảo**. G1 sẽ cố nhưng không hứa.

**Shenandoah** — tương tự ZGC; ZGC được Oracle đầu tư mạnh hơn.

### Khuyến nghị: **ZGC** — ✅ xác nhận bằng số liệu thật

✅ **[ADR-019](../01-architecture/adr/ADR-019-GC-Choice.md)** (2026-07-26) — soak test 2h14 thật, 80
client đồng thời: STW pause tối đa đo được **0.052ms** (không phải ước lượng), heap Live giữ
nguyên 39M suốt 34 chu kỳ GC liên tiếp (không leak). Không còn là "khuyến nghị dựa trên đặc tính".

---

## 3. JVM Flags đề xuất

✅ **Flags GC/bộ nhớ/chẩn đoán dưới đây đã chạy thật** trong soak test 2h14 (2026-07-26,
[ADR-019](../01-architecture/adr/ADR-019-GC-Choice.md)) — đúng tập flag này (trừ
`-XX:+ZGenerational`, đã bỏ, xem ghi chú ngay dưới) — kết quả STW pause tối đa **0.052ms**, không
leak suốt 34 chu kỳ GC. `deploy/docker/server.Dockerfile` (dòng ~155, viết ở Phase 10) dùng lại
đúng tập giá trị GC/bộ nhớ/JFR/GC-log này cho production hiện tại.

⚠️ **Nhưng lưu ý một khoảng trống thật**: soak test 2026-07-26 chạy **TRƯỚC KHI
`server.Dockerfile` tồn tại** (Dockerfile chỉ được viết ở Phase 10, sau khi Phase 9 đóng
2026-07-30 — xem `ROADMAP.md` Phase 9 lát cắt 10, liệt `server.Dockerfile` vào mục "CHƯA làm").
Nghĩa là các GIÁ TRỊ flag đã được đo thật, nhưng KHÔNG phải qua chính cơ chế đóng gói Docker (biến
môi trường `JAVA_OPTS` nối vào start script Gradle sinh ra, container cgroup limit tương tác với
`MaxRAMPercentage`, user `kage` non-root, runtime jlink) — bản thân pipeline đóng gói đó chưa có
soak test riêng của nó. Coi các flag dưới đây là "giá trị đã verify, đóng gói Docker thì chưa".

```bash
# --- GC (ADR-019 ✅ Accepted — ZGC, giá trị đã verify qua soak test 2h14) ---
-XX:+UseZGC
# ⚠️ PHÁT HIỆN THẬT (2026-07-26, verify trên JDK 25): -XX:+ZGenerational KHÔNG còn hợp lệ — JDK in
# cảnh báo "Ignoring option ZGenerational; support was removed in 24.0". Từ JDK 24, ZGC thế hệ là
# chế độ DUY NHẤT khi bật -XX:+UseZGC, không cần (và không còn) flag riêng. KHÔNG thêm flag này.

# --- Bộ nhớ (✅ verify qua soak test + đang dùng thật trong server.Dockerfile) ---
-XX:MaxRAMPercentage=75.0       # Container: chừa chỗ cho native (DOCKER.md §7)
-XX:+ExitOnOutOfMemoryError     # OOM → chết hẳn, orchestrator restart

# --- Chẩn đoán (BẬT Ở PRODUCTION, ✅ verify qua soak test + đang dùng thật trong server.Dockerfile) ---
-XX:+FlightRecorder
-XX:StartFlightRecording=disk=true,maxsize=1g,maxage=24h,dumponexit=true
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=5,filesize=10m
# ⚠️ KHÁC BIỆT CHƯA ĐỒNG BỘ với server.Dockerfile thật (deploy/docker/server.Dockerfile dòng
# ~155-158): Dockerfile dùng /tmp/gc.log và /tmp/kage.jfr (container chạy user `kage` non-root,
# không chắc có quyền ghi /var/log) thay vì /var/log/gc.log ở trên, và thêm tham số
# filename=/tmp/kage.jfr vào StartFlightRecording (JVM.md không có tham số filename). Đường dẫn ở
# đây chỉ là ví dụ minh hoạ, KHÔNG khớp giá trị thật đang chạy production — ghi nhận là khoảng
# trống, không tự sửa 1 trong 2 file, cần chủ dự án chọn chuẩn.

# --- Native memory (khi điều tra leak, ⚠️ CHƯA verify — không bật mặc định, kể cả trong server.Dockerfile) ---
-XX:NativeMemoryTracking=summary
```

⚠️ **Không dùng `-Xmx` cố định trong container** — image chạy ở nhiều cấu hình RAM ([DOCKER.md §7](../07-infrastructure/DOCKER.md)).

⚠️ **JFR bật ở production là đúng** — overhead ~1%, và khi có sự cố thì đã có dữ liệu. Không có JFR = "chờ nó xảy ra lần nữa rồi bật lên xem".

⚠️ **`server.Dockerfile` còn thêm `-Dio.netty.leakDetection.level=simple`** ở production — không
liệt ở đây vì đó là system property của Netty, không phải flag JVM/GC; đã tài liệu hoá riêng ở
[§8](#8-native-memory) "Netty leak detector".

---

## 4. Flags **KHÔNG** dùng

| Flag | Vì sao không |
|---|---|
| `-XX:+UseParallelGC` | Pause quá dài |
| `-XX:+AggressiveOpts` | Đã bỏ; không có ý nghĩa |
| `-XX:+UseBiasedLocking` | Đã bỏ từ Java 15 |
| Copy flags từ StackOverflow | ⚠️ **Nguy hiểm nhất** — flags của người khác cho workload khác |
| `-Xmx` cố định trong container | §3 |

⚠️ **Cargo cult JVM tuning là có thật.** Nhiều "JVM flags tối ưu" trên mạng là từ Java 8, cho web service, và có hại cho ta. **Chỉ thêm flag khi biết nó giải quyết vấn đề đã đo được.**

---

## 5. JFR (Java Flight Recorder)

Công cụ profiling **tốt nhất và rẻ nhất** cho JVM.

| Ưu | Chi tiết |
|---|---|
| Overhead ~1% | Bật được ở production |
| Có sẵn trong JDK | Không thêm dependency |
| Ghi liên tục | Sự cố xảy ra → đã có dữ liệu |

**Xem gì trong JFR:**

| Sự kiện | Cho biết |
|---|---|
| GC pause | Pause bao lâu, bao nhiêu lần |
| Allocation | Ai tạo rác nhiều nhất |
| Hot method | CPU đi đâu |
| Thread contention | Lock nào bị tranh chấp |
| Native memory | Leak ngoài heap |

```bash
jcmd <pid> JFR.dump name=recording filename=dump.jfr
# Mở bằng JDK Mission Control
```

---

## 6. Async Profiler

Bổ sung cho JFR khi cần **flame graph** chi tiết hơn.

| | JFR | Async Profiler |
|---|---|---|
| Overhead | ~1% | ~1–3% |
| Có sẵn | ✅ | ❌ Cài thêm |
| Flame graph | Hạn chế | ✅ Xuất sắc |
| Native + Java stack | Hạn chế | ✅ Cả hai |
| Safepoint bias | ⚠️ Có | ✅ Không |

⚠️ **Safepoint bias**: profiler dựa trên safepoint (như một số profiler cũ) chỉ lấy mẫu tại safepoint → **bỏ sót hot loop không có safepoint** → chỉ sai bottleneck. Async Profiler không bị.

**Dùng khi**: JFR chỉ ra "chậm ở đâu đó" nhưng cần flame graph để thấy chính xác.

---

## 7. Đo GC

| Metric | Ngưỡng đề xuất (⚠️ chưa verify) |
|---|---|
| GC pause p99 | < 10ms (nếu ZGC) |
| GC pause max | < 50ms |
| Tần suất major GC | Hiếm |
| Heap sau major GC | Không tăng đều (tăng đều = leak) |

⚠️ **Heap tăng đều sau mỗi major GC = memory leak.** Đây là tín hiệu leak đáng tin nhất.

Metrics ở production: [OBSERVABILITY.md §3.4](../07-infrastructure/OBSERVABILITY.md).

---

## 8. Native Memory

⚠️ JVM heap **không phải** toàn bộ bộ nhớ. Kage có nhiều native memory:

| Nguồn | Ghi chú |
|---|---|
| **Netty direct ByteBuf** | ⚠️ Không được GC quản — refcount thủ công |
| **LWJGL/OpenGL** (client) | Texture, buffer |
| Thread stack | ~1MB/thread |
| Metaspace | Class metadata |

→ Vì vậy `MaxRAMPercentage=75`, không 100%.

**Điều tra native leak:**
```bash
-XX:NativeMemoryTracking=summary
jcmd <pid> VM.native_memory summary
```

⚠️ **Netty leak detector — bật ở dev:**
```bash
-Dio.netty.leakDetection.level=paranoid   # dev
-Dio.netty.leakDetection.level=simple     # production
```

Quên `ByteBuf.release()` là lỗi phổ biến nhất khi dùng Netty ([ADR-003](../01-architecture/adr/ADR-003-Why-Netty.md)).

---

## 9. JVM trong Container

Xem [DOCKER.md §7](../07-infrastructure/DOCKER.md).

| Bẫy | Sửa |
|---|---|
| JVM không thấy giới hạn container | `MaxRAMPercentage` (JVM hiện đại tự nhận container limit, nhưng vẫn nên set tường minh) |
| CPU limit → JVM tính sai số core | `-XX:ActiveProcessorCount=N` nếu cần |
| OOM killer giết container | Chừa chỗ native (§8) |

---

## 10. Client JVM

Client khác server:

| | Server | Client |
|---|---|---|
| GC | ZGC ([ADR-019](../01-architecture/adr/ADR-019-GC-Choice.md) ✅) | ⚠️ **Không kiểm soát được** trên Android |
| Heap | Lớn | Nhỏ (mobile) |
| JFR | ✅ | Chỉ khi debug |

⚠️ **Android không dùng HotSpot** — dùng **ART**, GC hoàn toàn khác, **không có ZGC/G1**. Mọi thứ về GC ở tài liệu này **không áp dụng cho Android**.

→ Trên Android, cách duy nhất là **giảm allocation** ([JAVA-GUIDELINES.md §4](../02-coding-standards/JAVA-GUIDELINES.md)).

---

## Technical Decisions

| # | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| 1 | **ZGC** cho server | Pause đo thật 0.052ms max (soak test 2h14, 80 client); heap Live không tăng suốt 34 chu kỳ GC | ✅ [ADR-019](../01-architecture/adr/ADR-019-GC-Choice.md) — Accepted |
| 2 | **JFR bật ở production** | Overhead 1%; có dữ liệu khi sự cố | Accepted |
| 3 | `MaxRAMPercentage=75`, **không `-Xmx` cố định** | Chừa chỗ native; image chạy nhiều cấu hình | Accepted |
| 4 | `ExitOnOutOfMemoryError` | Chết hẳn > sống dở | Accepted |
| 5 | **Không copy JVM flags từ internet** | Cargo cult; flags của workload khác | Accepted |
| 6 | Chỉ thêm flag khi giải quyết vấn đề **đã đo** | Điều 30 | Accepted |
| 7 | Netty leak detector: `paranoid` ở dev | Quên `release()` là lỗi phổ biến nhất | Accepted |
| 8 | **Android dùng ART — mọi thứ về GC ở đây không áp dụng** | Trung thực về giới hạn | Accepted |
| 9 | Flags §3 (GC/bộ nhớ/JFR/GC-log cốt lõi) **đã verify** qua soak test 2h14 + đang dùng thật trong `server.Dockerfile` | Soak test 2h14 chạy đúng tập flag này; đóng gói Docker viết sau, dùng lại giá trị | 🟡 Giá trị Accepted, đóng gói Docker + đường dẫn log chưa đồng bộ chưa có soak test riêng |
| 10 | `-XX:NativeMemoryTracking=summary` **chưa verify**, không bật mặc định | Chỉ dùng khi điều tra leak, có overhead | ⏳ |

## Tài liệu liên quan

- [PERFORMANCE.md](PERFORMANCE.md) — ngân sách
- [BENCHMARK.md](BENCHMARK.md) — đo lúc dev
- [OBSERVABILITY.md](../07-infrastructure/OBSERVABILITY.md) — đo lúc chạy
- [JAVA-GUIDELINES.md](../02-coding-standards/JAVA-GUIDELINES.md) — giảm allocation
- [DOCKER.md §7](../07-infrastructure/DOCKER.md) — JVM trong container

## Việc cần làm tiếp theo

1. ✅ Phase 0: bật JFR + GC log ngay — đã dùng thật lúc soak test 2026-07-26.
2. ✅ Phase 9: đo ZGC **trên workload thật** (soak 2h14, 80 client) → [ADR-019](../01-architecture/adr/ADR-019-GC-Choice.md) Accepted (2026-07-28). Chưa đo G1 thực nghiệm trên chính dự án (xem Revisit Trigger ADR-019) — quyết định dựa trên số liệu ZGC thật + đặc tính G1 đã biết rộng rãi.
3. ✅ Phase 2: bật Netty leak detector `paranoid` ở dev (§8).
4. ✅ Chủ dự án đã xác nhận ADR-019 Accepted (2026-07-28).
5. Đồng bộ đường dẫn `gc.log`/JFR filename giữa JVM.md §3 (`/var/log/gc.log`, không có tham số
   `filename` cho JFR) và `deploy/docker/server.Dockerfile` thật (`/tmp/gc.log`, `/tmp/kage.jfr`) —
   chủ dự án chọn 1 chuẩn, hiện đang khác nhau thật (xem §3).
6. Cân nhắc 1 soak test riêng chạy ĐÚNG qua `server.Dockerfile`/container thật (không chỉ giá trị
   flag) — soak test gốc dùng cho ADR-019 chạy trước khi Dockerfile tồn tại (Phase 9 lát cắt 3,
   2026-07-26, so với Dockerfile viết ở Phase 10 sau 2026-07-30), nên phần đóng gói Docker
   (`JAVA_OPTS`, container cgroup, user non-root, jlink runtime) chưa tự nó có số liệu.
