-- ADR-068 — cấp bang + cống hiến; ADR-069 — nhiệm vụ bang tuần. Chỉ THÊM cột có DEFAULT và một bảng mới.
ALTER TABLE guild ADD COLUMN level INT NOT NULL DEFAULT 1 CHECK (level BETWEEN 1 AND 5);
ALTER TABLE guild ADD COLUMN exp BIGINT NOT NULL DEFAULT 0 CHECK (exp >= 0);
ALTER TABLE guild_member ADD COLUMN contribution BIGINT NOT NULL DEFAULT 0 CHECK (contribution >= 0);

-- Một dòng mỗi bang mỗi tuần (thứ Hai giờ Việt Nam). completed_at chốt đúng một lần.
CREATE TABLE guild_weekly_progress (
    guild_id      BIGINT      NOT NULL REFERENCES guild (id) ON DELETE CASCADE,
    week_start    DATE        NOT NULL,
    kills         INT         NOT NULL CHECK (kills >= 0),
    completed_at  TIMESTAMPTZ,
    PRIMARY KEY (guild_id, week_start)
);

CREATE INDEX idx_guild_exp ON guild (exp DESC, id);
