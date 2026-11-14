# ADR-359: Đánh dấu tất cả thư đã đọc

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Đánh dấu tất cả thư đã đọc" trong lượt hỏi lô 54 (AskUserQuestion; mô tả kèm: /dochetthu: mọi thư chưa đọc trong hộp chuyển thành đã đọc một lần (hiện chỉ đánh dấu từng thư)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-314](ADR-314-Unread-Mail.md) |

---

## Decision

**D1 — Một câu UPDATE** trên cột `mail.read_at` có sẵn (ADR-314): mọi thư của mình đang `read_at IS NULL` ⇒ đặt mốc hiện tại; trả số thư vừa đổi. Không thêm bảng, không đổi thư đã đọc.

**D2 — Dây:** `MarkAllMailReadCommand{}` / `MarkAllMailReadResponse{marked}` **931/932**. Client `/dochetthu` (`/readall`) báo số thư và hỏi lại hộp thư để bỏ nhãn "(mới)".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | UPDATE `read_at` mọi thư chưa đọc; 931/932 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 54): `MailReadPort.markAllRead`, `MailReadFacade.markAllRead`, `PostgresMailReadAdapter`, `Lot54Commands.markAllMailRead`, `ProfileVisitsCoordinator`.
2. ✅ Test: `Lot54IntegrationTest.markAllReadCountsOnlyUnread`.
3. LIVE359
