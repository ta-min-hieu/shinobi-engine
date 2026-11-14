# ADR-416: Bạn bè lâu vắng

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Bạn bè lâu vắng" trong lượt hỏi lô 61 (AskUserQuestion; mô tả kèm: /banlauvang: liệt kê bạn bè đã vắng ≥ 7 ngày kèm số ngày vắng). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-192](ADR-192-Last-Online.md) · [ADR-394](ADR-394-Comeback-Gift.md) |

---

## Decision

**D1:** đọc mốc rời world gần nhất (ADR-192) của từng bạn; bạn đang online hoặc chưa có mốc thì bỏ qua; vắng ≥ **7** ngày thì liệt kê, vắng lâu nhất trước. Gợi ý nhắn hỏi thăm (kết hợp quà quay lại ADR-394).

**D2 — Dây:** `GetAbsentFriendsRequest{}` / `GetAbsentFriendsResponse{entries{name, days}}` **1164/1165**. Client `/banlauvang` (`/absentfriends`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lệnh 1164/1165, ngưỡng 7 ngày | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 61): `Lot61Commands.absentFriends`, `FriendDigestCoordinator`.
2. ✅ Test: kiểm sống (lùi mốc online trong DB).
3. ✅ Kiểm sống 2026-11-14: lùi mốc online của BaoDo01 về 9 ngày trước (BaoDo01 đã thoát) ⇒ `absentFriends:` trả `BaoDo01=9`; khi BaoDo01 đang online thì không bị liệt kê (đúng luật). Đã trả lại mốc sau khi kiểm.
