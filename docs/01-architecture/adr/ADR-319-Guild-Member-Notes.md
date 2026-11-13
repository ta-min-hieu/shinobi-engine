# ADR-319: Ghi chú thành viên bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Ghi chú thành viên bang" trong lượt hỏi lô 49 (AskUserQuestion; mô tả kèm: bang chủ/phó ghi chú ngắn về từng thành viên, chỉ cấp quản lý thấy). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-184](ADR-184-Guild-Vice-Leader.md) · [ADR-171](ADR-171-Inspect-Player.md) |

---

## Decision

**D1 — Cột `guild_member.officer_note`** (V175, `VARCHAR(100)`, rỗng = chưa ghi). Chỉ bang chủ / phó đọc và ghi; ≤ 100
ký tự, không ký tự điều khiển, không từ cấm. Ghi cho người KHÔNG ở bang ⇒ `NOT_MEMBER`.

**D2 — Dây:** `SetGuildMemberNoteCommand{query_only, member_name, note}` / `SetGuildMemberNoteResponse{outcome, repeated
GuildMemberNote{name, note}}` **785/786** — mọi trả lời kèm toàn bộ ghi chú hiện có. Client `/ghichubang` (xem),
`/ghichubang <tên> <ghi chú>`, `/ghichubang <tên> xoa`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Cột `officer_note`; chỉ cấp quản lý; 785/786; `/ghichubang` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 49): `GuildMemberNotePort`/`Facade`, `PostgresGuildMemberNoteAdapter`, `Lot49Commands.setMemberNote`, `GuildMemberNoteCoordinator`.
2. ✅ Test: `Lot49IntegrationTest.memberNoteOnlyForMembers`, `Lot49ServerTest.validatesMemberNote`.
3. ⏳ Kiểm sống gameProbe.
