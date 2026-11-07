-- ADR-186 (2026-11-07) — Điểm danh bang. Một lần mỗi NHÂN VẬT mỗi ngày (giờ Việt Nam) — đổi bang trong ngày không điểm
-- danh lại được. guild_id chỉ để đếm "hôm nay bang mình bao nhiêu người điểm danh".
CREATE TABLE guild_checkin (
    character_id BIGINT      NOT NULL REFERENCES character (id) ON DELETE CASCADE,
    day          DATE        NOT NULL,
    guild_id     BIGINT      NOT NULL REFERENCES guild (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (character_id, day)
);

CREATE INDEX idx_guild_checkin_guild_day ON guild_checkin (guild_id, day);
