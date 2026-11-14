# ADR-400: Báo đã đọc thư

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Báo đã đọc thư" trong lượt hỏi lô 59 (AskUserQuestion; mô tả kèm: Người nhận mở thư mình gửi ⇒ người gửi (đang online) nhận một dòng báo). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-314](ADR-314-Unread-Mail.md) · [ADR-290](ADR-290-Sent-Mail.md) |

---

## Decision

**D1:** khi người nhận mở một thư lần ĐẦU (ADR-314 chuyển `read_at` từ NULL), nếu thư có người gửi là người chơi (không phải thư hệ thống) và người gửi đang online ⇒ người gửi nhận một dòng hệ thống trong chat "<tên> đã đọc thư «tiêu đề»". Đọc hết một lượt (ADR-359) KHÔNG báo — tránh dội hàng loạt. Người gửi offline thì bỏ qua (không lưu).

**D2:** không opcode mới: dòng báo đi bằng `ChatMessageEvent` hệ thống sẵn có (như ADR-370); thêm port nhỏ đọc (người gửi, tiêu đề) của thư.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Dòng hệ thống cho người gửi online, chỉ lần đọc đầu | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 59): `MailReceiptPort/Facade`, `PostgresMailReceiptAdapter`, `Lot59Commands.notifyRead` gọi từ `Lot48Commands.markRead`.
2. ✅ Test: `Lot59IntegrationTest.receiptOnlyForPlayerMail`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14: BaoDo01 (đang online) gửi thư cho ĐạoHữu01, ĐạoHữu01 `readMail` ⇒ BaoDo01 nhận dòng hệ thống "ĐạoHữu01 đã đọc thư «probe»" (đã xoá thư + trả lại linh thạch sau khi kiểm).
