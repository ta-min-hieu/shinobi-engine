# ADR-192: Lần cuối online

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Lần cuối online" trong lượt hỏi lô 33 (AskUserQuestion; mô tả kèm: danh sách bạn bè và thành viên bang hiện "online 3 ngày trước" cho người đang offline). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-095](ADR-095-Seclusion-Offline-Exp.md) (`last_logout_at` của bế quan) · [ADR-173](ADR-173-Friend-Notes.md) |

---

## Decision

**D1 — Cột riêng**: V150 thêm `character.last_online_at` (NULL = chưa từng có mốc), ghi lúc nhân vật **rời world**.
Không dùng lại `last_logout_at` của bế quan: cột đó bị xoá mỗi lần vào world và có nghĩa riêng (quỹ phút offline) — hai
tính năng không được trói vào nhau. V150 mượn `last_logout_at` làm giá trị đầu cho người đang offline.

**D2 — Ghi ở đâu**: cùng việc nền với mốc thoát bế quan trong `releaseCharacter` (Virtual Thread `authExecutor`),
`recordLeftWorld` ghi `last_online_at` trước rồi mới tới bế quan.

**D3 — Đọc**: một câu `WHERE id = ANY(?)` cho cả danh sách bạn / cả bang (không thêm N+1). Gateway đặt
`last_online_epoch_seconds = 0` cho người đang online (theo `ConnectedCharacterRegistry`) hoặc chưa có mốc.

**D4 — Giao thức, chỉ thêm**: `FriendView.last_online_epoch_seconds = 7`, `GuildMemberView.last_online_epoch_seconds =
7`. Không opcode mới, `protocolVersion` không tăng. Nhóm (party) không có — thành viên nhóm gần như luôn online và
trưởng nhóm vắng đã tự đổi tay.

**D5 — Client**: bạn bè offline hiện "(3 ngày trước)" sau tên; thành viên bang hiện "Ngoại tuyến 3 ngày trước". Bậc:
vừa xong / phút / giờ / ngày, tính theo giờ máy client. Bạn vừa offline trong lúc đang chơi ⇒ mốc = giờ máy lúc nhận
`FriendOfflineEvent` (1113).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cột `character.last_online_at` (V150), ghi khi rời world, đọc theo lô, field 7 ở `FriendView`/`GuildMemberView` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 33): V150, `LastOnlinePort`/`LastOnlineFacade`, `PostgresLastOnlineAdapter`, `Lot33Facades`,
   `recordLeftWorld`, `LastOnlineText`.
2. ✅ Test: `LastOnlineIntegrationTest` (Postgres thật), `LastOnlineTextTest`.
3. ✅ Kiểm sống gameProbe 2026-11-07: V150 chạy trên DB dev (mượn `last_logout_at` làm giá trị đầu); BaoDo01 vào rồi
   thoát ⇒ `last_online_at` đổi sang mốc vừa thoát; ĐạoHữu01 `listFriends` ⇒ `BaoDo01 online=false lastOnline=<mốc đó>`.
4. ✅ Kèm theo — dọn vi phạm static check lọt qua ở lô 32 (server-database chưa được check đủ lúc đó):
   `RetentionJanitor.purgeQuietly` vượt CyclomaticComplexity (tách `purgeSocialQuietly`), `PostgresOfflineWhisperAdapter.store`
   5 tham số theo chữ ký port (chú thích PMD có lý do), hai dòng test quá 120 ký tự, SpotBugs `FS` (`%n`) trong
   `TradeHistoryIntegrationTest`.
