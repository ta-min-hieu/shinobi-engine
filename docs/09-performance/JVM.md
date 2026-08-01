# JVM.md

**GC, JVM flags, profiling**

| | |
|---|---|
| Version | 1.0.0 |
| Status | Draft — ✅ **ADR-019 đề xuất ZGC** (2026-07-26, số liệu thật từ soak test — chờ chủ dự án xác nhận Accepted) |
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

⚠️ **CHƯA VERIFY** — là điểm khởi đầu để đo, không phải cấu hình cuối.

```bash
# --- GC (ADR-019 ✅ — ZGC) ---
-XX:+UseZGC
# ⚠️ PHÁT HIỆN THẬT (2026-07-26, verify trên JDK 25): -XX:+ZGenerational KHÔNG còn hợp lệ — JDK in
# cảnh báo "Ignoring option ZGenerational; support was removed in 24.0". Từ JDK 24, ZGC thế hệ là
# chế độ DUY NHẤT khi bật -XX:+UseZGC, không cần (và không còn) flag riêng. KHÔNG thêm flag này.

# --- Bộ nhớ ---
-XX:MaxRAMPercentage=75.0       # Container: chừa chỗ cho native (DOCKER.md §7)
-XX:+ExitOnOutOfMemoryError     # OOM → chết hẳn, orchestrator restart

# --- Chẩn đoán (BẬT Ở PRODUCTION) ---
-XX:+FlightRecorder
-XX:StartFlightRecording=disk=true,maxsize=1g,maxage=24h,dumponexit=true
-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=5,filesize=10m

# --- Native memory (khi điều tra leak) ---
-XX:NativeMemoryTracking=summary
```

⚠️ **Không dùng `-Xmx` cố định trong container** — image chạy ở nhiều cấu hình RAM ([DOCKER.md §7](../07-infrastructure/DOCKER.md)).

⚠️ **JFR bật ở production là đúng** — overhead ~1%, và khi có sự cố thì đã có dữ liệu. Không có JFR = "chờ nó xảy ra lần nữa rồi bật lên xem".

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
| 9 | Flags §3 **chưa verify** | Điểm khởi đầu để đo | ⏳ |

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
4. Chủ dự án xác nhận ADR-019 Accepted.
