-- V9 — item "Bảo Vệ" chống rớt cấp cường hoá (GAMEPLAY.md §7.1, feature-inventory/CLAUDE.md).
-- Tiêu hao, KHÔNG mang được (equip_slot NULL) — tra theo item_template.code
-- ('enhance_protection_charm') qua ItemTemplatePort.findIdByCode, không hardcode ID số.
INSERT INTO item_template (code, name_key, max_stack)
VALUES ('enhance_protection_charm', 'item.enhance_protection_charm.name', 99);

-- Bán ở shop toàn cục (feature-shop, V8) — giá PLACEHOLDER (GDD chưa chốt cân bằng kinh tế, cùng
-- tinh thần các seed giá khác ở V8).
INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 3000, 500 FROM item_template WHERE code = 'enhance_protection_charm';
