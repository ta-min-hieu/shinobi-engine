# ADR-290: Thư đã gửi

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Thư đã gửi" trong lượt hỏi lô 45 (AskUserQuestion; mô tả kèm: /thudagui: 20 thư gần nhất mình đã gửi, đã được lĩnh chưa). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-216](ADR-216-Delete-Finished-Mail.md) · [ADR-275](ADR-275-Mail-Search.md) |

---

## Decision

**D1 — Đọc `mail` theo `sender_id`.** `SentMailPort`/`SentMailFacade` (feature-mail) + `PostgresSentMailAdapter`: tên
người nhận (đã xoá ⇒ "?"), tiêu đề, linh thạch, số món đính kèm, đã lĩnh chưa, thời gian. **V172** thêm chỉ mục
`mail (sender_id, id DESC) WHERE sender_id IS NOT NULL`.

**D2 — Dây:** `ListSentMailRequest{}` / `ListSentMailResponse{repeated SentMailEntry}` **919/920** (dải Mail). Client
`/thudagui` (hoặc `/sentmail`). **Thư người nhận đã xoá thì không còn trong danh sách** — không lưu bản sao phía người
gửi (đổi schema lớn hơn, chủ dự án chưa yêu cầu).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | `mail.sender_id` + V172 chỉ mục; 919/920; `/thudagui` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 45): `SentMailPort`/`Facade`, `PostgresSentMailAdapter`, `Lot45Commands.sentMail`, `SentMailCoordinator`.
2. ✅ Test: `Lot45IntegrationTest.readsSentMailWithRecipientAndAttachments`.
3. ✅ Kiểm sống gameProbe 2026-11-12: `--sendMail=BaoDo01:1` ⇒ SEND_MAIL_OUTCOME_OK (thư 33); `sentMail` ⇒ `BaoDo01:probe(chưa lĩnh)`. Đã xoá thư thử, trả vàng.
