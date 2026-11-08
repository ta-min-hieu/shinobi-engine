# ADR-266: Báo thành viên bang online

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Báo thành viên bang online" trong lượt hỏi lô 42–43 (AskUserQuestion; mô tả kèm: Thông báo khi thành viên bang vào game, có thể tắt). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-059](ADR-059-Party-And-Guild-Changed-Events.md) · báo bạn bè online (`FriendOnlineEvent`) |

---

## Decision

**D1 —** Event `GuildMemberOnlineEvent{character_id, name}` (782, dải Guild) gửi cho các thành viên KHÁC đang online của
bang, ngay sau lời báo bạn bè online lúc vào world — việc PHỤ: lỗi dừng ở `announceOnlineToGuild`, không thành "chọn nhân
vật thất bại" (cùng luật `announceOnlineToFriends`).

**D2 — Tắt ở client.** `/baobang` (hoặc `/guildonline`) bật/tắt, `bat | tat` đặt hẳn; lưu Preferences, mặc định BẬT.
Server vẫn gửi (một gói nhỏ mỗi lần vào game của một đồng môn) — không thêm cột DB cho một lựa chọn hiển thị.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 782 tới đồng môn online lúc vào world; tắt ở client (Preferences) | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 42): `Lot42Commands.announceGuildOnline`, `GatewayChannelHandler.announceOnlineToGuild`, `GuildOnlineNoticeCoordinator`.
2. ✅ Test: biên dịch + `ClientOpcodeCoverageTest`.
