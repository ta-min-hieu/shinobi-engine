# ADR-019: Dùng ZGC cho server production

| | |
|---|---|
| Status | ⏳ **Proposed** — số liệu thật đã có (soak test 2026-07-26), chờ chủ dự án xác nhận Accepted |
| Date | 2026-07-26 |
| Deciders | Chủ dự án (qua kết quả soak test — xem "Ghi chú" cuối file) |
| Related | [JVM.md](../../09-performance/JVM.md), [BENCHMARK.md §5](../../09-performance/BENCHMARK.md), [PERFORMANCE.md](../../09-performance/PERFORMANCE.md), [ROADMAP.md — Phase 9](../../11-process/ROADMAP.md) |

---

## Context

JVM.md đã khuyến nghị ZGC từ trước ("pause gần như bằng 0 bất kể heap lớn cỡ nào") nhưng tự đánh
dấu ⏳ "chờ số liệu Phase 9 mới chốt được — đây là khuyến nghị dựa trên đặc tính, không phải kết
luận từ số liệu của dự án này". ROADMAP.md liệt "Chốt ADR-019 (GC)" là hạng mục 🔴 chặn Phase 9,
cần "đo G1 vs ZGC trên workload thật".

**Soak test thật đã chạy** (Phase 9 lát cắt 3, 2026-07-26): `server-gateway` thật (không mock/fake
Facade) + `tools:load-test` (Phase 9 lát cắt 2) — 80 client đồng thời (move + auto-attack + chat +
ping) giữ liên tục **~2 giờ 14 phút** (10:10:45→13:24 giờ hệ thống, +0700), JVM chạy với
`-XX:+UseZGC` + JFR + GC log bật đầy đủ theo đúng flags đề xuất ở JVM.md §3 (trừ `-XX:+ZGenerational`
— xem "Phát hiện phụ" bên dưới).

## Decision

Dùng **ZGC** cho server production.

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Đánh giá |
|---|---|---|---|
| **ZGC** | Đo thật trên soak test 2h14 này: **STW pause tối đa 0.052ms** (52 micro giây) trên TOÀN BỘ 38 chu kỳ GC major/proactive trong suốt 2h+ tải 80 client — không phải ước lượng, là số liệu `-Xlog:gc*` thật. Tick p99 (Micrometer `kage.tick.duration`, World chính) dao động 2-10ms trong lúc GC chạy song song — luôn dưới xa ngân sách 16.6ms (60Hz) | Tốn RAM hơn G1 (heap ceiling 24290M ở máy 32GB RAM/`MaxRAMPercentage=75`, dù used thực tế chỉ ~100-150M sau mỗi lần collect) | **Chọn** |
| G1 (mặc định JVM nếu không chỉ định) | Quen thuộc, không cần flag đặc biệt | **Chưa đo thật trên dự án này** — JVM.md §2 ghi nhận đặc tính chung (pause ~50-200ms, `MaxGCPauseMillis` chỉ là mục tiêu không phải đảm bảo). Với game server 60Hz (ngân sách 16.6ms/tick), pause 50-200ms = 3-12 tick bị bỏ mỗi lần GC — chênh lệch bậc-độ-lớn (order of magnitude) so với ZGC đo được (0.052ms) đủ rõ để không cần chạy so sánh thực nghiệm riêng | Không chọn |
| Shenandoah | Tương tự ZGC (pause thấp) | Oracle đầu tư ZGC mạnh hơn (JVM.md §2); không có lý do kỹ thuật để chọn thay ZGC ở dự án này | Không chọn |

## Consequences

**Tích cực:**
- Xác nhận bằng số liệu thật (không còn "khuyến nghị dựa trên đặc tính") — pause 0.052ms nghĩa là GC gần như KHÔNG BAO GIỜ là nguyên nhân tick bị bỏ ở tải 80 người chơi đồng thời.
- Soak test cũng xác nhận **không leak**: heap "Live" (dữ liệu còn sống sau mỗi lần collect) giữ NGUYÊN 39M từ chu kỳ GC(5) tới GC(37) — 34 lần collect liên tiếp, ~1h33 phút liên tục, không tăng dù tải liên tục.

