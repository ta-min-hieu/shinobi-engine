# ADR-003: Chọn Netty làm nền tảng Network I/O phía Server

| | |
|---|---|
| Status | Accepted |
| Date | 2026-07-16 |
| Deciders | Chủ dự án |
| Related | [ADR-001](ADR-001-Why-Java25.md), [Hiến pháp Điều 23, 31](../../00-governance/KAGE-CONSTITUTION.md), [NETWORK.md](../../05-server/NETWORK.md) |

---

## Context

Game server MMORPG cần: hàng nghìn kết nối TCP đồng thời, độ trễ thấp và ổn định, và **kiểm soát chặt allocation** — vì GC pause ảnh hưởng trực tiếp tới combat realtime (Điều 31).

## Decision

Dùng **Netty 4.1.x (LTS branch)** cho toàn bộ tầng network. `server-gateway` là **module duy nhất** được import `io.netty.*` (Điều 23).

## Alternatives Considered

| Phương án | Ưu điểm | Nhược điểm | Vì sao chọn / không chọn |
|---|---|---|---|
| **Netty 4.1** | Zero-copy; buffer pooling → ít GC pressure; framing/codec sẵn có; production-proven ở quy mô rất lớn; cộng đồng lớn | API phức tạp; dễ block EventLoop nếu không hiểu mô hình | **Chọn** |
| Netty 5 | Thiết kế mới hơn | Chưa đủ adoption; lịch sử Netty 5 từng bị huỷ một lần | Không chọn — chờ trưởng thành, đánh giá lại sau |
| Java NIO thuần | Không thêm dependency | Phải tự viết framing, buffer pooling, backpressure — tức là tự viết lại Netty, tệ hơn | Không chọn — vi phạm Điều 9 |
| **Virtual Thread + blocking socket** | Code đơn giản hơn nhiều; Java 25 hỗ trợ sẵn | Chưa production-proven cho game server realtime ở quy mô lớn; mỗi kết nối 1 thread ảo vẫn tốn stack; khó kiểm soát buffer reuse | Không chọn **cho tầng network chính** — nhưng dùng cho blocking I/O phụ trợ (DB call, gọi API ngoài) |
| gRPC | Codegen sẵn, streaming | HTTP/2 overhead không cần cho game packet; khó kiểm soát byte-level; thiên về request-response, không hợp Event push | Không chọn cho game packet — có thể cân nhắc cho admin/internal API |

## Consequences

**Tích cực:**
- Kiểm soát được allocation ở tầng network — điều kiện tiên quyết để giữ GC pause thấp.
- `server-world` và `feature-*` **không biết Netty tồn tại** → thay transport không đụng gameplay ([ARCHITECTURE.md §4](../ARCHITECTURE.md)).
- Có sẵn `LengthFieldBasedFrameDecoder` → không tự viết framing.

**Đánh đổi phải chấp nhận:**
- **Đội ngũ phải hiểu mô hình EventLoop.** Block EventLoop bằng logic đồng bộ nặng (VD: query DB) sẽ làm chết toàn bộ kết nối trên loop đó. Đây là lỗi kinh điển — phải có quy tắc rõ trong [NETWORK.md](../../05-server/NETWORK.md).
- `ByteBuf` cần quản lý refcount thủ công → rò rỉ bộ nhớ nếu quên release. Phải bật leak detector ở môi trường dev.
- Đường cong học tập dốc hơn socket thuần.

**Module bị ảnh hưởng:** `server-gateway` (duy nhất).

## Revisit Trigger

- Benchmark Phase 9 ([ROADMAP.md](../../11-process/ROADMAP.md)) cho thấy Netty không đạt latency mục tiêu → điều tra nguyên nhân trước khi kết luận là lỗi Netty.
- Virtual Thread I/O model trở nên production-proven rộng rãi cho game server realtime (có case study ở quy mô tương đương) → mở ADR đánh giá lại.
- Netty 5 đạt adoption rộng và có lộ trình migration rõ ràng.
