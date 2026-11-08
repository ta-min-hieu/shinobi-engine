# ADR-269: Đo ping /ping

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Đo ping /ping" trong lượt hỏi lô 42–43 (AskUserQuestion; mô tả kèm: Đo độ trễ khứ hồi tới server, in ms). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-027](ADR-027-TLS-Transport-Security.md) (đường truyền) · heartbeat `PingCommand`/`PongResponse` |

---

## Decision

**D1 — Dùng lại gói heartbeat (opcode 1/2), không opcode mới.** `/ping` gửi MỘT `PingCommand` mang dấu thời gian riêng,
chờ `PongResponse` echo đúng dấu đó (pong của heartbeat định kỳ bị bỏ qua), rồi in `N ms (tốt < 100 / trung bình < 250 /
cao)`. Dùng chung bộ lọc `ConnectionCoordinator.plausibleRoundTrip`. Không giành gói — HUD độ trễ vẫn cập nhật như cũ.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | dùng lại PING/PONG; ngưỡng 100/250 ms | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 42): `PingCoordinator`.
2. ✅ Test: `Lot42ClientTest.pingQualityBands`.
