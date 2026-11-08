# ADR-224: Lịch sự kiện tuần

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Lịch sự kiện tuần" trong lượt hỏi lô 37 (AskUserQuestion; mô tả kèm: xem lịch cố định trong tuần — boss thế giới, giải đấu, mốc làm mới, sự kiện x2). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-081](ADR-081-World-Boss.md) (boss thế giới) · [ADR-200](ADR-200-Reset-Timers.md) (mốc làm mới) |

---

## Decision

**D1 — Bốn mốc, đều do server biết**: lần xuất hiện kế tiếp của boss thế giới (hoặc "đang diễn ra" khi boss còn sống —
lịch boss tính từ lúc con trước chết, nên chỉ có mốc KẾ TIẾP, không có lịch cả tuần), làm mới ngày, làm mới tuần (cùng
nguồn ADR-200), luận võ đại hội (giờ bắt đầu kỳ hiện tại / kế tiếp). Sớm nhất trước.

**D2 — Lệch so với mô tả**: "sự kiện x2 đang bật" đã có dòng HUD riêng (ADR-067 báo khi bật / tắt), lịch không lặp lại.

**D3 — Protocol**: `GetEventCalendarRequest{}` / `GetEventCalendarResponse{server_now, repeated CalendarEntry{kind,
at_epoch_seconds, active}}` 263/264.

**D4 — Client**: `/lich` (hoặc `/calendar`) in từng mốc với giờ theo máy và thời gian còn lại.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Boss kế tiếp + làm mới ngày / tuần + luận võ; 263/264; `/lich` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 37): `WorldBossTimes` (lịch boss ghi ra cho lệnh đọc), `Lot37Commands.calendar`,
   `EventCalendarCoordinator`.
2. ✅ Test: `PersonalStatsTextTest` (dòng lịch thường và "đang diễn ra").
3. ✅ Kiểm sống gameProbe 2026-11-08: server chạy với boss xuất hiện sau 320 giây ⇒ `calendar` trả `WORLD_BOSS+5ph
   TOURNAMENT+646ph DAILY_RESET+886ph WEEKLY_RESET+886ph` (Chủ nhật: làm mới tuần trùng làm mới ngày lúc 00:00 thứ Hai).
