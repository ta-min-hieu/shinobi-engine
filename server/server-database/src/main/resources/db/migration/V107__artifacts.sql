-- V107 — pháp bảo (ADR-127, lô 24). Ô trang bị MỚI 'ARTIFACT' (cột equip_slot là chữ, không có CHECK — như MOUNT/PET
-- ở V71). Mỗi pháp bảo cộng chỉ số phẳng như mọi trang bị + MỘT chiêu chủ động (bảng artifact_skill), luyện ở lò
-- luyện khí (ADR-120, có tung phẩm chất). Mọi con số là ĐỀ XUẤT CÂN BẰNG.
INSERT INTO item_template (code, name_key, max_stack, equip_slot, bonus_atk, bonus_def, bonus_res, bonus_hp, required_level)
SELECT * FROM (VALUES
        ('golden_bell_artifact',   'item.golden_bell_artifact.name',   1, 'ARTIFACT', 0, 8, 8, 20, 20),
        ('spring_mirror_artifact', 'item.spring_mirror_artifact.name', 1, 'ARTIFACT', 0, 4, 10, 30, 30),
        ('wind_fan_artifact',      'item.wind_fan_artifact.name',      1, 'ARTIFACT', 10, 0, 4, 0, 25)
    ) AS incoming (code, name_key, max_stack, equip_slot, bonus_atk, bonus_def, bonus_res, bonus_hp, required_level)
 WHERE NOT EXISTS (SELECT 1 FROM item_template it WHERE it.code = incoming.code);

CREATE TABLE artifact_skill (
    item_template_id  BIGINT      PRIMARY KEY REFERENCES item_template (id),
    effect            VARCHAR(8)  NOT NULL CHECK (effect IN ('SHIELD', 'HEAL', 'SPEED')),
    magnitude_percent INT         NOT NULL CHECK (magnitude_percent BETWEEN 1 AND 100),
    duration_seconds  INT         NOT NULL CHECK (duration_seconds >= 0),
    cooldown_seconds  INT         NOT NULL CHECK (cooldown_seconds >= 1)
);

INSERT INTO artifact_skill (item_template_id, effect, magnitude_percent, duration_seconds, cooldown_seconds)
SELECT it.id, v.effect, v.magnitude, v.duration, v.cooldown
  FROM (VALUES ('golden_bell_artifact',   'SHIELD', 25, 10, 60),
               ('spring_mirror_artifact', 'HEAL',   30,  0, 90),
               ('wind_fan_artifact',      'SPEED',  40,  6, 45)) AS v (code, effect, magnitude, duration, cooldown)
  JOIN item_template it ON it.code = v.code
ON CONFLICT DO NOTHING;

INSERT INTO crafting_recipe (code, output_item_template_id, output_quantity, gold_cost, required_level, sort_order, kind)
SELECT v.code, it.id, 1, v.gold, v.lvl, v.sort, 'FORGE'
  FROM (VALUES ('forge_golden_bell_artifact',   'golden_bell_artifact',   1500, 20, 103),
               ('forge_wind_fan_artifact',      'wind_fan_artifact',      1800, 25, 104),
               ('forge_spring_mirror_artifact', 'spring_mirror_artifact', 2500, 30, 105)) AS v (code, item_code, gold, lvl, sort)
  JOIN item_template it ON it.code = v.item_code
ON CONFLICT (code) DO NOTHING;

INSERT INTO crafting_recipe_material (recipe_id, item_template_id, quantity)
SELECT r.id, it.id, v.qty
  FROM (VALUES ('forge_golden_bell_artifact',   'spirit_ore',      10),
               ('forge_golden_bell_artifact',   'spirit_jade_earth', 1),
               ('forge_wind_fan_artifact',      'spirit_herb',     12),
               ('forge_wind_fan_artifact',      'spirit_jade_wood',  1),
               ('forge_spring_mirror_artifact', 'demon_core',       6),
               ('forge_spring_mirror_artifact', 'spirit_jade_water', 1)) AS v (recipe_code, material_code, qty)
  JOIN crafting_recipe r ON r.code = v.recipe_code
  JOIN item_template it ON it.code = v.material_code
ON CONFLICT DO NOTHING;
