# ADR-200: Đếm ngược mốc làm mới

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Đếm ngược reset" trong lượt hỏi lô 34 (AskUserQuestion; mô tả kèm: hiện còn bao lâu tới lúc làm mới nhiệm vụ ngày / tuần, điểm danh, bế quan). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-055](ADR-055-Daily-Quests.md) · [ADR-060](ADR-060-Daily-Check-In.md) · [ADR-154](ADR-154-Weekly-Tasks.md) |

---

## Decision

**D1 — Hai mốc**: mọi thứ hằng ngày của server đổi ngày lúc **00:00 giờ Việt Nam** (`DailyReset.ZONE`, cùng múi giờ mọi
facade hằng ngày đang dùng); hằng tuần bắt đầu **00:00 thứ Hai** giờ Việt Nam (`WeeklyTaskRules.weekStart`). Bế quan
không có mốc làm mới (là quỹ phút offline, ADR-095) nên không nằm trong đây.

**D2 — Server tính, client trừ**: `GetResetTimersRequest` / `GetResetTimersResponse{server_now, next_daily,
next_weekly}` 255/256 — client lấy hiệu với `server_now`, không dùng đồng hồ máy (lệch giờ máy không làm sai). Tính ở
`ResetSchedule` (gateway, không chạm DB).

**D3 — Client**: lệnh chat `/lammoi` (hoặc `/reset`) in một dòng "Làm mới hằng ngày … sau 3 giờ 12 phút; hằng tuần …
sau 2 ngày 5 giờ" (hai đơn vị lớn nhất).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Mốc 00:00 giờ VN / thứ Hai, 255/256 mang mốc + giờ server, `/lammoi` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 34): `ResetSchedule`, `Lot33Commands.resetTimers`, `ResetTimersCoordinator`, `Lot34Windows`.
2. ✅ Test: `ResetScheduleTest` (ranh ngày / tuần theo UTC+7, kể cả đúng thứ Hai), `ResetTimersCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-07 (bước `resetTimers`, thứ Bảy 23:08 giờ VN): `now=16:08:52Z`, mốc ngày
   `2026-11-07T17:00Z` (= 00:00 Chủ Nhật giờ VN), mốc tuần `2026-11-08T17:00Z` (= 00:00 thứ Hai) — đúng.
