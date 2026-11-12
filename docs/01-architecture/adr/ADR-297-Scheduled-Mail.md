# ADR-297: Thư hẹn giờ gửi

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Thư hẹn giờ gửi" trong lượt hỏi lô 46 (AskUserQuestion; mô tả kèm: soạn thư thường (không kèm đồ) nhưng server giữ lại và gửi đúng giờ). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-058](ADR-058-Send-Mail-Idempotency.md) · [ADR-212](ADR-212-Stranger-Mail-Block.md) |

---

## Decision

**D1 — Bảng `scheduled_mail`** (V173): người gửi, người nhận, tiêu đề, nội dung, `send_at`. Hẹn 1–10080 phút (7 ngày),
tối đa 5 thư chờ mỗi người, chỉ chữ (không vàng, không đồ — tránh phải ký quỹ tài sản suốt 7 ngày).

**D2 — Gửi:** `ScheduledMailJob` (mỗi 60 giây) lấy thư tới giờ, kiểm **chặn thư người lạ LÚC GỬI**, rồi gửi qua ĐÚNG
`MailFacade.sendMail` với người gửi thật (tem thư trừ lúc gửi), khoá chống trùng = UUID tên của `scheduled-mail:<id>` ⇒
server sập giữa chừng không gửi đôi. Gửi được hay không đều xoá dòng (không thử lại vô hạn); không gửi được thì ghi log.

**D3 — Dây:** `ScheduleMailCommand{recipient_name, subject, body, delay_minutes}` / `ScheduleMailResponse{outcome, pending}`
**921/922**. Client `/thuhengio <phút> <tên> <tiêu đề> | <nội dung>`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `scheduled_mail`; job 60 giây qua đường thư thường; 921/922; `/thuhengio` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 46): `ScheduledMailPort`/`Facade`, `PostgresScheduledMailAdapter`, `ScheduledMailJob`, `Lot46Commands.scheduleMail`, `ScheduledMailCoordinator`.
2. ✅ Test: `Lot46IntegrationTest.schedulesListsDueAndDeletes`, `Lot46ServerTest`, `Lot46ClientTest.parsesScheduledMail`.
3. ⏳ Kiểm sống gameProbe.
