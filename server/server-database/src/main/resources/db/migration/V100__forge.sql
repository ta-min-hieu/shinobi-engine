-- V100 — luyện khí (ADR-120, lô 23). Dùng lại bảng công thức của luyện đan (V76) với cột loại mới; công thức luyện khí
-- ra ĐÚNG MỘT món trang bị, server tung phẩm chất thường/tốt/hiếm (70/25/5) ⇒ 0/1/2 dòng tẩy luyện gieo sẵn (ADR-111).
-- Hai món Lv20 nằm giữa bộ Ngoại Môn (Lv15) và Trúc Cơ (Lv30). Chỉ số, nguyên liệu, linh thạch là ĐỀ XUẤT CÂN BẰNG.
ALTER TABLE crafting_recipe
    ADD COLUMN kind VARCHAR(8) NOT NULL DEFAULT 'ALCHEMY' CHECK (kind IN ('ALCHEMY', 'FORGE'));

ALTER TABLE crafting_recipe
    ADD CONSTRAINT crafting_recipe_forge_single_output CHECK (kind <> 'FORGE' OR output_quantity = 1);

INSERT INTO item_template (code, name_key, max_stack, equip_slot, bonus_atk, bonus_def, bonus_res, bonus_hp, required_level)
SELECT * FROM (VALUES
        ('spirit_iron_sword', 'item.spirit_iron_sword.name', 1, 'WEAPON', 27,  0, 0,  0, 20),
        ('spirit_iron_robe',  'item.spirit_iron_robe.name',  1, 'ARMOR',   0, 14, 9, 30, 20)
    ) AS incoming (code, name_key, max_stack, equip_slot, bonus_atk, bonus_def, bonus_res, bonus_hp, required_level)
 WHERE NOT EXISTS (SELECT 1 FROM item_template it WHERE it.code = incoming.code);

INSERT INTO crafting_recipe (code, output_item_template_id, output_quantity, gold_cost, required_level, sort_order, kind)
SELECT v.code, it.id, 1, v.gold, 20, v.sort, 'FORGE'
  FROM (VALUES ('forge_spirit_iron_sword', 'spirit_iron_sword', 600, 101),
               ('forge_spirit_iron_robe',  'spirit_iron_robe',  500, 102)) AS v (code, item_code, gold, sort)
  JOIN item_template it ON it.code = v.item_code
ON CONFLICT (code) DO NOTHING;

INSERT INTO crafting_recipe_material (recipe_id, item_template_id, quantity)
SELECT r.id, it.id, v.qty
  FROM (VALUES ('forge_spirit_iron_sword', 'spirit_ore',  8),
               ('forge_spirit_iron_sword', 'demon_core',  2),
               ('forge_spirit_iron_robe',  'spirit_ore',  6),
               ('forge_spirit_iron_robe',  'spirit_fish', 4)) AS v (recipe_code, material_code, qty)
  JOIN crafting_recipe r ON r.code = v.recipe_code
  JOIN item_template it ON it.code = v.material_code
ON CONFLICT DO NOTHING;
