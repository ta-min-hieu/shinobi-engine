-- V123 — Truyền Âm Phù (ADR-152, lô 28). Một phù = một dòng loa thế giới tới mọi người online (qua lọc từ cấm, khoá
-- chat, hồi chiêu 30 giây). Giá là PLACEHOLDER (Điều 27).
INSERT INTO item_template (code, name_key, max_stack)
SELECT 'broadcast_talisman', 'item.broadcast_talisman.name', 20
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'broadcast_talisman');

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 2000, 200 FROM item_template WHERE code = 'broadcast_talisman'
   AND NOT EXISTS (SELECT 1 FROM shop_item si JOIN item_template it ON it.id = si.item_template_id
                    WHERE it.code = 'broadcast_talisman');
