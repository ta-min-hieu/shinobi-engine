# ADR-260: Đồng hồ giờ server

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Đồng hồ giờ server" trong lượt hỏi lô 41 (AskUserQuestion; mô tả kèm: /gio xem giờ server và giờ máy mình (dùng cho lịch sự kiện, reset)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-200](ADR-200-Reset-Timers.md) (đếm ngược reset — dùng lại gói) · [ADR-224](ADR-224-Event-Calendar.md) (lịch sự kiện) |

---

## Decision

**D1 — Dùng lại gói ADR-200**, không opcode mới: `/gio` (hoặc `/time`) gửi `GetResetTimersRequest` sẵn có; gói trả về có
`server_now_epoch_seconds`. Client in giờ server theo **giờ Việt Nam** (múi của mốc reset `DailyReset.ZONE`), giờ máy
mình và **độ lệch** giữa hai đồng hồ (giây). Bảng đếm ngược reset của ADR-200 cũng hiện theo vì cùng gói — chủ ý, đó
là thứ người chơi hay hỏi cùng lúc.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Dùng lại 255/256; in giờ server (UTC+7), giờ máy, độ lệch; `/gio` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 41): `ServerClockCoordinator`.
2. ✅ Test: `Lot41bClientTest`.
3. ✅ Kiểm sống: thuần client, không có client đồ hoạ trong môi trường kiểm sống — kiểm bằng test đơn vị; không opcode, không migration, server không đổi.
