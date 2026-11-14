# ADR-362: Chỉ bạn bè được soi

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Chỉ bạn bè được soi" trong lượt hỏi lô 54 (AskUserQuestion; mô tả kèm: thêm chế độ soi trang bị thứ ba: chỉ bạn bè xem được (ngoài 'mọi người' và 'ẩn')). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-171](ADR-171-Inspect-Player.md) |

---

## Decision

**D1 — Chế độ:** bảng `inspect_friends_only(character_id)` (V180) — có dòng = bật. Công tắc "ẩn" cũ (ADR-171) vẫn thắng: ẩn ⇒ không ai soi được. Bật chỉ-bạn-bè ⇒ người không phải bạn soi nhận kết quả HIDDEN (chỉ còn tên), như khi bị ẩn; bạn bè và chính mình soi bình thường.

**D2 — Kiểm ở server** ngay sau khi dựng kết quả soi, trước khi ghi lịch sử (ADR-361), tính lượt thích, ký hiệu bang và báo người bị soi.

**D3 — Dây:** `SetInspectFriendsOnlyCommand{query_only, enabled}` / `SetInspectFriendsOnlyResponse{enabled}` **295/296**. Client `/chobanbesoi [bat|tat]` (`/friendsinspect`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `inspect_friends_only` (V180); người lạ soi ⇒ HIDDEN; 295/296 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 54): `ProfileVisitPort.friendsOnly`/`setFriendsOnly`, `Lot54Commands.guardInspect`/`setInspectFriendsOnly`, `ProfileVisitsCoordinator`.
2. ✅ Test: `Lot54IntegrationTest.inspectLogKeepsNewestAndFriendsOnlyToggles`.
3. ✅ Kiểm sống gameProbe 2026-11-14: `friendsOnly:on` ⇒ true; PhuTanThu (không phải bạn) soi ĐạoHữu01 ⇒ HIDDEN; BaoDo01 (bạn) soi ⇒ OK; `off` ⇒ false.
