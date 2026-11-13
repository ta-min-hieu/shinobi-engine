# ADR-337: Lịch sử tử vong

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-13) — chủ dự án chọn "Lịch sử tử vong" trong lượt hỏi lô 51 (AskUserQuestion; mô tả kèm: server lưu 10 lần chết gần nhất (bị ai/quái nào hạ, ở đâu, lúc nào); xem bằng lệnh). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-13 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-195](ADR-195-Death-Recap.md) · [ADR-223](ADR-223-Personal-Stats.md) |

---

## Decision

**D1 — Ghi:** mỗi lần nhân vật bị hạ (đường `onCharacterDefeated` của registry, cùng chỗ ADR-195), ghi audit `DEATH` {killer, monster, x} trên Virtual Thread riêng (không chạm DB ở tick thread). Người chơi hạ ⇒ tên; quái hạ ⇒ mã quái — gateway nhớ mã theo id thực thể từ các gói spawn đã phát (LRU 4.096 con, không phình). Vị trí = hoành độ làm tròn lúc chết.

**D2 — Xem:** 10 lần mới nhất; client đổi mã quái thành tên theo bản dịch `hud.monster.<mã>`.

**D3 — Dây:** `GetDeathHistoryRequest{}` / `GetDeathHistoryResponse{repeated DeathEntry{killer_name, monster_code, x, at}}` **422/423**; chỉ mục audit V177. Client `/tuvong` (`/deaths`).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Audit `DEATH` ghi trên VT; mã quái từ LRU gói spawn; 422/423; chỉ mục V177 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-13 (lô 51): `DeathLogPort`/`Facade`, `PostgresDeathLogAdapter`, `DeathLogHooks` (nối trong `ConnectedCharacterRegistry`), `Lot51Commands.deathHistory`, `DeathHistoryCoordinator`.
2. ✅ Test: `Lot51IntegrationTest.deathLogNewestFirst`, `Lot51ServerTest.deathLogRemembersMonsterCodes`.
3. ⏳ Kiểm sống gameProbe 2026-11-13: `deaths` ⇒ tửVong=0 (opcode, quyền, đường đọc chạy thật); chưa gây được một lần chết qua probe — đường ghi phủ bằng `Lot51IntegrationTest.deathLogNewestFirst` và `Lot51ServerTest.deathLogRemembersMonsterCodes`.
