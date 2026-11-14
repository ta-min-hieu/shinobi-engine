# ADR-388: Hẹn giờ tự thoát game

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Hẹn giờ tự thoát game" trong lượt hỏi lô 57 (AskUserQuestion; mô tả kèm: /henthoat <phút>: tự thoát game, báo trước 1 phút). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-259](ADR-259-Reminder-Timer.md) · [ADR-174](ADR-174-Rest-Reminder.md) |

---

## Decision

**D1:** `/henthoat <1-600>` (`/autoexit`) hẹn một lần (hẹn mới thay hẹn cũ); còn 60 giây báo một dòng; tới giờ gọi `Gdx.app.exit()` như đóng cửa sổ. `/henthoat huy` huỷ, `/henthoat` hỏi còn bao lâu. Dùng `Timer` libGDX (chạy trên luồng vẽ) như hẹn giờ nhắc (ADR-259); chỉ sống trong phiên.

**D2:** không đổi protocol; server thấy như người chơi tự thoát.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, `Timer` + `Gdx.app.exit()` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 57): `AutoExitCoordinator`.
2. ✅ Test: `Lot57ClientTest.parsesAutoExitMinutes`.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
