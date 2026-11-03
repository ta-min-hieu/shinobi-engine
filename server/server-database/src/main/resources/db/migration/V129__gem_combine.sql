-- ADR-161 (2026-11-03) — Ghép Linh Ngọc. Hai bậc mới cho mỗi loại ngọc (bậc 2, bậc 3) và công thức ghép: 3 viên cùng
-- loại cùng bậc + vàng ⇒ 1 viên bậc trên, 100% thành công. Mọi con số là THAM SỐ (Điều 27) — PLACEHOLDER cân bằng.

INSERT INTO item_template (code, name_key, max_stack)
SELECT v.code, v.name_key, 99
  FROM (VALUES ('spirit_jade_fire_2',  'item.spirit_jade_fire_2.name'),
               ('spirit_jade_earth_2', 'item.spirit_jade_earth_2.name'),
               ('spirit_jade_water_2', 'item.spirit_jade_water_2.name'),
               ('spirit_jade_wood_2',  'item.spirit_jade_wood_2.name'),
               ('spirit_jade_fire_3',  'item.spirit_jade_fire_3.name'),
               ('spirit_jade_earth_3', 'item.spirit_jade_earth_3.name'),
               ('spirit_jade_water_3', 'item.spirit_jade_water_3.name'),
               ('spirit_jade_wood_3',  'item.spirit_jade_wood_3.name')) AS v (code, name_key)
 WHERE NOT EXISTS (SELECT 1 FROM item_template it WHERE it.code = v.code);

-- Bậc 2 ≈ ×2,7 bậc 1 (3 viên ghép ra một, cộng phí ⇒ hơi lời hơn khảm 3 viên rời — mà một món chỉ có MỘT ô khảm).
-- Bậc 3 ≈ ×2,5 bậc 2.
INSERT INTO gem_template (item_template_id, bonus_atk, bonus_def, bonus_res, bonus_hp)
SELECT id,
       CASE code WHEN 'spirit_jade_fire_2'  THEN 8  WHEN 'spirit_jade_fire_3'  THEN 20  ELSE 0 END,
       CASE code WHEN 'spirit_jade_earth_2' THEN 8  WHEN 'spirit_jade_earth_3' THEN 20  ELSE 0 END,
       CASE code WHEN 'spirit_jade_water_2' THEN 8  WHEN 'spirit_jade_water_3' THEN 20  ELSE 0 END,
       CASE code WHEN 'spirit_jade_wood_2'  THEN 40 WHEN 'spirit_jade_wood_3'  THEN 100 ELSE 0 END
  FROM item_template
 WHERE code LIKE 'spirit\_jade\_%\_2' OR code LIKE 'spirit\_jade\_%\_3';

-- Một công thức mỗi viên NGUỒN (PK): bậc 1 → 2 → 3. Bậc 3 là trần (không có công thức).
CREATE TABLE gem_combine_recipe (
    source_item_template_id BIGINT PRIMARY KEY REFERENCES gem_template (item_template_id),
    result_item_template_id BIGINT NOT NULL REFERENCES gem_template (item_template_id),
    source_quantity         INT    NOT NULL DEFAULT 3 CHECK (source_quantity >= 2),
    gold_fee                BIGINT NOT NULL CHECK (gold_fee >= 0),
    sort_order              INT    NOT NULL,
    CHECK (source_item_template_id <> result_item_template_id)
);

INSERT INTO gem_combine_recipe (source_item_template_id, result_item_template_id, source_quantity, gold_fee, sort_order)
SELECT src.id, dst.id, 3, v.fee, v.sort_order
  FROM (VALUES ('spirit_jade_fire',    'spirit_jade_fire_2',  2000, 1),
               ('spirit_jade_earth',   'spirit_jade_earth_2', 2000, 2),
               ('spirit_jade_water',   'spirit_jade_water_2', 2000, 3),
               ('spirit_jade_wood',    'spirit_jade_wood_2',  2000, 4),
               ('spirit_jade_fire_2',  'spirit_jade_fire_3',  8000, 5),
               ('spirit_jade_earth_2', 'spirit_jade_earth_3', 8000, 6),
               ('spirit_jade_water_2', 'spirit_jade_water_3', 8000, 7),
               ('spirit_jade_wood_2',  'spirit_jade_wood_3',  8000, 8)) AS v (src_code, dst_code, fee, sort_order)
  JOIN item_template src ON src.code = v.src_code
  JOIN item_template dst ON dst.code = v.dst_code;
