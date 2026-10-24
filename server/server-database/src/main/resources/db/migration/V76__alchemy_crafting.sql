-- ADR-079 — luyện đan. Công thức + nguyên liệu là THAM SỐ (Điều 27): giá, số lượng, cấp chỉnh bằng SQL.

-- Hai nguyên liệu mới: không dùng, không mặc — chỉ để luyện (và bán lại cửa hàng 1 linh thạch như mọi món vào túi được).
INSERT INTO item_template (code, name_key, max_stack)
SELECT v.code, v.name_key, 99
  FROM (VALUES ('spirit_herb', 'item.spirit_herb.name'),
               ('demon_core',  'item.demon_core.name')) AS v (code, name_key)
 WHERE NOT EXISTS (SELECT 1 FROM item_template it WHERE it.code = v.code);

-- Bất biến của V21 §4: món nào vào túi được thì bán được (tool load-test dựa vào đó). Không bán ra (buy_price NULL
-- không được phép ⇒ giá mua cao để không ai mua thay vì đi săn).
INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT it.id, CASE it.code WHEN 'spirit_herb' THEN 200 ELSE 1500 END, CASE it.code WHEN 'spirit_herb' THEN 5 ELSE 40 END
  FROM item_template it
 WHERE it.code IN ('spirit_herb', 'demon_core')
   AND NOT EXISTS (SELECT 1 FROM shop_item si WHERE si.item_template_id = it.id);

-- Rơi đồ: Linh Thảo từ mọi bảng rơi đang có; Yêu Đan từ bảng của quái mạnh + boss. TRỪ `trial_construct`: cỗ máy
-- khảo hạch cố ý không rơi gì (V33 §1), và bù nhìn tập luyện (đứng yên, đánh mãi không chết người — không phải nơi cày nguyên liệu).
INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 35, 1, 2
  FROM drop_table dt CROSS JOIN item_template it
 WHERE it.code = 'spirit_herb'
   AND dt.code NOT IN ('trial_construct', 'training_dummy')
   AND NOT EXISTS (SELECT 1 FROM drop_table_entry e WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);

INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, CASE WHEN dt.code = 'sample_boss' THEN 100 ELSE 20 END, 1, CASE WHEN dt.code = 'sample_boss' THEN 3 ELSE 1 END
  FROM drop_table dt CROSS JOIN item_template it
 WHERE it.code = 'demon_core'
   AND dt.code IN ('stone_ogre', 'wandering_bandit', 'blood_sand_enforcer', 'archive_warden', 'sample_boss')
   AND NOT EXISTS (SELECT 1 FROM drop_table_entry e WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);

CREATE TABLE crafting_recipe (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                    VARCHAR(64) NOT NULL UNIQUE,
    output_item_template_id BIGINT      NOT NULL REFERENCES item_template (id),
    output_quantity         INT         NOT NULL CHECK (output_quantity >= 1),
    gold_cost               BIGINT      NOT NULL CHECK (gold_cost >= 0),
    required_level          INT         NOT NULL CHECK (required_level >= 1),
    sort_order              INT         NOT NULL DEFAULT 0
);

CREATE TABLE crafting_recipe_material (
    recipe_id        BIGINT NOT NULL REFERENCES crafting_recipe (id) ON DELETE CASCADE,
    item_template_id BIGINT NOT NULL REFERENCES item_template (id),
    quantity         INT    NOT NULL CHECK (quantity >= 1),
    PRIMARY KEY (recipe_id, item_template_id)
);

INSERT INTO crafting_recipe (code, output_item_template_id, output_quantity, gold_cost, required_level, sort_order)
SELECT v.code, it.id, v.qty, v.gold, v.lvl, v.sort
  FROM (VALUES ('minor_healing_pill', 'minor_healing_pill', 3, 10,   1,  1),
               ('spirit_jade_fire',   'spirit_jade_fire',   1, 300,  15, 2),
               ('spirit_jade_water',  'spirit_jade_water',  1, 300,  15, 3),
               ('spirit_jade_wood',   'spirit_jade_wood',   1, 300,  15, 4),
               ('spirit_beast_pill',  'spirit_beast_pill',  1, 3000, 20, 5))
       AS v (code, output_code, qty, gold, lvl, sort)
  JOIN item_template it ON it.code = v.output_code;

INSERT INTO crafting_recipe_material (recipe_id, item_template_id, quantity)
SELECT r.id, it.id, v.qty
  FROM (VALUES ('minor_healing_pill', 'spirit_herb', 2),
               ('spirit_jade_fire',   'demon_core',  3),
               ('spirit_jade_fire',   'spirit_herb', 5),
               ('spirit_jade_water',  'demon_core',  3),
               ('spirit_jade_water',  'spirit_herb', 5),
               ('spirit_jade_wood',   'demon_core',  3),
               ('spirit_jade_wood',   'spirit_herb', 5),
               ('spirit_beast_pill',  'demon_core',  10),
               ('spirit_beast_pill',  'spirit_herb', 10)) AS v (recipe_code, material_code, qty)
  JOIN crafting_recipe r ON r.code = v.recipe_code
  JOIN item_template it ON it.code = v.material_code;
