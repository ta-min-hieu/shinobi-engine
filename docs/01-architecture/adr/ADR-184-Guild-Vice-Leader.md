# ADR-184: Phó bang chủ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Phó bang chủ" trong lượt hỏi lô 32 (AskUserQuestion; mô tả kèm: thêm vai Phó bang chủ (tối đa 2): được duyệt đơn xin vào và đăng thông báo bang, không được đuổi người hay giải tán). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-179](ADR-179-Guild-Notice.md) · [ADR-180](ADR-180-Guild-Log.md) · [ADR-183](ADR-183-Guild-Leader-Transfer.md) |

---

## Decision

**D1 — Vai mới `VICE_LEADER`** (`GuildRole`, cột `guild_member.role` VARCHAR(16) không có CHECK nên không đổi cột). Tối đa
**2** mỗi bang (PLACEHOLDER), đếm SAU khi khoá dòng bang.

**D2 — Quyền**: `GuildRole.canManageMembership()` = bang chủ hoặc phó ⇒ **xem / duyệt / từ chối đơn xin vào**, **đăng
thông báo bang**. Mọi việc khác vẫn chỉ bang chủ: đuổi người, giải tán, đổi tên, nhường chức, bổ nhiệm phó, tuyên chiến,
nâng kỹ năng bang. Phó **rời bang được** như thành viên; bang chủ **đuổi được** cả phó.

**D3 — Sửa các chỗ cũ giả định "chỉ LEADER / MEMBER"**: SQL rời bang và đuổi đổi `role = 'MEMBER'` → `role <> 'LEADER'`
(trước đây phó sẽ không rời được và không bị đuổi được); duyệt đơn kiểm vai người duyệt trong bang (thay vì so
`guild.leader_character_id`); từ chối đơn dùng `EXISTS` vai; danh sách đơn và thông báo bang mở cho phó. Nhật ký bang:
`VICE_APPOINTED` / `VICE_DISMISSED` (V145 mở rộng CHECK); dòng `JOINED` nay ghi đúng người duyệt (bang chủ hoặc phó).

**D4 — Giao thức, chỉ thêm**: `SetGuildViceLeaderCommand{target_name, appoint}` / `Response{outcome, target_name,
appoint}` 749/750; `GuildMemberView.is_vice_leader = 6`, `GetMyGuildResponse.is_vice_leader = 7`;
`GUILD_CHANGE_REASON_VICE_CHANGED = 12`; `GuildLogKind` 10/11.

**D5 — Client**: `/phobang <tên>` / `/bophobang <tên>` (hoặc `/vice` / `/unvice`); danh sách thành viên ghi "Phó bang
chủ"; phó thấy mục đơn xin vào trong cửa sổ bang (không thấy nút giải tán).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Vai `VICE_LEADER` ≤ 2: duyệt đơn + thông báo; rời / bị đuổi được; V145; 749/750; 2 field proto mới | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 32): `GuildRole.VICE_LEADER`/`canManageMembership`, `setViceLeader` trong
   `GuildLeadershipPort`/`PostgresGuildLeadershipAdapter`, sửa SQL rời/đuổi/duyệt/từ chối, `GuildViceCoordinator`.
2. ✅ Test: `GuildLeadershipIntegrationTest.viceLeaderApprovesAndPostsButCannotKick_limitTwo` (Postgres thật) + toàn bộ
   test bang cũ vẫn xanh, `GuildViceCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-07 (bước `vice:<tên>`, `unvice:<tên>`): ĐạoHữu01 bổ nhiệm chính mình ⇒ `ALREADY_VICE`;
   bổ nhiệm BaoDo01 ⇒ `OK` (lần hai ⇒ `ALREADY_VICE`); BaoDo01 (phó) đăng thông báo bang ⇒ `OK`, nhường chức ⇒
   `NOT_LEADER`, tự bổ nhiệm ⇒ `NOT_LEADER`; bãi nhiệm ⇒ `OK` (lần hai ⇒ `NOT_VICE`); nhật ký bang có
   `VICE_APPOINTED`/`NOTICE_CHANGED(BaoDo01)`/`VICE_DISMISSED`. **Lần kiểm sống đầu tìm ra lỗi nhỏ**: phó tự bổ nhiệm
   mình nhận `ALREADY_VICE` thay vì `NOT_LEADER` (facade kiểm "chính mình" trước quyền) — đã chuyển vào adapter, sau bước
   kiểm bang chủ.
4. Chờ chủ dự án: trần 2 phó; có cho phó đuổi thành viên thường không (hiện: không).
