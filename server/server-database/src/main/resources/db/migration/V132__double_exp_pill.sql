-- ADR-164 (2026-11-03) — Đan x2 kinh nghiệm. Dùng một viên ⇒ EXP đánh quái nhân đôi trong use_value phút; dùng tiếp
-- thì CỘNG DỒN thời gian, tới trần 300 phút (code: InventoryPort#extendDoubleExp). Mọi số là THAM SỐ (Điều 27).

CREATE TABLE character_exp_buff (
    character_id BIGINT      PRIMARY KEY REFERENCES character (id),
    expires_at   TIMESTAMPTZ NOT NULL
);

INSERT INTO item_template (code, name_key, max_stack, use_effect, use_value)
SELECT 'double_exp_pill', 'item.double_exp_pill.name', 20, 'DOUBLE_EXP', 30
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'double_exp_pill');

-- Nguồn: cửa hàng tông môn (đắt) + boss thế giới (50%).
INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 3000, 300 FROM item_template WHERE code = 'double_exp_pill'
   AND NOT EXISTS (SELECT 1 FROM shop_item si JOIN item_template it ON it.id = si.item_template_id
                    WHERE it.code = 'double_exp_pill');

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT d.id, it.id, 50, 1, 1
  FROM drop_table d JOIN item_template it ON it.code = 'double_exp_pill'
 WHERE d.code = 'demon_king';
