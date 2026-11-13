# ADR-311: Báo bạn bè vào game (dòng chat + công tắc)

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Thêm dòng chat + công tắc" trong lượt hỏi lô 48 (AskUserQuestion; mô tả kèm: thay cho "Báo bạn bè vào game" vốn đã có chữ nổi từ Phase 6: thêm dòng chat + /baobanbe tắt cả hai). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-266](ADR-266-Guild-Member-Online-Notice.md) |

---

## Decision

**Bối cảnh (ghi thật):** món "Báo bạn bè vào game" AI đề xuất cho lô 48 hoá ra ĐÃ CÓ từ Phase 6 — server phát
`FriendOnlineEvent`/`FriendOfflineEvent`, client hiện chữ nổi "X vừa online" (`FriendCoordinator`). AI chỉ grep ADR mà
không grep code lõi; chủ dự án chọn phương án thay "thêm dòng chat + công tắc".

**D1 — Thuần client.** Mỗi lần bạn bè vào/rời game: ngoài chữ nổi có sẵn còn thêm một dòng chat (chữ nổi dễ lỡ).
`/baobanbe` (hoặc `/friendnotice`) bật/tắt CẢ HAI; nhớ trong Preferences `social.friend_presence` (mặc định bật).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Dòng chat kèm chữ nổi; `/baobanbe` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 48): `FriendPresenceCoordinator`, `FriendCoordinator.usePresenceNotice`.
2. ✅ Test: biên dịch + kiểm tay (UI).
3. ⏳ Thuần client — probe headless không vẽ UI/không chạy lệnh gạch chéo; phủ bằng biên dịch + test đơn vị, cần kiểm tay trên client thật.
