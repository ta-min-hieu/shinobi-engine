# ADR-312: Thư gửi mọi bạn bè

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Thư gửi mọi bạn bè" trong lượt hỏi lô 48 (AskUserQuestion; mô tả kèm: /thubanbe <tiêu đề> | <nội dung>: gửi một thư chữ cho toàn bộ bạn bè cùng lúc (có giới hạn mỗi ngày)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-235](ADR-235-Guild-Mail.md) |

---

## Decision

**D1 — Cùng khuôn thư toàn bang (ADR-235).** Mỗi bạn bè nhận MỘT thư thường (đúng đường gửi thư: tem thư, kiểm nội
dung, hộp đầy), không vàng không đồ; ai online được báo thư mới. **1 đợt mỗi người mỗi ngày** — đếm trên audit
`FRIEND_MAIL` (V174 chỉ mục một phần), không bảng mới.

**D2 — Dây:** `SendFriendsMailCommand{subject, body}` / `SendFriendsMailResponse{outcome, recipients}` **925/926**. Client
`/thubanbe` (hoặc `/friendmail`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Mỗi bạn một thư thường; 1 đợt/ngày qua audit; 925/926 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 48): `FriendMailQuotaPort`/`FriendMailFacade`, `PostgresFriendMailQuotaAdapter`, `Lot48Commands.sendFriendsMail`, `FriendsMailCoordinator`.
2. ✅ Test: `Lot48IntegrationTest.countsFriendMailBatchesSinceDayStart`.
3. ✅ Kiểm sống gameProbe 2026-11-13: `friendsMail` ⇒ OK gửi=1 (BaoDo01); lần hai ⇒ DAILY_LIMIT. Đã xoá thư + audit thử, trả vàng.
