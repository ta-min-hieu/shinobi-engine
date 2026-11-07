-- ADR-203 — Phi Kiếm Tinh Thạch: dùng ⇒ phi kiếm ĐANG MẶC lên một cấp (cấp lưu ở inventory_item.enhance_level, cường
-- hoá thường không đụng ô MOUNT), mỗi cấp +5% tốc độ, tối đa cấp 5, tổng không quá 100%. use_value bắt buộc > 0 theo
-- CHECK của V36 nhưng hiệu ứng này không đọc nó. Giá là THAM SỐ đề xuất (Điều 27).
INSERT INTO item_template (code, name_key, max_stack, use_effect, use_value)
SELECT 'flying_sword_essence', 'item.flying_sword_essence.name', 20, 'UPGRADE_MOUNT', 1
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'flying_sword_essence');

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 6000, 600 FROM item_template WHERE code = 'flying_sword_essence'
ON CONFLICT DO NOTHING;
