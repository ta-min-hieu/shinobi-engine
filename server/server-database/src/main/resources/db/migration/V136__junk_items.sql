-- ADR-170 (2026-11-04) — Bán nhanh đồ rác. Cờ dữ liệu "món này là rác": chỉ đáng để bán. Bộ đầu tiên: phi đao (kunai),
-- sắt vụn, thẻ bài ngoại môn, bùa Huyết Sa — không dùng được, không làm nguyên liệu; hai món sau rơi 100% mãi mãi kể cả
-- khi quest đã xong (V55). Còn đang gom cho nhiệm vụ? Cửa sổ bán rác LIỆT KÊ trước, người chơi bấm mới bán.
ALTER TABLE item_template ADD COLUMN junk BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE item_template SET junk = TRUE
 WHERE code IN ('kunai', 'iron_scrap', 'outer_sect_tally', 'blood_sand_talisman')
   AND EXISTS (SELECT 1 FROM shop_item s WHERE s.item_template_id = item_template.id AND s.sell_price > 0);
