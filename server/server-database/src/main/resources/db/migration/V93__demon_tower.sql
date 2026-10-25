-- ADR-113 — Trấn Yêu Tháp. Tầng và thưởng là DỮ LIỆU (Điều 27): 20 tầng, mỗi tầng 1–3 con của MỘT loài đã có, mạnh
-- dần theo tầng; thưởng thư lần đầu qua tầng 5/10/15/20.

CREATE TABLE tower_floor (
    floor         INT         PRIMARY KEY CHECK (floor > 0),
    monster_code  VARCHAR(32) NOT NULL REFERENCES monster_template (code),
    monster_count INT         NOT NULL CHECK (monster_count BETWEEN 1 AND 3)
);

INSERT INTO tower_floor (floor, monster_code, monster_count) VALUES
    (1,  'training_dummy',        2),
    (2,  'wandering_bandit',      1),
    (3,  'wandering_bandit',      2),
    (4,  'archive_warden',        1),
    (5,  'stone_ogre',            1),
    (6,  'blood_sand_scout',      2),
    (7,  'trial_construct',       2),
    (8,  'stone_ogre',            2),
    (9,  'vein_raider',           2),
    (10, 'raider_chieftain',      1),
    (11, 'blood_sand_enforcer',   2),
    (12, 'vein_raider',           3),
    (13, 'raider_chieftain',      2),
    (14, 'blood_sand_acolyte',    1),
    (15, 'blood_sand_ringleader', 1),
    (16, 'blood_sand_acolyte',    2),
    (17, 'wasteland_beast',       1),
    (18, 'blood_sand_ringleader', 2),
    (19, 'foundation_warden',     1),
    (20, 'foundation_warden',     2);

CREATE TABLE tower_reward (
    floor            INT    PRIMARY KEY REFERENCES tower_floor (floor),
    gold             BIGINT NOT NULL CHECK (gold >= 0),
    item_template_id BIGINT REFERENCES item_template (id),
    quantity         INT    NOT NULL DEFAULT 0 CHECK (quantity >= 0)
);

INSERT INTO tower_reward (floor, gold, item_template_id, quantity)
SELECT v.floor, v.gold, it.id, v.quantity
  FROM (VALUES (5,  1000,  'refine_stone',             2),
               (10, 3000,  'spirit_jade_fire',         1),
               (15, 6000,  'enhance_protection_charm', 1),
               (20, 10000, 'refine_stone',             10)) AS v (floor, gold, code, quantity)
  JOIN item_template it ON it.code = v.code;

CREATE TABLE character_tower (
    character_id BIGINT      PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    best_floor   INT         NOT NULL CHECK (best_floor >= 0),
    updated_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX character_tower_best ON character_tower (best_floor DESC, updated_at);
