# ADR-345: Báo khi bị xoá bạn

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Báo khi bị xoá bạn" trong lượt hỏi lô 52 (AskUserQuestion; mô tả kèm: người bị xoá khỏi danh sách bạn bè (đang online) nhận một dòng báo và danh sách tự làm mới). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-091](ADR-091-Block-List.md) |

---

## Decision

**D1 — Khi nào:** lệnh xoá bạn (đường có sẵn) thành công ⇒ người bị xoá, nếu đang online, nhận `FriendRemovedEvent{character_id, name}` **1159**. Offline thì không lưu (lần mở danh sách sau tự thấy).

**D2 — Client:** một dòng hệ thống "X đã xoá bạn khỏi danh sách bạn bè" rồi hỏi lại danh sách bạn bè. Không thêm bảng.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Event 1159 cho người bị xoá đang online; client báo + làm mới danh sách | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 52): `Lot52Commands.friendRemoved` (gọi từ `handleRemoveFriendBlocking`), `FriendRemovedCoordinator`.
2. ✅ Test: đường xoá bạn giữ nguyên test sẵn có; phần báo kiểm sống.
3. ⏳ Chưa kiểm sống (cần xoá rồi kết bạn lại một cặp có sẵn); phủ bằng `FriendWireTest.shouldStopPresenceEvents_onceTheFriendshipIsRemoved` — người bị xoá đang online nhận đúng một gói 1159.
