# ADR-183: Nhường chức bang chủ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-07) — chủ dự án chọn "Nhường chức bang chủ" trong lượt hỏi lô 32 (AskUserQuestion; mô tả kèm: bang chủ trao chức cho một thành viên, bang chủ cũ thành thành viên thường, báo cả bang, ghi nhật ký bang). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-07 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-059](ADR-059-Party-And-Guild-Changed-Events.md) (event 720) · [ADR-180](ADR-180-Guild-Log.md) (nhật ký bang) |

---

## Decision

**D1 — Ai, cho ai**: chỉ **bang chủ**, cho một **thành viên cùng bang** (theo tên). Bang chủ cũ thành thành viên thường —
từ đó rời bang được như mọi người (trước đây bang chủ chỉ có giải tán). Miễn phí, không hồi chiêu.

**D2 — Một transaction, không cột mới**: khoá dòng bang, đọc lại vai SAU khi khoá (hai lệnh nhường chức đồng thời không
cùng thấy mình là bang chủ), đổi `guild_member.role` của hai người và `guild.leader_character_id`, ghi nhật ký bang loại
mới `LEADER_CHANGED` (actor = bang chủ cũ, subject = bang chủ mới). V144 chỉ mở rộng CHECK loại dòng của `guild_log`.

**D3 — Báo cả bang**: thành viên đang online (trừ người nhường) nhận `GuildChangedEvent` lý do mới
`GUILD_CHANGE_REASON_LEADER_CHANGED = 11` ⇒ dòng "Bang vừa có bang chủ mới" + client hỏi lại bang.

**D4 — Giao thức, chỉ thêm**: `TransferGuildLeaderCommand{target_name}` / `TransferGuildLeaderResponse{outcome,
target_name}` 747/748; `GuildLogKind.LEADER_CHANGED = 9`.

**D5 — Client**: lệnh chat `/nhuongbang <tên>` (hoặc `/guildleader`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Bang chủ nhường cho thành viên cùng bang, một transaction đọc-lại-sau-khoá, V144, 747/748, 720 LEADER_CHANGED | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-07 (lô 32): V144, `GuildLeadershipPort`/`GuildLeadershipFacade`, `PostgresGuildLeadershipAdapter`,
   `GuildLeadershipCommands` 747/748, `GuildLeadershipCoordinator` `/nhuongbang`.
2. ✅ Test: `GuildLeadershipIntegrationTest` (Postgres thật: từ chối 3 kiểu, nhường được, bang chủ cũ rời bang được,
   nhật ký có dòng), `GuildLeadershipCoordinatorTest`.
3. ✅ Kiểm sống gameProbe 2026-11-07 (bước `transferLeader:<tên>`): ĐạoHữu01 nhường cho chính mình ⇒
   `CANNOT_TRANSFER_TO_SELF`; tên lạ ⇒ `TARGET_NOT_IN_GUILD`; nhường cho BaoDo01 ⇒ `OK`, BaoDo01 đang online nhận 720;
   nhường lần hai ⇒ `NOT_LEADER`; BaoDo01 nhường lại ⇒ `OK`; nhật ký bang có hai dòng `LEADER_CHANGED` đúng chiều.
