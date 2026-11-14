# ADR-383: Chế độ ẩn (hiện offline với bạn bè)

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Chế độ ẩn (hiện offline)" trong lượt hỏi lô 57 (AskUserQuestion; mô tả kèm: /an bat|tat: bạn bè thấy mình như đang offline). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-192](ADR-192-Last-Online.md) · [ADR-335](ADR-335-Status-Message.md) |

---

## Decision

**D1 — Lưu:** bảng MỚI `invisible_mode(character_id PK, since)` (V182) — có dòng = đang ẩn; nhớ qua các lần đăng nhập. Phạm vi CHỈ với bạn bè: danh sách bạn bè trả `online = online thật VÀ không ẩn`; vào/thoát world khi đang ẩn thì KHÔNG phát `FriendOnlineEvent`/`FriendOfflineEvent`; bật ẩn lúc đang online ⇒ phát `FriendOfflineEvent` cho bạn bè, tắt ẩn ⇒ `FriendOnlineEvent`. Bang, nhóm, tìm người, đếm online, tin riêng KHÔNG đổi (ẩn không phải biến mất khỏi thế giới).

**D2 — Dây:** `SetInvisibleCommand{enabled, query_only}` / `SetInvisibleResponse{enabled}` **1160/1161**. Client `/an [bat|tat]` (`/invisible on|off`), bỏ trống ⇒ hỏi trạng thái.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `invisible_mode` (V182), lệnh 1160/1161, chỉ ảnh hưởng góc nhìn bạn bè | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 57): `InvisibleModeFacade`, `PostgresInvisibleModeAdapter`, `Lot57Commands.setInvisible/shownOnline`, chặn trong `GatewayChannelHandler.notifyFriendsPresenceChanged`, `InvisibleCoordinator`.
2. ✅ Test: `Lot57IntegrationTest.invisibleModeToggles`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14 (gameProbe `invisible:`): query/on/query/off ⇒ `ẩn=false/true/true/false`; ĐạoHữu01 đang online + ẩn ⇒ BaoDo01 `listFriends` thấy `online=false`; bỏ ẩn ⇒ `online=true` (đã xoá dòng `invisible_mode` sau khi kiểm).
