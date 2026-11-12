# ADR-296: Lịch sử boss đã hạ

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-12) — chủ dự án chọn "Lịch sử boss đã hạ" trong lượt hỏi lô 46 (AskUserQuestion; mô tả kèm: /bossdaha: các lần mình tham gia hạ boss thế giới/hầm ngục). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-12 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-157](ADR-157-Live-World-Boss-Board.md) · [ADR-280](ADR-280-Duel-History.md) |

---

## Decision

**D1 — Ghi:** `BossKillHooks` (trong `ConnectedCharacterRegistry`) ghi audit `BOSS_KILL {boss, kind, rank, damage}` cho
MỖI người tham gia: boss thế giới ghi lúc `WorldBossScheduler.settle` (hạng + sát thương từ `WorldBossRanking`), boss Bí
Cảnh ghi lúc `DungeonInstanceRegistry` hạ boss (mọi thành viên phòng, không xếp hạng). Luôn trên luồng nền.

**D2 — Đọc:** `GetBossKillsRequest{}` / `GetBossKillsResponse{repeated BossKillEntry}` **418/419** (dải Combat), 20 dòng
mới nhất. **V173** thêm chỉ mục một phần cho `BOSS_KILL`. Client `/bossdaha`. Trận trước ADR này không có lịch sử.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Audit `BOSS_KILL` mỗi người; 418/419; `/bossdaha` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-12 (lô 46): `BossKillLogPort`/`Facade`, `PostgresBossKillLogAdapter`, `BossKillHooks`, `Lot46Commands.bossKills`, `BossKillsCoordinator`.
2. ✅ Test: `Lot46IntegrationTest.recordsAndReadsBossKills`.
3. ✅ Kiểm sống gameProbe 2026-11-12 (`bossKills`): `boss=0` — đúng kỳ vọng (chỉ ghi từ ADR này, chưa trận nào). Đường ghi + đọc phủ bởi `Lot46IntegrationTest` (Postgres thật).
