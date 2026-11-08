# ADR-255: Xem thông tin quái

| | |
|---|---|
| Status | ✅ **Accepted** (2026-11-08) — chủ dự án chọn "Xem thông tin quái" trong lượt hỏi lô 41 (AskUserQuestion; mô tả kèm: /xemquai <tên>: cấp, máu, EXP và các món có thể rơi của một loại quái). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-11-08 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-048](ADR-048-Boss-Into-Data-Tables.md) (boss vào `monster_template`) · [ADR-204](ADR-204-Loot-Filter.md) |

---

## Decision

**D1 — Thông tin công khai, chỉ đọc.** `GetMonsterInfoRequest{monster_code}` / `GetMonsterInfoResponse{found, monster_code,
name_key, level, max_hp, atk, def, exp_reward, boss, repeated MonsterDropView{item_template_id, chance_percent,
min_quantity, max_quantity}}` 416/417 (dải Combat). Đọc `monster_template` + `drop_table_entry` — đúng con số server
dùng khi đánh và khi rơi đồ. Không lộ thông số AI (tầm đuổi, kỹ năng…).

**D2 — Lệch so với mô tả lúc hỏi:** tra theo **mã quái**, không theo tên gõ tay — server chỉ biết khoá tên i18n, còn
tên hiển thị nằm ở bundle client. Bù lại: `/xemquai` không kèm gì xem **con quái đang nhắm** (mã lấy từ HUD chiến đấu),
đúng cách người chơi hay dùng nhất; `/xemquai <mã>` cho ai biết mã. Client in tên quái và tên vật phẩm theo bundle.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | 416/417 đọc `monster_template` + `drop_table_entry`; `/xemquai` (mục tiêu đang nhắm) hoặc `/xemquai <mã>` | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ✅ Thi hành 2026-11-08 (lô 41): `MonsterInfoPort`/`MonsterInfoFacade`, `PostgresMonsterInfoAdapter`, `Lot41Commands.monsterInfo`, `MonsterInfoCoordinator`.
2. ✅ Test: `Lot41IntegrationTest.readsSeededMonsterWithDrops`.
3. ✅ Kiểm sống gameProbe 2026-11-08: `training_dummy` ⇒ cấp 1, máu 50, 20 EXP, rơi mẫu 1 @50%, 17 @30%, 6 @5% (khớp `drop_table_entry`); mã lạ ⇒ `found=false`.
