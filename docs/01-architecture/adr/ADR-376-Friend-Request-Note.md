# ADR-376: Lời nhắn kèm lời mời kết bạn

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Lời nhắn kèm lời mời kết bạn" trong lượt hỏi lô 56 (AskUserQuestion; mô tả kèm: /ketban <tên> | <lời nhắn>: người được mời thấy lời nhắn trong danh sách lời mời). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-211](ADR-211-Friend-Suggestions.md) · [ADR-060](ADR-060-Daily-Check-In.md) |

---

## Decision

**D1 — Lưu:** bảng MỚI `friend_request_note(requester_id, addressee_id, message ≤ 60, created_at)` (V181), khoá cặp; chỉ ghi khi lời mời gửi THÀNH CÔNG, gửi lại thì đè. Làm sạch phía server: một dòng, bỏ ký tự điều khiển (Postgres từ chối NUL), cắt về 60 ký tự, có từ cấm ⇒ bỏ lời nhắn (lời mời vẫn đi). Không xoá khi lời mời được nhận/từ chối — dòng nhỏ, chỉ đọc khi lời mời còn chờ.

**D2 — Dây:** CHỈ THÊM `SendFriendRequestCommand.message = 2` và `PendingFriendRequestView.message = 4`, không opcode mới. Client `/ketban Tên | lời nhắn`; cửa sổ Bạn bè hiện `Tên — "lời nhắn"`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `friend_request_note` (V181), field 2/4 chỉ thêm | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 56): `FriendRequestNoteFacade`, `PostgresFriendRequestNoteAdapter`, `Lot56Commands.rememberRequestNote/requestNote`, `FriendCoordinator.withNote`.
2. ✅ Test: `Lot56IntegrationTest.requestNoteUpsertsPerPair`, `Lot56ServerTest.cleansRequestNote`, `Lot56ClientTest.pendingRowShowsNote`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14 (gameProbe `friendNote:PhuTanThu/chào tân thủ`): `SEND_FRIEND_REQUEST_OUTCOME_OK`, dòng `friend_request_note(2,3,'chào tân thủ')` có trong DB (đã dọn lại lời mời + lời nhắn).
