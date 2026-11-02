-- V119 — Tàng Bảo Đồ (ADR-147, lô 27). Quái world chính đôi khi rơi một tấm bản đồ; đọc bản đồ thì server chọn ngẫu
-- nhiên MỘT điểm chôn (treasure_spot) và ghi vào character_treasure_hunt (mỗi nhân vật tối đa một cuộc tìm đang mở);
-- tới đúng chỗ (≤ 2,5 m theo trục X) rồi đào thì server tung MỘT phần thưởng theo trọng số (treasure_reward), có phần
-- thưởng còn gọi quái canh kho của điểm đó ra. Mọi số là PLACEHOLDER (Điều 27) — chỉnh bằng migration sau.

INSERT INTO item_template (code, name_key, max_stack)
SELECT 'treasure_map', 'item.treasure_map.name', 20
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'treasure_map');

-- Điểm chôn: dọc trục chính world chính, NGOÀI hai dải PvP (179–205, 307–417 — ADR-137/141) để đi đào không thành
-- mồi cho Ma Đầu. guardian_monster_code = loài canh kho của vùng đó (một con, không hồi sinh — như kỳ ngộ ADR-121 D4).
CREATE TABLE treasure_spot (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                  VARCHAR(48)  NOT NULL UNIQUE,
    name_key              VARCHAR(128) NOT NULL,
    x                     REAL         NOT NULL,
    y                     REAL         NOT NULL DEFAULT 0 CHECK (y = 0),
    guardian_monster_code VARCHAR(32)  NOT NULL
);

INSERT INTO treasure_spot (code, name_key, x, guardian_monster_code) VALUES
    ('old_pine_west',      'treasure.spot.old_pine_west',      -35, 'wandering_bandit'),
    ('broken_stele_field', 'treasure.spot.broken_stele_field',  40, 'wandering_bandit'),
    ('spirit_vein_rocks',  'treasure.spot.spirit_vein_rocks',   95, 'stone_ogre'),
    ('east_ridge_hollow',  'treasure.spot.east_ridge_hollow',  118, 'stone_ogre'),
    ('ringleader_cairn',   'treasure.spot.ringleader_cairn',   152, 'stone_ogre'),
    ('trial_road_shrine',  'treasure.spot.trial_road_shrine',  225, 'stone_ogre');

CREATE TABLE treasure_reward (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                    VARCHAR(48)  NOT NULL UNIQUE,
    weight                  INT          NOT NULL CHECK (weight > 0),
    result_key              VARCHAR(128) NOT NULL,
    reward_gold             BIGINT       NOT NULL DEFAULT 0 CHECK (reward_gold >= 0),
    reward_item_template_id BIGINT       REFERENCES item_template (id),
    reward_quantity         INT          NOT NULL DEFAULT 0 CHECK (reward_quantity >= 0),
    spawns_guardian         BOOLEAN      NOT NULL DEFAULT FALSE,
    CHECK ((reward_item_template_id IS NULL) = (reward_quantity = 0))
);

INSERT INTO treasure_reward
    (code, weight, result_key, reward_gold, reward_item_template_id, reward_quantity, spawns_guardian)
SELECT v.code, v.weight, v.result_key, v.gold, it.id, v.qty, v.guardian
  FROM (VALUES
        (1, 'small_cache',   40, 'treasure.result.small_cache',   250, NULL,                       0, FALSE),
        (2, 'rich_cache',    12, 'treasure.result.rich_cache',    800, NULL,                       0, FALSE),
        (3, 'pill_cache',    20, 'treasure.result.pill_cache',      0, 'minor_healing_pill',       3, FALSE),
        (4, 'herb_cache',    10, 'treasure.result.herb_cache',      0, 'spirit_herb',              5, FALSE),
        (5, 'charm_cache',   10, 'treasure.result.charm_cache',     0, 'enhance_protection_charm', 1, FALSE),
        (6, 'guarded_cache',  8, 'treasure.result.guarded_cache', 400, NULL,                       0, TRUE)
    ) AS v (ord, code, weight, result_key, gold, item_code, qty, guardian)
  LEFT JOIN item_template it ON it.code = v.item_code
 ORDER BY v.ord;  -- id theo đúng thứ tự trên (JOIN không giữ thứ tự VALUES)

-- Một cuộc tìm đang mở mỗi nhân vật (PRIMARY KEY character_id). Không có hạn: đăng nhập lại vẫn còn.
CREATE TABLE character_treasure_hunt (
    character_id BIGINT      PRIMARY KEY REFERENCES character (id),
    spot_id      BIGINT      NOT NULL REFERENCES treasure_spot (id),
    started_at   TIMESTAMPTZ NOT NULL
);

-- Nguồn bản đồ: ba loài quái đầu arc 1–2 ở world chính.
INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, v.chance, 1, 1
  FROM (VALUES ('wandering_bandit', 6),
               ('archive_warden',   8),
               ('stone_ogre',      10)) AS v (dt_code, chance)
  JOIN drop_table dt ON dt.code = v.dt_code
  JOIN item_template it ON it.code = 'treasure_map'
 WHERE NOT EXISTS (SELECT 1 FROM drop_table_entry e WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);
