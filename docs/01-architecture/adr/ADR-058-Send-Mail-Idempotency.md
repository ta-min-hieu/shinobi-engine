# ADR-058: `SendMailCommand` mang `idempotency_key` — gửi lại khi mạng chậm không trừ vàng/đồ hai lần

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-18) — chủ dự án chọn dòng "74(c): idempotency cho thư" trong lượt hỏi lô 15 (AskUserQuestion; mô tả kèm: SendMail mang idempotency_key, thêm field). AI soạn văn bản sau khi được chọn. |
| Date | 2026-10-18 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [docs/README.md dòng 74(c)](../../README.md) · [ADR-025](ADR-025-Idempotency.md) (idempotency cho Command có side-effect) |

---

## Context

ADR-025 bắt mọi Command có side-effect mang `idempotency_key`. Rà 2026-10-10 (dòng 74(c)) thấy
`SendMailCommand` lọt lưới: nó trừ vàng ký quỹ + đồ đính kèm của người gửi, nhưng không có khoá — client
tự gửi lại khi mạng chậm là trừ hai lần và tạo hai thư. `ClaimMailCommand` thì đã có khoá từ 2026-08.

## Decision

1. `mail.proto`: `SendMailCommand.idempotency_key = 6` (string) và
   `SEND_MAIL_OUTCOME_DUPLICATE_REQUEST = 9`. Chỉ thêm — `protocolVersion` không tăng.
2. Server ghi khoá bằng `IdempotencyPort` sẵn có của feature-mail, TRONG transaction gửi, NGAY SAU khoá
   túi đồ người gửi (thứ tự khoá DATABASE.md §5.1) và TRƯỚC khi trừ vàng/đồ. Khoá trùng ⇒ rollback trọn,
   trả `DUPLICATE_REQUEST`, không thư nào được tạo.
3. **Khoá rỗng = client cũ**: gửi như trước (không chặn) — để client chưa cập nhật vẫn gửi thư được. Khoá
   không rỗng phải đúng định dạng (`rejectMalformedIdempotencyKey` của gateway, như mọi lệnh khác).
4. Thư hệ thống (server tự gửi, `senderId = null`) không qua đường này — không có khoá.
5. Client sinh UUID mỗi lần BẤM GỬI, giữ nguyên khi tự gửi lại cùng một lá thư; `DUPLICATE_REQUEST` được
   coi là ĐÃ GỬI (cùng luật ADR-025 với lĩnh thưởng).

## Alternatives Considered

| Phương án | Vì sao không |
|---|---|
| Bắt buộc khoá, từ chối khoá rỗng | Client cũ không gửi thư được nữa cho tới khi cập nhật |
| Chống trùng theo nội dung thư trong N giây | Gửi hai lá giống nhau có chủ đích là hợp lệ; đoán sai thì nuốt thư thật |

## Consequences

- Hết đường trừ vàng/đồ hai lần khi mạng chậm. Một dòng `idempotency_key` mỗi lá thư người chơi gửi.

## Revisit Trigger

- Mọi client đã cập nhật ⇒ cân nhắc bắt buộc khoá (bỏ nhánh khoá rỗng).

## Technical Decisions

| # | Quyết định |
|---|---|
| 1 | Field 6 + outcome 9, chỉ thêm |
| 2 | Ghi khoá sau khoá túi đồ, trước trừ tài sản |
| 3 | Khoá rỗng = hành vi cũ |

## ADR cần tạo

Không.

## Tài liệu liên quan

[feature-mail](../../../server/server-features/feature-mail/) · [NETWORK.md §6](../../05-server/NETWORK.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành (2026-10-18) — outcome thật là **9** (bản nháp ghi 10; 9 còn trống). `SendMailIdempotencyIntegrationTest` (Postgres thật: cùng khoá ⇒ một thư, vàng trừ một lần; khoá rỗng vẫn gửi). Client giữ khoá tới khi có trả lời (`MailCoordinator#sendKey`).