**Đánh đổi phải chấp nhận:**
- RAM ceiling cao hơn G1 (`MaxRAMPercentage=75` → 24GB trên máy 32GB) — chấp nhận được vì server production sẽ có RAM riêng, không chia sẻ với máy dev nhiều dự án khác như lúc test.
- Chưa có số liệu G1 THẬT của chính dự án này để so sánh trực tiếp (xem Revisit Trigger) — quyết định dựa trên: số liệu ZGC thật + đặc tính G1 đã biết rộng rãi (JVM.md §2), không phải "chưa đo gì cả".

**Module bị ảnh hưởng:** [DOCKER.md §7](../../07-infrastructure/DOCKER.md) (GC flags cho container), [JVM.md §3](../../09-performance/JVM.md) (JVM flags đề xuất — cập nhật bỏ `-XX:+ZGenerational`, xem "Phát hiện phụ").

## Phát hiện phụ (không phải quyết định GC, nhưng phát hiện thật lúc verify)

`-XX:+ZGenerational` trong JVM.md §3 **không còn hợp lệ** — JDK cảnh báo thẳng lúc khởi động:
`"Ignoring option ZGenerational; support was removed in 24.0"`. Từ JDK 24, ZGC thế hệ (generational)
là chế độ DUY NHẤT khi bật `-XX:+UseZGC`, không cần (và không còn) flag riêng. JVM.md §3 cần sửa bỏ
dòng flag này — verify bằng `java -XX:+PrintFlagsFinal -version` trên chính JDK 25 dự án dùng
(`loom-ea-25-loom+1-11`, dùng thật để soak test).

## Ghi chú — quy trình dẫn tới đề xuất này

Chủ dự án yêu cầu "chạy rồi theo dõi nền" (2026-07-26). Trong lúc thực thi, AI (Claude) phát hiện
VÀ SỬA 2 bug thật trong `tools:load-test` (không phải trong server) trước khi có được số liệu sạch:
1. Lần chạy đầu: thiếu backoff khi lỗi → busy-loop, 20 triệu lỗi/5 phút — sửa bằng cách chuyển
   `Thread.sleep` throttle vào `finally`.
2. Lần chạy hai: tần suất chat giả lập (20%/cycle) vượt `RateLimiter` World chat (5 lần/10s) →
   server ngắt kết nối ~1 client/phút đúng theo thiết kế chống spam, nhưng client bị ngắt lại
   không rút lui — sửa bằng cách giảm tần suất chat (20→3%) + thêm ngưỡng "bỏ cuộc sau 5 lỗi liên
   tiếp".

Server tự nó **không có bug nào bị phát hiện** trong suốt quá trình — cả 2 lần sự cố đều ở phía
công cụ đo, không phải phía đo đạc. Lần chạy thứ ba (sau khi sửa cả 2 bug) là lần cho số liệu sạch
dùng trong ADR này, chạy liên tục ~2h14 không restart server giữa chừng (giữ nguyên lịch sử GC/heap
để soak test có ý nghĩa thật).

**AI không tự đặt Status = Accepted** (adr/README.md quy tắc #5) — cần chủ dự án xác nhận.

## Revisit Trigger

- Nếu sau này có lý do nghi ngờ số liệu (vd. workload thay đổi hẳn — nhiều map hơn, nhiều hệ thống
  combat hơn) → chạy lại soak test tương tự, so sánh với baseline 0.052ms này.
- Nếu muốn có số liệu G1 THẬT của chính dự án để so sánh trực tiếp (không chỉ dựa JVM.md §2 mô tả
  chung) → chạy thêm 1 soak test riêng với G1 (bỏ `-XX:+UseZGC`), so sánh trực tiếp cùng kịch bản
  tải 80 client 2h.
- Nếu RAM production bị giới hạn chặt (không đủ dư cho ZGC ceiling cao) → cân nhắc lại G1.
