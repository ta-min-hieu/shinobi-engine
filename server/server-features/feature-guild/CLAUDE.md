# CLAUDE.md — feature-guild

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-guild/**`.

## Cấu trúc (Clean Arch)

```
api/            GuildFacade (+ CreateResult/SendJoinRequestResult/ApproveResult/RejectResult/
                 LeaveResult/DisbandResult/KickResult/MemberView/PendingJoinRequestView/
                 MyGuildView — type RIÊNG, không lộ GuildRole hay UseCase.Result ra ngoài)
application/    CreateGuildUseCase · SendGuildJoinRequestUseCase · ApproveGuildJoinRequestUseCase
                RejectGuildJoinRequestUseCase · LeaveGuildUseCase · DisbandGuildUseCase
                KickGuildMemberUseCase · ListGuildMembersUseCase
                ListPendingGuildJoinRequestsUseCase · FindMyGuildUseCase
                GuildUseCases (gộp 10 UseCase, tránh GuildFacade vượt 4 tham số — Checkstyle
                ParameterNumber, cùng tinh thần FriendUseCases/ShopPorts)
domain/         Guild (aggregate) · GuildMember (VO) · GuildJoinRequest (VO) · GuildRole (enum)
                GuildPort · DuplicateGuildNameException · DuplicateGuildJoinRequestException
```

Chiều: `api → application → domain`. **`domain/` không import gì bên ngoài** — adapter thật
(`PostgresGuildAdapter`) nằm ở `server-database`.

## Quyết định MVP (2026-07-23, Phase 7 lát cắt 1)

3 quyết định chốt cùng chủ dự án qua `AskUserQuestion` trước khi code: (1) thứ tự Phase 7 —
Guild trước Party/Trade (feature-chat đã để dành kênh "Bang hội" chờ đúng module này); (2)
**luồng gia nhập = xin gia nhập + leader duyệt** (KHÔNG phải mời trực tiếp như feature-friend —
nhân vật tự xin theo TÊN BANG, leader duyệt/từ chối qua đơn); (3) phạm vi lát cắt 1 = **chỉ thành
viên + kênh chat Bang hội**, kho bang + nhiệm vụ bang dời lát cắt sau (cùng cách Quest tách khỏi
Dialogue ở Phase 6).

- **Tên bang là khái niệm CỦA CHÍNH feature-guild** (khác feature-friend cần gateway resolve tên
  nhân vật qua `CharacterFacade` TRƯỚC khi gọi facade) — `SendGuildJoinRequestUseCase` nhận thẳng
  `guildName`, tự tra `GuildPort.findByName`, không cần bước resolve tên ở gateway.
- **1 nhân vật tối đa 1 bang tại 1 thời điểm** — ràng buộc ở tầng DB (`guild_member.character_id
  UNIQUE`), không chỉ ở application/.
- **LEADER không "rời" bang được** — chỉ có `DisbandGuildUseCase` (giải tán, CASCADE xoá hết thành
  viên + đơn chờ). `LeaveGuildUseCase` chỉ áp dụng MEMBER thường.
- **Đơn xin gia nhập KHÔNG bị chặn multi-apply tới nhiều bang khác nhau ở tầng DB** (UNIQUE chỉ
  khoá `(guild_id, character_id)`, không khoá toàn cục theo `character_id`) — application chặn
  bằng cách kiểm `findPendingRequestOfCharacter` trước khi cho gửi đơn mới, nhưng đây là
  race-tolerant, không phải bất biến cứng. Race hiếm hơn: nhân vật gửi đơn xong rồi **tự tạo bang
  khác** trước khi đơn cũ được duyệt → `approveJoinRequest` phát hiện UNIQUE violation ở
  `guild_member.character_id`, trả `TARGET_ALREADY_IN_GUILD` và **tự xoá đơn cũ** (coi như hết
  hạn) thay vì để lại rác — không có `CancelJoinRequestUseCase` (nhân vật không tự huỷ đơn đã gửi,
  ngoài phạm vi lát cắt này, tránh mở rộng scope không cần).
- **`createGuild`/`approveJoinRequest` nguyên tử bằng transaction thủ công trong adapter** (không
  `TransactionRunner` riêng) — cả 2 bảng liên quan đều thuộc feature-guild, không xuyên feature
  khác như shop/quest cần gọi ngược `server-world`.
- **Thứ tự khoá: dòng `guild` TRƯỚC dòng `guild_join_request`** (2026-09-25). `approveJoinRequest`
  bản cũ khoá đơn trước rồi mới chạm `guild` (qua kiểm FK của INSERT `guild_member`), còn
  `disbandGuild` DELETE `guild` rồi CASCADE sang đơn — ngược thứ tự ⇒ deadlock THẬT khi leader giải
  tán đúng lúc đang duyệt (`GuildConcurrencyIntegrationTest`, đỏ ngay vòng 1). Nay duyệt khoá
  `guild` `FOR NO KEY UPDATE` trước. Mọi giao dịch mới chạm cả hai bảng phải giữ đúng thứ tự này.
- **Test application/ dùng 1 `FakeGuildPort` DÙNG CHUNG** (test-fixture package riêng), KHÔNG lặp
  lại nested Fake ở mỗi file test như feature-friend — `GuildPort` có 13 method, lặp lại 9+ lần sẽ
  là trùng lặp thật (không phải "3 dòng giống nhau", CLAUDE.md §"cấm kỵ" ưu tiên maintainability),
  quyết định lệch khỏi tiền lệ feature-friend có ghi chú rõ ở đây.
- **Kênh chat "Bang hội"** nối vào `ChatChannel.GUILD` (feature-chat) — gateway tự gọi
  `GuildFacade.listMembers(senderCharacterId)` SAU KHI `ChatFacade.sendMessage` OK để lấy danh
  sách nhận, broadcast qua `ConnectedCharacterRegistry` (không cần AoI/tick thread, giống World/
  Riêng tư — guild membership không phải dữ liệu world-tick).

## Cứng

✅ Validate nghiệp vụ (đã-ở-bang/tên-trùng/không-phải-leader...) ở `application/`, trước khi chạm Port
✅ Port là interface trong `domain/`; Adapter implement ở `server-database`
✅ Value Object = `record`, validate trong compact constructor (`Guild`/`GuildMember`/`GuildJoinRequest`)
✅ Module khác gọi qua `GuildFacade`, không đọc bảng `guild`/`guild_member`/`guild_join_request` trực tiếp

❌ SQL/JDBC/Redis client trong module này (Điều 23 — enforce bằng ArchUnit)
❌ `domain/` biết PostgreSQL
❌ `feature-guild` phụ thuộc Gradle vào `feature-character`/`feature-chat` (chỉ gateway compose)

## Test

`domain/` test không mock. `application/` test với **`FakeGuildPort` dùng chung** (xem quyết định
ở trên) — không Mockito.
