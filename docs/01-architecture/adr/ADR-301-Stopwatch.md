# ADR-301: Đồng hồ bấm giờ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Đồng hồ bấm giờ" trong lượt hỏi lô 46 (AskUserQuestion; mô tả kèm: /bamgio bắt đầu/dừng, hiện thời gian đã trôi). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-259](ADR-259-Reminder-Timer.md) |

---

## Decision

**D1 — Thuần client.** `/bamgio` (hoặc `/stopwatch`): lần đầu bắt đầu, lần sau dừng và in thời gian (`M:SS.t` hoặc
`H:MM:SS.t`); `/bamgio xem` xem mà không dừng. Giờ máy người chơi, chỉ sống trong phiên.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | `/bamgio [xem]` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 46): `StopwatchCoordinator`.
2. ✅ Test: `Lot46ClientTest.stopwatchFormatsElapsedTime`.
3. ⏳ Thuần client — probe headless không chạy lệnh gạch chéo/không vẽ UI; phủ bằng test đơn vị + biên dịch, cần kiểm tay trên client thật.
