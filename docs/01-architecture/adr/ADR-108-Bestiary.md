# ADR-108: Sổ sưu tầm yêu thú

| | |
|---|---|
| Status | ✅ **Accepted** (2026-10-25) — chủ dự án chọn "Sổ sưu tầm yêu thú" trong lượt hỏi lô 22 (AskUserQuestion; mô tả kèm: hạ đủ N con mỗi loài mở trang sưu tầm (lore i18n) + chỉ số nhỏ vĩnh viễn; cửa sổ riêng; migration + opcode). AI soạn văn bản và chọn chi tiết sau khi được chọn. |
| Date | 2026-10-25 |
| Deciders | Chủ dự án quyết; AI soạn |
| Related | [ADR-037](ADR-037-Monster-Template-And-Spawn-Tables.md) (quái từ DB) · ADR-089 (Đạo Vận — cùng ống chỉ số cộng phẳng) |

---

## Decision

**D1 — Tham số (V90)**: `bestiary_entry(monster_code PK, kills_required, bonus_stat, bonus_value, lore_key)` — mỗi
loài quái thường một dòng (boss không vào sổ). `bonus_stat` ∈ {`ATK`, `DEF`, `RES`, `HP`}; giá trị nhỏ (1–2 công/thủ/kháng
hoặc 10–20 máu), mốc 50–200 con. PLACEHOLDER.

**D2 — Tiến độ**: `character_bestiary(character_id, monster_code, kills, unlocked_at)`; mỗi cú hạ quái của người ra đòn
cuối (cùng đường thưởng hạ quái, ADR-101) cộng 1 trên Virtual Thread; chạm `kills_required` lần đầu ⇒ `unlocked_at`
+ một dòng hệ thống "Mở trang sưu tầm …" + áp lại chỉ số ngay (cùng đường linh thú lên cấp).

**D3 — Chỉ số**: cộng phẳng vào ống chỉ số trang bị như Đạo Vận (`BestiaryEffects.plus`), vĩnh viễn, chỉ trang ĐÃ mở.

**D4 — Giao thức, chỉ thêm**: 226/227 `GetBestiaryRequest`/`Response{entries[]}` (mã quái, tên/lore key, đã hạ / cần,
mở chưa, thưởng).

**D5 — Client**: cửa sổ "Sổ sưu tầm" phím **N**: mỗi loài một dòng; trang đã mở hiện lời lore.

## Technical Decisions

| # | Quyết định | Trạng thái |
|---|---|---|
| 1 | Sổ theo loài, mốc hạ, chỉ số cộng phẳng vĩnh viễn, cửa sổ phím N | Accepted |

## ADR cần tạo

- Không.

## Tài liệu liên quan

- [adr/README.md](README.md)

## Việc cần làm tiếp theo

1. ⏳ Thi hành (lô 22 phần 2).
2. ⏳ Kiểm sống bằng gameProbe.
