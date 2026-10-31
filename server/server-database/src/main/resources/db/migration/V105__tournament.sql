-- V105 — luận võ đại hội (ADR-125, lô 23). Giải tuần (mặc định Chủ nhật 20:00 giờ VN), đăng ký trong ngày, loại
-- trực tiếp qua World đấu trường (ADR-051). Vô địch: thư linh thạch + thành tựu "tournament_1" (danh hiệu, ADR-062).
CREATE TABLE tournament (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    start_at    TIMESTAMPTZ NOT NULL UNIQUE,
    status      VARCHAR(8)  NOT NULL CHECK (status IN ('OPEN', 'RUNNING', 'FINISHED')),
    round       INT         NOT NULL DEFAULT 0 CHECK (round >= 0),
    champion_id BIGINT      REFERENCES character (id)
);

CREATE TABLE tournament_entry (
    tournament_id    BIGINT      NOT NULL REFERENCES tournament (id),
    character_id     BIGINT      NOT NULL REFERENCES character (id),
    registered_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    eliminated_round INT,
    PRIMARY KEY (tournament_id, character_id)
);

CREATE TABLE tournament_match (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tournament_id BIGINT      NOT NULL REFERENCES tournament (id),
    round         INT         NOT NULL CHECK (round >= 1),
    a_id          BIGINT      NOT NULL REFERENCES character (id),
    b_id          BIGINT      REFERENCES character (id),
    winner_id     BIGINT      REFERENCES character (id),
    started_at    TIMESTAMPTZ
);

CREATE INDEX tournament_match_by_round ON tournament_match (tournament_id, round);

ALTER TABLE achievement DROP CONSTRAINT achievement_kind_check;
ALTER TABLE achievement ADD CONSTRAINT achievement_kind_check
    CHECK (kind IN ('KILL_MONSTERS', 'CLAIM_QUESTS', 'WIN_DUELS', 'CHECK_IN', 'REACH_LEVEL', 'WIN_TOURNAMENTS'));
ALTER TABLE character_achievement_counter DROP CONSTRAINT character_achievement_counter_kind_check;
ALTER TABLE character_achievement_counter ADD CONSTRAINT character_achievement_counter_kind_check
    CHECK (kind IN ('KILL_MONSTERS', 'CLAIM_QUESTS', 'WIN_DUELS', 'CHECK_IN', 'WIN_TOURNAMENTS'));

INSERT INTO achievement (code, kind, target, name_key, title_key, sort_order)
VALUES ('tournament_1', 'WIN_TOURNAMENTS', 1, 'achievement.tournament_1.name', 'title.tournament_1', 100)
ON CONFLICT DO NOTHING;
