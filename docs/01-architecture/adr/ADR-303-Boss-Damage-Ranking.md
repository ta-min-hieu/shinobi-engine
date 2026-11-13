# ADR-303: Xếp hạng sát thương boss

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Xếp hạng sát thương boss" trong lượt hỏi lô 47 (AskUserQuestion 2026-11-12; mô tả kèm: /topboss: bảng top sát thương boss thế giới mọi thời (dựa trên lịch sử boss của lô 46)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-081](ADR-081-World-Boss.md) · [ADR-296](ADR-296-Boss-Kill-History.md) · [ADR-157](ADR-157-Live-World-Boss-Board.md) |

---

## Decision

**D1 — Nguồn số:** tổng sát thương đọc từ sổ **`world_boss_damage`** (ADR-081 — cộng dồn từ khi có boss thế giới,
cùng nguồn với thẻ `BOSS_DAMAGE` của Bia Anh Danh); số trận đếm từ audit `BOSS_KILL` (ADR-296, chỉ có từ 2026-11-12).
Top 10, cao trước. Không migration.

**D2 — Trùng một phần với tính năng cũ (ghi thật):** Bia Anh Danh đã có thẻ xếp hạng sát thương boss (ADR-081/057).
`/topboss` là đường tắt in vào chat, thêm cột số trận; khi viết lựa chọn lô 47 AI không phát hiện ra thẻ cũ. Lúc thi
hành đã chuyển sang ĐÚNG nguồn số của thẻ cũ để hai nơi không bao giờ lệch nhau.

**D3 — Dây:** `GetBossDamageRankingRequest{}` / `GetBossDamageRankingResponse{repeated BossDamageRank{name, total_damage,
kills}}` **420/421**. Client `/topboss`.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Tổng từ `world_boss_damage` + số trận từ `BOSS_KILL`; 420/421; `/topboss` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 47): `BossKillLogPort.topWorldDamage`, `Lot47Commands.bossDamageRanking`, `BossDamageRankingCoordinator`.
2. ✅ Test: `Lot47IntegrationTest.ranksWorldBossDamageAcrossKills`.
3. ⏳ Kiểm sống gameProbe.
