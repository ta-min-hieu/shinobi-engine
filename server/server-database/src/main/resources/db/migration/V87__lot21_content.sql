-- Lô 21 phần 3 (2026-10-24): lượt Bí Cảnh + thưởng lần đầu (ADR-099), truyền tống phù (ADR-100), bảng truy nã (ADR-101).

-- ADR-099 — lượt đã dùng mỗi ngày (giờ Việt Nam) mỗi Bí Cảnh.
CREATE TABLE dungeon_entry (
    character_id BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    dungeon_code VARCHAR(32) NOT NULL,
    day          DATE        NOT NULL,
    entries      INT         NOT NULL DEFAULT 0 CHECK (entries >= 0),
    PRIMARY KEY (character_id, dungeon_code, day)
);

-- Lần đầu hạ boss Bí Cảnh — khoá chính chặn thưởng lần hai.
CREATE TABLE dungeon_first_clear (
    character_id BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    dungeon_code VARCHAR(32) NOT NULL,
    cleared_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (character_id, dungeon_code)
);

-- ADR-100 — điểm truyền tống (tham số). Trùng chỗ các trạm (safe_zone waypost_*) dọc trục chính.
CREATE TABLE waypoint (
    code        VARCHAR(32) PRIMARY KEY,
    name_key    VARCHAR(64) NOT NULL,
    x           REAL        NOT NULL,
    y           REAL        NOT NULL DEFAULT 0,
    sort_order  INT         NOT NULL,
    always_open BOOLEAN     NOT NULL DEFAULT FALSE
);

INSERT INTO waypoint (code, name_key, x, y, sort_order, always_open)
VALUES ('sect_gate', 'waypoint.sect_gate.name', -5, 0, 0, TRUE),
       ('spirit_vein', 'waypoint.spirit_vein.name', 83, 0, 1, FALSE),
       ('east_ridge', 'waypoint.east_ridge.name', 107, 0, 2, FALSE),
       ('dead_vein', 'waypoint.dead_vein.name', 127, 0, 3, FALSE),
       ('ringleader_ridge', 'waypoint.ringleader_ridge.name', 144, 0, 4, FALSE),
       ('wasteland_gate', 'waypoint.wasteland_gate.name', 176, 0, 5, FALSE),
       ('trial_approach', 'waypoint.trial_approach.name', 208, 0, 6, FALSE)
ON CONFLICT DO NOTHING;

CREATE TABLE character_waypoint (
    character_id  BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    waypoint_code VARCHAR(32) NOT NULL REFERENCES waypoint (code),
    unlocked_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (character_id, waypoint_code)
);

INSERT INTO item_template (code, name_key, max_stack)
SELECT 'teleport_talisman', 'item.teleport_talisman.name', 99
 WHERE NOT EXISTS (SELECT 1 FROM item_template WHERE code = 'teleport_talisman');

INSERT INTO shop_item (item_template_id, buy_price, sell_price)
SELECT id, 500, 50 FROM item_template WHERE code = 'teleport_talisman'
   AND NOT EXISTS (SELECT 1 FROM shop_item si JOIN item_template it ON it.id = si.item_template_id
                    WHERE it.code = 'teleport_talisman');

-- ADR-101 — mẫu truy nã (tham số). Bảng mỗi ngày chọn 3 mẫu tất định theo ngày.
CREATE TABLE bounty_template (
    code         VARCHAR(32) PRIMARY KEY,
    monster_code VARCHAR(32) NOT NULL,
    kill_count   INT         NOT NULL CHECK (kill_count > 0),
    reward_gold  BIGINT      NOT NULL CHECK (reward_gold >= 0),
    reward_exp   BIGINT      NOT NULL CHECK (reward_exp >= 0),
    min_level    INT         NOT NULL DEFAULT 1 CHECK (min_level >= 1)
);

INSERT INTO bounty_template (code, monster_code, kill_count, reward_gold, reward_exp, min_level)
VALUES ('bandit_sweep', 'wandering_bandit', 8, 600, 400, 1),
       ('ogre_hunt', 'stone_ogre', 5, 900, 700, 8),
       ('scout_purge', 'blood_sand_scout', 6, 1200, 900, 12),
       ('raider_rout', 'vein_raider', 6, 1600, 1400, 18),
       ('acolyte_cull', 'blood_sand_acolyte', 5, 2200, 2000, 24),
       ('beast_slaying', 'wasteland_beast', 3, 3000, 2800, 28)
ON CONFLICT DO NOTHING;

-- Truy nã của người chơi: một dòng mỗi lần nhận. Một truy nã ACTIVE mỗi người (chỉ mục duy nhất một phần).
CREATE TABLE character_bounty (
    id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    character_id BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    day          DATE        NOT NULL,
    bounty_code  VARCHAR(32) NOT NULL REFERENCES bounty_template (code),
    monster_code VARCHAR(32) NOT NULL,
    progress     INT         NOT NULL DEFAULT 0 CHECK (progress >= 0),
    target       INT         NOT NULL CHECK (target > 0),
    status       VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'ABANDONED'))
);

CREATE UNIQUE INDEX uq_character_bounty_active ON character_bounty (character_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_character_bounty_day ON character_bounty (character_id, day);
