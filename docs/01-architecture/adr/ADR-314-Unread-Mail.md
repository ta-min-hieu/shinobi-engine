# ADR-314: Thư chưa đọc

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Thư chưa đọc" trong lượt hỏi lô 48 (AskUserQuestion; mô tả kèm: đánh dấu thư đã đọc/chưa đọc; vào game báo "Bạn có N thư chưa đọc"). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-217](ADR-217-Pin-Mail.md) |

---

## Decision

**D1 — Cột `mail.read_at`** (V174; NULL = chưa đọc). Thư có từ trước V174 được đặt đã đọc (người chơi đã thấy chúng),
chỉ mục một phần cho thư chưa đọc.

**D2 — Dây:** `MailView.unread = 10` (chỉ THÊM; mặc định false = đã đọc nên client cũ không đổi); `MarkMailReadCommand
{mail_id}` / `MarkMailReadResponse{mail_id, unread}` **923/924**. Client: thư chưa đọc có tiền tố "(mới) " (cùng khuôn
dấu ghim ADR-217); mở thư ⇒ gửi lệnh đánh dấu rồi hỏi lại danh sách. Vào world còn thư chưa đọc ⇒ một dòng chat.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cột `read_at`; `MailView.unread`; 923/924; báo lúc vào world | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 48): `MailReadPort`/`Facade`, `PostgresMailReadAdapter`, `Lot48Commands.markRead`/`onEnteredWorld`, `MailController.UNREAD_MARK`, `MailReadCoordinator`.
2. ✅ Test: `Lot48IntegrationTest.tracksUnreadMailAndMarksRead`, `Lot48ClientTest.unreadMailGetsMarkAndForwardStripsIt`.
3. ⏳ Kiểm sống gameProbe.
