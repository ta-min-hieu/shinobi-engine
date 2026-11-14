# ADR-391: Xếp hạng sát thủ PvP

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-14) — chủ dự án chọn "Xếp hạng sát thủ PvP" trong lượt hỏi lô 58 (AskUserQuestion; mô tả kèm: /topsatthu: top 10 người hạ nhiều người chơi nhất (dùng số liệu PvP của lô 56)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-14 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-378](ADR-378-Personal-PvP-Stats.md) · [ADR-305](ADR-305-Like-Ranking.md) |

---

## Decision

**D1:** đọc cột `player_kills` (ADR-378) của `character_lifetime_stats`, chỉ người có ≥ 1 lần, top 10, nhiều trước, cùng số theo tên. Không bảng mới.

**D2 — Dây:** một cặp opcode chung cho các bảng trọn đời: `GetLifetimeRankingRequest{kind}` / `GetLifetimeRankingResponse{kind, entries{name, value}}` **1206/1207**, enum `LifetimeRankingKind` (`PLAYER_KILLS`, `PLAYTIME`); thiếu kind ⇒ sát thủ. Client `/topsatthu` (`/topkillers`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Lệnh 1206/1207 kind `PLAYER_KILLS`, đọc `player_kills` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-14 (lô 58): `LifetimeRankingPort/Facade`, `PostgresLifetimeRankingAdapter`, `Lot58Commands.lifetimeRanking`, `LifetimeRankingCoordinator`.
2. ✅ Test: `Lot58IntegrationTest.ranksKillsAndPlaytime`; kiểm sống.
3. ✅ Kiểm sống 2026-11-14 (gameProbe `lifetimeRank:kills`): bảng rỗng đúng (dữ liệu thử chưa có ai hạ người chơi).
