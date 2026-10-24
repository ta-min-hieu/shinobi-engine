-- ADR-088 — sức chứa túi đồ theo nhân vật (20 → tối đa 40, +5 mỗi Túi Càn Khôn). Mọi nhân vật hiện có giữ 20.
ALTER TABLE character_stat ADD COLUMN bag_capacity INT NOT NULL DEFAULT 20 CHECK (bag_capacity BETWEEN 20 AND 40);

INSERT INTO item_template (code, name_key, max_stack, use_effect, use_value)
SELECT 'bag_expansion_pouch', 'item.bag_expansion_pouch.name', 10, 'EXPAND_BAG', 5
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'bag_expansion_pouch');

-- Giá là THAM SỐ (Điều 27): đắt — bốn túi là cả một chặng tích luỹ.
INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 25000, 2500 FROM item_template WHERE code = 'bag_expansion_pouch'
   AND NOT EXISTS (SELECT 1 FROM shop_item si JOIN item_template it ON it.id = si.item_template_id
                    WHERE it.code = 'bag_expansion_pouch');
