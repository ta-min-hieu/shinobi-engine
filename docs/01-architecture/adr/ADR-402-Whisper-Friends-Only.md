# ADR-402: Chỉ bạn bè được nhắn riêng

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Chỉ bạn bè được nhắn riêng" trong lượt hỏi lô 59 (AskUserQuestion; mô tả kèm: /chibanbe bat|tat: người lạ không nhắn riêng được cho mình (bạn bè, bang vẫn được)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-236](ADR-236-Do-Not-Disturb.md) · [ADR-369](ADR-369-Stranger-Whisper-Limit.md) |

---

## Decision

**D1:** bảng MỚI `whisper_friends_only(character_id PK, since)` (V183). Bật ⇒ tin riêng tới mình từ người KHÔNG phải bạn bè và KHÔNG cùng bang bị từ chối; lời mời nhóm/giao dịch không đổi. Khác "không làm phiền" (ADR-236): DND chặn cả người cùng bang và mọi lời mời; công tắc này chỉ chặn tin riêng của người lạ.

**D2 — Dây:** `SetWhisperFriendsOnlyCommand{enabled, query_only}` / `SetWhisperFriendsOnlyResponse{enabled}` **820/821**; người gửi bị chặn nhận outcome MỚI `SEND_CHAT_MESSAGE_OUTCOME_FRIENDS_ONLY = 18` (chỉ thêm). Client `/chibanbe [bat|tat]` (`/whisperfriends`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bảng `whisper_friends_only` (V183), 820/821, outcome 18 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 59): `WhisperPrivacyPort/Facade`, `PostgresWhisperPrivacyAdapter`, `Lot59Commands.mayWhisperTo`, kiểm trong `GatewayChannelHandler` sau ADR-369, `GuildToolsCoordinator`.
2. ✅ Test: `Lot59IntegrationTest.whisperFriendsOnlyToggles`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14: bật công tắc cho BaoDo01 ⇒ PhuTanThu (người lạ, khác bang) nhắn ⇒ `SEND_CHAT_MESSAGE_OUTCOME_FRIENDS_ONLY`; ĐạoHữu01 (bạn bè) nhắn ⇒ `OK`; lệnh `whisperFriends:` query/on/off ⇒ `false/true/false` (đã xoá dòng sau khi kiểm).
