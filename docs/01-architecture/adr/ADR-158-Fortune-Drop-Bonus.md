# ADR-158: Rơi đồ theo quẻ bói

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-02) — chủ dự án chọn "Rơi đồ theo quẻ bói" trong lượt hỏi lô 28 (AskUserQuestion; mô tả kèm: nối tiếp ADR-149 D4: quẻ tốt cộng thêm lượt roll bảng rơi cho RIÊNG người đó (đổi API feature-inventory: số lượt roll theo từng người)). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-02 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-149](ADR-149-Daily-Fortune.md) D4 (phần còn thiếu) · [ADR-067](ADR-067-Double-Events.md) D6 (`withRollCount` — hệ số chung) |

---

## Decision

**D1 — Dữ liệu (V127)**: `fortune.drop_bonus_percent` (0–100) — **Đại Cát 30, Trung Cát 15, Tiểu Cát 5, Bình 0, Hung
0**: % cơ hội tung bảng rơi THÊM một lượt mỗi lần người đó hạ quái, tới hết ngày. Hung không trừ rơi đồ (đã −10% EXP).
PLACEHOLDER (Điều 27).

**D2 — API feature-inventory, chỉ thêm**: `GrantLootUseCase.withBonusRollPercent(LongToIntFunction)` — % theo
`characterId` người hạ gục; mặc định 0. Lượt roll = hệ số chung (`withRollCount`: sự kiện x2 × đêm) **+ 1** nếu trúng %
riêng. Module vẫn không biết quẻ là gì; random sinh ở server (`ThreadLocalRandom`, như `LootRoller`).

**D3 — Một bộ nhớ quẻ dùng chung**: % đọc qua `FortuneFacade.dropBonusPercent` (cùng bộ nhớ theo ngày với % EXP — một lần
đọc DB/người/ngày). Đường gieo quẻ (gateway) và đường rơi đồ (server-database) phải là CÙNG một `FortuneFacade`, nếu
không gieo xong vẫn rơi như chưa gieo tới hết ngày ⇒ `ServerDatabaseBootstrap.expansion()` và
`ExpansionWiring.wireFortuneFacade()` nay giữ một bản.

**D4 — Giao thức, chỉ thêm**: `DrawFortuneResponse.drop_bonus_percent = 9`; client ghi thêm "+X% cơ hội rơi thêm đồ".

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | % rơi thêm một lượt theo quẻ, theo từng người, qua hook mới của GrantLootUseCase, một FortuneFacade dùng chung | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md) · [feature-inventory/CLAUDE.md](../../../server/server-features/feature-inventory/CLAUDE.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-02 (lô 28): V127, `GrantLootUseCase.withBonusRollPercent` (+ test), `FortuneFacade.dropBonusPercent`,
   `ExpansionWiring`/`ServerDatabaseBootstrap.expansion()` một bản, field 9 của `DrawFortuneResponse`.
2. ✅ Kiểm sống 2026-11-02: V127 chạy; rút quẻ ở x=-5 (cạnh trưởng lão) ra `tieu_cat` ⇒ `drop%=5`, `hung` ⇒
   `drop%=0` — khớp bảng. Phép cộng lượt rơi đồ do `GrantLootUseCaseTest.bonusRollPercentShouldApplyOnlyToThatCharacter` gánh.
