# ADR-262: Gọi lại tin đã gõ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Gọi lại tin đã gõ" trong lượt hỏi lô 41 (AskUserQuestion; mô tả kèm: phím ↑/↓ trong ô chat gọi lại những dòng vừa gửi). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-190](ADR-190-Key-Rebinding.md) (đổi phím — ↑/↓ chỉ có nghĩa khi đang gõ chat, không vào bảng đổi phím) |

---

## Decision

**D1 — Thuần client.** Ô chat nhớ tối đa 30 dòng vừa gửi trong phiên (bỏ dòng rỗng và dòng trùng dòng liền trước).
Đang gõ: **↑** gọi dòng trước, **↓** dòng sau; qua dòng mới nhất ⇒ ô trống. Gửi dòng mới ⇒ con trỏ về cuối. Phím chỉ
được bắt khi ô nhập đang mở, nên không đụng phím di chuyển ngoài chat.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 30 dòng trong phiên; ↑/↓ khi đang gõ; `SentLineHistory` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 41): `SentLineHistory`, `ChatPanel`.
2. ✅ Test: `SentLineHistoryTest`.
3. ✅ Kiểm sống: thuần client, không có client đồ hoạ trong môi trường kiểm sống — kiểm bằng test đơn vị; không opcode, không migration, server không đổi.
