# ADR-411: Sự kiện kế tiếp trên HUD

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Sự kiện kế tiếp trên HUD" trong lượt hỏi lô 60 (AskUserQuestion; mô tả kèm: /sukienhud: dòng HUD đếm ngược tới sự kiện gần nhất lấy từ lịch sự kiện). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-224](ADR-224-Event-Calendar.md) · [ADR-396](ADR-396-Coordinates-Hud.md) |

---

## Decision

**D1:** khi bật, client hỏi lịch sự kiện (ADR-224) ngay và mỗi 60 giây; dòng HUD "Sự kiện kế tiếp H:MM:SS" đếm tới mốc chưa diễn ra sớm nhất (lệch đồng hồ máy được bù theo `server_now`). `/lich` vẫn in lịch như cũ: lớp lịch nay chỉ in khi chính người chơi hỏi, gói HUD tự hỏi thì im.

**D2:** `/sukienhud` (`/nexteventhud`), nhớ trên máy, mặc định tắt. Dùng dòng số chung ADR-396. Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, hỏi lịch 60 giây/lần khi bật | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 60): `HudStatsCoordinator` (dòng `NEXT_EVENT`), `EventCalendarCoordinator` (cờ chỉ in khi hỏi).
2. ✅ Test: kiểm bằng mắt.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
