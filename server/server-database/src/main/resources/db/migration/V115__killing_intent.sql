-- ADR-142 (2026-11-01, lô 26) — sát khí: hạ người chơi trong dải PvP tự do cộng điểm, tự tan theo thời gian (tính
-- lười từ updated_at, không job nào ghi lại). Một dòng mỗi nhân vật từng có sát khí; không dòng = 0 điểm.
CREATE TABLE character_killing_intent (
    character_id BIGINT      PRIMARY KEY REFERENCES character (id) ON DELETE CASCADE,
    points       INT         NOT NULL CHECK (points BETWEEN 0 AND 10),
    updated_at   TIMESTAMPTZ NOT NULL
);
