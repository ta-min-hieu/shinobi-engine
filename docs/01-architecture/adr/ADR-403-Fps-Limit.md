# ADR-403: Giới hạn FPS

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Giới hạn FPS" trong lượt hỏi lô 59 (AskUserQuestion; mô tả kèm: /gioihanfps 30|60|144|0: đặt trần khung hình, nhớ trên máy). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-283](ADR-283-Fps-Counter.md) |

---

## Decision

**D1:** `/gioihanfps <30|60|90|120|144|0>` (`/fpslimit`) gọi `Gdx.graphics.setForegroundFPS` (0 = không giới hạn); chỉ nhận các mức trong danh sách. Nhớ trên máy, áp lại khi vào world. Mặc định giữ như trình khởi chạy (144).

**D2:** không đổi protocol; mô phỏng chạy tick 60 Hz phía server nên trần FPS client không đổi gameplay.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, `setForegroundFPS` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 59): `ClientToolsCoordinator`.
2. ✅ Test: `Lot59ClientTest.acceptsOnlyListedFpsCaps`.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
