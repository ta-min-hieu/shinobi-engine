# ADR-018: Tick rate 60Hz

| | |
|---|---|
| Status | ✅ Accepted |
| Date | 2026-07-18 |
| Deciders | Chủ dự án |
| Related | [ENGINE.md §4](../../03-engine/ENGINE.md), [SERVER.md §3–4](../../05-server/SERVER.md), [PERFORMANCE.md §2](../../09-performance/PERFORMANCE.md) |

---

## Context

Cần chốt tick rate **trước Phase 1** vì fixed timestep ([ENGINE.md §4](../../03-engine/ENGINE.md)) cần con số cụ thể, và mọi ngân sách hiệu năng tính theo nó. Server và client dùng **cùng** tick rate để đồng bộ.

## Decision

**60Hz** — mỗi tick 16.6ms.

## Alternatives Considered

| Phương án | Ngân sách/tick | Ưu điểm | Nhược điểm | Đánh giá |
|---|---|---|---|---|
| **60Hz** | 16.6ms | Mượt nhất; input phản hồi nhanh; combat/animation cảm giác tốt | ⚠️ Tốn ~2× CPU + băng thông so với 30Hz; ngân sách mỗi tick khắt khe (ECS/AI/physics phải gọn) | **Chọn** |
| 30Hz | 33ms | Đủ mượt cho 2D MMORPG; tiết kiệm 50% CPU/băng thông; phổ biến trong thể loại | Kém mượt hơn 60Hz với combat nhanh | Không chọn |
| 20Hz | 50ms | Nhẹ server nhất | Combat cảm giác trễ | Không chọn |

## Consequences

**Tích cực:**
- Trải nghiệm mượt nhất, đặc biệt hợp nếu ưu tiên desktop và combat tốc độ cao.

**Đánh đổi phải chấp nhận — QUAN TRỌNG:**
- ⚠️ **Ngân sách mỗi tick chỉ còn 16.6ms** (nửa so với 30Hz). Toàn bộ ECS + physics + AI + state sync + event + dự phòng phải nằm trong 16.6ms. Các bảng ngân sách ở [SERVER.md §11](../../05-server/SERVER.md), [ECS.md §9](../../03-engine/ECS.md), [GAMEPLAY.md §13](../../05-server/GAMEPLAY.md), [PERFORMANCE.md §2](../../09-performance/PERFORMANCE.md) đã cập nhật theo 16.6ms.
- **Băng thông ~2× so với 30Hz.** → [AoI + delta compression + tần suất theo khoảng cách](../../05-server/SERVER.md) ([SERVER.md §6](../../05-server/SERVER.md)) trở nên **bắt buộc sớm hơn**, không phải tối ưu về sau. Cân nhắc: **snapshot rate có thể thấp hơn tick rate** (VD tick 60Hz nhưng gửi snapshot 20–30Hz cho entity xa) để cắt băng thông mà vẫn giữ logic mượt.
- **Rủi ro Ashley ECS**: ngân sách ECS ~4ms/tick @60Hz là khá chặt. Điều này **tăng khả năng kích hoạt Revisit Trigger của [ADR-006](ADR-006-Why-ECS.md)** (thay Ashley) sớm hơn. Phải theo dõi ở benchmark Phase 9.

**Module bị ảnh hưởng:** `engine-core` (game loop), `server-world`, `server-gateway` (băng thông), mọi tài liệu có bảng ngân sách tick.

## Revisit Trigger

- Nếu benchmark/load test [Phase 9](../../11-process/ROADMAP.md) cho thấy server không giữ được tick p99 trong 16.6ms dưới tải mục tiêu, **hoặc** băng thông 60Hz quá tốn → **hạ xuống 30Hz**. Vì kiến trúc là server-authoritative + fixed timestep, đổi tick rate về sau chủ yếu là đổi hằng số + **tính lại ngân sách** (không phá gameplay), nhưng phải re-tune và test lại kỹ.
- Cân nhắc phương án lai sớm nếu băng thông là vấn đề: **giữ logic 60Hz, hạ snapshot rate** thay vì hạ hẳn tick rate.
