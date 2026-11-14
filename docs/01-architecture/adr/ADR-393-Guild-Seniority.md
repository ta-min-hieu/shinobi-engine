# ADR-393: Thâm niên trong bang

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Thâm niên trong bang" trong lượt hỏi lô 58 (AskUserQuestion; mô tả kèm: /thamnien: thành viên bang kèm ngày vào bang, lâu nhất trước). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-181](ADR-181-Guild-Leaderboard-Columns.md) · [ADR-385](ADR-385-Guild-Level-Ranking.md) |

---

## Decision

**D1:** đọc cột `guild_member.joined_at` (có từ V14, trước nay không lộ ra) cho bang của người hỏi, vào sớm nhất trước, cùng lúc theo tên. Không đổi schema.

**D2 — Dây:** `GetGuildSeniorityRequest{}` / `GetGuildSeniorityResponse{in_guild, entries{name, joined_epoch_seconds}}` **793/794**. Client `/thamnien` (`/seniority`) in ngày `dd/MM/yyyy` theo giờ máy.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lệnh 793/794, đọc `joined_at` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 58): `GuildDirectoryPort/Facade`, `PostgresGuildDirectoryAdapter`, `Lot58Commands.guildSeniority`, `LifetimeRankingCoordinator`.
2. ✅ Test: `Lot58IntegrationTest.listsSeniorityOldestFirst`, `Lot58ClientTest.formatsJoinDate`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14 (gameProbe `seniority:`): `BaoDo01 ĐạoHữu01` — vào bang sớm nhất trước.
