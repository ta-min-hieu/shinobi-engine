# ADR-162: Quét nhanh Bí Cảnh

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-03) — chủ dự án chọn "Quét nhanh hầm ngục" trong lượt hỏi lô 29 (AskUserQuestion; mô tả kèm: hầm ngục đã từng phá đảo thì bấm 'quét' nhận thưởng ngay (tốn lượt ngày + một ít vàng) thay vì đánh lại). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-03 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-099](ADR-099-Dungeon-Daily-Runs-And-First-Clear.md) (lượt ngày + lần đầu hạ boss — điều kiện và trần dùng chung) · [ADR-095](ADR-095-Seclusion-Offline-Exp.md) (cùng cách cộng EXP ngoài World rồi đồng bộ cấp) |

---

## Decision

**D1 — Dữ liệu (V130)**: `dungeon_sweep_rule(dungeon_code PK, gold_fee, exp_reward, drop_table_code)`. Bí Cảnh duy nhất
`sample_dungeon`: **1.000 vàng, 400 EXP** (boss thật cho 200; phần còn lại thay cho quái dọc đường), tung bảng rơi
`sample_boss` một lần. PLACEHOLDER cân bằng (Điều 27).

**D2 — Điều kiện**: đã có dòng `dungeon_first_clear` (từng hạ boss thật); còn lượt ngày — **dùng chung** bảng và trần
`dungeon_entry` (3 lượt/ngày, ADR-099): quét một lần = mất một lượt vào thật. Khoá dòng lượt `FOR UPDATE` như lệnh vào
thật nên quét và vào song song không vượt trần. Trừ vàng, cộng lượt, cộng EXP, audit `DUNGEON_SWEPT` trong MỘT
transaction; thiếu vàng thì không mất lượt.

**D3 — Thưởng**: EXP cộng thẳng vào `character.exp` rồi gateway đồng bộ cấp như thưởng quest/bế quan
(`syncExperienceAfterQuestReward` + `EXPERIENCE_CHANGED_EVENT`). Đồ: tung bảng rơi qua `InventoryFacade.grantLoot(…,
divert = mọi chồng)` (cùng hệ số sự kiện x2/quẻ bói như hạ thật) nhưng KHÔNG cho vào túi — gửi **thư hệ thống** (túi đầy
không mất đồ). Ghi hoạt động `DUNGEON_RUN` (nhiệm vụ ngày/tuần đếm như một lượt thật).

**D4 — Giao thức, chỉ thêm**: `SweepDungeonCommand{dungeon_code}` / `SweepDungeonResponse{outcome, exp_gained,
entries_left, loot[]}` — 1510/1511 (dải Bí Cảnh). Outcome: OK / UNKNOWN_DUNGEON / NOT_CLEARED / DAILY_LIMIT_REACHED /
NOT_ENOUGH_GOLD.

**D5 — Client**: lệnh chat `/quetbicanh` (hoặc `/sweep`) quét Bí Cảnh hiện có (`DungeonController.DUNGEON_CODE`); kết
quả một dòng hệ thống (EXP, lượt còn, đồ gửi qua thư).

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Quét = cần đã hạ boss, tốn một lượt ngày chung + phí vàng, EXP cộng thẳng, đồ bảng rơi boss đi bằng thư; 1510/1511 | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-03 (lô 29): V130, `DungeonSweepPort`/`DungeonSweepFacade` (feature-world),
   `PostgresDungeonSweepAdapter`, `DungeonSweepCommands` 1510/1511, `DungeonSweepCoordinator` `/quetbicanh`.
2. ✅ Test Postgres thật: `DungeonSweepIntegrationTest` (3 ô: chưa hạ boss; trừ vàng + lượt + EXP tới khi hết lượt; thiếu
   vàng không mất lượt + mã lạ).
3. ✅ Kiểm sống gameProbe 2026-11-03: chưa hạ boss ⇒ `NOT_CLEARED`; ghi lần đầu hạ boss rồi `--lot21=sweep×4` ⇒ ba lần
   `OK exp=400` (còn 2/1/0, `EXPERIENCE_CHANGED_EVENT` 19021 → 20221), lần bốn `DAILY_LIMIT_REACHED`; vàng 5.000 → 2.000;
   ba thư "Quét Bí Cảnh" mang đồ bảng rơi boss.
4. Chờ chủ dự án: phí/EXP (PLACEHOLDER); quét có nên tốn lượt CHUNG với lượt vào thật (đang chung) hay lượt riêng.
