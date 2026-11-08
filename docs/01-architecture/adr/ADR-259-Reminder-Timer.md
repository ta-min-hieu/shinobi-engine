# ADR-259: Hẹn giờ nhắc

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Hẹn giờ nhắc" trong lượt hỏi lô 41 (AskUserQuestion; mô tả kèm: /hengio <phút> <nội dung>: đến giờ thì hiện dòng nhắc trong chat). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-178](ADR-178-Away-Auto-Reply.md) |

---

## Decision

**D1 — Thuần client.** `/hengio <phút> <nội dung>` (hoặc `/remind`), 1–600 phút, tối đa 5 lời nhắc; `/hengio` liệt kê,
`/hengio huy` huỷ hết. Hẹn bằng `Timer` của libGDX (chạy việc trên luồng vẽ, cùng cách `ExpBuffCoordinator`); đến giờ
hiện một dòng hệ thống trong chat. Chỉ sống trong phiên — thoát game / đổi màn là huỷ (`reset`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | `Timer` libGDX, tối đa 5 lời nhắc 1–600 phút, mất khi thoát; `/hengio` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 41): `ReminderTimerCoordinator`.
2. ✅ Test: `Lot41bClientTest`.
3. ✅ Kiểm sống: thuần client, không có client đồ hoạ trong môi trường kiểm sống — kiểm bằng test đơn vị; không opcode, không migration, server không đổi.
