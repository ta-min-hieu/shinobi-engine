# CLAUDE.md — feature-party

> Kế thừa [server/CLAUDE.md](../../CLAUDE.md). Auto-load khi sửa `feature-party/**`.

## Cấu trúc (Clean Arch)

```
api/            PartyFacade (+ CreateResult/InviteResult/AcceptResult/RejectResult/LeaveResult/
                 KickResult/MemberView/PendingInviteView/MyPartyView — type RIÊNG, không lộ
                 PartyRole hay UseCase.Result ra ngoài)
application/    CreatePartyUseCase · InvitePartyMemberUseCase · AcceptPartyInviteUseCase
                RejectPartyInviteUseCase · LeavePartyUseCase · KickPartyMemberUseCase
                ListPartyMembersUseCase · ListPendingPartyInvitesUseCase · FindMyPartyUseCase
                PartyUseCases (gộp 9 UseCase, tránh PartyFacade vượt 4 tham số — Checkstyle
                ParameterNumber, cùng tinh thần GuildUseCases/FriendUseCases)
domain/         Party (aggregate, có hằng số MAX_MEMBERS=5) · PartyMember (VO) · PartyInvite (VO)
                PartyRole (enum) · PartyPort · DuplicatePartyInviteException
                DuplicatePartyMembershipException
```

Chiều: `api → application → domain`. **`domain/` không import gì bên ngoài** — adapter thật
(`PostgresPartyAdapter`) nằm ở `server-database`.

## Quyết định MVP (2026-07-24, Phase 7 lát cắt 2)

3 quyết định chốt cùng chủ dự án qua `AskUserQuestion` trước khi code: (1) luồng tham gia =
**mời + chấp nhận** (leader mời theo tên nhân vật → invitee chấp nhận/từ chối) — NGƯỢC hướng với
feature-guild ("xin gia nhập + leader duyệt"), giống feature-friend hơn vì party thường rủ người
quen chứ không phải người lạ xin vào; (2) **phạm vi lát cắt 2 = chỉ thành viên + kênh chat Party**,
KHÔNG chia EXP — chia EXP bị chặn bởi thiếu tính năng gốc: giết quái solo hiện KHÔNG cấp EXP trực
tiếp (chỉ có qua `ClaimQuestRewardUseCase`, không có công thức EXP/quái hay level-up nào tồn tại),
nên "chia EXP party" cần thiết kế Combat/Character progression trước — ngoài phạm vi feature-party,
để dành quyết định riêng khi làm; (3) **leader rời KHÔNG cần "giải tán" như guild** — tự động thăng
thành viên vào sớm nhất (`joined_at` nhỏ nhất còn lại) làm leader mới; party chỉ mất khi rỗng hẳn.

- **`Party` không có `name`** (khác `Guild`) — GDD không đặt tên party, chỉ leader + thành viên.
- **`MAX_MEMBERS = 5`** (GDD "Bí Cảnh party (2–5)") — kiểm 2 lớp: `InvitePartyMemberUseCase` kiểm
  mềm trước khi mời (UX phản hồi sớm), `PostgresPartyAdapter.acceptInvite` khoá dòng `party` FOR
  UPDATE + đếm lại thành viên NGAY TRONG transaction trước khi insert — chống race nhiều lời mời
  được chấp nhận đồng thời vượt quá 5 (không có UNIQUE/CHECK constraint nào ở DB bắt được việc này,
  phải khoá dòng thủ công, cùng lý do `approveJoinRequest` của feature-guild cần SAVEPOINT).
- **Thứ tự khoá: dòng `party` TRƯỚC mọi dòng `party_member`/`party_invite`** — ở CẢ `acceptInvite`
  (2026-09-23) lẫn `leaveParty` (2026-10-03: leader và thành viên vào sớm nhất cùng rời từng
  deadlock thật, `PartyDissolveRaceIntegrationTest`). Giao dịch mới chạm các bảng này phải giữ đúng
  thứ tự. Mời vào party vừa bị giải tán ⇒ adapter ném `PartyDissolvedException` (vi phạm FK), use
  case trả `NOT_LEADER`.
- **1 nhân vật tối đa 1 party tại 1 thời điểm** — ràng buộc ở tầng DB (`party_member.character_id
  UNIQUE`), không chỉ ở application/. Bên THUA race (1 nhân vật vào 2 party cùng lúc) phải ra
  outcome "đã ở trong nhóm", không phải lỗi chung: `insertMember` dùng `ON CONFLICT (character_id)
  DO NOTHING` → `acceptInvite` trả `ALREADY_IN_PARTY` (xoá lời mời), `createParty` ném
  `DuplicatePartyMembershipException` (rollback cả dòng `party`) → `CreatePartyUseCase` dịch thành
  `ALREADY_IN_PARTY`. Bằng chứng: `PartyMembershipRaceIntegrationTest` (2026-09-25).
- **`leaveParty` xử lý CẢ 2 vai trò** (khác feature-guild tách `LeaveGuildUseCase`/
  `DisbandGuildUseCase` riêng) — MEMBER rời chỉ xoá dòng; LEADER rời tự động thăng người vào sớm
  nhất còn lại (UPDATE role + `party.leader_character_id`) hoặc xoá luôn party nếu là người cuối.
  Không có `DisbandPartyUseCase` riêng — rời hết người tự nhiên giải tán, không cần hành động
  tường minh.
- **Lời mời KHÔNG bị chặn multi-invite tới nhiều party khác nhau ở tầng DB** (UNIQUE chỉ khoá
  `(party_id, invitee_character_id)`) — application chặn bằng `findPendingInviteOfInvitee` (global
  theo characterId) trước khi cho mời, cùng race-tolerant pattern `findPendingRequestOfCharacter`
  của feature-guild. Không có `CancelInviteUseCase` (leader không tự huỷ lời mời đã gửi) — ngoài
  phạm vi lát cắt này.
- **Test application/ dùng 1 `FakePartyPort` DÙNG CHUNG** (cùng lý do/tiền lệ feature-guild —
  `PartyPort` nhiều method, lặp lại nested Fake mỗi file test là trùng lặp thật).
- **Kênh chat "Party"** nối vào `ChatChannel.PARTY` (feature-chat) — cùng cơ chế kênh "Bang hội":
  gateway gọi `PartyFacade.listMembers(senderCharacterId)` sau khi `ChatFacade.sendMessage` OK để
  lấy danh sách nhận, broadcast qua `ConnectedCharacterRegistry`.

## Cứng

✅ Validate nghiệp vụ (đã-ở-party/không-phải-leader/party-đầy...) ở `application/`, trước khi chạm Port
✅ Port là interface trong `domain/`; Adapter implement ở `server-database`
✅ Value Object = `record`, validate trong compact constructor (`Party`/`PartyMember`/`PartyInvite`)
✅ Module khác gọi qua `PartyFacade`, không đọc bảng `party`/`party_member`/`party_invite` trực tiếp

❌ SQL/JDBC/Redis client trong module này (Điều 23 — enforce bằng ArchUnit)
❌ `domain/` biết PostgreSQL
❌ `feature-party` phụ thuộc Gradle vào `feature-character`/`feature-chat` (chỉ gateway compose)

## Test

`domain/` test không mock. `application/` test với **`FakePartyPort` dùng chung** — không Mockito.
