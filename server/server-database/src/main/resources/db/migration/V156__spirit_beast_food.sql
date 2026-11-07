-- ADR-201 — Linh Thú Thực: dùng ⇒ linh thú ĐANG MẶC nhận use_value EXP (qua cấp như EXP đánh quái, PetPolicy.gain).
-- Giá và lượng EXP là THAM SỐ đề xuất (Điều 27): 200 EXP, 600 linh thạch — một con cấp 1→10 cần 4.500 EXP ≈ 23 cái.
INSERT INTO item_template (code, name_key, max_stack, use_effect, use_value)
SELECT 'spirit_beast_food', 'item.spirit_beast_food.name', 99, 'FEED_PET', 200
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'spirit_beast_food');

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 600, 60 FROM item_template WHERE code = 'spirit_beast_food'
ON CONFLICT DO NOTHING;
