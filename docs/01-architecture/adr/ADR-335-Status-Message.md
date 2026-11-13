# ADR-335: Lời nhắn trạng thái

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Lời nhắn trạng thái" trong lượt hỏi lô 51 (AskUserQuestion; mô tả kèm: đặt một dòng trạng thái ngắn (≤60 ký tự) hiện cạnh tên trong danh sách bạn bè của người khác). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-173](ADR-173-Friend-Notes.md) · [ADR-192](ADR-192-Last-Online.md) |

---

## Decision

**D1 — Lưu:** bảng `status_message(character_id, message VARCHAR(60))` (V177); không có dòng = không đặt. Kiểm như chat: ≤ 60 ký tự, không ký tự điều khiển, không từ cấm.

**D2 — Hiện:** `FriendView` thêm `status_message = 8` (CHỈ THÊM field); danh sách bạn bè ghép "tên (ghi chú) — lời nhắn". Không đẩy sự kiện khi đổi: người xem thấy ở lần mở/làm mới danh sách.

**D3 — Dây:** `SetStatusMessageCommand{query_only, message}` / `SetStatusMessageResponse{outcome, message}` **1157/1158**. Client `/trangthai [chữ|xoa]` (`/status`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `status_message` (V177); `FriendView.status_message = 8`; 1157/1158 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 51): `StatusMessagePort`/`Facade` (feature-friend), `PostgresStatusMessageAdapter`, `Lot51Commands.setStatusMessage`/`statusesOf`, `GatewayChannelHandler.friendStatuses`, `StatusMessageCoordinator`, `FriendController.displayNameOf`.
2. ✅ Test: `Lot51IntegrationTest.statusMessagesSetReadAndClear`, `Lot51ServerTest.validatesText`, `Lot51ClientTest.friendNameShowsStatus`.
3. ✅ Kiểm sống gameProbe 2026-11-13: `status:?` ⇒ ''; đặt "Bế quan luyện đan" ⇒ OK; `listFriends` của BaoDo01 ⇒ ĐạoHữu01 status='Bế quan luyện đan'. Đã xoá dòng thử.
