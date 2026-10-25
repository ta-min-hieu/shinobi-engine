-- Lô 22 phần 2 (2026-10-25): câu cá + khai khoáng (ADR-107), sổ sưu tầm yêu thú (ADR-108), danh vọng tông môn
-- (ADR-109), kết nghĩa huynh đệ (ADR-110).

-- ADR-107 — cùng cơ chế với bụi linh thảo (ADR-098): một điểm thu thập có LOẠI và số giây đứng thu.
ALTER TABLE herb_node
    ADD COLUMN kind           VARCHAR(8) NOT NULL DEFAULT 'HERB' CHECK (kind IN ('HERB', 'FISH', 'ORE')),
    ADD COLUMN gather_seconds INT        NOT NULL DEFAULT 3 CHECK (gather_seconds BETWEEN 1 AND 30);

INSERT INTO item_template (code, name_key, max_stack)
SELECT v.code, v.name_key, 99
  FROM (VALUES ('spirit_fish', 'item.spirit_fish.name'), ('spirit_ore', 'item.spirit_ore.name')) AS v (code, name_key)
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = v.code);

INSERT INTO herb_node (code, x, y, item_template_id, quantity, respawn_seconds, kind, gather_seconds)
SELECT v.code, v.x, 0, it.id, v.qty, 240, v.kind, v.secs
  FROM (VALUES ('sect_pond_fishing',      2.0,   'spirit_fish', 1, 'FISH', 5),
               ('east_ridge_fishing',     107.0, 'spirit_fish', 2, 'FISH', 5),
               ('ringleader_ridge_ore',   144.0, 'spirit_ore',  1, 'ORE',  4),
               ('trial_approach_ore',     207.0, 'spirit_ore',  2, 'ORE',  4)) AS v (code, x, item_code, qty, kind, secs)
  JOIN item_template it ON it.code = v.item_code
ON CONFLICT (code) DO NOTHING;

INSERT INTO crafting_recipe (code, output_item_template_id, output_quantity, gold_cost, required_level, sort_order)
SELECT v.code, it.id, v.qty, v.gold, v.lvl, v.sort
  FROM (VALUES ('minor_healing_pill_fish',       'minor_healing_pill',       3, 10,  1,  6),
               ('enhance_protection_charm_ore',  'enhance_protection_charm', 1, 500, 15, 7))
       AS v (code, output_code, qty, gold, lvl, sort)
  JOIN item_template it ON it.code = v.output_code
ON CONFLICT (code) DO NOTHING;

INSERT INTO crafting_recipe_material (recipe_id, item_template_id, quantity)
SELECT r.id, it.id, v.qty
  FROM (VALUES ('minor_healing_pill_fish',      'spirit_fish', 2),
               ('minor_healing_pill_fish',      'spirit_herb', 1),
               ('enhance_protection_charm_ore', 'spirit_ore',  5),
               ('enhance_protection_charm_ore', 'spirit_herb', 2)) AS v (recipe_code, material_code, qty)
  JOIN crafting_recipe r ON r.code = v.recipe_code
  JOIN item_template it ON it.code = v.material_code
ON CONFLICT DO NOTHING;

-- ADR-108 — THAM SỐ: mỗi loài quái thường một trang. Boss không vào sổ.
CREATE TABLE bestiary_entry (
    monster_code   VARCHAR(32) PRIMARY KEY,
    kills_required INT         NOT NULL CHECK (kills_required > 0),
    bonus_stat     VARCHAR(4)  NOT NULL CHECK (bonus_stat IN ('ATK', 'DEF', 'RES', 'HP')),
    bonus_value    INT         NOT NULL CHECK (bonus_value > 0),
    lore_key       VARCHAR(64) NOT NULL,
    sort_order     INT         NOT NULL
);

INSERT INTO bestiary_entry (monster_code, kills_required, bonus_stat, bonus_value, lore_key, sort_order) VALUES
    ('training_dummy',        50,  'HP',  10, 'bestiary.training_dummy.lore',        1),
    ('wandering_bandit',      50,  'ATK', 1,  'bestiary.wandering_bandit.lore',      2),
    ('archive_warden',        60,  'DEF', 1,  'bestiary.archive_warden.lore',        3),
    ('stone_ogre',            60,  'HP',  15, 'bestiary.stone_ogre.lore',            4),
    ('blood_sand_scout',      80,  'ATK', 1,  'bestiary.blood_sand_scout.lore',      5),
    ('trial_construct',       80,  'RES', 1,  'bestiary.trial_construct.lore',       6),
    ('vein_raider',           100, 'DEF', 1,  'bestiary.vein_raider.lore',           7),
    ('raider_chieftain',      100, 'ATK', 2,  'bestiary.raider_chieftain.lore',      8),
    ('blood_sand_acolyte',    120, 'RES', 1,  'bestiary.blood_sand_acolyte.lore',    9),
    ('blood_sand_ringleader', 150, 'HP',  20, 'bestiary.blood_sand_ringleader.lore', 10),
    ('wasteland_beast',       150, 'DEF', 2,  'bestiary.wasteland_beast.lore',       11),
    ('foundation_warden',     200, 'ATK', 2,  'bestiary.foundation_warden.lore',     12);

CREATE TABLE character_bestiary (
    character_id BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    monster_code VARCHAR(32) NOT NULL REFERENCES bestiary_entry (monster_code),
    kills        INT         NOT NULL CHECK (kills >= 0),
    unlocked_at  TIMESTAMPTZ,
    PRIMARY KEY (character_id, monster_code)
);

-- ADR-109 — bậc theo earned (không tụt khi tiêu); điểm tiêu được = earned − spent.
CREATE TABLE character_reputation (
    character_id BIGINT PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    earned       BIGINT NOT NULL DEFAULT 0 CHECK (earned >= 0),
    spent        BIGINT NOT NULL DEFAULT 0 CHECK (spent >= 0 AND spent <= earned)
);

CREATE TABLE reputation_shop_item (
    item_template_id BIGINT PRIMARY KEY REFERENCES item_template (id),
    price            INT    NOT NULL CHECK (price > 0),
    required_tier    INT    NOT NULL CHECK (required_tier BETWEEN 0 AND 4),
    sort_order       INT    NOT NULL
);

INSERT INTO reputation_shop_item (item_template_id, price, required_tier, sort_order)
SELECT it.id, v.price, v.tier, v.sort
  FROM (VALUES ('minor_healing_pill',       20,  0, 1),
               ('teleport_talisman',        60,  1, 2),
               ('spirit_beast_pill',        150, 2, 3),
               ('enhance_protection_charm', 250, 3, 4),
               ('bag_expansion_pouch',      400, 4, 5)) AS v (code, price, tier, sort)
  JOIN item_template it ON it.code = v.code
ON CONFLICT DO NOTHING;

-- ADR-110 — khoá chính character_id chặn một người ở hai nhóm.
CREATE TABLE sworn_group (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE sworn_member (
    character_id BIGINT      PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    group_id     BIGINT      NOT NULL REFERENCES sworn_group (id) ON DELETE CASCADE,
    joined_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX sworn_member_group_idx ON sworn_member (group_id);
