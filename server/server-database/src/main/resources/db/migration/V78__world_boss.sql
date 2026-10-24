-- ADR-081 — boss thế giới định kỳ. Lịch, thưởng theo hạng, chỉ số boss là THAM SỐ (Điều 27).

-- Yêu Vương: boss thế giới đầu tiên — model riêng (tools/blender/build_monster_demon_king.py), mượn script AI
-- của boss mẫu.
INSERT INTO monster_template (
        code, name_key, model_key, level, max_hp, atk, skill_dmg, def, res,
        hit_percent, dodge_percent, crit_percent, element, exp_reward,
        aggro_radius_meters, leash_radius_meters, chase_speed_mps,
        patrol_speed_mps, patrol_radius_meters, idle_duration_seconds,
        is_boss, ai_script_ref, ai_decision_interval_seconds, ai_skill_cooldown_seconds)
SELECT 'demon_king', 'hud.monster.demon_king', 'mob_demon-king', 35, 6000, 55, 0, 30, 25,
       95, 0, 5, 'NONE', 3000,
       6, 15, 3, 1.5, 3, 3,
       true, '/scripts/boss/sample_boss.js', 0.5, 3.0
 WHERE NOT EXISTS (SELECT 1 FROM monster_template WHERE code = 'demon_king');

-- Người hạ đòn cuối vẫn nhận rơi đồ thường (drop_table.code = mã quái); thưởng xếp hạng đi thư (bảng dưới).
INSERT INTO drop_table (code) SELECT 'demon_king' WHERE NOT EXISTS (SELECT 1 FROM drop_table WHERE code = 'demon_king');
INSERT INTO drop_table_entry (drop_table_id, item_template_id, drop_chance_percent, min_quantity, max_quantity)
SELECT dt.id, it.id, 100, 3, 5
  FROM drop_table dt CROSS JOIN item_template it
 WHERE dt.code = 'demon_king' AND it.code = 'demon_core'
   AND NOT EXISTS (SELECT 1 FROM drop_table_entry e WHERE e.drop_table_id = dt.id AND e.item_template_id = it.id);
UPDATE monster_template SET drop_table_id = (SELECT id FROM drop_table WHERE code = 'demon_king')
 WHERE code = 'demon_king';

CREATE TABLE world_boss_schedule (
    monster_code     VARCHAR(32) PRIMARY KEY,
    spawn_x          REAL        NOT NULL,
    spawn_y          REAL        NOT NULL CHECK (spawn_y = 0),
    interval_minutes INT         NOT NULL CHECK (interval_minutes >= 1)
);

-- x = -30: giữa sân tông môn (vùng an toàn tới -15) và điểm spawn -47, cách cả hai quá tầm aggro (luật V29).
INSERT INTO world_boss_schedule (monster_code, spawn_x, spawn_y, interval_minutes)
VALUES ('demon_king', -30, 0, 60)
ON CONFLICT (monster_code) DO NOTHING;

CREATE TABLE world_boss_reward (
    monster_code VARCHAR(32) NOT NULL REFERENCES world_boss_schedule (monster_code) ON DELETE CASCADE,
    rank_from    INT         NOT NULL CHECK (rank_from >= 1),
    rank_to      INT         NOT NULL,
    gold         BIGINT      NOT NULL CHECK (gold >= 0),
    item_template_id BIGINT  REFERENCES item_template (id),
    quantity     INT         NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    CHECK (rank_to >= rank_from),
    PRIMARY KEY (monster_code, rank_from)
);

-- Bậc ĐẦU TIÊN (theo rank_from) phủ hạng là bậc được trao; bậc cuối 1..1000000 = phần "tham gia".
INSERT INTO world_boss_reward (monster_code, rank_from, rank_to, gold, item_template_id, quantity)
SELECT 'demon_king', v.rank_from, v.rank_to, v.gold, it.id, v.qty
  FROM (VALUES (1,  1,       5000, 'spirit_beast_pill', 1),
               (2,  3,       3000, 'demon_core',        5),
               (4,  10,      1500, 'demon_core',        2),
               (11, 1000000, 300,  NULL,                0)) AS v (rank_from, rank_to, gold, item_code, qty)
  LEFT JOIN item_template it ON it.code = v.item_code
ON CONFLICT DO NOTHING;

CREATE TABLE world_boss_damage (
    character_id BIGINT PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    total_damage BIGINT NOT NULL CHECK (total_damage >= 0)
);

CREATE INDEX idx_world_boss_damage_total ON world_boss_damage (total_damage DESC, character_id);
