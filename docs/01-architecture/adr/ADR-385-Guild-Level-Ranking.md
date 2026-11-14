# ADR-385: Xếp hạng cấp trong bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Xếp hạng cấp trong bang" trong lượt hỏi lô 57 (AskUserQuestion; mô tả kèm: /capbang: mọi thành viên bang theo cảnh giới, cao trước). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-181](ADR-181-Guild-Leaderboard-Columns.md) · [ADR-287](ADR-287-Guild-Contribution-Board.md) |

---

## Decision

**D1:** mọi thành viên bang của người hỏi, cấp cao trước, cùng cấp theo tên; kèm cờ online. Đọc `GuildFacade.listMembers` + `CharacterFacade.findById` từng người (bang có trần thành viên — chấp nhận N+1 như danh sách bạn bè). Chưa vào bang ⇒ `in_guild = false`.

**D2 — Dây:** `GetGuildLevelRankingRequest{}` / `GetGuildLevelRankingResponse{in_guild, entries{name, level, online}}` **791/792**. Client `/capbang` (`/guildlevels`) in cảnh giới thay số cấp.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lệnh 791/792, thành viên theo cấp | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 57): `Lot57Commands.guildLevelRanking`, `InvisibleCoordinator`.
2. ✅ Test: `Lot57ServerTest.ordersByLevelThenName`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14 (gameProbe `guildLevels:`): `ĐạoHữu01=20 BaoDo01=2` — cấp cao trước.
