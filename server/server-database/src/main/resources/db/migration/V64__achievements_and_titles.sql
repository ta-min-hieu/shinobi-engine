-- ADR-062 — thành tựu + danh hiệu. Bảng định nghĩa là THAM SỐ (Điều 27): thêm thành tựu = thêm dòng.
-- name_key/title_key là khoá i18n (cổng verifyContentKeys kiểm chúng có trong cả hai bundle).

CREATE TABLE achievement (
    code        VARCHAR(32) PRIMARY KEY,
    kind        VARCHAR(16) NOT NULL
                CHECK (kind IN ('KILL_MONSTERS', 'CLAIM_QUESTS', 'WIN_DUELS', 'CHECK_IN', 'REACH_LEVEL')),
    target      BIGINT      NOT NULL CHECK (target >= 1),
    name_key    VARCHAR(64) NOT NULL,
    title_key   VARCHAR(64) NOT NULL,
    sort_order  INT         NOT NULL
);

INSERT INTO achievement (code, kind, target, name_key, title_key, sort_order) VALUES
    ('slayer_10',   'KILL_MONSTERS', 10,   'achievement.slayer_10.name',   'title.slayer_10',   10),
    ('slayer_100',  'KILL_MONSTERS', 100,  'achievement.slayer_100.name',  'title.slayer_100',  20),
    ('slayer_1000', 'KILL_MONSTERS', 1000, 'achievement.slayer_1000.name', 'title.slayer_1000', 30),
    ('level_10',    'REACH_LEVEL',   10,   'achievement.level_10.name',    'title.level_10',    40),
    ('level_30',    'REACH_LEVEL',   30,   'achievement.level_30.name',    'title.level_30',    50),
    ('quests_10',   'CLAIM_QUESTS',  10,   'achievement.quests_10.name',   'title.quests_10',   60),
    ('duel_1',      'WIN_DUELS',     1,    'achievement.duel_1.name',      'title.duel_1',      70),
    ('duel_10',     'WIN_DUELS',     10,   'achievement.duel_10.name',     'title.duel_10',     80),
    ('checkin_7',   'CHECK_IN',      7,    'achievement.checkin_7.name',   'title.checkin_7',   90);

-- Bộ đếm cộng dồn mỗi loại (REACH_LEVEL không có dòng ở đây — so thẳng với cấp).
CREATE TABLE character_achievement_counter (
    character_id  BIGINT      NOT NULL REFERENCES character (id),
    kind          VARCHAR(16) NOT NULL CHECK (kind IN ('KILL_MONSTERS', 'CLAIM_QUESTS', 'WIN_DUELS', 'CHECK_IN')),
    value         BIGINT      NOT NULL CHECK (value >= 0),
    PRIMARY KEY (character_id, kind)
);

CREATE TABLE character_achievement (
    character_id      BIGINT      NOT NULL REFERENCES character (id),
    achievement_code  VARCHAR(32) NOT NULL REFERENCES achievement (code),
    unlocked_at       TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (character_id, achievement_code)
);

-- Danh hiệu đang đeo: tối đa một; FK tới thành tựu ĐÃ MỞ của chính nhân vật đó.
CREATE TABLE character_title (
    character_id      BIGINT      PRIMARY KEY,
    achievement_code  VARCHAR(32) NOT NULL,
    FOREIGN KEY (character_id, achievement_code)
        REFERENCES character_achievement (character_id, achievement_code)
);
