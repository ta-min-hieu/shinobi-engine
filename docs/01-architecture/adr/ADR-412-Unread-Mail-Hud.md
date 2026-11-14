# ADR-412: Thư chưa đọc trên HUD

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Thư chưa đọc trên HUD" trong lượt hỏi lô 60 (AskUserQuestion; mô tả kèm: /thuhud: dòng HUD hiện số thư chưa đọc, tự cập nhật). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-314](ADR-314-Unread-Mail.md) · [ADR-359](ADR-359-Mark-All-Mail-Read.md) |

---

## Decision

**D1:** số ban đầu từ danh sách thư (hỏi một lần khi bật dòng); sau đó +1 mỗi `NewMailEvent`, lấy đúng số server trả trong `MarkMailReadResponse.unread`, về 0 sau "đọc hết" (ADR-359). Chưa biết số thì để trống.

**D2:** `/thuhud` (`/mailhud`), nhớ trên máy, mặc định tắt. Không đổi protocol.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Client thuần, đếm từ các gói thư sẵn có | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 60): `HudStatsCoordinator` (dòng `UNREAD_MAIL`, `countUnread`).
2. ✅ Test: `Lot60ClientTest.countsUnreadMail`.
3. Client thuần — kiểm bằng test + compile, không có đường server để kiểm sống.
