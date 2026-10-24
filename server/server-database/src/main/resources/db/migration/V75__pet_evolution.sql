-- ADR-078 — linh thú tiến hoá. Bậc 1 = như cũ; bậc 2 nhân chỉ số linh thú 150% (PetPolicy). Dòng cũ nhận bậc 1.
ALTER TABLE pet_state ADD COLUMN stage INT NOT NULL DEFAULT 1 CHECK (stage BETWEEN 1 AND 2);

-- Linh Thú Đan: dùng (UseItemCommand) ⇒ linh thú ĐANG MẶC cấp >= 20 lên bậc 2. use_value bắt buộc > 0 theo CHECK
-- của V36 nhưng hiệu ứng này không đọc nó. Giá là THAM SỐ (Điều 27).
INSERT INTO item_template (code, name_key, max_stack, use_effect, use_value)
SELECT 'spirit_beast_pill', 'item.spirit_beast_pill.name', 10, 'EVOLVE_PET', 1
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'spirit_beast_pill');

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 20000, 2000 FROM item_template WHERE code = 'spirit_beast_pill'
   AND NOT EXISTS (SELECT 1 FROM shop_item si JOIN item_template it ON it.id = si.item_template_id
                    WHERE it.code = 'spirit_beast_pill');
