# ADR-303: Xếp hạng sát thương boss

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Xếp hạng sát thương boss" trong lượt hỏi lô 47 (AskUserQuestion 2026-11-12; mô tả kèm: /topboss: bảng top sát thương boss thế giới mọi thời (dựa trên lịch sử boss của lô 46)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-296](ADR-296-Boss-Kill-History.md) · [ADR-157](ADR-157-Live-World-Boss-Board.md) |

---

## Decision

**D1 — Đọc audit `BOSS_KILL` (ADR-296)**, chỉ dòng `kind = WORLD`: tổng sát thương + số trận theo người, top 10, cao
trước (hoà ⇒ theo tên). `BossKillLogPort.topWorldDamage`. Không migration (dùng lại chỉ mục V173).

**D2 — Dây:** `GetBossDamageRankingRequest{}` / `GetBossDamageRankingResponse{repeated BossDamageRank{name, total_damage,
kills}}` **420/421**. Client `/topboss`. Chỉ tính từ khi có ADR-296 (2026-11-12).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Tổng `BOSS_KILL` WORLD theo người; 420/421; `/topboss` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 47): `BossKillLogPort.topWorldDamage`, `Lot47Commands.bossDamageRanking`, `BossDamageRankingCoordinator`.
2. ✅ Test: `Lot47IntegrationTest.ranksWorldBossDamageAcrossKills`.
3. ⏳ Kiểm sống gameProbe.
