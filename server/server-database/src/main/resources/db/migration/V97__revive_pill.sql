-- V97 — Hồi Hồn Đan (ADR-117, lô 23): gục ở world chính thì nằm chờ tối đa 30 giây; dùng một viên để đứng dậy TẠI CHỖ
-- với 30% máu (không dùng qua "dùng vật phẩm" — không có use_effect; tiêu qua lệnh hồi sinh). Bán ở cửa hàng tông môn.
INSERT INTO item_template (code, name_key, max_stack)
SELECT 'revive_pill', 'item.revive_pill.name', 10
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'revive_pill');

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 300, 75 FROM item_template WHERE code = 'revive_pill'
   AND NOT EXISTS (SELECT 1 FROM shop_item s JOIN item_template t ON t.id = s.item_template_id WHERE t.code = 'revive_pill');
