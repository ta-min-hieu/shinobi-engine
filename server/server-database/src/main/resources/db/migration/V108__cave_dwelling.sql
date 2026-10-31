-- V108 — động phủ (ADR-129, lô 24). Mỗi nhân vật một động phủ (dựng lười ở lần xem đầu): tiểu linh mạch cấp 1..5 sinh
-- linh thạch theo giờ thật tính từ last_collected_at (luật ở CaveRules); bốn món bày trí mua một lần, mỗi món cộng % sản
-- lượng. Mọi con số là ĐỀ XUẤT CÂN BẰNG.
CREATE TABLE cave_dwelling (
    character_id      BIGINT      PRIMARY KEY REFERENCES character (id),
    vein_level        INT         NOT NULL DEFAULT 1 CHECK (vein_level BETWEEN 1 AND 5),
    last_collected_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE cave_decoration_template (
    code          VARCHAR(32) PRIMARY KEY,
    name_key      VARCHAR(64) NOT NULL,
    gold_cost     BIGINT      NOT NULL CHECK (gold_cost > 0),
    bonus_percent INT         NOT NULL CHECK (bonus_percent BETWEEN 1 AND 100),
    sort_order    INT         NOT NULL
);

INSERT INTO cave_decoration_template (code, name_key, gold_cost, bonus_percent, sort_order) VALUES
    ('spirit_pine',  'cave.decoration.spirit_pine',  1500, 10, 1),
    ('jade_lantern', 'cave.decoration.jade_lantern', 2000, 10, 2),
    ('cloud_screen', 'cave.decoration.cloud_screen', 2500, 10, 3),
    ('lotus_pond',   'cave.decoration.lotus_pond',   3000, 10, 4);

CREATE TABLE cave_decoration (
    character_id    BIGINT      NOT NULL REFERENCES character (id),
    decoration_code VARCHAR(32) NOT NULL REFERENCES cave_decoration_template (code),
    placed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (character_id, decoration_code)
);
